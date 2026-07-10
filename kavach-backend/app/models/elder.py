import uuid
from sqlalchemy.dialects.postgresql import ENUM, UUID as PgUUID
from sqlalchemy.orm import Mapped, mapped_column
from app.core.db import Base
from app.models.base import TimestampUUIDMixin

onboarding_status_enum = ENUM("invited", "configured", "active", name="onboarding_status", create_type=False)


class Elder(TimestampUUIDMixin, Base):
    __tablename__ = "elders"
    family_id: Mapped[uuid.UUID] = mapped_column(PgUUID(as_uuid=True), nullable=False)
    user_id: Mapped[uuid.UUID] = mapped_column(PgUUID(as_uuid=True), unique=True, nullable=False)
    onboarding_status: Mapped[str] = mapped_column(onboarding_status_enum, nullable=False, server_default="invited")
