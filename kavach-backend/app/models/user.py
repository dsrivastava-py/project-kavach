import uuid
from sqlalchemy import Boolean, Enum, String, UniqueConstraint, Uuid
from sqlalchemy.orm import Mapped, mapped_column
from app.core.db import Base
from app.models.base import TimestampUUIDMixin, SoftDeleteMixin

role_enum = Enum("adult_child", "guardian", "elder", "investigator", name="role")
language_pref_enum = Enum("hi", "en", "ta", "te", "bn", "mr", "gu", "kn", name="language_pref")


class User(TimestampUUIDMixin, SoftDeleteMixin, Base):
    __tablename__ = "users"
    family_id: Mapped[uuid.UUID] = mapped_column(Uuid, nullable=False)
    role: Mapped[str] = mapped_column(role_enum, nullable=False)
    phone_e164: Mapped[str] = mapped_column(String(64), unique=True, nullable=False)
    whatsapp_opt_in: Mapped[bool] = mapped_column(Boolean, nullable=False, server_default="0")
    language_pref: Mapped[str] = mapped_column(language_pref_enum, nullable=False, server_default="en")
