"""
Phase 3 DoD tests.

Tests:
  1. assistive_only always present in any response carrying spoof_score
  2. hash chain tamper-detection (alter one event → all subsequent hashes invalid)
  3. verify_hash_chain on unmodified chain returns True
  4. spoof_detector returns score in [0, 1] range
  5. deepcheck_chain.run_chain (mocked LLM) returns DeepCheckVerdict
  6. graph seed returns non-trivial subgraph for a seeded phone number
  7. evidence_builder.generate_evidence_pdf produces a real PDF (file signature)

Tests 1–5 run without any network/DB (pure unit). Tests 6–7 require
running services and are marked with pytest.mark.integration.
"""
from __future__ import annotations

import hashlib
import io
import json
import uuid
from dataclasses import dataclass, field
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

# ---------------------------------------------------------------------------
# Test 1: assistive_only always present
# ---------------------------------------------------------------------------

def test_spoof_score_response_always_has_assistive_only():
    """
    Any dict/model that carries spoof_score must also carry assistive_only=True.
    This test mirrors what the API layer enforces — fail the build if the field
    ever goes missing from the response shape.
    """
    from app.api.v1.deepcheck import DeepcheckSessionResponse

    resp = DeepcheckSessionResponse(
        session_id=uuid.uuid4(),
        status="done",
        transcript="hello",
        red_flags=["flag"],
        spoof_score=0.45,
        assistive_only=True,
        spoof_disclaimer="disclaimer",
        summary="test",
        confidence=0.7,
    )

    # Serialise and check
    data = resp.model_dump()
    assert "spoof_score" in data
    assert data["assistive_only"] is True, "assistive_only MUST be True when spoof_score is present"
    assert data["spoof_disclaimer"], "spoof_disclaimer must not be empty when spoof_score is present"


def test_deepcheck_session_response_without_spoof_has_no_assistive_only():
    """Pending/failed responses should not include spoof_score or assistive_only."""
    from app.api.v1.deepcheck import DeepcheckSessionResponse
    resp = DeepcheckSessionResponse(session_id=uuid.uuid4(), status="pending")
    data = resp.model_dump()
    assert data["spoof_score"] is None
    assert data["assistive_only"] is None


# ---------------------------------------------------------------------------
# Test 2 & 3: hash chain integrity
# ---------------------------------------------------------------------------

def test_hash_chain_verify_clean():
    """Unmodified hash chain verifies as True."""
    from app.services.evidence_builder import verify_hash_chain, _compute_link_hash

    events = [
        {"event_type": "incident_opened", "risk": 0.2},
        {"event_type": "guardian_alerted", "risk": 0.6},
        {"event_type": "deepcheck_completed", "verdict": "suspicious"},
    ]

    chain = []
    prev_hash = "0" * 64
    for event in events:
        h = _compute_link_hash(prev_hash, event)
        chain.append({"event": event, "hash": h, "prev_hash": prev_hash, "timestamp": "2026-01-01T00:00:00"})
        prev_hash = h

    assert verify_hash_chain(chain) is True


def test_hash_chain_tamper_breaks_chain():
    """Altering a single event payload breaks verification of that link and all after."""
    from app.services.evidence_builder import verify_hash_chain, _compute_link_hash

    events = [
        {"event_type": "incident_opened", "risk": 0.2},
        {"event_type": "guardian_alerted", "risk": 0.6},
        {"event_type": "deepcheck_completed", "verdict": "suspicious"},
        {"event_type": "incident_resolved", "resolution": "false_positive"},
    ]

    chain = []
    prev_hash = "0" * 64
    for event in events:
        h = _compute_link_hash(prev_hash, event)
        chain.append({"event": event, "hash": h, "prev_hash": prev_hash, "timestamp": "2026-01-01T00:00:00"})
        prev_hash = h

    # Tamper with link #1 (second event) — changes payload without updating hash
    tampered = [dict(link) for link in chain]
    tampered[1] = {**tampered[1], "event": {"event_type": "guardian_alerted", "risk": 0.0}}  # altered risk

    assert verify_hash_chain(tampered) is False, "Tampered chain must not verify"


def test_hash_chain_empty_returns_true():
    from app.services.evidence_builder import verify_hash_chain
    assert verify_hash_chain([]) is True


# ---------------------------------------------------------------------------
# Test 4: spoof_detector output range
# ---------------------------------------------------------------------------

