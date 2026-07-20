import uuid
from sqlalchemy import Column, ForeignKey, Uuid
from app.core.db import Base


class IncidentSignal(Base):
    """Composite PK join table — no UUID/timestamp mixin."""
    __tablename__ = "incident_signals"
    incident_id = Column(Uuid, ForeignKey("incidents.id"), primary_key=True)
    signal_event_id = Column(Uuid, ForeignKey("signal_events.id"), primary_key=True)
