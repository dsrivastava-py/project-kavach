from fastapi import APIRouter, Depends
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession
import redis.asyncio as aioredis
from neo4j import AsyncGraphDatabase
from app.core.db import get_session
from app.core.config import get_settings

router = APIRouter(tags=["health"])


async def _check_db(session: AsyncSession) -> str:
    try:
        await session.execute(text("SELECT 1"))
        return "ok"
    except Exception:
        return "error"


async def _check_redis() -> str:
    r = None
    try:
        r = aioredis.from_url(get_settings().REDIS_URL)
        result = await r.ping()
        return "ok" if result else "error"
    except Exception:
        return "error"
    finally:
        if r:
            await r.aclose()


async def _check_neo4j() -> str:
    s = get_settings()
    try:
        drv = AsyncGraphDatabase.driver(s.NEO4J_URI, auth=(s.NEO4J_USER, s.NEO4J_PASSWORD))
        async with drv.session() as sess:
            result = await sess.run("RETURN 1 AS n")
            await result.consume()
        await drv.close()
        return "ok"
    except Exception:
        return "error"


@router.get("/health")
async def health(session: AsyncSession = Depends(get_session)) -> dict:
    db = await _check_db(session)
    redis_ok = await _check_redis()
    neo = await _check_neo4j()
    overall = "ok" if db == redis_ok == neo == "ok" else "degraded"
    return {"status": overall, "db": db, "redis": redis_ok, "neo4j": neo}
