"""0001_initial — all tables for kavach-backend Phase 0

Revision ID: 0001
Revises:
Create Date: 2026-07-10
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    # ----- Extension -----
    op.execute("CREATE EXTENSION IF NOT EXISTS vector")

    # ----- ENUM types (created once here; models use create_type=False) -----
    plan_tier = postgresql.ENUM(
        "free", "family_99", "family_199", name="plan_tier", create_type=True
    )
    role = postgresql.ENUM(
        "adult_child", "guardian", "elder", "investigator", name="role", create_type=True
    )
    language_pref = postgresql.ENUM(
        "hi", "en", "ta", "te", "bn", "mr", "gu", "kn", name="language_pref", create_type=True
    )
    onboarding_status = postgresql.ENUM(
        "invited", "configured", "active", name="onboarding_status", create_type=True
    )
    platform = postgresql.ENUM("android", "ios", name="platform", create_type=True)
    signal_event_type = postgresql.ENUM(
        "call_start", "call_end", "video_call_start", "video_call_end",
        "screen_share_start", "screen_share_end", "foreground_app",
        "unknown_number", "first_time_payee", "banking_app_opened",
        name="signal_event_type", create_type=True,
    )
    incident_status = postgresql.ENUM(
        "open", "graduated_1", "graduated_2", "graduated_3", "graduated_4",
        "resolved", "false_positive",
        name="incident_status", create_type=True,
    )
    alert_channel = postgresql.ENUM(
        "push", "call_prompt", name="alert_channel", create_type=True
    )
    message_type = postgresql.ENUM(
        "text", "image", "voice", "forwarded", name="message_type", create_type=True
    )
    verdict = postgresql.ENUM(
        "scam", "suspicious", "safe", "unclear", name="verdict", create_type=True
    )
    scam_source = postgresql.ENUM(
        "press", "advisory", "synthetic", "user_reported", name="scam_source", create_type=True
    )
    plan = postgresql.ENUM("free", "family_99", "family_199", name="plan", create_type=True)
    sub_status = postgresql.ENUM(
        "stub_active", "stub_pending", name="sub_status", create_type=True
    )
    sub_provider = postgresql.ENUM(
        "razorpay", "stripe", name="sub_provider", create_type=True
    )

    for e in [
        plan_tier, role, language_pref, onboarding_status, platform,
        signal_event_type, incident_status, alert_channel, message_type,
        verdict, scam_source, plan, sub_status, sub_provider,
    ]:
        e.create(op.get_bind(), checkfirst=True)

    # ----- Table 1: families -----
    op.create_table(
        "families",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("name", sa.Text, nullable=False),
        sa.Column("plan_tier", plan_tier, nullable=False, server_default="free"),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
    )

    # ----- Table 2: users -----
    op.create_table(
        "users",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("family_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("role", role, nullable=False),
        sa.Column("phone_e164", sa.Text, unique=True, nullable=False),
        sa.Column("whatsapp_opt_in", sa.Boolean, nullable=False, server_default="false"),
        sa.Column("language_pref", language_pref, nullable=False, server_default="en"),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.Column("deleted_at", sa.DateTime(timezone=True), nullable=True),
        sa.ForeignKeyConstraint(["family_id"], ["families.id"]),
    )

    # ----- Table 3: elders -----
    op.create_table(
        "elders",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("family_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), unique=True, nullable=False),
        sa.Column("onboarding_status", onboarding_status, nullable=False, server_default="invited"),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.ForeignKeyConstraint(["family_id"], ["families.id"]),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"]),
    )

    # ----- Table 4: guardians -----
    op.create_table(
        "guardians",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("family_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("elder_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("priority_order", sa.Integer, nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.ForeignKeyConstraint(["family_id"], ["families.id"]),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"]),
        sa.ForeignKeyConstraint(["elder_id"], ["elders.id"]),
        sa.UniqueConstraint("elder_id", "user_id"),
    )

    # ----- Table 5: devices -----
    op.create_table(
        "devices",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("elder_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("platform", platform, nullable=False),
        sa.Column("fcm_token", sa.Text, nullable=True),
        sa.Column("app_version", sa.Text, nullable=True),
        sa.Column("permissions_granted", postgresql.JSONB, nullable=False, server_default="{}"),
        sa.Column("last_seen_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.ForeignKeyConstraint(["elder_id"], ["elders.id"]),
    )

    # ----- Table 6: consent_events (INSERT-ONLY) -----
    op.create_table(
        "consent_events",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("signal_type", sa.Text, nullable=False),
        sa.Column("granted", sa.Boolean, nullable=False),
        sa.Column("granted_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("revoked_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"]),
    )

    # ----- Table 7: signal_events (append-only, write-heavy) -----
    op.create_table(
        "signal_events",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("elder_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("device_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("event_type", signal_event_type, nullable=False),
        sa.Column("payload", postgresql.JSONB, nullable=False, server_default="{}"),
        sa.Column("occurred_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("ingested_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.ForeignKeyConstraint(["elder_id"], ["elders.id"]),
        sa.ForeignKeyConstraint(["device_id"], ["devices.id"]),
    )
    op.create_index("ix_signal_events_elder_time", "signal_events", ["elder_id", "occurred_at"])

    # ----- Table 8: incidents -----
    op.create_table(
        "incidents",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("elder_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("status", incident_status, nullable=False, server_default="open"),
        sa.Column("risk_score", sa.Float, nullable=False, server_default="0"),
        sa.Column("started_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("resolved_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("resolution_note", sa.Text, nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.ForeignKeyConstraint(["elder_id"], ["elders.id"]),
    )

    # ----- Table 9: incident_signals (composite PK join table) -----
    op.create_table(
        "incident_signals",
        sa.Column("incident_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("signal_event_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.PrimaryKeyConstraint("incident_id", "signal_event_id"),
        sa.ForeignKeyConstraint(["incident_id"], ["incidents.id"]),
        sa.ForeignKeyConstraint(["signal_event_id"], ["signal_events.id"]),
    )

    # ----- Table 10: alerts (append-only) -----
    op.create_table(
        "alerts",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("incident_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("guardian_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("channel", alert_channel, nullable=False),
        sa.Column("sent_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("acknowledged_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.ForeignKeyConstraint(["incident_id"], ["incidents.id"]),
        sa.ForeignKeyConstraint(["guardian_id"], ["guardians.id"]),
    )

    # ----- Table 11: llm_call_log -----
    op.create_table(
        "llm_call_log",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("task", sa.Text, nullable=False),
        sa.Column("provider", sa.Text, nullable=False),
        sa.Column("model", sa.Text, nullable=False),
        sa.Column("tokens_in", sa.Integer, nullable=True),
        sa.Column("tokens_out", sa.Integer, nullable=True),
        sa.Column("latency_ms", sa.Integer, nullable=True),
        sa.Column("cost_estimate_usd", sa.Numeric(12, 8), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
    )

    # ----- Table 12: whatsapp_verdicts (append-only) -----
    op.create_table(
        "whatsapp_verdicts",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("family_id", postgresql.UUID(as_uuid=True), nullable=True),
        sa.Column("sender_phone", sa.Text, nullable=False),
        sa.Column("message_type", message_type, nullable=False),
        sa.Column("raw_content_ref", sa.Text, nullable=True),
        sa.Column("verdict", verdict, nullable=False),
        sa.Column("matched_red_flags", postgresql.JSONB, nullable=False, server_default="[]"),
        sa.Column("confidence", sa.Float, nullable=True),
        sa.Column("llm_provider_used", sa.Text, nullable=True),
        sa.Column("latency_ms", sa.Integer, nullable=True),
        sa.Column("language", sa.Text, nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.ForeignKeyConstraint(["family_id"], ["families.id"]),
    )

    # ----- Table 13: scam_corpus (pgvector) -----
    op.create_table(
        "scam_corpus",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("source", scam_source, nullable=False),
        sa.Column("script_text", sa.Text, nullable=False),
        sa.Column("red_flag_tags", postgresql.ARRAY(sa.Text), nullable=False, server_default="{}"),
        sa.Column("embedding", sa.Text, nullable=True),  # pgvector type set via raw SQL below
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
    )
    # Replace TEXT placeholder with actual vector(1536) type
    op.execute("ALTER TABLE scam_corpus ALTER COLUMN embedding TYPE vector(1536) USING embedding::vector(1536)")
    # ivfflat index deferred until corpus > ~1000 rows:
    # op.execute("CREATE INDEX ix_scam_corpus_embedding ON scam_corpus USING ivfflat (embedding vector_cosine_ops)")

    # ----- Table 14: deepcheck_sessions -----
    op.create_table(
        "deepcheck_sessions",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("incident_id", postgresql.UUID(as_uuid=True), nullable=True),
        sa.Column("elder_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("audio_ref", sa.Text, nullable=False),
        sa.Column("transcript", sa.Text, nullable=True),
        sa.Column("whisper_latency_ms", sa.Integer, nullable=True),
        sa.Column("red_flags", postgresql.JSONB, nullable=True),
        sa.Column("spoof_score", sa.Float, nullable=True),
        sa.Column("spoof_features", postgresql.JSONB, nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.ForeignKeyConstraint(["incident_id"], ["incidents.id"]),
        sa.ForeignKeyConstraint(["elder_id"], ["elders.id"]),
    )

    # ----- Table 15: graph_sync_log -----
    op.create_table(
        "graph_sync_log",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("entity_type", sa.Text, nullable=False),
        sa.Column("entity_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("neo4j_synced_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
    )

    # ----- Table 16: evidence_packages (append-only) -----
    op.create_table(
        "evidence_packages",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("incident_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("hash_chain", postgresql.JSONB, nullable=False, server_default="{}"),
        sa.Column("pdf_ref", sa.Text, nullable=False),
        sa.Column("generated_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("section_65b_cert_ref", sa.Text, nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.ForeignKeyConstraint(["incident_id"], ["incidents.id"]),
    )

    # ----- Table 17: subscriptions (STUB — no live billing) -----
    op.create_table(
        "subscriptions",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("family_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("plan", plan, nullable=False),
        sa.Column("status", sub_status, nullable=False),
        sa.Column("provider", sub_provider, nullable=True),
        sa.Column("external_ref", sa.Text, nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.ForeignKeyConstraint(["family_id"], ["families.id"]),
    )


def downgrade() -> None:
    op.drop_table("subscriptions")
    op.drop_table("evidence_packages")
    op.drop_table("graph_sync_log")
    op.drop_table("deepcheck_sessions")
    op.drop_table("scam_corpus")
    op.drop_table("whatsapp_verdicts")
    op.drop_table("llm_call_log")
    op.drop_table("alerts")
    op.drop_table("incident_signals")
    op.drop_table("incidents")
    op.drop_table("signal_events")
    op.drop_table("consent_events")
    op.drop_table("devices")
    op.drop_table("guardians")
    op.drop_table("elders")
    op.drop_table("users")
    op.drop_table("families")

    from sqlalchemy.dialects import postgresql
    bind = op.get_bind()
    for name in [
        "sub_provider", "sub_status", "plan", "scam_source", "verdict",
        "message_type", "alert_channel", "incident_status", "signal_event_type",
        "platform", "onboarding_status", "language_pref", "role", "plan_tier",
    ]:
        postgresql.ENUM(name=name, create_type=False).drop(bind, checkfirst=True)

    op.execute("DROP EXTENSION IF EXISTS vector")
