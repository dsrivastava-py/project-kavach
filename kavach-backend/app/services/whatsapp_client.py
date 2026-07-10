"""
WhatsApp client adapter.
Business logic depends only on WhatsAppClient protocol — never imports adapters directly.
"""
from __future__ import annotations

import hashlib
import hmac
from typing import Protocol

import httpx

from app.core.config import get_settings
from app.core.logging import log


class WhatsAppClient(Protocol):
    async def send_text(self, to: str, body: str) -> None: ...
    async def download_media(self, media_id: str) -> bytes: ...


class MetaCloudAdapter:
    """Primary. Uses META_WABA_TOKEN / META_PHONE_NUMBER_ID."""

    async def send_text(self, to: str, body: str) -> None:
        s = get_settings()
        url = f"https://graph.facebook.com/v20.0/{s.META_PHONE_NUMBER_ID}/messages"
        payload = {
            "messaging_product": "whatsapp",
            "to": to,
            "type": "text",
            "text": {"body": body},
        }
        async with httpx.AsyncClient(timeout=10) as client:
            resp = await client.post(url, json=payload, headers={"Authorization": f"Bearer {s.META_WABA_TOKEN}"})
            resp.raise_for_status()

    async def download_media(self, media_id: str) -> bytes:
        s = get_settings()
        url = f"https://graph.facebook.com/v20.0/{media_id}"
        async with httpx.AsyncClient(timeout=30) as client:
            info = await client.get(url, headers={"Authorization": f"Bearer {s.META_WABA_TOKEN}"})
            info.raise_for_status()
            dl_url = info.json()["url"]
            media = await client.get(dl_url, headers={"Authorization": f"Bearer {s.META_WABA_TOKEN}"})
            media.raise_for_status()
            return media.content


class TwilioSandboxAdapter:
    """Dev/demo fallback. Uses TWILIO_* config."""

    async def send_text(self, to: str, body: str) -> None:
        s = get_settings()
        url = f"https://api.twilio.com/2010-04-01/Accounts/{s.TWILIO_ACCOUNT_SID}/Messages.json"
        data = {
            "From": f"whatsapp:{s.TWILIO_WHATSAPP_NUMBER}",
            "To": f"whatsapp:{to}",
            "Body": body,
        }
        async with httpx.AsyncClient(timeout=10) as client:
            resp = await client.post(
                url, data=data,
                auth=(s.TWILIO_ACCOUNT_SID, s.TWILIO_AUTH_TOKEN),
            )
            if resp.status_code not in (200, 201):
                log.error("twilio_send_failed", status=resp.status_code, body=resp.text[:200])
            resp.raise_for_status()

    async def download_media(self, media_id: str) -> bytes:
        s = get_settings()
        async with httpx.AsyncClient(timeout=30) as client:
            resp = await client.get(media_id, auth=(s.TWILIO_ACCOUNT_SID, s.TWILIO_AUTH_TOKEN))
            resp.raise_for_status()
            return resp.content


def get_whatsapp_client() -> WhatsAppClient:
    """Reads WHATSAPP_PROVIDER from config, returns right adapter."""
    provider = get_settings().WHATSAPP_PROVIDER
    if provider == "meta":
        return MetaCloudAdapter()
    return TwilioSandboxAdapter()
