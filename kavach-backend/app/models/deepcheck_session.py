import uuid
from sqlalchemy import Float, Integer, Text
from sqlalchemy.dialects.postgresql import ENUM, JSONB, UUID as PgUUID
from sqlalchemy.orm import Mapped, mapped_column
from app.core.db import Base
from app.models.base import TimestampUUIDMixin

deepcheck_status_enum = ENUM(
    "pending", "transcribing", "analyzing", "done", "failed",
    name="deepcheck_status", create_type=False,
)


class DeepcheckSession(TimestampUUIDMixin, Base):
    __tablename__ = "deepcheck_sessions"
    incident_id: Mapped[uuid.UUID | None] = mapped_column(PgUUID(as_uuid=True), nullable=True)
    elder_id: Mapped[uuid.UUID] = mapped_column(PgUUID(as_uuid=True), nullable=False)
    audio_ref: Mapped[str] = mapped_column(Text, nullable=False)
    status: Mapped[str] = mapped_column(deepcheck_status_enum, nullable=False, server_default="pending")
    transcript: Mapped[str | None] = mapped_column(Text, nullable=True)
    whisper_latency_ms: Mapped[int | None] = mapped_column(Integer, nullable=True)
    red_flags: Mapped[dict | None] = mapped_column(JSONB, nullable=True)
    spoof_score: Mapped[float | None] = mapped_column(Float, nullable=True)
    # Always paired with assistive_only=true + disclaimer in API response
    spoof_features: Mapped[dict | None] = mapped_column(JSONB, nullable=True)
