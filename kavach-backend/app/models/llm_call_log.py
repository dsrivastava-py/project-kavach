from decimal import Decimal
from sqlalchemy import Integer, Numeric, Text
from sqlalchemy.orm import Mapped, mapped_column
from app.core.db import Base
from app.models.base import TimestampUUIDMixin


class LlmCallLog(TimestampUUIDMixin, Base):
    __tablename__ = "llm_call_log"
    task: Mapped[str] = mapped_column(Text, nullable=False)
    provider: Mapped[str] = mapped_column(Text, nullable=False)
    model: Mapped[str] = mapped_column(Text, nullable=False)
    tokens_in: Mapped[int | None] = mapped_column(Integer, nullable=True)
    tokens_out: Mapped[int | None] = mapped_column(Integer, nullable=True)
    latency_ms: Mapped[int | None] = mapped_column(Integer, nullable=True)
    cost_estimate_usd: Mapped[Decimal | None] = mapped_column(Numeric(12, 8), nullable=True)