def test_spoof_detector_score_in_range():
    """spoof_score must be in [0, 1] and assistive_only always True."""
    import numpy as np
    from app.services.spoof_detector import extract_spoof_features, SpoofResult
    import soundfile as sf

    # Generate a synthetic sine wave as "audio"
    sr = 16000
    duration = 2.0
    freq = 440.0
    t = np.linspace(0, duration, int(sr * duration))
    audio = (0.3 * np.sin(2 * np.pi * freq * t)).astype(np.float32)

    buf = io.BytesIO()
    sf.write(buf, audio, sr, format="WAV")
    audio_bytes = buf.getvalue()

    result = extract_spoof_features(audio_bytes)

    assert isinstance(result, SpoofResult)
    assert 0.0 <= result.spoof_score <= 1.0, f"Score {result.spoof_score} out of [0,1]"
    assert result.assistive_only is True, "assistive_only must always be True"
    assert "method" in result.features.raw
    assert result.features.raw["method"] == "heuristic", "Must explicitly declare heuristic method"


# ---------------------------------------------------------------------------
# Test 5: deepcheck_chain run (mocked LLM)
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_deepcheck_chain_returns_verdict():
    """Run deepcheck_chain with a mocked LLM — verify verdict structure."""
    from app.services.deepcheck_chain import run_chain, DeepCheckVerdict
    from app.services.spoof_detector import SpoofResult, SpoofFeatures

    mock_llm_response = json.dumps({
        "red_flags": ["official_impersonation", "demands_otp"],
        "evidence_spans": ["I am calling from CBI", "share your OTP now"],
        "reasoning": "Caller impersonates CBI and demands OTP — classic digital arrest scam.",
    })

    mock_llm = AsyncMock()
    mock_llm.return_value = MagicMock(content=mock_llm_response, provider="groq")

    spoof = SpoofResult(
        spoof_score=0.72,
        features=SpoofFeatures(10.0, 0.01, 50000.0, {"method": "heuristic"}),
    )

    with patch("app.services.llm_router.call_llm", mock_llm):
        verdict = await run_chain(
            transcript="I am calling from CBI. This is a fraud investigation. Share your OTP now.",
            spoof_result=spoof,
            language="en",
        )

    assert isinstance(verdict, DeepCheckVerdict)
    assert verdict.verdict in ("scam", "suspicious", "safe", "unclear")
    assert 0.0 <= verdict.confidence <= 1.0
    assert verdict.assistive_only is True, "assistive_only must always be True on verdict"
    assert isinstance(verdict.red_flags, list)
    assert isinstance(verdict.spoof_score, float)


# ---------------------------------------------------------------------------
# Test 6: mule ring subgraph (integration)
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
@pytest.mark.integration
async def test_mule_ring_returns_nontrivial_subgraph():
    """
    find_mule_ring against seeded synthetic data returns >1 node.
    Requires: Neo4j running + seed_demo_data.py already executed.
    """
    from app.services.graph_service import GraphService

    gs = GraphService()
    try:
        # Use a phone from the seed data
        subgraph = await gs.find_mule_ring("+919876540001", depth=2)
        assert len(subgraph.nodes) > 1, (
            f"Expected >1 node for seeded ring phone, got {len(subgraph.nodes)}"
        )
    finally:
        await gs.close()


# ---------------------------------------------------------------------------
# Test 7: evidence PDF has real file signature (integration)
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
@pytest.mark.integration
async def test_evidence_pdf_has_pdf_signature():
    """
    generate_evidence_pdf must produce a real PDF (starts with %PDF-).
    Requires: Postgres + MinIO running.
    """
    from app.services.evidence_builder import generate_evidence_pdf
    from app.core.db import SessionLocal
    from app.models.family import Family
    from app.models.user import User
    from app.models.elder import Elder
    from app.models.incident import Incident
    import datetime, uuid

    async with SessionLocal() as session:
        # Minimal fixture
        family = Family(name="Test Family")
        session.add(family)
        await session.flush()

        phone = f"+91{uuid.uuid4().int % 10_000_000_000:010d}"
        user = User(family_id=family.id, role="elder", phone_e164=phone)
        session.add(user)
        await session.flush()

        elder = Elder(family_id=family.id, user_id=user.id, onboarding_status="active")
        session.add(elder)
        await session.flush()

        now = datetime.datetime.now(datetime.timezone.utc)
        incident = Incident(
            elder_id=elder.id,
            status="graduated_3",
            risk_score=0.82,
            started_at=now,
        )
        session.add(incident)
        await session.commit()
        await session.refresh(incident)

        pdf_ref = await generate_evidence_pdf(incident.id, session)

    # Download from MinIO and check file signature
    from app.core.config import get_settings
    from minio import Minio

    s = get_settings()
    client = Minio(s.MINIO_ENDPOINT, access_key=s.MINIO_ACCESS_KEY, secret_key=s.MINIO_SECRET_KEY, secure=False)
    response = client.get_object(s.MINIO_BUCKET, pdf_ref)
    first_bytes = response.read(8)
    response.close()

    assert first_bytes.startswith(b"%PDF-"), f"Expected PDF file signature, got: {first_bytes!r}"
