"""
Deep-check reasoning chain — the ONLY place LangGraph is used in Kavach.

Three nodes:
  1. extract_signals  — LLM + rules_engine over transcript
  2. spoof_fusion     — combine spoof features with transcript signals
  3. verdict          — final DeepCheckVerdict

Justification for LangGraph vs plain function chain:
  State is passed between nodes as a typed dict; the graph makes node
  boundaries explicit and testable independently. Each node can be unit-tested
  without running the full graph. The design anticipates adding active-probe
  questions (a planned add-on) as a fourth node without restructuring.

If you find yourself fighting the framework, use a plain function — don't
force graph structure. Each node here does earn its keep.
"""
from __future__ import annotations

import json
from dataclasses import dataclass, field
from typing import Any, TypedDict

from langgraph.graph import END, StateGraph

from app.core.config import get_settings
from app.core.logging import log
from app.services.rules_engine import match_rules
from app.services.spoof_detector import SpoofFeatures, DISCLAIMER


# ---------------------------------------------------------------------------
# State schema
# ---------------------------------------------------------------------------

class DeepCheckState(TypedDict):
    transcript: str
    language: str
    spoof_features: dict          # SpoofFeatures.raw
    spoof_score: float
    # Outputs from node 1
    rule_flags: list[str]
    rule_confidence: float
    llm_red_flags: list[str]
    llm_evidence_spans: list[str]
    llm_reasoning: str
    # Outputs from node 2
    compound_flags: list[str]
    compound_score: float
    # Final
    verdict: str                  # final verdict string
    confidence: float
    summary: str


# ---------------------------------------------------------------------------
# Output dataclass
# ---------------------------------------------------------------------------

@dataclass
class DeepCheckVerdict:
    verdict: str                        # scam | suspicious | safe | unclear
    confidence: float
    summary: str
    red_flags: list[str]
    evidence_spans: list[str]
    spoof_score: float
    assistive_only: bool = True         # ALWAYS True
    spoof_disclaimer: str = DISCLAIMER


# ---------------------------------------------------------------------------
# Node 1: extract_signals
# ---------------------------------------------------------------------------

_DEEPCHECK_PROMPT = """\
You are an expert fraud analyst specialising in digital-arrest scams targeting elderly victims.
Analyse the following call transcript for scam red flags.

Extract:
1. Red flag phrases (quoted directly from the transcript).
2. For each red flag, a short evidence span (the exact words that triggered it).
3. A one-sentence reasoning summary.

Known scam patterns already matched by rule engine: {rule_flags}

Transcript:
\"\"\"
{transcript}
\"\"\"

Respond with JSON exactly:
{{
  "red_flags": ["flag1", "flag2"],
  "evidence_spans": ["quote1", "quote2"],
  "reasoning": "one sentence"
}}
"""


async def _node_extract_signals(state: DeepCheckState) -> dict:
    """Node 1: rules_engine + LLM extraction of transcript red flags."""
    from app.services.llm_router import call_llm, LLMUnavailableError

    transcript = state["transcript"]
    language = state.get("language", "en")

    # Rules first — deterministic, cheap
    rule_result = match_rules(transcript, language)

    llm_flags: list[str] = []
    evidence_spans: list[str] = []
    reasoning = ""

    try:
        s = get_settings()
        prompt = _DEEPCHECK_PROMPT.format(
            rule_flags=rule_result.matched_flags or "none",
            transcript=transcript[:3000],
        )
        result = await call_llm(prompt, task="deepcheck_reasoning", timeout_s=10.0)
        parsed = json.loads(result.content)
        llm_flags = parsed.get("red_flags", [])
        evidence_spans = parsed.get("evidence_spans", [])
        reasoning = parsed.get("reasoning", "")
    except (LLMUnavailableError, Exception) as e:
        log.warning("deepcheck_llm_failed_using_rules_only", error=str(e))

    return {
        "rule_flags": rule_result.matched_flags,
        "rule_confidence": rule_result.rule_confidence,
        "llm_red_flags": llm_flags,
        "llm_evidence_spans": evidence_spans,
        "llm_reasoning": reasoning,
    }


# ---------------------------------------------------------------------------
# Node 2: spoof_fusion
# ---------------------------------------------------------------------------

