import uuid
from datetime import datetime
from sqlalchemy import DateTime, Enum, Float, Text, Uuid
from sqlalchemy.orm import Mapped, mapped_column
from app.core.db import Base
from app.models.base import TimestampUUIDMixin

incident_status_enum = Enum(
    "open", "graduated_1", "graduated_2", "graduated_3", "graduated_4",
    "resolved", "false_positive",
    name="incident_status",
)


class Incident(TimestampUUIDMixin, Base):
    __tablename__ = "incidents"
    elder_id: Mapped[uuid.UUID] = mapped_column(Uuid, nullable=False)
    status: Mapped[str] = mapped_column(incident_status_enum, nullable=False, server_default="open")
    risk_score: Mapped[float] = mapped_column(Float, nullable=False, server_default="0")
    started_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    resolved_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    resolution_note: Mapped[str | None] = mapped_column(Text, nullable=True)
