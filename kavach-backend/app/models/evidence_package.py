import uuid
from datetime import datetime
from sqlalchemy import DateTime, Text
from sqlalchemy.dialects.postgresql import JSONB, UUID as PgUUID
from sqlalchemy.orm import Mapped, mapped_column
from app.core.db import Base
from app.models.base import TimestampUUIDMixin


class EvidencePackage(TimestampUUIDMixin, Base):
    """Append-only."""
    __tablename__ = "evidence_packages"
    incident_id: Mapped[uuid.UUID] = mapped_column(PgUUID(as_uuid=True), nullable=False)
    hash_chain: Mapped[dict] = mapped_column(JSONB, nullable=False, default=dict)
    pdf_ref: Mapped[str] = mapped_column(Text, nullable=False)
    generated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    section_65b_cert_ref: Mapped[str | None] = mapped_column(Text, nullable=True)
