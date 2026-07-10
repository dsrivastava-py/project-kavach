import uuid
from sqlalchemy import Text
from sqlalchemy.dialects.postgresql import ENUM, UUID as PgUUID
from sqlalchemy.orm import Mapped, mapped_column
from app.core.db import Base
from app.models.base import TimestampUUIDMixin

plan_enum = ENUM("free", "family_99", "family_199", name="plan", create_type=False)
sub_status_enum = ENUM("stub_active", "stub_pending", name="sub_status", create_type=False)
sub_provider_enum = ENUM("razorpay", "stripe", name="sub_provider", create_type=False)


class Subscription(TimestampUUIDMixin, Base):
    """STUB TABLE — no live charge logic this build. See Phase 4."""
    __tablename__ = "subscriptions"
    family_id: Mapped[uuid.UUID] = mapped_column(PgUUID(as_uuid=True), nullable=False)
    plan: Mapped[str] = mapped_column(plan_enum, nullable=False)
    status: Mapped[str] = mapped_column(sub_status_enum, nullable=False)
    provider: Mapped[str | None] = mapped_column(sub_provider_enum, nullable=True)
    external_ref: Mapped[str | None] = mapped_column(Text, nullable=True)
