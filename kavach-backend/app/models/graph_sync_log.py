import uuid
from datetime import datetime
from sqlalchemy import DateTime, Text, Uuid
from sqlalchemy.orm import Mapped, mapped_column
from app.core.db import Base
from app.models.base import TimestampUUIDMixin


class GraphSyncLog(TimestampUUIDMixin, Base):
    """Postgres shadow log of what's pushed to Neo4j — for audit only."""
    __tablename__ = "graph_sync_log"
    entity_type: Mapped[str] = mapped_column(Text, nullable=False)
    entity_id: Mapped[uuid.UUID] = mapped_column(Uuid, nullable=False)
    neo4j_synced_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
