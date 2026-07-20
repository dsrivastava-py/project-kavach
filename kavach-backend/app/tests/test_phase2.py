"""
Phase 2 Integration Tests — Definition of Done

Tests run against a live stack (docker-compose up).

DoD Coverage:
  1. Siege signal sequence → incident reaches graduated_3 → alerts row exists.
  2. dispatch_guardian_alert does not double-send within cooldown window.
  3. POST /api/v1/incidents/{id}/resolve with false_positive updates incident
     correctly and is queryable for FP count.
  4. Manual/FCM smoke: Celery task send_fcm_push skips gracefully when
     FCM_SERVER_KEY is not set (CI-safe), and logs correctly when set.

Run: pytest app/tests/test_phase2.py -v
     (Requires: postgres + redis running, KAVACH_TEST_JWT set or JWT_SECRET in env)
"""
from __future__ import annotations

import asyncio
import hashlib
import uuid
from datetime import datetime, timedelta, timezone
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
import httpx
from sqlalchemy import select, func

BASE_URL = "http://localhost:8000"


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

_test_device_id = None
_test_raw_key = None
_test_family_id = None
_test_elder_user = None


@pytest.fixture
async def http():
    async with httpx.AsyncClient(base_url=BASE_URL, timeout=30) as client:
        yield client


@pytest.fixture
async def db_session():
    """Direct DB session for assertion queries."""
    from app.core.db import SessionLocal
    async with SessionLocal() as session:
        yield session


@pytest.fixture
async def test_elder_id(db_session) -> uuid.UUID:
    """
    Create a minimal elder fixture: family → user → elder → device.
    Returns elder.id.
    """
    global _test_device_id, _test_raw_key, _test_family_id, _test_elder_user
    from app.models.family import Family
    from app.models.user import User
    from app.models.elder import Elder
    from app.models.device import Device

    # Family
    family = Family(name="Test Family Phase2")
    db_session.add(family)
    await db_session.flush()

    # Elder user
    phone = f"+91{uuid.uuid4().int % 10_000_000_000:010d}"
    elder_user = User(family_id=family.id, role="elder", phone_e164=phone)
    db_session.add(elder_user)
    await db_session.flush()

    # Elder
    elder = Elder(family_id=family.id, user_id=elder_user.id, onboarding_status="active")
    db_session.add(elder)
    await db_session.flush()

    # Device with API key
    raw_key = f"test-device-key-phase2-{uuid.uuid4()}"
    key_hash = hashlib.sha256(raw_key.encode()).hexdigest()
    device = Device(
        elder_id=elder.id,
        platform="android",
        device_api_key_hash=key_hash,
    )
    db_session.add(device)
    await db_session.flush()

    await db_session.commit()

    # Store device info for use in tests
    _test_device_id = device.id
    _test_raw_key = raw_key
    _test_family_id = family.id
    _test_elder_user = elder_user

    return elder.id


@pytest.fixture
async def test_guardian_id(db_session, test_elder_id) -> uuid.UUID:
    """Create a guardian paired to the test elder."""
    from app.models.user import User
    from app.models.guardian import Guardian

    phone = f"+91{uuid.uuid4().int % 10_000_000_000:010d}"
    guardian_user = User(
        family_id=_test_family_id,
        role="guardian",
        phone_e164=phone,
    )
    db_session.add(guardian_user)
    await db_session.flush()

    guardian = Guardian(
        family_id=_test_family_id,
        user_id=guardian_user.id,
        elder_id=test_elder_id,
        priority_order=1,
    )
    db_session.add(guardian)
    await db_session.commit()

    return guardian.id


# ---------------------------------------------------------------------------
# Helper: run risk engine evaluate synchronously in tests
# ---------------------------------------------------------------------------

async def _run_risk_evaluate(elder_id: uuid.UUID) -> dict:
    """Call the async core of risk_engine_evaluate directly (bypasses Celery)."""
    from app.workers.tasks import _risk_engine_evaluate_async
    return await _risk_engine_evaluate_async(str(elder_id))


