import hashlib
import hmac
import time
from typing import Any

from fastapi import APIRouter, Depends, Header, HTTPException, Query, Request, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import get_settings
from app.core.db import get_session
from app.core.logging import log
from app.services.classifier import classify_message
from app.services.whatsapp_client import get_whatsapp_client

router = APIRouter(prefix="/webhooks/whatsapp", tags=["whatsapp"])


def _verify_meta_signature(body: bytes, sig_header: str | None) -> bool:
    secret = get_settings().META_WEBHOOK_SECRET
    if not secret or not sig_header:
        return False
    expected = "sha256=" + hmac.new(secret.encode(), body, hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, sig_header)


def _verify_twilio_signature(body: bytes, sig_header: str | None) -> bool:
    # Twilio signature validation requires the full URL; simplified check here.
    # In prod: use twilio.request_validator.RequestValidator
    token = get_settings().TWILIO_AUTH_TOKEN
    if not token or not sig_header:
        return False
    return True  # stub — replace with full validator in Phase 2


@router.get("")
async def verify_webhook(
    hub_mode: str = Query(None, alias="hub.mode"),
    hub_verify_token: str = Query(None, alias="hub.verify_token"),
    hub_challenge: str = Query(None, alias="hub.challenge"),
) -> Any:
    """Meta Cloud API webhook verification handshake."""
    s = get_settings()
    if s.WHATSAPP_PROVIDER != "meta":
        return {"status": "ok"}  # Twilio doesn't use this step
    if hub_mode == "subscribe" and hub_verify_token == s.META_WEBHOOK_SECRET:
        return int(hub_challenge)
    raise HTTPException(status.HTTP_403_FORBIDDEN, "verification failed")


@router.post("")
async def receive_webhook(
    request: Request,
    x_hub_signature_256: str | None = Header(None),
    x_twilio_signature: str | None = Header(None),
    session: AsyncSession = Depends(get_session),
) -> dict:
    body = await request.body()
    s = get_settings()

    # Verify signature before processing — reject unsigned/mis-signed with 403
    if s.WHATSAPP_PROVIDER == "meta":
        if not _verify_meta_signature(body, x_hub_signature_256):
            raise HTTPException(status.HTTP_403_FORBIDDEN, "invalid signature")
    else:
        if not _verify_twilio_signature(body, x_twilio_signature):
            raise HTTPException(status.HTTP_403_FORBIDDEN, "invalid signature")

    # Parse body — Meta sends JSON, Twilio sends form-encoded
    if s.WHATSAPP_PROVIDER == "meta":
        payload = await request.json() if request.headers.get("content-type", "").startswith("application/json") else {}
    else:
        # Twilio sends application/x-www-form-urlencoded
        form_data = await request.form()
        payload = dict(form_data)
    t0 = time.monotonic()

    # Extract fields — Meta Cloud format
    sender_phone, text_body, message_type, voice_ref = _parse_inbound(payload, s.WHATSAPP_PROVIDER)

    if message_type == "image":
        log.warning("image_received_ocr_not_implemented", sender=sender_phone)
        # OCR is explicitly out of scope per architecture.md — flagging here
        wa = get_whatsapp_client()
        await wa.send_text(
            sender_phone,
            "Image received. Screenshot analysis is coming soon. "
            "Please forward the text content of the suspicious message.",
        )
        return {"status": "image_not_supported"}

    result = await classify_message(
        text=text_body,
        voice_ref=voice_ref,
        language="en",  # TODO: detect language in Phase 2
        sender_phone=sender_phone,
        session=session,
    )

    latency_ms = int((time.monotonic() - t0) * 1000)
    log.info("verdict_sent", verdict=result.verdict, latency_ms=latency_ms, sender=sender_phone)

    wa = get_whatsapp_client()
    await wa.send_text(sender_phone, result.user_message)
    return {"status": "ok", "verdict": result.verdict}


def _parse_inbound(payload: dict, provider: str) -> tuple[str, str | None, str, str | None]:
    """Return (sender_phone, text, message_type, voice_ref)."""
    if provider == "meta":
        try:
            entry = payload["entry"][0]["changes"][0]["value"]
            msg = entry["messages"][0]
            sender = msg["from"]
            mtype = msg.get("type", "text")
            text = msg.get("text", {}).get("body") if mtype == "text" else None
            voice_ref = msg.get("audio", {}).get("id") if mtype == "audio" else None
            return sender, text, mtype, voice_ref
        except (KeyError, IndexError):
            return "unknown", None, "text", None
    else:
        # Twilio form-encoded
        sender = payload.get("From", "unknown").replace("whatsapp:", "")
        text = payload.get("Body")
        num_media = int(payload.get("NumMedia", 0))
        if num_media > 0:
            media_type = payload.get("MediaContentType0", "")
            if "audio" in media_type:
                return sender, None, "voice", payload.get("MediaUrl0")
            return sender, None, "image", None
        return sender, text, "text", None
