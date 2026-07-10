import time
from dataclasses import dataclass
from typing import Literal

import litellm
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import get_settings
from app.core.logging import log
from app.models.llm_call_log import LlmCallLog


class LLMUnavailableError(Exception):
    pass


@dataclass
class LLMResult:
    content: str
    provider: str
    model: str
    tokens_in: int
    tokens_out: int
    latency_ms: int
    cost_usd: float


async def call_llm(
    prompt: str,
    task: Literal["verdict", "redflag_explain", "deepcheck_reasoning"],
    *,
    session: AsyncSession | None = None,
    timeout_s: float = 4.0,
    max_retries: int = 2,
) -> LLMResult:
    """
    Single-shot LLM call. Walks LLM_FALLBACK_ORDER on failure.
    deepcheck_reasoning is called from within the LangGraph chain (Phase 3).
    Writes llm_call_log row if session provided.
    """
    s = get_settings()
    model = s.task_model_map.get(task, s.TASK_MODEL_VERDICT)
    providers = s.llm_fallback_list
    last_error: Exception | None = None

    for provider in providers:
        provider_model = _provider_model(provider, task, s)
        t0 = time.monotonic()
        for attempt in range(max_retries + 1):
            try:
                resp = await litellm.acompletion(
                    model=provider_model,
                    messages=[{"role": "user", "content": prompt}],
                    timeout=timeout_s,
                    api_key=_api_key(provider, s) or None,
                )
                latency_ms = int((time.monotonic() - t0) * 1000)
                result = LLMResult(
                    content=resp.choices[0].message.content or "",
                    provider=provider,
                    model=provider_model,
                    tokens_in=resp.usage.prompt_tokens,
                    tokens_out=resp.usage.completion_tokens,
                    latency_ms=latency_ms,
                    cost_usd=litellm.completion_cost(completion_response=resp) or 0.0,
                )
                if session:
                    await _log_call(session, task, result, error=None)
                return result
            except Exception as e:
                last_error = e
                log.warning("llm_attempt_failed", provider=provider, attempt=attempt, error=str(e))

    if session and last_error:
        await _log_call_error(session, task, model, last_error)
    raise LLMUnavailableError(f"All providers failed. Last: {last_error}")


def _provider_model(provider: str, task: str, s) -> str:
    task_map = s.task_model_map
    model = task_map.get(task, s.TASK_MODEL_VERDICT)
    # If the configured model already names the provider prefix, use it directly
    if model.startswith(provider + "/"):
        return model
    # Otherwise pick a sensible default per provider
    defaults = {
        "groq": "groq/llama-3.3-70b-versatile",
        "openai": "gpt-4o-mini",
        "anthropic": "claude-haiku-4-5-20251001",
        "gemini": "gemini/gemini-1.5-flash",
    }
    return defaults.get(provider, model)


def _api_key(provider: str, s) -> str:
    return {
        "groq": s.GROQ_API_KEY,
        "openai": s.OPENAI_API_KEY,
        "anthropic": s.ANTHROPIC_API_KEY,
        "gemini": s.GEMINI_API_KEY,
    }.get(provider, "")


async def _log_call(session: AsyncSession, task: str, result: LLMResult, error) -> None:
    row = LlmCallLog(
        task=task,
        provider=result.provider,
        model=result.model,
        tokens_in=result.tokens_in,
        tokens_out=result.tokens_out,
        latency_ms=result.latency_ms,
        cost_estimate_usd=result.cost_usd,
    )
    session.add(row)
    await session.commit()


async def _log_call_error(session: AsyncSession, task: str, model: str, error: Exception) -> None:
    row = LlmCallLog(task=task, provider="failed", model=model)
    session.add(row)
    await session.commit()
