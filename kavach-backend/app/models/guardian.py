import uuid
from sqlalchemy import Integer, UniqueConstraint
from sqlalchemy.dialects.postgresql import UUID as PgUUID
from sqlalchemy.orm import Mapped, mapped_column
from app.core.db import Base
from app.models.base import TimestampUUIDMixin


class Guardian(TimestampUUIDMixin, Base):
    __tablename__ = "guardians"
    family_id: Mapped[uuid.UUID] = mapped_column(PgUUID(as_uuid=True), nullable=False)
    user_id: Mapped[uuid.UUID] = mapped_column(PgUUID(as_uuid=True), nullable=False)
    elder_id: Mapped[uuid.UUID] = mapped_column(PgUUID(as_uuid=True), nullable=False)
    priority_order: Mapped[int] = mapped_column(Integer, nullable=False)

    __table_args__ = (UniqueConstraint("elder_id", "user_id"),)
