"""
Guardian pairing and incident resolution API.

POST /api/v1/guardians/pair
  Auth: none (pairing code redeemed here)
  Body: { pairing_code, guardian_phone }
  -> Validate short-lived pairing_code from Redis (TTL-keyed by elder device).
  -> Create/find guardian user, insert into guardians (max 2 per elder).
  -> Return JWT for guardian's session.

POST /api/v1/incidents/{id}/resolve
  Auth: guardian JWT
  Body: { resolution: "resolved" | "false_positive", note: str | null }
  -> Update incidents.status, resolved_at, resolution_note.
  -> false_positive resolutions are preserved for Phase 4 FP-rate reporting.
"""
from __future__ import annotations

import uuid
from datetime import datetime, timezone

import redis.asyncio as aioredis
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import get_settings
from app.core.db import get_session
from app.core.security import current_claims, issue_token, require_role
from app.models.elder import Elder
from app.models.family import Family
from app.models.guardian import Guardian
from app.models.incident import Incident
from app.models.user import User

router = APIRouter(tags=["guardians"])


# ---------------------------------------------------------------------------
# Redis helper
# ---------------------------------------------------------------------------

def _redis_client() -> aioredis.Redis:
    s = get_settings()
    return aioredis.from_url(s.REDIS_URL, decode_responses=True)


PAIRING_CODE_PREFIX = "pairing:"
PAIRING_CODE_TTL = 300  # 5 minutes


# ---------------------------------------------------------------------------
# Schemas
# ---------------------------------------------------------------------------

class PairRequest(BaseModel):
    pairing_code: str
    guardian_phone: str  # E.164 format


class PairResponse(BaseModel):
    guardian_id: uuid.UUID
    token: str


class ResolveRequest(BaseModel):
    resolution: str  # "resolved" | "false_positive"
    note: str | None = None


class ResolveResponse(BaseModel):
    incident_id: uuid.UUID
    status: str
    resolved_at: datetime


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------

@router.post("/guardians/pair", response_model=PairResponse)
async def pair_guardian(
    body: PairRequest,
    session: AsyncSession = Depends(get_session),
) -> PairResponse:
    """
    Redeem a pairing code and register a guardian.

    The elder-side Android app generates the pairing_code and stores it in
    Redis with a TTL (use POST /guardians/generate-pairing-code from the
    elder's authenticated session, implemented in Phase 3). Here we only
    redeem it.

    Enforces max 2 guardians per elder.
    """
    rc = _redis_client()
    try:
        redis_key = f"{PAIRING_CODE_PREFIX}{body.pairing_code}"
        elder_id_str: str | None = await rc.get(redis_key)
        if not elder_id_str:
            raise HTTPException(status.HTTP_400_BAD_REQUEST, "Invalid or expired pairing code")

        elder_id = uuid.UUID(elder_id_str)

        # Enforce max 2 guardians per elder
        guardian_count_result = await session.execute(
            select(func.count(Guardian.id)).where(Guardian.elder_id == elder_id)
        )
        count = guardian_count_result.scalar_one()
        if count >= 2:
            raise HTTPException(
                status.HTTP_409_CONFLICT,
                "Maximum 2 guardians allowed per elder",
            )

        # Look up elder → family
        elder_result = await session.execute(select(Elder).where(Elder.id == elder_id))
        elder: Elder | None = elder_result.scalar_one_or_none()
        if not elder:
            raise HTTPException(status.HTTP_404_NOT_FOUND, "Elder not found")

        # Find or create guardian user
        user_result = await session.execute(
            select(User).where(User.phone_e164 == body.guardian_phone)
        )
        guardian_user: User | None = user_result.scalar_one_or_none()

        if guardian_user is None:
            guardian_user = User(
                family_id=elder.family_id,
                role="guardian",
                phone_e164=body.guardian_phone,
            )
            session.add(guardian_user)
            await session.flush()

        # Check not already paired
        existing_result = await session.execute(
            select(Guardian).where(
                Guardian.elder_id == elder_id,
                Guardian.user_id == guardian_user.id,
            )
        )
        existing_guardian = existing_result.scalar_one_or_none()

        if existing_guardian is None:
            priority_order = count + 1  # 1-indexed
            new_guardian = Guardian(
                family_id=elder.family_id,
                user_id=guardian_user.id,
                elder_id=elder_id,
                priority_order=priority_order,
            )
            session.add(new_guardian)
            await session.flush()
            guardian = new_guardian
        else:
            guardian = existing_guardian

        await session.commit()

        # Consume pairing code
        await rc.delete(redis_key)

        token = issue_token(sub=str(guardian_user.id), role="guardian")
        return PairResponse(guardian_id=guardian.id, token=token)

    finally:
        await rc.aclose()


