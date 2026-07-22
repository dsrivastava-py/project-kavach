"""0002_add_device_api_key — add hashed API key to devices table

Revision ID: 0002
Revises: 0001
Create Date: 2026-07-10
"""
from alembic import op
import sqlalchemy as sa

revision = "0002"
down_revision = "0001"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Hashed API key — nullable so existing rows aren't broken.
    # The pairing flow issues and stores the hash; plaintext never persists.
    op.add_column(
        "devices",
        sa.Column("device_api_key_hash", sa.Text, nullable=True),
    )


def downgrade() -> None:
    op.drop_column("devices", "device_api_key_hash")
