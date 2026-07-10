"""
Signal ingestion API — Phone A → backend.

POST /api/v1/signals/ingest
  Auth: X-API-Key header (device API key, issued at pairing, hashed at rest)
  Body: {
    device_id: UUID,
    elder_id: UUID,
    events: [{ event_type, payload, occurred_at }]
  }

IMPORTANT FOR ANDROID DEVELOPER: batch events — do NOT make one HTTP call
per event. The backend expects batches. Aim for at most one call per 30s
or on significant state change (screen share start, banking app open).
Ingestion is intentionally async: risk evaluation runs in a Celery task
after the HTTP response returns — do not poll for risk level here.
"""
from __future__ import annotations

import hashlib
import uuid
from datetime import datetime

from fastapi import APIRouter, Depends, Header, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.db import get_session
from app.models.device import Device
from app.models.signal_event import SignalEvent
from app.workers.tasks import risk_engine_evaluate

router = APIRouter(prefix="/signals", tags=["signals"])


# ---------------------------------------------------------------------------
# Schemas
# ---------------------------------------------------------------------------

class EventIn(BaseModel):
    event_type: str = Field(max_length=64)
    payload: dict = Field(default_factory=dict)
    occurred_at: datetime


class SignalIngestRequest(BaseModel):
    device_id: uuid.UUID
    elder_id: uuid.UUID
    events: list[EventIn] = Field(min_length=1, max_length=100)


class SignalIngestResponse(BaseModel):
    ingested: int
    task_id: str


# ---------------------------------------------------------------------------
# Auth dependency
# ---------------------------------------------------------------------------

async def verify_device_api_key(
    x_api_key: str = Header(..., alias="X-API-Key"),
    session: AsyncSession = Depends(get_session),
) -> Device:
    """
    Validate device API key.
    Key is stored as bcrypt hash in devices.device_api_key_hash.
    Returns the Device row on success.
    """
    if not x_api_key:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Missing X-API-Key")

    # Hash incoming key with SHA-256 for fast lookup, then compare bcrypt hash.
    # For simplicity in Phase 2 we use PBKDF2-SHA256 (stdlib, no extra dep).
    # Production note: migrate to bcrypt or Argon2 for stronger security.
    key_hash = hashlib.sha256(x_api_key.encode()).hexdigest()

    result = await session.execute(
        select(Device).where(Device.device_api_key_hash == key_hash)
    )
    device = result.scalar_one_or_none()
    if device is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid device API key")
    return device


# ---------------------------------------------------------------------------
# Route
# ---------------------------------------------------------------------------

@router.post("/ingest", response_model=SignalIngestResponse)
async def ingest_signals(
    body: SignalIngestRequest,
    device: Device = Depends(verify_device_api_key),
    session: AsyncSession = Depends(get_session),
) -> SignalIngestResponse:
    """
    Batch-ingest signal events from the Android app.

    - Validates that device_id matches the authenticated device.
    - Bulk inserts into signal_events.
    - Enqueues risk_engine.evaluate Celery task (async — does not block response).
    """
    # Device must match authenticated key
    if device.id != body.device_id:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "device_id mismatch")
    # Device must belong to the claimed elder
    if device.elder_id != body.elder_id:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "elder_id mismatch for this device")

    # Bulk insert
    new_events = [
        SignalEvent(
            elder_id=body.elder_id,
            device_id=body.device_id,
            event_type=evt.event_type,
            payload=evt.payload,
            occurred_at=evt.occurred_at,
        )
        for evt in body.events
    ]
    session.add_all(new_events)
    await session.commit()

    # Enqueue risk evaluation — do NOT await; return immediately
    task = risk_engine_evaluate.delay(str(body.elder_id))

    return SignalIngestResponse(ingested=len(new_events), task_id=task.id)
