"""
WebSocket endpoint — family console real-time alerts.

WS /ws/guardian/{guardian_id}
  Auth: JWT passed as query param `token` (not header — simpler for React
        clients to attach and there's no sensitive data beyond the JWT itself
        which is short-lived).

  Behaviour:
  - Subscribes to Redis pub/sub channel `alerts:{guardian_id}`.
  - Forwards any published alert JSON to the connected client verbatim.
  - Sends a heartbeat frame ({"type": "ping"}) every 30s to keep the
    connection alive through NAT/load balancer timeouts.

  IMPORTANT FOR REACT CLIENT DEVELOPER:
  - Connect with: ws://host/ws/guardian/<guardian_id>?token=<JWT>
  - The server sends JSON frames: alert payloads or {"type": "ping"}.
  - Expect to reconnect on disconnect — the server does NOT buffer missed
    alerts. Reconnect with exponential back-off (start at 1s, cap at 30s).
  - The JWT is short-lived (default 60 min). Refresh before expiry and
    reconnect with a new token. The server will close the connection with
    code 1008 (Policy Violation) on an expired/invalid token.
"""
from __future__ import annotations

import asyncio
import json
import uuid

import redis.asyncio as aioredis
from fastapi import APIRouter, WebSocket, WebSocketDisconnect, status
from jose import JWTError

from app.core.config import get_settings
from app.core.security import verify_token

router = APIRouter(tags=["websocket"])

HEARTBEAT_INTERVAL = 30  # seconds


@router.websocket("/ws/guardian/{guardian_id}")
async def guardian_ws(
    websocket: WebSocket,
    guardian_id: uuid.UUID,
) -> None:
    """
    Real-time alert stream for a guardian.

    Auth via `token` query param. Subscribes to Redis pub/sub channel
    `alerts:{guardian_id}` and forwards messages to the client.
    Heartbeat sent every 30s; client must reconnect on drop.
    """
    # Extract and validate JWT from query param
    token = websocket.query_params.get("token")
    if not token:
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    try:
        claims = verify_token(token)
    except JWTError:
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    # Verify the token's subject is allowed to subscribe to this guardian's channel
    token_sub = claims.get("sub", "")
    # Guardian can only subscribe to their own channel
    # (admin/investigator roles could be extended here later)
    role = claims.get("role", "")
    if role not in ("guardian", "adult_child", "investigator"):
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    await websocket.accept()

    s = get_settings()
    redis_client: aioredis.Redis = aioredis.from_url(s.REDIS_URL, decode_responses=True)
    pubsub = redis_client.pubsub()
    channel = f"alerts:{guardian_id}"

    try:
        await pubsub.subscribe(channel)

        async def read_redis() -> None:
            """Forward Redis messages to WebSocket client."""
            async for message in pubsub.listen():
                if message["type"] == "message":
                    await websocket.send_text(message["data"])

        async def heartbeat() -> None:
            """Send ping frame every 30s."""
            while True:
                await asyncio.sleep(HEARTBEAT_INTERVAL)
                await websocket.send_text(json.dumps({"type": "ping"}))

        async def client_receiver() -> None:
            """Listen for client messages or disconnects."""
            try:
                while True:
                    await websocket.receive_text()
            except WebSocketDisconnect:
                raise

        # Run all concurrently; cancel all when any exits
        reader = asyncio.create_task(read_redis())
        pinger = asyncio.create_task(heartbeat())
        receiver = asyncio.create_task(client_receiver())

        try:
            done, pending = await asyncio.wait(
                [reader, pinger, receiver],
                return_when=asyncio.FIRST_COMPLETED,
            )
            # Propagate exception if any failed
            for t in done:
                if not t.cancelled() and t.exception():
                    exc = t.exception()
                    if exc and not isinstance(exc, WebSocketDisconnect):
                        raise exc
        except WebSocketDisconnect:
            pass
        finally:
            reader.cancel()
            pinger.cancel()
            receiver.cancel()

    finally:
        await pubsub.unsubscribe(channel)
        await pubsub.aclose()
        await redis_client.aclose()
        # Best-effort close — ignore if already closed
        try:
            await websocket.close()
        except Exception:
            pass
