import uuid
from sqlalchemy import Enum, Uuid
from sqlalchemy.orm import Mapped, mapped_column
from app.core.db import Base
from app.models.base import TimestampUUIDMixin

onboarding_status_enum = Enum("invited", "configured", "active", name="onboarding_status")


class Elder(TimestampUUIDMixin, Base):
    __tablename__ = "elders"
    family_id: Mapped[uuid.UUID] = mapped_column(Uuid, nullable=False)
    user_id: Mapped[uuid.UUID] = mapped_column(Uuid, unique=True, nullable=False)
    onboarding_status: Mapped[str] = mapped_column(onboarding_status_enum, nullable=False, server_default="invited")
