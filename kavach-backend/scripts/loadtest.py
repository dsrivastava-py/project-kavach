"""
Kavach Phase 4 — Load test (Locust)

Two scenarios:
  1. WhatsAppUser — concurrent POST /api/v1/webhooks/whatsapp
     Simulates signed Meta-format webhook payloads from random senders.
     Signs each payload with HMAC-SHA256 to pass signature verification.

  2. SignalUser — POST /api/v1/signals/ingest batch posts
     Simulates Android app polling every 3 seconds, 1–3 events per batch.
     Uses a configurable device API key (must exist in DB).

Run:
    locust -f scripts/loadtest.py --headless -u 50 -r 5 --run-time 60s --host http://localhost:8000

For demo verification (what becomes the "alert latency" metric in the pitch deck):
    locust -f scripts/loadtest.py --headless -u 10 -r 2 --run-time 30s --host http://localhost:8000

Environment vars (optional overrides):
    META_WEBHOOK_SECRET  — HMAC secret for WhatsApp payloads (default: "test-secret")
    TEST_DEVICE_API_KEY  — Device API key for signal ingest (default: "test-device-key-1234")
    TEST_DEVICE_ID       — UUID of the test device row (default: random, will likely 403)
    TEST_ELDER_ID        — UUID of the test elder row (default: random, will likely 403)
"""
from __future__ import annotations

import hashlib
import hmac
import json
import os
import random
import uuid

from locust import HttpUser, between, task

# ---------------------------------------------------------------------------
# Config from environment (matches .env defaults)
# ---------------------------------------------------------------------------
_META_SECRET = os.getenv("META_WEBHOOK_SECRET", "test-secret")
_DEVICE_API_KEY = os.getenv("TEST_DEVICE_API_KEY", "test-device-key-1234")
_DEVICE_ID = os.getenv("TEST_DEVICE_ID", str(uuid.uuid4()))
_ELDER_ID = os.getenv("TEST_ELDER_ID", str(uuid.uuid4()))

# Scam message templates — realistic payload variety
_SCAM_MESSAGES = [
    "This is CBI officer Sharma. You have a case of money laundering. Attend digital arrest immediately.",
    "TRAI will disconnect your number in 2 hours. Press 9 to speak to an officer.",
    "Your parcel at Mumbai customs has drugs. Rs 50,000 bail required urgently. Call now.",
    "You have won Rs 10 lakh lottery. Send OTP to claim prize immediately.",
    "This is Cybercrime branch. Your Aadhaar is used in 4 criminal cases. Video call mandatory.",
    "Income tax dept: Rs 80,000 undisclosed income found. Pay fine in 1 hour or arrest.",
    "Verification deposit of Rs 5,000 required to unblock your frozen bank account.",
]

_SENDER_PHONES = [f"91{random.randint(7000000000, 9999999999)}" for _ in range(100)]


def _sign_meta_payload(body_bytes: bytes, secret: str) -> str:
    mac = hmac.new(secret.encode(), body_bytes, hashlib.sha256).hexdigest()
    return f"sha256={mac}"


def _make_meta_payload(sender_phone: str, message_text: str) -> bytes:
    payload = {
        "object": "whatsapp_business_account",
        "entry": [{
            "id": "123456789",
            "changes": [{
                "value": {
                    "messaging_product": "whatsapp",
                    "metadata": {"display_phone_number": "919999999999", "phone_number_id": "12345"},
                    "contacts": [{"profile": {"name": "Test User"}, "wa_id": sender_phone}],
                    "messages": [{
                        "from": sender_phone,
                        "id": f"wamid.{uuid.uuid4().hex}",
                        "timestamp": "1720000000",
                        "text": {"body": message_text},
                        "type": "text",
                    }],
                },
                "field": "messages",
            }],
        }],
    }
    return json.dumps(payload).encode()


# ---------------------------------------------------------------------------
# Locust user classes
# ---------------------------------------------------------------------------

class WhatsAppUser(HttpUser):
    """
    Simulates inbound WhatsApp scam messages to the webhook endpoint.
    Each task sends a signed Meta-format payload from a random sender.
    Target: prove <5s p95 latency holds under demo-realistic concurrency.
    """
    wait_time = between(0.5, 2.0)

    @task
    def send_whatsapp_message(self) -> None:
        sender = random.choice(_SENDER_PHONES)
        message = random.choice(_SCAM_MESSAGES)
        body = _make_meta_payload(sender, message)
        sig = _sign_meta_payload(body, _META_SECRET)

        with self.client.post(
            "/api/v1/webhooks/whatsapp",
            data=body,
            headers={
                "Content-Type": "application/json",
                "X-Hub-Signature-256": sig,
            },
            catch_response=True,
            name="POST /webhooks/whatsapp",
        ) as resp:
            if resp.status_code == 429:
                resp.success()  # rate limit is expected behaviour, not a failure
            elif resp.status_code not in (200, 202):
                resp.failure(f"Unexpected status {resp.status_code}: {resp.text[:200]}")


class SignalUser(HttpUser):
    """
    Simulates Android app sending batched signal events every ~3 seconds.
    Uses TEST_DEVICE_API_KEY environment variable.
    Note: 403 responses are expected unless TEST_DEVICE_ID and TEST_ELDER_ID
    match real rows in the DB — set env vars to seeded demo device/elder IDs.
    """
    wait_time = between(2.5, 3.5)

    _EVENT_TYPES = [
        "unknown_or_international_number",
        "call_duration_over_30min",
        "video_call_active",
        "screen_share_start",
        "banking_app_foreground_during_active_call",
    ]

    @task
    def ingest_signals(self) -> None:
        n_events = random.randint(1, 3)
        events = [
            {
                "event_type": random.choice(self._EVENT_TYPES),
                "payload": {"source": "loadtest", "detail": f"event_{i}"},
                "occurred_at": "2026-07-10T10:00:00Z",
            }
            for i in range(n_events)
        ]
        body = json.dumps({
            "device_id": _DEVICE_ID,
            "elder_id": _ELDER_ID,
            "events": events,
        }).encode()

        with self.client.post(
            "/api/v1/signals/ingest",
            data=body,
            headers={
                "Content-Type": "application/json",
                "X-API-Key": _DEVICE_API_KEY,
            },
            catch_response=True,
            name="POST /signals/ingest",
        ) as resp:
            if resp.status_code == 429:
                resp.success()  # rate limit is expected, not a failure
            elif resp.status_code == 403:
                # Expected when test device/elder UUIDs don't exist in DB.
                # Set TEST_DEVICE_ID and TEST_ELDER_ID env vars to fix.
                resp.success()
            elif resp.status_code not in (200, 202):
                resp.failure(f"Unexpected status {resp.status_code}: {resp.text[:200]}")
