import pytest
from app.core.db import engine

@pytest.fixture(autouse=True)
async def cleanup_connections():
    yield
    await engine.dispose()
