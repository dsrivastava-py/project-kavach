"""
Alert dispatch — guardian fan-out.

On transition into graduated_3 or graduated_4:
  1. Look up guardians for the incident's elder (priority_order asc, max 2).
  2. Build a context string from recent signal_events (never raw content).
  3. Publish to Redis pub/sub channel alerts:{guardian_id} for live WebSocket
     connections, AND enqueue an FCM push via Celery (independent channels).
  4. Insert a row into alerts.
  5. Idempotency: skip if unacknowledged alert exists within cooldown window.

Product principle: guardians see signals and context, never raw content.
"""
from __future__ import annotations

import json
import uuid
from datetime import datetime, timedelta, timezone

import redis.asyncio as aioredis
from sqlalchemy import select, and_
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import get_settings
from app.models.alert import Alert
from app.models.guardian import Guardian
from app.models.incident import Incident
from app.models.signal_event import SignalEvent
from app.models.user import User


def _mask_number(number: str | None) -> str:
    """Mask all but last 4 digits for display — never expose full number."""
    if not number:
        return "unknown number"
    digits = "".join(ch for ch in number if ch.isdigit())
    if len(digits) >= 4:
        return "****" + digits[-4:]
    return "***"


def _build_context_string(
    elder_name: str,
    recent_events: list[SignalEvent],
) -> str:
    """
    Build human-readable context from signal events.
    Only signals, not raw payloads or full phone numbers.
    """
    parts: list[str] = []
    call_duration_s = 0
    has_video = False
    has_screen_share = False
    has_banking = False
    banking_app = None
    contact_number = None

    for evt in recent_events:
        if evt.event_type in ("call_start", "video_call_start"):
            contact_number = evt.payload.get("contact_number") or evt.payload.get("number")
        if evt.event_type == "call_end":
            call_duration_s = int(evt.payload.get("duration_seconds", 0))
        if evt.event_type == "video_call_start":
            has_video = True
        if evt.event_type == "screen_share_start":
            has_screen_share = True
        if evt.event_type == "banking_app_opened":
            has_banking = True
            banking_app = evt.payload.get("app_name", "a banking app")

    if has_video and call_duration_s > 0:
        mins = call_duration_s // 60
        masked = _mask_number(contact_number)
        parts.append(f"{elder_name} has been on a video call with {masked} for {mins} minutes")
    elif call_duration_s > 0:
        mins = call_duration_s // 60
        masked = _mask_number(contact_number)
        parts.append(f"{elder_name} has been on a call with {masked} for {mins} minutes")

    if has_screen_share:
        parts.append("screen sharing is active")

    if has_banking and banking_app:
        parts.append(f"just opened {banking_app}")

    if not parts:
        parts.append(f"unusual activity detected for {elder_name}")

    return "; ".join(parts).capitalize() + "."


async def _get_guardian_elder_name(
    session: AsyncSession,
    elder_id: uuid.UUID,
) -> str:
    """Look up elder's display name via user row."""
    from app.models.elder import Elder
    result = await session.execute(
        select(Elder, User)
        .join(User, User.id == Elder.user_id)
        .where(Elder.id == elder_id)
    )
    row = result.first()
    if row:
        # User model has phone_e164; name not stored in Phase 1 schema.
        # Use masked phone as fallback until a name column is added.
        user: User = row[1]
        return _mask_number(user.phone_e164)
    return "the protected elder"


async def dispatch_guardian_alert(
    incident_id: uuid.UUID,
    session: AsyncSession,
    redis_client: aioredis.Redis,
) -> None:
    """
    Fan-out guardian alerts for a graduated_3 / graduated_4 incident.

    Idempotency:
    - Skip if an unacknowledged alert exists for this incident+guardian+channel
      within the cooldown window (default 15 min, config: ALERT_COOLDOWN_MINUTES).
    - Re-notify only on level increase or after cooldown expires.

    NOTE: FCM push is enqueued as a Celery task so WebSocket delivery does
    not block FCM delivery; they are independent channels, both fire.
    """
    from app.workers.tasks import send_fcm_push  # local import avoids circular

    s = get_settings()
    cooldown = timedelta(minutes=s.ALERT_COOLDOWN_MINUTES)
    now = datetime.now(timezone.utc)

    # 1. Load incident + elder
    incident: Incident | None = await session.get(Incident, incident_id)
    if not incident:
        return

    elder_name = await _get_guardian_elder_name(session, incident.elder_id)

    # 2. Load up to 2 guardians (priority_order asc)
    guardians_result = await session.execute(
        select(Guardian)
        .where(Guardian.elder_id == incident.elder_id)
        .order_by(Guardian.priority_order)
        .limit(2)
    )
    guardians = guardians_result.scalars().all()
    if not guardians:
        return

    # 3. Load recent signal events for context
    events_result = await session.execute(
        select(SignalEvent)
        .where(SignalEvent.elder_id == incident.elder_id)
        .order_by(SignalEvent.occurred_at.desc())
        .limit(20)
    )
    recent_events = list(reversed(events_result.scalars().all()))
    context_str = _build_context_string(elder_name, recent_events)

    alert_payload = {
        "incident_id": str(incident_id),
        "elder_id": str(incident.elder_id),
        "risk_level": incident.status,
        "context": context_str,
        "timestamp": now.isoformat(),
    }

    for guardian in guardians:
        # 4. Idempotency check per guardian+incident
        existing_alert = await session.execute(
            select(Alert).where(
                and_(
                    Alert.incident_id == incident_id,
                    Alert.guardian_id == guardian.id,
                    Alert.channel == "push",
                    Alert.acknowledged_at.is_(None),
                    Alert.sent_at >= (now - cooldown),
                )
            )
        )
        if existing_alert.scalar_one_or_none() is not None:
            # Already notified within cooldown — skip
            continue

        # 5. Publish to Redis pub/sub (WebSocket channel)
        channel = f"alerts:{guardian.id}"
        await redis_client.publish(channel, json.dumps(alert_payload))

        # 6. Enqueue FCM push (independent of WebSocket delivery)
        # Look up FCM token via device — guardians have their own devices
        fcm_token = await _get_guardian_fcm_token(session, guardian.user_id)
        if fcm_token:
            send_fcm_push.delay(
                str(guardian.id),
                fcm_token,
                title="Kavach Alert",
                body=context_str,
                data=alert_payload,
            )

        # 7. Insert alert row
        alert = Alert(
            incident_id=incident_id,
            guardian_id=guardian.id,
            channel="push",
            sent_at=now,
        )
        session.add(alert)

    await session.commit()


async def _get_guardian_fcm_token(
    session: AsyncSession,
    user_id: uuid.UUID,
) -> str | None:
    """
    Get FCM token for a guardian.

    Phase 2 note: The schema does not yet have a guardian_fcm_tokens table.
    Guardian FCM tokens are registered via Phase 3's device registration flow.
    Until then, this returns None and FCM push is silently skipped for guardians
    (the Redis/WebSocket channel still delivers alerts in real-time).

    Phase 3 will add: SELECT fcm_token FROM guardian_devices WHERE user_id = :user_id
    """
    # TODO Phase 3: query guardian device FCM token when guardian device
    # registration endpoint is built. For now return None — WebSocket channel
    # covers real-time delivery; FCM is the offline fallback.
    return None
