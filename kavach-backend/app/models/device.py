import uuid
from datetime import datetime
from sqlalchemy import DateTime, Text
from sqlalchemy.dialects.postgresql import ENUM, JSONB, UUID as PgUUID
from sqlalchemy.orm import Mapped, mapped_column
from app.core.db import Base
from app.models.base import TimestampUUIDMixin

platform_enum = ENUM("android", "ios", name="platform", create_type=False)


class Device(TimestampUUIDMixin, Base):
    __tablename__ = "devices"
    elder_id: Mapped[uuid.UUID] = mapped_column(PgUUID(as_uuid=True), nullable=False)
    platform: Mapped[str] = mapped_column(platform_enum, nullable=False)
    fcm_token: Mapped[str | None] = mapped_column(Text, nullable=True)
    app_version: Mapped[str | None] = mapped_column(Text, nullable=True)
    permissions_granted: Mapped[dict] = mapped_column(JSONB, nullable=False, default=dict)
    last_seen_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    device_api_key_hash: Mapped[str | None] = mapped_column(Text, nullable=True)
