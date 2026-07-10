"""
Token-bucket rate limiter backed by Redis.

Applied only to public/device-facing webhook surfaces:
  - POST /api/v1/webhooks/whatsapp  (keyed per sender_phone)
  - POST /api/v1/signals/ingest     (keyed per device_id)

Internal dashboard and investigator routes are NOT rate-limited.
"""
from __future__ import annotations

import json
import time

import redis.asyncio as aioredis
from fastapi import Request, Response
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.types import ASGIApp

from app.core.config import get_settings

_RATE_LIMITED_PATHS = {
    "/api/v1/webhooks/whatsapp",
    "/api/v1/signals/ingest",
}


def _path_matches(path: str) -> bool:
    return any(path.startswith(p) for p in _RATE_LIMITED_PATHS)


async def _extract_key(path: str, body: bytes) -> str | None:
    """Extract the rate-limit bucket key from the request body."""
    try:
        data = json.loads(body)
    except (json.JSONDecodeError, ValueError):
        return None

    if "/webhooks/whatsapp" in path:
        # Meta format: entry[0].changes[0].value.messages[0].from
        try:
            return data["entry"][0]["changes"][0]["value"]["messages"][0]["from"]
        except (KeyError, IndexError, TypeError):
            # Twilio format: From field
            return data.get("From") or data.get("from")

    if "/signals/ingest" in path:
        device_id = data.get("device_id")
        return str(device_id) if device_id else None

    return None


class RateLimitMiddleware(BaseHTTPMiddleware):
    def __init__(self, app: ASGIApp) -> None:
        super().__init__(app)
        self._settings = get_settings()

    async def dispatch(self, request: Request, call_next) -> Response:
        s = self._settings

        if not s.RATE_LIMIT_ENABLED or request.method != "POST" or not _path_matches(request.url.path):
            return await call_next(request)

        # Read body; must cache it so the downstream handler can also read it
        body = await request.body()

        # Re-inject body for downstream handlers (Starlette streams are consumed once)
        async def _body_iterator():
            yield body

        request._body = body  # noqa: SLF001  # Starlette caches via this attr

        bucket_key = await _extract_key(request.url.path, body)
        if bucket_key is None:
            # Can't identify caller — let request through; downstream auth will reject invalid payloads
            return await call_next(request)

        rps = (
            s.RATE_LIMIT_WHATSAPP_RPS
            if "/webhooks/whatsapp" in request.url.path
            else s.RATE_LIMIT_SIGNALS_RPS
        )

        allowed = await _check_token_bucket(request, bucket_key, rps)
        if not allowed:
            return Response(
                content=json.dumps({"error": "rate_limit_exceeded", "retry_after": 1}),
                status_code=429,
                headers={"Retry-After": "1", "Content-Type": "application/json"},
            )

        return await call_next(request)


async def _check_token_bucket(request: Request, key: str, rps: int) -> bool:
    """
    Sliding-window counter using Redis INCR + EXPIRE.

    Simple approximation: allow `rps` requests per second per key.
    Uses a 1-second window bucket — cheap and sufficient for webhook abuse prevention.
    """
    redis_client: aioredis.Redis = request.app.state.redis  # type: ignore[attr-defined]
    if redis_client is None:
        return True  # Redis unavailable → fail open (don't block legitimate traffic)

    ts = int(time.time())
    redis_key = f"rl:{key}:{ts}"

    try:
        pipe = redis_client.pipeline(transaction=True)
        pipe.incr(redis_key)
        pipe.expire(redis_key, 2)  # keep 2s window for safety
        results = await pipe.execute()
        count = results[0]
        return count <= rps
    except Exception:
        return True  # Redis error → fail open
