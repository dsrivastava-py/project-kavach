package com.kavach.app.utils

/**
 * Application-wide constants. No magic strings scattered across the codebase.
 */
object Constants {

    // ── Notification Channels ──────────────────────────────────
    const val CHANNEL_ALERTS = "kavach_alerts"
    const val CHANNEL_SIGNAL = "kavach_signal_monitor"

    // ── DataStore keys are defined in SessionManager ───────────

    // ── Room DB ────────────────────────────────────────────────
    const val DB_NAME = "kavach.db"
    const val DB_VERSION = 1

    // ── Network ────────────────────────────────────────────────
    const val CONNECT_TIMEOUT_SECONDS = 30L
    const val READ_TIMEOUT_SECONDS    = 60L
    const val WRITE_TIMEOUT_SECONDS   = 60L

    // ── WebSocket ──────────────────────────────────────────────
    const val WS_RECONNECT_DELAY_MS   = 1_000L
    const val WS_MAX_RECONNECT_DELAY_MS = 30_000L

    // ── Signal ingestion ───────────────────────────────────────
    const val SIGNAL_BATCH_INTERVAL_MS = 30_000L
    const val SIGNAL_MAX_BATCH_SIZE    = 100

    // ── Foreground service ─────────────────────────────────────
    const val NOTIFICATION_ID_SIGNAL_MONITOR = 1001
    const val NOTIFICATION_ID_ALERT          = 2001

    // ── Deep check ────────────────────────────────────────────
    const val DEEPCHECK_POLL_INTERVAL_MS = 3_000L
    const val DEEPCHECK_MAX_POLLS        = 40       // 2 min max

    // ── Deep link scheme ──────────────────────────────────────
    const val DEEP_LINK_SCHEME = "kavach"
}
