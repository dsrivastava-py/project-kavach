import uuid
from sqlalchemy import Enum, Text, Uuid
from sqlalchemy.orm import Mapped, mapped_column
from app.core.db import Base
from app.models.base import TimestampUUIDMixin

plan_enum = Enum("free", "family_99", "family_199", name="plan")
sub_status_enum = Enum("stub_active", "stub_pending", name="sub_status")
sub_provider_enum = Enum("razorpay", "stripe", name="sub_provider")


class Subscription(TimestampUUIDMixin, Base):
    """STUB TABLE — no live charge logic this build. See Phase 4."""
    __tablename__ = "subscriptions"
    family_id: Mapped[uuid.UUID] = mapped_column(Uuid, nullable=False)
    plan: Mapped[str] = mapped_column(plan_enum, nullable=False)
    status: Mapped[str] = mapped_column(sub_status_enum, nullable=False)
    provider: Mapped[str | None] = mapped_column(sub_provider_enum, nullable=True)
    external_ref: Mapped[str | None] = mapped_column(Text, nullable=True)
