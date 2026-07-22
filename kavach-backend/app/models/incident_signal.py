import uuid
from sqlalchemy import Column, ForeignKey
from sqlalchemy.dialects.postgresql import UUID as PgUUID
from app.core.db import Base


class IncidentSignal(Base):
    """Composite PK join table — no UUID/timestamp mixin."""
    __tablename__ = "incident_signals"
    incident_id = Column(PgUUID(as_uuid=True), ForeignKey("incidents.id"), primary_key=True)
    signal_event_id = Column(PgUUID(as_uuid=True), ForeignKey("signal_events.id"), primary_key=True)
