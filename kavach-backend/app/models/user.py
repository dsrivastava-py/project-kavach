import uuid
from sqlalchemy import Boolean, Text, UniqueConstraint
from sqlalchemy.dialects.postgresql import ENUM, UUID as PgUUID
from sqlalchemy.orm import Mapped, mapped_column
from app.core.db import Base
from app.models.base import TimestampUUIDMixin, SoftDeleteMixin

role_enum = ENUM("adult_child", "guardian", "elder", "investigator", name="role", create_type=False)
language_pref_enum = ENUM("hi", "en", "ta", "te", "bn", "mr", "gu", "kn", name="language_pref", create_type=False)


class User(TimestampUUIDMixin, SoftDeleteMixin, Base):
    __tablename__ = "users"
    family_id: Mapped[uuid.UUID] = mapped_column(PgUUID(as_uuid=True), nullable=False)
    role: Mapped[str] = mapped_column(role_enum, nullable=False)
    phone_e164: Mapped[str] = mapped_column(Text, unique=True, nullable=False)
    whatsapp_opt_in: Mapped[bool] = mapped_column(Boolean, nullable=False, server_default="false")
    language_pref: Mapped[str] = mapped_column(language_pref_enum, nullable=False, server_default="en")
