"""
Whisper transcription service.

Pulls audio bytes from MinIO, sends to Groq's whisper-large-v3-turbo
endpoint via the Groq API (using httpx directly — litellm audio is
non-standard). Returns TranscriptResult.

Used by:
  - deepcheck_chain (Phase 3 deep-check flow)
  - classifier._transcribe (Phase 1 WhatsApp voice note handling)
Don't fork — import this from both callers.
"""
from __future__ import annotations

import io
import time
from dataclasses import dataclass

import httpx

from app.core.config import get_settings
from app.core.logging import log


@dataclass
class TranscriptResult:
    text: str
    language: str
    latency_ms: int


class TranscriptionError(Exception):
    pass


async def transcribe(audio_ref: str, timeout_s: float = 15.0) -> TranscriptResult:
    """
    Pull audio bytes from MinIO by object key (audio_ref), send to
    Groq whisper-large-v3-turbo, return transcript text + language + latency_ms.

    Fails closed: raises TranscriptionError on timeout or API failure.
    Never returns partial results.
    """
    s = get_settings()
    t0 = time.monotonic()

    audio_bytes = await _pull_from_minio(audio_ref, s)

    if not s.GROQ_API_KEY:
        raise TranscriptionError("GROQ_API_KEY not configured")

    try:
        async with httpx.AsyncClient(timeout=timeout_s) as client:
            resp = await client.post(
                "https://api.groq.com/openai/v1/audio/transcriptions",
                headers={"Authorization": f"Bearer {s.GROQ_API_KEY}"},
                files={"file": (audio_ref.split("/")[-1] or "audio.ogg", audio_bytes, "audio/ogg")},
                data={"model": "whisper-large-v3-turbo", "response_format": "verbose_json"},
            )
            resp.raise_for_status()
            payload = resp.json()
    except httpx.TimeoutException as e:
        raise TranscriptionError(f"Groq whisper timeout after {timeout_s}s") from e
    except httpx.HTTPStatusError as e:
        raise TranscriptionError(f"Groq whisper API error {e.response.status_code}: {e.response.text}") from e

    latency_ms = int((time.monotonic() - t0) * 1000)
    text = payload.get("text", "").strip()
    language = payload.get("language", "en")

    log.info("whisper_transcribed", audio_ref=audio_ref, language=language, latency_ms=latency_ms)

    return TranscriptResult(text=text, language=language, latency_ms=latency_ms)


async def _pull_from_minio(audio_ref: str, s) -> bytes:
    """Download object from MinIO S3-compatible API and return raw bytes."""
    from minio import Minio

    client = Minio(
        s.MINIO_ENDPOINT,
        access_key=s.MINIO_ACCESS_KEY,
        secret_key=s.MINIO_SECRET_KEY,
        secure=False,
    )
    try:
        response = client.get_object(s.MINIO_BUCKET, audio_ref)
        data = response.read()
        response.close()
        response.release_conn()
        return data
    except Exception as e:
        raise TranscriptionError(f"MinIO pull failed for {audio_ref}: {e}") from e
