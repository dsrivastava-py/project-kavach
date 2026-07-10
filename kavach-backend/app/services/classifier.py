"""
Classifier — orchestrates rules_engine → RAG → LLM → verdict.
Rules run first (cheap, deterministic). High-confidence rules skip LLM entirely.
"""
from __future__ import annotations

import json
import time
from dataclasses import dataclass

from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import get_settings
from app.core.logging import log
from app.models.whatsapp_verdict import WhatsappVerdict
from app.services import llm_router, rag
from app.services.llm_router import LLMUnavailableError
from app.services.rules_engine import match_rules

_VERDICT_PROMPT = """\
You are an expert fraud analyst. Classify the following message as: scam, suspicious, safe, or unclear.

Known scam patterns matched: {rule_flags}

Similar known scam scripts for context:
{rag_context}

Message to classify:
\"\"\"
{message}
\"\"\"

Respond with JSON exactly:
{{"verdict": "scam|suspicious|safe|unclear", "confidence": 0.0-1.0, "red_flags": ["flag1", "flag2"], "reasoning": "one sentence"}}"""


@dataclass
class VerdictResult:
    verdict: str  # scam | suspicious | safe | unclear
    confidence: float
    red_flags: list[str]
    user_message: str
    llm_provider_used: str | None
    latency_ms: int


async def classify_message(
    text: str | None,
    voice_ref: str | None,
    language: str,
    sender_phone: str,
    session: AsyncSession,
) -> VerdictResult:
    t0 = time.monotonic()
    s = get_settings()

    # Step 1: transcribe voice if present (reuse whisper_service from Phase 3)
    if voice_ref and not text:
        text = await _transcribe(voice_ref)

    if not text:
        result = VerdictResult(
            verdict="unclear", confidence=0.0, red_flags=[],
            user_message=_build_user_message("unclear", [], language),
            llm_provider_used=None,
            latency_ms=int((time.monotonic() - t0) * 1000),
        )
        await _persist(session, sender_phone, "text", None, result, language)
        return result

    # Step 2: rules engine (always runs first)
    rule_result = match_rules(text, language)

    # Step 3: short-circuit if rules cross high-confidence threshold
    if rule_result.rule_confidence >= s.HIGH_CONFIDENCE_THRESHOLD:
        result = VerdictResult(
            verdict="scam",
            confidence=rule_result.rule_confidence,
            red_flags=rule_result.matched_flags,
            user_message=_build_user_message("scam", rule_result.matched_flags, language),
            llm_provider_used=None,
            latency_ms=int((time.monotonic() - t0) * 1000),
        )
        await _persist(session, sender_phone, "text", None, result, language)
        return result

    # Step 4: RAG context + LLM
    similar = await rag.retrieve_similar(text, k=5, session=session)
    rag_context = "\n---\n".join(
        f"[{m.red_flag_tags}] {m.script_text[:300]}" for m in similar
    ) or "None available"

    prompt = _VERDICT_PROMPT.format(
        rule_flags=rule_result.matched_flags or "none",
        rag_context=rag_context,
        message=text[:2000],
    )

    llm_result = None
    verdict_str = "unclear"
    confidence = 0.0
    llm_flags: list[str] = []
    provider_used: str | None = None

    try:
        llm_result = await llm_router.call_llm(
            prompt, task="verdict", session=session, timeout_s=4.0
        )
        parsed = json.loads(llm_result.content)
        verdict_str = parsed.get("verdict", "unclear")
        confidence = float(parsed.get("confidence", 0.0))
        llm_flags = parsed.get("red_flags", [])
        provider_used = llm_result.provider
    except (LLMUnavailableError, Exception) as e:
        log.warning("llm_unavailable_rules_only", error=str(e))
        # Degrade: rules-only verdict, lower confidence
        verdict_str = "suspicious" if rule_result.matched_flags else "unclear"
        confidence = rule_result.rule_confidence * 0.8

    # Step 5: hybrid policy — merge rule flags + LLM flags
    all_flags = list(dict.fromkeys(rule_result.matched_flags + llm_flags))
    # "scam" or "suspicious" if EITHER rules OR LLM crosses its threshold
    if rule_result.rule_confidence >= s.LLM_CONFIDENCE_THRESHOLD or confidence >= s.LLM_CONFIDENCE_THRESHOLD:
        if verdict_str not in ("scam", "suspicious"):
            verdict_str = "suspicious"
        final_confidence = max(rule_result.rule_confidence, confidence)
    else:
        final_confidence = confidence

    result = VerdictResult(
        verdict=verdict_str,
        confidence=final_confidence,
        red_flags=all_flags,
        user_message=_build_user_message(verdict_str, all_flags[:3], language),
        llm_provider_used=provider_used,
        latency_ms=int((time.monotonic() - t0) * 1000),
    )

    await _persist(session, sender_phone, "text", None, result, language)
    return result


def _build_user_message(verdict: str, flags: list[str], language: str) -> str:
    """
    Build user-facing WhatsApp reply. Keep as its own function so adding
    languages later doesn't touch classification logic.
    """
    flags_str = ", ".join(flags[:3]) if flags else "suspicious patterns"
    safe_next_step = "Hang up immediately. Real police/CBI never video-call. Call 1930 (National Cybercrime Helpline)."

    if verdict == "scam":
        if language == "hi":
            return (
                f"⚠️ *SCAM ALERT*: यह संदेश धोखाधड़ी है!\n"
                f"लाल झंडे: {flags_str}\n"
                f"अभी करें: {safe_next_step}"
            )
        return (
            f"⚠️ *SCAM ALERT*: This message is a known scam!\n"
            f"Red flags: {flags_str}\n"
            f"Do this now: {safe_next_step}"
        )
    elif verdict == "suspicious":
        if language == "hi":
            return (
                f"⚠️ *संदिग्ध*: यह संदेश संदिग्ध लगता है।\n"
                f"संकेत: {flags_str}\n"
                f"सावधान रहें। {safe_next_step}"
            )
        return (
            f"⚠️ *SUSPICIOUS*: This message shows warning signs.\n"
            f"Flags: {flags_str}\n"
            f"Be careful. {safe_next_step}"
        )
    elif verdict == "safe":
        return "✅ This message appears safe. Stay alert — scammers change tactics often."
    else:
        return (
            "❓ Could not determine if this is a scam. When in doubt, don't respond. "
            "Call 1930 if you're concerned."
        )


async def _transcribe(voice_ref: str) -> str | None:
    """Pull audio from MinIO, transcribe via Groq Whisper (whisper_service)."""
    from app.services.whisper_service import transcribe, TranscriptionError
    try:
        result = await transcribe(voice_ref, timeout_s=15.0)
        return result.text or None
    except TranscriptionError as e:
        log.warning("voice_transcribe_failed", ref=voice_ref, error=str(e))
        return None


async def _persist(
    session: AsyncSession,
    sender_phone: str,
    message_type: str,
    raw_content_ref: str | None,
    result: VerdictResult,
    language: str,
) -> None:
    row = WhatsappVerdict(
        family_id=None,
        sender_phone=sender_phone,
        message_type=message_type,
        raw_content_ref=raw_content_ref,
        verdict=result.verdict,
        matched_red_flags=result.red_flags,
        confidence=result.confidence,
        llm_provider_used=result.llm_provider_used,
        latency_ms=result.latency_ms,
        language=language,
    )
    session.add(row)
    await session.commit()
