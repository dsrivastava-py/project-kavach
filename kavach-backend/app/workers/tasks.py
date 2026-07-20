"""
Celery tasks for Phase 2 + 3.

Tasks:
  - risk_engine_evaluate(elder_id): pull signal events, run risk engine,
    persist incident update, trigger alert dispatch if threshold crossed.
  - send_fcm_push(guardian_id, fcm_token, title, body, data): fire FCM push.
  - run_deepcheck(session_id): transcribe → spoof → LangGraph chain → persist.

Each task creates its own sync DB session (Celery workers are sync).
"""
from __future__ import annotations

import asyncio
import uuid
from datetime import datetime, timedelta, timezone

import httpx
from sqlalchemy import select, and_
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import get_settings
from app.workers.celery_app import celery_app


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _run(coro):
    """Run a coroutine from a sync Celery task. Safe for Python 3.12+."""
    try:
        loop = asyncio.get_running_loop()
    except RuntimeError:
        # No running loop — common in Celery workers. Create a fresh one.
        return asyncio.run(coro)
    else:
        # If we're somehow inside a running loop already, use it directly.
        return loop.run_until_complete(coro)


async def _get_session() -> AsyncSession:
    from app.core.db import SessionLocal
    return SessionLocal()


async def _get_redis():
    import redis.asyncio as aioredis
    s = get_settings()
    return aioredis.from_url(s.REDIS_URL, decode_responses=True)


# ---------------------------------------------------------------------------
# Task 1: risk_engine_evaluate
# ---------------------------------------------------------------------------

@celery_app.task(name="risk_engine.evaluate", bind=True, max_retries=3, default_retry_delay=5)
def risk_engine_evaluate(self, elder_id_str: str) -> dict:
    """
    Evaluate risk for an elder:
      1. Load signal events since last resolved incident or last 6h.
      2. Run pure risk engine.
      3. Open/update incident row.
      4. Link contributing signal_events via incident_signals.
      5. If threshold crossed to graduated_3/4, dispatch guardian alert.
    """
    try:
        return _run(_risk_engine_evaluate_async(elder_id_str))
    except Exception as exc:
        raise self.retry(exc=exc)


