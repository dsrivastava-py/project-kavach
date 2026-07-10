"""
Deep-check API — opt-in audio analysis.

Fire-and-poll flow (never synchronous):
  POST /api/v1/deepcheck/sessions
    -> Upload audio to MinIO, insert deepcheck_sessions row (status=pending),
       enqueue Celery task, return session_id immediately.

  GET /api/v1/deepcheck/sessions/{id}
    -> Poll status + results. When status=done, response includes transcript,
       red_flags, spoof_score (with assistive_only=true + disclaimer always).

HARD RULE: every response that carries spoof_score MUST include
  "assistive_only": true
A test in test_phase3.py fails the build if this field is missing.
"""
from __future__ import annotations

import io
import uuid
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status
from pydantic import BaseModel
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.db import get_session
from app.core.security import current_claims
from app.models.deepcheck_session import DeepcheckSession
from app.services.spoof_detector import DISCLAIMER
from app.workers.tasks import run_deepcheck

router = APIRouter(prefix="/deepcheck", tags=["deepcheck"])

_AUDIO_CONTENT_TYPES = {
    "audio/ogg", "audio/mpeg", "audio/mp4", "audio/wav",
    "audio/webm", "audio/x-m4a", "application/octet-stream",
}
MAX_AUDIO_BYTES = 25 * 1024 * 1024  # 25 MB


# ---------------------------------------------------------------------------
# Schemas
# ---------------------------------------------------------------------------

class DeepcheckSessionResponse(BaseModel):
    session_id: uuid.UUID
    status: str
    # Populated when status == "done"
    transcript: str | None = None
    red_flags: list[str] | None = None
    spoof_score: float | None = None
    assistive_only: bool | None = None
    spoof_disclaimer: str | None = None
    summary: str | None = None
    confidence: float | None = None


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------

@router.post("/sessions", response_model=DeepcheckSessionResponse, status_code=status.HTTP_202_ACCEPTED)
async def create_deepcheck_session(
    audio_file: UploadFile = File(...),
    elder_id: uuid.UUID = Form(...),
    incident_id: uuid.UUID | None = Form(None),
    claims: dict = Depends(current_claims),
    session: AsyncSession = Depends(get_session),
) -> DeepcheckSessionResponse:
    """
    Start a deep-check session.

    Upload audio → MinIO. Insert deepcheck_sessions row (status=pending).
    Enqueue Celery task run_deepcheck(session_id). Return session_id.

    The caller must poll GET /deepcheck/sessions/{id} for results.
    Opt-in only — this endpoint is never called passively.
    """
    # Validate size
    audio_bytes = await audio_file.read()
    if len(audio_bytes) > MAX_AUDIO_BYTES:
        raise HTTPException(status.HTTP_413_REQUEST_ENTITY_TOO_LARGE, "Audio file too large (max 25 MB)")
    if len(audio_bytes) == 0:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Empty audio file")

    # Upload to MinIO
    from app.core.config import get_settings
    from minio import Minio

    s = get_settings()
    client = Minio(
        s.MINIO_ENDPOINT,
        access_key=s.MINIO_ACCESS_KEY,
        secret_key=s.MINIO_SECRET_KEY,
        secure=False,
    )
    if not client.bucket_exists(s.MINIO_BUCKET):
        client.make_bucket(s.MINIO_BUCKET)

    ext = (audio_file.filename or "audio.ogg").rsplit(".", 1)[-1].lower()
    object_key = f"audio/{elder_id}/{uuid.uuid4()}.{ext}"

    client.put_object(
        s.MINIO_BUCKET,
        object_key,
        io.BytesIO(audio_bytes),
        length=len(audio_bytes),
        content_type=audio_file.content_type or "audio/ogg",
    )

    # Insert deepcheck_sessions row
    dc = DeepcheckSession(
        elder_id=elder_id,
        incident_id=incident_id,
        audio_ref=object_key,
        status="pending",
    )
    session.add(dc)
    await session.commit()
    await session.refresh(dc)

    # Enqueue Celery task (fire-and-forget — do not await)
    run_deepcheck.delay(str(dc.id))

    return DeepcheckSessionResponse(session_id=dc.id, status="pending")


@router.get("/sessions/{session_id}", response_model=DeepcheckSessionResponse)
async def get_deepcheck_session(
    session_id: uuid.UUID,
    claims: dict = Depends(current_claims),
    session: AsyncSession = Depends(get_session),
) -> DeepcheckSessionResponse:
    """
    Poll a deep-check session.

    When status == "done", the response includes:
      - transcript
      - red_flags
      - spoof_score (assistive_only=true ALWAYS, disclaimer always present)
      - summary + confidence

    Never interpret spoof_score as a definitive verdict — see disclaimer.
    """
    dc: DeepcheckSession | None = await session.get(DeepcheckSession, session_id)
    if dc is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Session not found")

    resp = DeepcheckSessionResponse(session_id=dc.id, status=dc.status)

    if dc.status == "done":
        red_flags = (dc.red_flags or {}).get("red_flags", []) if dc.red_flags else []
        resp.transcript = dc.transcript
        resp.red_flags = red_flags
        resp.spoof_score = dc.spoof_score
        resp.assistive_only = True          # HARD RULE — always present
        resp.spoof_disclaimer = DISCLAIMER  # always carry the hedged language
        resp.summary = (dc.red_flags or {}).get("summary", "")
        resp.confidence = (dc.red_flags or {}).get("confidence", 0.0)

    return resp