# ---------------------------------------------------------------------------
# Phase 2 Integrated Integration Flow
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_phase2_integration_flow(
    http: httpx.AsyncClient,
    db_session,
    test_elder_id: uuid.UUID,
    test_guardian_id: uuid.UUID,
):
    """
    Post a digital-arrest siege signal sequence:
      1. unknown_number (international) → +0.15
      2. video_call_start → +0.15
      3. call_end (2h40m = 9600s) → +0.25 (capped)
      4. banking_app_opened (during_active_call=True) → +0.20

    Total = 0.75 → graduated_4 (which is ≥ graduated_3 threshold 0.50)
    → alert row must exist.
    Also verifies:
      - Cooldown limits (no double-send of alerts).
      - False positive resolution (status updates and queryable).
    """
    # ---------------------------------------------------------------------------
    # Step 1: Siege signal sequence → graduated_3 → alert row
    # ---------------------------------------------------------------------------
    device_id = _test_device_id
    raw_key = _test_raw_key
    elder_id = test_elder_id
    now = datetime.now(timezone.utc)

    events = [
        {
            "event_type": "unknown_number",
            "payload": {"number": "+442071234567", "is_international": True},
            "occurred_at": (now - timedelta(minutes=170)).isoformat(),
        },
        {
            "event_type": "video_call_start",
            "payload": {"number": "+442071234567"},
            "occurred_at": (now - timedelta(minutes=165)).isoformat(),
        },
        {
            "event_type": "call_end",
            "payload": {"duration_seconds": 9600, "contact_number": "+442071234567"},
            "occurred_at": (now - timedelta(minutes=5)).isoformat(),
        },
        {
            "event_type": "banking_app_opened",
            "payload": {"app_name": "PhonePe", "during_active_call": True},
            "occurred_at": (now - timedelta(minutes=2)).isoformat(),
        },
    ]

    resp = await http.post(
        "/api/v1/signals/ingest",
        json={
            "device_id": str(device_id),
            "elder_id": str(elder_id),
            "events": events,
        },
        headers={"X-API-Key": raw_key},
    )
    assert resp.status_code == 200, f"Ingest failed: {resp.text}"
    data = resp.json()
    assert data["ingested"] == 4

    # Run risk engine directly (bypass Celery queue in test)
    result = await _run_risk_evaluate(elder_id)

    assert result["level"] in ("graduated_3", "graduated_4"), (
        f"Expected graduated_3 or graduated_4, got {result['level']}"
    )
    assert result["score"] >= 0.50, f"Score too low: {result['score']}"

    # Assert alert row exists
    from app.models.alert import Alert
    alert_result = await db_session.execute(
        select(Alert).where(
            Alert.incident_id == uuid.UUID(result["incident_id"]),
            Alert.guardian_id == test_guardian_id,
        )
    )
    alert = alert_result.scalar_one_or_none()
    assert alert is not None, "Alert row not created after graduated_3 transition"
    assert alert.channel == "push"
    assert alert.sent_at is not None

    # ---------------------------------------------------------------------------
    # Step 2: No double-send within cooldown
    # ---------------------------------------------------------------------------
    from app.models.incident import Incident

    # Get the open incident from previous step
    incident_result = await db_session.execute(
        select(Incident).where(
            Incident.elder_id == test_elder_id,
            Incident.status.notin_(["resolved", "false_positive"]),
        ).order_by(Incident.started_at.desc()).limit(1)
    )
    incident = incident_result.scalar_one_or_none()
    assert incident is not None, "No open incident found"

    # Run evaluate again (same state, cooldown active)
    await _run_risk_evaluate(test_elder_id)

    # Alert count must still be 1
    count_result = await db_session.execute(
        select(func.count(Alert.id)).where(
            Alert.incident_id == incident.id,
            Alert.guardian_id == test_guardian_id,
        )
    )
    count = count_result.scalar_one()
    assert count == 1, (
        f"Double-send detected: {count} alerts for same incident within cooldown"
    )

    # ---------------------------------------------------------------------------
    # Step 3: false_positive resolve updates incident + is queryable
    # ---------------------------------------------------------------------------
    from app.core.security import issue_token
    from app.models.guardian import Guardian

    # Get guardian user_id
    guardian_result = await db_session.execute(
        select(Guardian).where(Guardian.elder_id == test_elder_id).limit(1)
    )
    guardian = guardian_result.scalar_one_or_none()
    assert guardian is not None

    token = issue_token(sub=str(guardian.user_id), role="guardian")

    resp = await http.post(
        f"/api/v1/incidents/{incident.id}/resolve",
        json={"resolution": "false_positive", "note": "Test: caller was known family member"},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert resp.status_code == 200, f"Resolve failed: {resp.text}"
    data = resp.json()
    assert data["status"] == "false_positive"
    assert data["resolved_at"] is not None

    # Refresh and assert in DB
    await db_session.rollback()
    await db_session.refresh(incident)
    assert incident.status == "false_positive"
    assert incident.resolved_at is not None
    assert "family member" in (incident.resolution_note or "")

    # FP count query — this is exactly what Phase 4 reporting uses
    fp_count_result = await db_session.execute(
        select(func.count(Incident.id)).where(Incident.status == "false_positive")
    )
    fp_count = fp_count_result.scalar_one()
    assert fp_count >= 1, "false_positive incident not queryable"


# ---------------------------------------------------------------------------
# DoD Test 4: FCM task skips gracefully without FCM_SERVER_KEY
# ---------------------------------------------------------------------------

def test_send_fcm_push_skips_without_key():
    """
    send_fcm_push returns {"status": "skipped"} when FCM_SERVER_KEY is empty.
    This is CI-safe: no real FCM call made.
    """
    from app.workers.tasks import send_fcm_push

    with patch("app.workers.tasks.get_settings") as mock_settings:
        s = MagicMock()
        s.FCM_SERVER_KEY = ""
        mock_settings.return_value = s

        # Call the underlying function directly (bypassing Celery)
        # Celery tasks are decorated — access via .run() in tests
        result = send_fcm_push.run(
            guardian_id=str(uuid.uuid4()),
            fcm_token="dummy_token",
            title="Test Alert",
            body="Test body",
            data={"incident_id": str(uuid.uuid4())},
        )
        assert result["status"] == "skipped"
        assert "FCM_SERVER_KEY" in result["reason"]


def test_send_fcm_push_calls_fcm_api():
    """
    send_fcm_push calls FCM API when FCM_SERVER_KEY is set.
    Mocks the HTTP call to avoid real network.
    """
    from app.workers.tasks import send_fcm_push

    mock_response = MagicMock()
    mock_response.json.return_value = {"success": 1, "failure": 0}
    mock_response.raise_for_status = MagicMock()

    with patch("app.workers.tasks.get_settings") as mock_settings, \
         patch("app.workers.tasks.httpx.post", return_value=mock_response) as mock_post:

        s = MagicMock()
        s.FCM_SERVER_KEY = "test-fcm-key"
        mock_settings.return_value = s

        guardian_id = str(uuid.uuid4())
        result = send_fcm_push.run(
            guardian_id=guardian_id,
            fcm_token="valid_fcm_token",
            title="Test Alert",
            body="Test context string.",
            data={"incident_id": str(uuid.uuid4())},
        )

        assert result["status"] == "sent"
        assert result["guardian_id"] == guardian_id
        mock_post.assert_called_once()
        call_kwargs = mock_post.call_args
        assert call_kwargs[0][0] == "https://fcm.googleapis.com/fcm/send"
        payload = call_kwargs[1]["json"]
        assert payload["to"] == "valid_fcm_token"
        assert payload["notification"]["title"] == "Test Alert"


# ---------------------------------------------------------------------------
# Bonus: Pure risk engine unit tests (no DB needed)
# ---------------------------------------------------------------------------

def test_risk_engine_siege_pattern_score():
    """Unit test: siege pattern hits graduated_3/4 threshold."""
    from app.services.risk_engine import (
        SignalEvent,
        compute_cumulative_score,
        _score_to_level,
        RiskLevel,
        _load_weights,
    )
    _load_weights.cache_clear()

    now = datetime.now(timezone.utc)
    events = [
        SignalEvent("unknown_number", {"is_international": True}, now - timedelta(hours=3)),
        SignalEvent("video_call_start", {}, now - timedelta(hours=2, minutes=45)),
        SignalEvent("call_end", {"duration_seconds": 9600}, now - timedelta(minutes=5)),
        SignalEvent("banking_app_opened", {"during_active_call": True, "app_name": "PhonePe"}, now - timedelta(minutes=2)),
    ]
    score = compute_cumulative_score(events)
    level = _score_to_level(score)
    # 0.15 + 0.15 + 0.25 (capped) + 0.20 = 0.75 → graduated_4
    assert score >= 0.70, f"Score too low: {score}"
    assert level in (RiskLevel.GRADUATED_3, RiskLevel.GRADUATED_4)


def test_risk_engine_screen_share_alone():
    """screen_share_start alone = 0.25 → graduated_1 (0.25 < 0.30 threshold)."""
    from app.services.risk_engine import SignalEvent, compute_cumulative_score, _score_to_level, RiskLevel, _load_weights
    _load_weights.cache_clear()

    events = [SignalEvent("screen_share_start", {}, datetime.now(timezone.utc))]
    score = compute_cumulative_score(events)
    level = _score_to_level(score)
    assert score == pytest.approx(0.25, abs=0.01)
    assert level == RiskLevel.GRADUATED_1


def test_risk_engine_no_double_counting():
    """Same weight loaded each call — no accumulation bug."""
    from app.services.risk_engine import SignalEvent, compute_risk_delta, _load_weights
    _load_weights.cache_clear()

    e = SignalEvent("screen_share_start", {}, datetime.now(timezone.utc))
    d1 = compute_risk_delta(e)
    d2 = compute_risk_delta(e)
    assert d1 == d2
