"""
Smoke test: /health returns 200 with 4-key structure.
Run against live stack: pytest app/tests/test_health.py
"""
import pytest
import httpx


@pytest.mark.asyncio
async def test_health_endpoint():
    async with httpx.AsyncClient(base_url="http://localhost:8000") as client:
        resp = await client.get("/health")
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] in ("ok", "degraded")
    assert "db" in data
    assert "redis" in data
    assert "neo4j" in data


@pytest.mark.asyncio
async def test_health_all_ok():
    async with httpx.AsyncClient(base_url="http://localhost:8000") as client:
        resp = await client.get("/health")
    data = resp.json()
    assert data["status"] == "ok", f"Health not fully ok: {data}"
    assert data["db"] == "ok"
    assert data["redis"] == "ok"
    assert data["neo4j"] == "ok"


@pytest.mark.asyncio
async def test_metrics_endpoint():
    async with httpx.AsyncClient(base_url="http://localhost:8000") as client:
        resp = await client.get("/metrics")
    assert resp.status_code == 200
    assert "python_gc_objects_collected_total" in resp.text or "# HELP" in resp.text
