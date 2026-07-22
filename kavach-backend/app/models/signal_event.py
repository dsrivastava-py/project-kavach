import uuid
from datetime import datetime
from sqlalchemy import DateTime, Index, func
from sqlalchemy.dialects.postgresql import ENUM, JSONB, UUID as PgUUID
from sqlalchemy.orm import Mapped, mapped_column
from app.core.db import Base
from app.models.base import TimestampUUIDMixin

signal_event_type_enum = ENUM(
    "call_start", "call_end", "video_call_start", "video_call_end",
    "screen_share_start", "screen_share_end", "foreground_app",
    "unknown_number", "first_time_payee", "banking_app_opened",
    name="signal_event_type", create_type=False,
)


class SignalEvent(TimestampUUIDMixin, Base):
    """Append-only. Write-heavy — indexed on (elder_id, occurred_at)."""
    __tablename__ = "signal_events"
    elder_id: Mapped[uuid.UUID] = mapped_column(PgUUID(as_uuid=True), nullable=False)
    device_id: Mapped[uuid.UUID] = mapped_column(PgUUID(as_uuid=True), nullable=False)
    event_type: Mapped[str] = mapped_column(signal_event_type_enum, nullable=False)
    payload: Mapped[dict] = mapped_column(JSONB, nullable=False, default=dict)
    occurred_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    ingested_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now()
    )

    __table_args__ = (
        Index("ix_signal_events_elder_time", "elder_id", "occurred_at"),
    )
