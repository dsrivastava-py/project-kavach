from sqlalchemy import ARRAY, Text
from sqlalchemy.dialects.postgresql import ENUM
from sqlalchemy.orm import Mapped, mapped_column
from pgvector.sqlalchemy import Vector
from app.core.db import Base
from app.models.base import TimestampUUIDMixin

scam_source_enum = ENUM("press", "advisory", "synthetic", "user_reported", name="scam_source", create_type=False)


class ScamCorpus(TimestampUUIDMixin, Base):
    __tablename__ = "scam_corpus"
    source: Mapped[str] = mapped_column(scam_source_enum, nullable=False)
    script_text: Mapped[str] = mapped_column(Text, nullable=False)
    red_flag_tags: Mapped[list] = mapped_column(ARRAY(Text), nullable=False, default=list)
    embedding: Mapped[list | None] = mapped_column(Vector(1536), nullable=True)
    # ivfflat index deferred until corpus > ~1000 rows
