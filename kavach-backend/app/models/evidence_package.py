import uuid
from datetime import datetime
from sqlalchemy import DateTime, JSON, Text, Uuid
from sqlalchemy.orm import Mapped, mapped_column
from app.core.db import Base
from app.models.base import TimestampUUIDMixin


class EvidencePackage(TimestampUUIDMixin, Base):
    """Append-only."""
    __tablename__ = "evidence_packages"
    incident_id: Mapped[uuid.UUID] = mapped_column(Uuid, nullable=False)
    hash_chain: Mapped[dict] = mapped_column(JSON, nullable=False, default=dict)
    pdf_ref: Mapped[str] = mapped_column(Text, nullable=False)
    generated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    section_65b_cert_ref: Mapped[str | None] = mapped_column(Text, nullable=True)
