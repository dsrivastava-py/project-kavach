import uuid
from datetime import datetime
from sqlalchemy import DateTime, Enum, Uuid
from sqlalchemy.orm import Mapped, mapped_column
from app.core.db import Base
from app.models.base import TimestampUUIDMixin

alert_channel_enum = Enum("push", "call_prompt", name="alert_channel")


class Alert(TimestampUUIDMixin, Base):
    """Append-only."""
    __tablename__ = "alerts"
    incident_id: Mapped[uuid.UUID] = mapped_column(Uuid, nullable=False)
    guardian_id: Mapped[uuid.UUID] = mapped_column(Uuid, nullable=False)
    channel: Mapped[str] = mapped_column(alert_channel_enum, nullable=False)
    sent_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    acknowledged_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