def _node_spoof_fusion(state: DeepCheckState) -> dict:
    """
    Node 2: combine transcript signals with spoof features.

    Rules-based combination (not a black box):
      - If caller claims to be CBI/Police AND spoof_score > 0.5 → compound_flag.
      - If caller asks for banking credentials AND spoof_score > 0.3 → elevated.
      - Compound score = max(rule_confidence, spoof_score) with 20% boost when
        both signals fire together.
    """
    transcript = state["transcript"].lower()
    spoof_score = state.get("spoof_score", 0.0)
    rule_flags = state.get("rule_flags", [])
    llm_flags = state.get("llm_red_flags", [])
    all_flags = list(dict.fromkeys(rule_flags + llm_flags))

    compound_flags: list[str] = list(all_flags)
    base_score = state.get("rule_confidence", 0.0)

    # Compounding rule: official impersonation + synthetic voice
    claims_official = any(
        kw in transcript
        for kw in ("cbi", "police", "enforcement", "customs", "narcotics", "income tax")
    )
    if claims_official and spoof_score > 0.5:
        compound_flags.append("official_impersonation_with_synthetic_voice")
        base_score = min(base_score + 0.2, 1.0)

    # Compounding rule: financial demand + voice anomaly
    financial_demand = any(
        kw in transcript
        for kw in ("transfer", "deposit", "upi", "account number", "otp", "payment")
    )
    if financial_demand and spoof_score > 0.3:
        compound_flags.append("financial_demand_during_suspicious_call")
        base_score = min(base_score + 0.15, 1.0)

    # If spoof is high but transcript looks benign, still flag it
    if spoof_score > 0.7 and "synthetic_voice_detected" not in compound_flags:
        compound_flags.append("high_spoof_score")

    compound_score = max(base_score, spoof_score * 0.6)

    return {"compound_flags": compound_flags, "compound_score": round(compound_score, 4)}


# ---------------------------------------------------------------------------
# Node 3: verdict
# ---------------------------------------------------------------------------

def _node_verdict(state: DeepCheckState) -> dict:
    """Node 3: produce final DeepCheckVerdict."""
    compound_score = state.get("compound_score", 0.0)
    spoof_score = state.get("spoof_score", 0.0)
    compound_flags = state.get("compound_flags", [])
    reasoning = state.get("llm_reasoning", "")

    if compound_score >= 0.75 or spoof_score >= 0.8:
        verdict = "scam"
    elif compound_score >= 0.5:
        verdict = "suspicious"
    elif compound_flags:
        verdict = "suspicious"
    else:
        verdict = "safe" if compound_score < 0.2 else "unclear"

    confidence = round(min(max(compound_score, spoof_score * 0.5), 1.0), 4)

    flags_str = ", ".join(compound_flags[:5]) if compound_flags else "none detected"
    summary = (
        f"Deep-check verdict: {verdict}. "
        f"Risk signals: {flags_str}. "
        f"AI-voice likelihood: {spoof_score:.0%}. "
        f"{reasoning}"
    ).strip()

    return {"verdict": verdict, "confidence": confidence, "summary": summary}


# ---------------------------------------------------------------------------
# Graph builder
# ---------------------------------------------------------------------------

def _build_graph() -> Any:
    g = StateGraph(DeepCheckState)
    g.add_node("extract_signals", _node_extract_signals)
    g.add_node("spoof_fusion", _node_spoof_fusion)
    g.add_node("produce_verdict", _node_verdict)
    g.set_entry_point("extract_signals")
    g.add_edge("extract_signals", "spoof_fusion")
    g.add_edge("spoof_fusion", "produce_verdict")
    g.add_edge("produce_verdict", END)
    return g.compile()


_graph = None


def _get_graph():
    global _graph
    if _graph is None:
        _graph = _build_graph()
    return _graph


# ---------------------------------------------------------------------------
# Public entry point
# ---------------------------------------------------------------------------

async def run_chain(
    transcript: str,
    spoof_result,   # SpoofResult from spoof_detector
    language: str = "en",
) -> DeepCheckVerdict:
    """
    Run the full deep-check chain for a transcript + spoof result.
    Returns a DeepCheckVerdict. assistive_only is always True.
    """
    initial_state: DeepCheckState = {
        "transcript": transcript,
        "language": language,
        "spoof_features": spoof_result.features.raw,
        "spoof_score": spoof_result.spoof_score,
        "rule_flags": [],
        "rule_confidence": 0.0,
        "llm_red_flags": [],
        "llm_evidence_spans": [],
        "llm_reasoning": "",
        "compound_flags": [],
        "compound_score": 0.0,
        "verdict": "unclear",
        "confidence": 0.0,
        "summary": "",
    }

    graph = _get_graph()
    final_state = await graph.ainvoke(initial_state)

    all_flags = list(dict.fromkeys(
        final_state.get("compound_flags", [])
    ))

    return DeepCheckVerdict(
        verdict=final_state["verdict"],
        confidence=final_state["confidence"],
        summary=final_state["summary"],
        red_flags=all_flags,
        evidence_spans=final_state.get("llm_evidence_spans", []),
        spoof_score=spoof_result.spoof_score,
    )
