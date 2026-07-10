"""Alembic env — async SQLAlchemy."""
import asyncio
from logging.config import fileConfig

from sqlalchemy import pool
from sqlalchemy.ext.asyncio import async_engine_from_config
from alembic import context

# this is the Alembic Config object
config = context.config

if config.config_file_name is not None:
    fileConfig(config.config_file_name)

# Import ALL models so Base.metadata is fully populated
from app.core.db import Base  # noqa: F401
import app.models.family       # noqa: F401
import app.models.user         # noqa: F401
import app.models.elder        # noqa: F401
import app.models.guardian     # noqa: F401
import app.models.device       # noqa: F401
import app.models.consent_event  # noqa: F401
import app.models.signal_event   # noqa: F401
import app.models.incident       # noqa: F401
import app.models.incident_signal  # noqa: F401
import app.models.alert          # noqa: F401
import app.models.llm_call_log   # noqa: F401
import app.models.whatsapp_verdict  # noqa: F401
import app.models.scam_corpus    # noqa: F401
import app.models.deepcheck_session  # noqa: F401
import app.models.graph_sync_log  # noqa: F401
import app.models.evidence_package  # noqa: F401
import app.models.subscription   # noqa: F401

from app.core.config import get_settings

target_metadata = Base.metadata


def get_url() -> str:
    return get_settings().DATABASE_URL


def run_migrations_offline() -> None:
    url = get_url()
    context.configure(
        url=url,
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
    )
    with context.begin_transaction():
        context.run_migrations()


def do_run_migrations(connection):
    context.configure(connection=connection, target_metadata=target_metadata)
    with context.begin_transaction():
        context.run_migrations()


async def run_async_migrations() -> None:
    cfg = config.get_section(config.config_ini_section, {})
    cfg["sqlalchemy.url"] = get_url()
    connectable = async_engine_from_config(
        cfg,
        prefix="sqlalchemy.",
        poolclass=pool.NullPool,
    )
    async with connectable.connect() as connection:
        await connection.run_sync(do_run_migrations)
    await connectable.dispose()


def run_migrations_online() -> None:
    asyncio.run(run_async_migrations())


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