async def _risk_engine_evaluate_async(elder_id_str: str) -> dict:
    from app.models.incident import Incident
    from app.models.incident_signal import IncidentSignal
    from app.models.signal_event import SignalEvent
    from app.services.risk_engine import (
        SignalEvent as RiskSignalEvent,
        evaluate_risk_level,
        RiskLevel,
    )
    from app.services.alert_dispatch import dispatch_guardian_alert

    elder_id = uuid.UUID(elder_id_str)
    now = datetime.now(timezone.utc)
    
    # Normalize to offset-naive UTC to match database datetime format (MySQL/aiomysql returns naive)
    now_naive = now.replace(tzinfo=None)
    window_start_naive = now_naive - timedelta(hours=6)

    async with await _get_session() as session:
        # Find open incident for elder
        open_inc_result = await session.execute(
            select(Incident).where(
                and_(
                    Incident.elder_id == elder_id,
                    Incident.status.notin_(["resolved", "false_positive"]),
                )
            ).order_by(Incident.started_at.desc()).limit(1)
        )
        open_incident: Incident | None = open_inc_result.scalar_one_or_none()

        # Determine window start: since incident started or last 6h, whichever is shorter
        if open_incident:
            started_at = open_incident.started_at
            if started_at.tzinfo is not None:
                started_at = started_at.replace(tzinfo=None)
            effective_window = max(window_start_naive, started_at)
        else:
            effective_window = window_start_naive

        # Load signal events in window
        events_result = await session.execute(
            select(SignalEvent).where(
                and_(
                    SignalEvent.elder_id == elder_id,
                    SignalEvent.occurred_at >= effective_window,
                )
            ).order_by(SignalEvent.occurred_at)
        )
        db_events = events_result.scalars().all()

        if not db_events:
            return {"elder_id": elder_id_str, "action": "no_events"}

        # Map to pure risk engine dataclasses
        risk_events = [
            RiskSignalEvent(
                event_type=e.event_type,
                payload=e.payload,
                occurred_at=e.occurred_at,
            )
            for e in db_events
        ]
        event_ids = [e.id for e in db_events]

        existing_score = open_incident.risk_score if open_incident else 0.0
        prev_level = open_incident.status if open_incident else None

        result = evaluate_risk_level(
            elder_id=elder_id,
            events=risk_events,
            event_ids=event_ids,
            existing_score=0.0,  # recompute from scratch each time for idempotency
        )

        # Open or update incident
        if open_incident is None:
            incident = Incident(
                elder_id=elder_id,
                status=result.level.value,
                risk_score=result.cumulative_score,
                started_at=db_events[0].occurred_at,
            )
            session.add(incident)
            await session.flush()  # get incident.id
        else:
            incident = open_incident
            incident.status = result.level.value
            incident.risk_score = result.cumulative_score

        # Link contributing signal events (skip existing links)
        existing_links_result = await session.execute(
            select(IncidentSignal.signal_event_id).where(
                IncidentSignal.incident_id == incident.id
            )
        )
        already_linked = {r for r in existing_links_result.scalars().all()}

        for eid in result.contributing_event_ids:
            if eid not in already_linked:
                session.add(IncidentSignal(incident_id=incident.id, signal_event_id=eid))

        await session.commit()

        # Dispatch alert if in graduated_3 or graduated_4, and level increased
        new_level = result.level
        alert_levels = {RiskLevel.GRADUATED_3, RiskLevel.GRADUATED_4}
        level_increased = (prev_level != new_level.value)

        if new_level in alert_levels and (prev_level not in [l.value for l in alert_levels] or level_increased):
            redis_client = await _get_redis()
            try:
                await dispatch_guardian_alert(incident.id, session, redis_client)
            finally:
                await redis_client.aclose()

        return {
            "elder_id": elder_id_str,
            "incident_id": str(incident.id),
            "level": new_level.value,
            "score": result.cumulative_score,
        }


# ---------------------------------------------------------------------------
# Task 2: send_fcm_push
# ---------------------------------------------------------------------------

@celery_app.task(name="alert.send_fcm_push", bind=True, max_retries=3, default_retry_delay=10)
def send_fcm_push(
    self,
    guardian_id: str,
    fcm_token: str,
    title: str,
    body: str,
    data: dict,
) -> dict:
    """
    Send an FCM push notification to a guardian's device.
    Uses FCM HTTP v1 API (legacy server key, simple POST).

    The FCM_SERVER_KEY must be set in config. If empty, logs and skips
    (useful in test environments without a real FCM project).
    """
    s = get_settings()
    if not s.FCM_SERVER_KEY:
        # Not a hard failure — let CI run without real FCM
        return {"status": "skipped", "reason": "FCM_SERVER_KEY not configured"}

    try:
        payload = {
            "to": fcm_token,
            "notification": {"title": title, "body": body},
            "data": {k: str(v) for k, v in data.items()},
        }
        headers = {
            "Authorization": f"key={s.FCM_SERVER_KEY}",
            "Content-Type": "application/json",
        }
        resp = httpx.post(
            "https://fcm.googleapis.com/fcm/send",
            json=payload,
            headers=headers,
            timeout=10,
        )
        resp.raise_for_status()
        result = resp.json()
        if result.get("failure", 0) > 0:
            raise RuntimeError(f"FCM reported failure: {result}")
        return {"status": "sent", "guardian_id": guardian_id, "fcm_result": result}
    except Exception as exc:
        raise self.retry(exc=exc)


# ---------------------------------------------------------------------------
# Task 3: run_deepcheck (Phase 3)
# ---------------------------------------------------------------------------

