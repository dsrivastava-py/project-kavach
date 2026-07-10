"""
Billing stub — INTENTIONALLY INCOMPLETE.

This build does NOT integrate a real payment provider. These endpoints exist
to satisfy the webhook contract (providers disable URLs that don't ack) and
to give the frontend plan metadata to display. No money moves here.

POST /api/v1/billing/webhook/{provider}
  - Logs raw payload to billing_webhook_log audit table
  - Sets subscriptions.status = 'stub_pending' if family_id present in payload
  - Returns 200 with explicit stub message

GET /api/v1/billing/plans
  - Returns static plan metadata (three tiers from the product doc)
  - No payment flow attached

DO NOT add signature verification, subscription activation, or real charge
logic here. This is explicitly a stub for Phase 4 demo purposes only.
"""
from __future__ import annotations

import uuid
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, Request
from pydantic import BaseModel
from sqlalchemy import select, text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.db import get_session
from app.models.subscription import Subscription

router = APIRouter(prefix="/billing", tags=["billing"])


# ---------------------------------------------------------------------------
# Static plan config — no payment flow, display-only
# ---------------------------------------------------------------------------

_PLANS = [
    {
        "id": "free",
        "name": "Free",
        "price_inr": 0,
        "billing_cycle": None,
        "features": [
            "WhatsApp scam detection (basic)",
            "1 elder, 1 guardian",
            "Alert history (7 days)",
        ],
    },
    {
        "id": "family_99",
        "name": "Family (₹99/month)",
        "price_inr": 99,
        "billing_cycle": "monthly",
        "features": [
            "WhatsApp scam detection (advanced + voice)",
            "1 elder, up to 2 guardians",
            "Android signal monitoring",
            "Deep-check audio analysis (5 sessions/month)",
            "Alert history (30 days)",
        ],
    },
    {
        "id": "family_199",
        "name": "Family Pro (₹199/month)",
        "price_inr": 199,
        "billing_cycle": "monthly",
        "features": [
            "All Family features",
            "Unlimited deep-check sessions",
            "Fraud graph access",
            "Evidence PDF with Section 65B certificate",
            "Alert history (1 year)",
            "Priority support",
        ],
    },
]


# ---------------------------------------------------------------------------
# Schemas
# ---------------------------------------------------------------------------

class StubWebhookResponse(BaseModel):
    status: str
    message: str


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------

@router.post("/webhook/{provider}", response_model=StubWebhookResponse)
async def billing_webhook(
    provider: str,
    request: Request,
    session: AsyncSession = Depends(get_session),
) -> StubWebhookResponse:
    """
    Stub billing webhook handler.

    Logs the raw payload and returns 200 so the provider does not deactivate
    the URL. No real payment processing occurs.
    """
    try:
        payload = await request.json()
    except Exception:
        payload = {}

    received_at = datetime.now(timezone.utc)

    # Log to audit table
    await session.execute(
        text(
            "INSERT INTO billing_webhook_log (id, provider, payload, received_at) "
            "VALUES (:id, :provider, :payload::jsonb, :received_at)"
        ),
        {
            "id": str(uuid.uuid4()),
            "provider": provider[:64],  # cap provider name length
            "payload": __import__("json").dumps(payload),
            "received_at": received_at,
        },
    )

    # If family_id present, mark subscription stub_pending (best-effort)
    family_id_raw = payload.get("family_id") or payload.get("metadata", {}).get("family_id")
    if family_id_raw:
        try:
            family_id = uuid.UUID(str(family_id_raw))
            result = await session.execute(
                select(Subscription).where(Subscription.family_id == family_id)
            )
            sub = result.scalar_one_or_none()
            if sub is not None:
                sub.status = "stub_pending"
                session.add(sub)
        except (ValueError, TypeError):
            pass  # invalid UUID — ignore silently

    await session.commit()

    return StubWebhookResponse(
        status="stub",
        message="billing not live in this build",
    )


@router.get("/plans")
async def get_plans() -> dict:
    """Return static plan tier metadata for frontend display."""
    return {"plans": _PLANS}
