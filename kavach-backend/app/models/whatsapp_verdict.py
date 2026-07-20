import uuid
from sqlalchemy import Enum, Float, Integer, JSON, Text, Uuid
from sqlalchemy.orm import Mapped, mapped_column
from app.core.db import Base
from app.models.base import TimestampUUIDMixin

message_type_enum = Enum("text", "image", "voice", "forwarded", name="message_type")
verdict_enum = Enum("scam", "suspicious", "safe", "unclear", name="verdict")


class WhatsappVerdict(TimestampUUIDMixin, Base):
    """Append-only."""
    __tablename__ = "whatsapp_verdicts"
    family_id: Mapped[uuid.UUID | None] = mapped_column(Uuid, nullable=True)
    sender_phone: Mapped[str] = mapped_column(Text, nullable=False)
    message_type: Mapped[str] = mapped_column(message_type_enum, nullable=False)
    raw_content_ref: Mapped[str | None] = mapped_column(Text, nullable=True)
    verdict: Mapped[str] = mapped_column(verdict_enum, nullable=False)
    matched_red_flags: Mapped[dict] = mapped_column(JSON, nullable=False, default=list)
    confidence: Mapped[float | None] = mapped_column(Float, nullable=True)
    llm_provider_used: Mapped[str | None] = mapped_column(Text, nullable=True)
    latency_ms: Mapped[int | None] = mapped_column(Integer, nullable=True)
    language: Mapped[str | None] = mapped_column(Text, nullable=True)
