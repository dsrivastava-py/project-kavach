from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, PlainTextResponse
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest

from app.core.config import get_settings
from app.core.logging import configure_logging
from app.core.rate_limit import RateLimitMiddleware
from app.api.v1 import health
from app.api.v1 import whatsapp
from app.api.v1 import signals
from app.api.v1 import guardians
from app.api.v1 import ws
from app.api.v1 import deepcheck
from app.api.v1 import graph
from app.api.v1 import billing
from app.schemas.common import ErrorResponse


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Manage app-level resources: Redis connection for rate limiting."""
    import redis.asyncio as aioredis
    settings = get_settings()
    app.state.redis = aioredis.from_url(settings.REDIS_URL, decode_responses=False)
    try:
        yield
    finally:
        if hasattr(app.state, "redis") and app.state.redis:
            await app.state.redis.aclose()


def create_app() -> FastAPI:
    configure_logging()
    settings = get_settings()
    app = FastAPI(title="Kavach Backend", version="0.1.0", lifespan=lifespan)

    # CORS — explicit allowed origins, never wildcard
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.CORS_ALLOWED_ORIGINS,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # Rate limiting — only applies to public webhook surfaces
    app.add_middleware(RateLimitMiddleware)

    @app.exception_handler(Exception)
    async def global_exc(request: Request, exc: Exception) -> JSONResponse:
        return JSONResponse(
            status_code=500,
            content=ErrorResponse(
                code="internal_error",
                message="An unexpected error occurred.",
                detail=None,
            ).model_dump(),
        )

    # Health probe — both prefixed and unprefixed (for container orchestration)
    app.include_router(health.router, prefix="/api/v1")
    app.include_router(health.router)

    # Phase 1 — WhatsApp bot
    app.include_router(whatsapp.router, prefix="/api/v1")

    # Phase 2 — Signal ingestion, guardian pairing, WebSocket
    app.include_router(signals.router, prefix="/api/v1")
    app.include_router(guardians.router, prefix="/api/v1")
    app.include_router(ws.router)  # WebSocket — no /api/v1 prefix (WS path is /ws/...)

    # Phase 3 — Deep-check, fraud graph
    app.include_router(deepcheck.router, prefix="/api/v1")
    app.include_router(graph.router, prefix="/api/v1")

    # Phase 4 — Billing stub
    app.include_router(billing.router, prefix="/api/v1")

    @app.get("/metrics")
    def metrics() -> PlainTextResponse:
        return PlainTextResponse(generate_latest(), media_type=CONTENT_TYPE_LATEST)

    return app


app = create_app()

