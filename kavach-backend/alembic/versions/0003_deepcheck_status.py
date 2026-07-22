"""0003_deepcheck_status — add status column to deepcheck_sessions

Revision ID: 0003
Revises: 0002
Create Date: 2026-07-10
"""
from alembic import op
import sqlalchemy as sa

revision = "0003"
down_revision = "0002"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute(
        "CREATE TYPE deepcheck_status AS ENUM "
        "('pending', 'transcribing', 'analyzing', 'done', 'failed')"
    )
    op.add_column(
        "deepcheck_sessions",
        sa.Column(
            "status",
            sa.Enum(
                "pending", "transcribing", "analyzing", "done", "failed",
                name="deepcheck_status",
                create_type=False,
            ),
            nullable=False,
            server_default="pending",
        ),
    )


def downgrade() -> None:
    op.drop_column("deepcheck_sessions", "status")
    op.execute("DROP TYPE deepcheck_status")
