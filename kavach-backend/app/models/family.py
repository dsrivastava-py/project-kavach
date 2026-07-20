from sqlalchemy import Enum, String, Text
from sqlalchemy.orm import Mapped, mapped_column
from app.core.db import Base
from app.models.base import TimestampUUIDMixin

plan_tier_enum = Enum("free", "family_99", "family_199", name="plan_tier")


class Family(TimestampUUIDMixin, Base):
    __tablename__ = "families"
    name: Mapped[str] = mapped_column(Text, nullable=False)
    plan_tier: Mapped[str] = mapped_column(plan_tier_enum, nullable=False, server_default="free")
