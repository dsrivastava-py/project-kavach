from sqlalchemy import Enum, JSON, Text
from sqlalchemy.orm import Mapped, mapped_column
from app.core.db import Base
from app.models.base import TimestampUUIDMixin

scam_source_enum = Enum("press", "advisory", "synthetic", "user_reported", name="scam_source")


class ScamCorpus(TimestampUUIDMixin, Base):
    __tablename__ = "scam_corpus"
    source: Mapped[str] = mapped_column(scam_source_enum, nullable=False)
    script_text: Mapped[str] = mapped_column(Text, nullable=False)
    red_flag_tags: Mapped[list] = mapped_column(JSON, nullable=False, default=list)
    embedding: Mapped[list | None] = mapped_column(JSON, nullable=True)
