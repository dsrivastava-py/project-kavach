"""
Risk engine — pure state machine. No DB or network calls.
Takes signal events in, returns risk delta and new level.
The caller (Celery task) persists results.

Weights are loaded from scripts/rules/risk_weights.yaml so false-positive
tuning between test runs requires no code changes.
"""
from __future__ import annotations

import uuid
from dataclasses import dataclass
from enum import Enum
from functools import lru_cache
from pathlib import Path
from typing import Any

import yaml


WEIGHTS_PATH = Path(__file__).parent.parent.parent / "scripts" / "rules" / "risk_weights.yaml"

# Graduated thresholds
GRADUATED_1 = 0.30
GRADUATED_2 = 0.50
GRADUATED_3 = 0.75


class RiskLevel(str, Enum):
    GRADUATED_1 = "graduated_1"
    GRADUATED_2 = "graduated_2"
    GRADUATED_3 = "graduated_3"
    GRADUATED_4 = "graduated_4"


@dataclass(frozen=True)
class SignalEvent:
    event_type: str
    payload: dict[str, Any]
    occurred_at: Any  # datetime — kept Any to avoid coupling


@dataclass
class IncidentStateResult:
    elder_id: uuid.UUID
    cumulative_score: float
    level: RiskLevel
    # IDs of signal_events that contributed
    contributing_event_ids: list[uuid.UUID]


@lru_cache(maxsize=1)
def _load_weights() -> dict[str, float]:
    """Load once; call _load_weights.cache_clear() in tests to reload."""
    with open(WEIGHTS_PATH) as fh:
        raw = yaml.safe_load(fh)
    return {k: float(v) for k, v in raw.items() if not k.startswith("#")}


def _score_to_level(score: float) -> RiskLevel:
    if score >= GRADUATED_3:
        return RiskLevel.GRADUATED_4
    if score >= GRADUATED_2:
        return RiskLevel.GRADUATED_3
    if score >= GRADUATED_1:
        return RiskLevel.GRADUATED_2
    return RiskLevel.GRADUATED_1


def compute_risk_delta(event: SignalEvent) -> float:
    """
    Return the weight delta for a single signal event.
    Pure function — no side effects.
    """
    weights = _load_weights()
    etype = event.event_type
    payload = event.payload

    delta = 0.0

    if etype == "unknown_number":
        delta += weights.get("unknown_or_international_number", 0.0)

    elif etype == "call_start":
        # Duration bonus applied at call_end when duration is known
        pass

    elif etype == "call_end":
        # payload: { duration_seconds: int }
        duration_s = int(payload.get("duration_seconds", 0))
        if duration_s >= 1800:  # 30 min
            base = weights.get("call_duration_over_30min", 0.0)
            extra_periods = max(0, (duration_s - 1800) // 1800)
            # +0.05 per further 30min, capped at 0.25 total
            bonus = min(base + extra_periods * 0.05, 0.25)
            delta += bonus

    elif etype == "video_call_start":
        delta += weights.get("video_call_active", 0.0)

    elif etype == "screen_share_start":
        delta += weights.get("screen_share_start", 0.0)

    elif etype == "banking_app_opened":
        # Only scored heavily when opened during an active call;
        # the Celery task passes context via payload: { during_active_call: bool }
        if payload.get("during_active_call", False):
            delta += weights.get("banking_app_foreground_during_active_call", 0.0)

    elif etype == "first_time_payee":
        delta += weights.get("first_time_payee_detected", 0.0)

    return delta


def compute_cumulative_score(events: list[SignalEvent]) -> float:
    """Sum deltas for an ordered list of events."""
    return sum(compute_risk_delta(e) for e in events)


def evaluate_risk_level(
    elder_id: uuid.UUID,
    events: list[SignalEvent],
    event_ids: list[uuid.UUID],
    existing_score: float = 0.0,
) -> IncidentStateResult:
    """
    Given the window of signal events for an elder, compute the cumulative
    risk score and map it to a graduated level.

    Args:
        elder_id: Elder being evaluated.
        events: Signal events within the evaluation window (since last resolved
                incident, or last 6 hours, whichever is shorter).
        event_ids: Parallel list of DB UUIDs for the events (for incident_signals).
        existing_score: Risk score already on an open incident row, if any.
                        Pass 0.0 if opening fresh.

    Returns:
        IncidentStateResult with cumulative score, level, and contributing IDs.
    """
    incremental = compute_cumulative_score(events)
    total = existing_score + incremental
    level = _score_to_level(total)
    return IncidentStateResult(
        elder_id=elder_id,
        cumulative_score=total,
        level=level,
        contributing_event_ids=event_ids,
    )