@celery_app.task(name="deepcheck.run", bind=True, max_retries=2, default_retry_delay=10)
def run_deepcheck(self, session_id_str: str) -> dict:
    """
    Deep-check pipeline for an audio session:
      1. Load deepcheck_sessions row.
      2. Update status → "transcribing"; pull audio from MinIO; call Groq Whisper.
      3. Update status → "analyzing"; extract spoof features; run LangGraph chain.
      4. Persist transcript, red_flags (as JSONB), spoof_score, spoof_features.
      5. Update status → "done".
      6. Append hash chain event to incident's evidence package (if incident_id set).
      7. Sync incident to Neo4j graph (if incident_id set).
    """
    try:
        return _run(_run_deepcheck_async(session_id_str))
    except Exception as exc:
        _run(_mark_deepcheck_failed(session_id_str, str(exc)))
        raise self.retry(exc=exc)


async def _run_deepcheck_async(session_id_str: str) -> dict:
    from app.models.deepcheck_session import DeepcheckSession
    from app.services.whisper_service import transcribe, TranscriptionError
    from app.services.spoof_detector import extract_spoof_features
    from app.services.deepcheck_chain import run_chain
    from app.services.evidence_builder import append_hash_chain_event
    from app.services.graph_service import GraphService
    from app.models.incident import Incident

    session_id = uuid.UUID(session_id_str)

    async with await _get_session() as session:
        dc: DeepcheckSession | None = await session.get(DeepcheckSession, session_id)
        if dc is None:
            return {"error": "session not found", "session_id": session_id_str}

        # Step 1: transcribe
        dc.status = "transcribing"
        await session.commit()

        try:
            transcript_result = await transcribe(dc.audio_ref, timeout_s=20.0)
        except TranscriptionError as e:
            dc.status = "failed"
            await session.commit()
            return {"error": str(e), "session_id": session_id_str}

        dc.transcript = transcript_result.text
        dc.whisper_latency_ms = transcript_result.latency_ms

        # Step 2: spoof + LangGraph
        dc.status = "analyzing"
        await session.commit()

        try:
            from app.services.whisper_service import _pull_from_minio
            from app.core.config import get_settings as _gs
            audio_bytes = await _pull_from_minio(dc.audio_ref, _gs())
            spoof_result = extract_spoof_features(audio_bytes)
        except Exception:
            from app.services.spoof_detector import SpoofFeatures, SpoofResult
            spoof_result = SpoofResult(
                spoof_score=0.0,
                features=SpoofFeatures(0.0, 0.0, 0.0, {"error": "extraction_failed", "method": "heuristic"}),
            )

        verdict = await run_chain(
            transcript=transcript_result.text,
            spoof_result=spoof_result,
            language=transcript_result.language,
        )

        dc.spoof_score = verdict.spoof_score
        dc.spoof_features = spoof_result.features.raw
        dc.red_flags = {
            "red_flags": verdict.red_flags,
            "evidence_spans": verdict.evidence_spans,
            "summary": verdict.summary,
            "confidence": verdict.confidence,
            "assistive_only": True,
        }
        dc.status = "done"
        await session.commit()

        if dc.incident_id:
            await append_hash_chain_event(
                dc.incident_id,
                {
                    "event_type": "deepcheck_completed",
                    "session_id": session_id_str,
                    "verdict": verdict.verdict,
                    "confidence": verdict.confidence,
                    "spoof_score": verdict.spoof_score,
                },
                session,
            )

            incident: Incident | None = await session.get(Incident, dc.incident_id)
            if incident:
                gs_client = GraphService()
                try:
                    await gs_client.sync_incident_to_graph(
                        dc.incident_id,
                        risk_score=incident.risk_score,
                    )
                except Exception:
                    pass
                finally:
                    await gs_client.close()

    return {
        "session_id": session_id_str,
        "status": "done",
        "verdict": verdict.verdict,
        "confidence": verdict.confidence,
    }


async def _mark_deepcheck_failed(session_id_str: str, error: str) -> None:
    from app.models.deepcheck_session import DeepcheckSession
    session_id = uuid.UUID(session_id_str)
    async with await _get_session() as session:
        dc: DeepcheckSession | None = await session.get(DeepcheckSession, session_id)
        if dc:
            dc.status = "failed"
            await session.commit()