@router.post("/incidents/{incident_id}/resolve", response_model=ResolveResponse)
async def resolve_incident(
    incident_id: uuid.UUID,
    body: ResolveRequest,
    claims: dict = Depends(require_role("guardian")),
    session: AsyncSession = Depends(get_session),
) -> ResolveResponse:
    """
    Resolve an incident as a guardian.

    false_positive resolutions are stored verbatim for Phase 4's FP-rate
    reporting script — that script queries incidents WHERE status =
    'false_positive' to compute the FP rate. Do not change the status value.

    Guardians can only resolve incidents for elders they are paired with.
    """
    if body.resolution not in ("resolved", "false_positive"):
        raise HTTPException(
            status.HTTP_422_UNPROCESSABLE_ENTITY,
            "resolution must be 'resolved' or 'false_positive'",
        )

    incident: Incident | None = await session.get(Incident, incident_id)
    if not incident:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Incident not found")

    # Verify guardian is paired with this elder
    guardian_user_id = uuid.UUID(claims["sub"])
    guardian_result = await session.execute(
        select(Guardian).where(
            Guardian.user_id == guardian_user_id,
            Guardian.elder_id == incident.elder_id,
        )
    )
    if guardian_result.scalar_one_or_none() is None:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Not a guardian for this elder")

    now = datetime.now(timezone.utc)
    incident.status = body.resolution
    incident.resolved_at = now
    incident.resolution_note = body.note
    await session.commit()

    return ResolveResponse(
        incident_id=incident.id,
        status=incident.status,
        resolved_at=now,
    )


@router.post("/incidents/{incident_id}/evidence")
async def generate_incident_evidence(
    incident_id: uuid.UUID,
    claims: dict = Depends(require_role("guardian", "investigator")),
    session: AsyncSession = Depends(get_session),
) -> dict:
    """
    Trigger evidence PDF generation for an incident.
    Uploads to MinIO, returns pdf_ref and a signed 1-hour download URL.
    """
    from app.services.evidence_builder import generate_evidence_pdf, get_signed_url

    incident: Incident | None = await session.get(Incident, incident_id)
    if not incident:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Incident not found")

    pdf_ref = await generate_evidence_pdf(incident_id, session)
    try:
        signed_url = get_signed_url(pdf_ref)
    except Exception:
        signed_url = None  # non-fatal — caller can re-request

    return {
        "incident_id": str(incident_id),
        "pdf_ref": pdf_ref,
        "download_url": signed_url,
    }


@router.post("/guardians/generate-pairing-code")
async def generate_pairing_code(
    claims: dict = Depends(require_role("elder")),
    session: AsyncSession = Depends(get_session),
) -> dict:
    """
    Elder generates a short-lived pairing code.
    Called from the elder-side Android app (authenticated as elder role).

    Stores elder.id (NOT user_id) in Redis with TTL=5min under key pairing:<code>.
    Returns the code to display as a QR / 6-digit string.
    """
    import secrets
    elder_user_id = uuid.UUID(claims["sub"])

    # Resolve elder row from user_id — store elder.id, not user_id
    elder_result = await session.execute(select(Elder).where(Elder.user_id == elder_user_id))
    elder = elder_result.scalar_one_or_none()
    if not elder:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Elder profile not found for this user")

    code = str(secrets.randbelow(1_000_000)).zfill(6)
    rc = _redis_client()
    try:
        redis_key = f"{PAIRING_CODE_PREFIX}{code}"
        await rc.setex(redis_key, PAIRING_CODE_TTL, str(elder.id))
    finally:
        await rc.aclose()

    return {"pairing_code": code, "expires_in_seconds": PAIRING_CODE_TTL}
