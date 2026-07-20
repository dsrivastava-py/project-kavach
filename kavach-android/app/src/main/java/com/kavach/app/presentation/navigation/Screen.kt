package com.kavach.app.presentation.navigation

/**
 * All navigation destinations in the app.
 * Route strings are the single source of truth — no magic strings elsewhere.
 */
sealed class Screen(val route: String) {

    // ── Auth flow ──────────────────────────────────────────────
    data object Auth           : Screen("auth")
    data object PairGuardian   : Screen("pair_guardian")
    data object ElderPairing   : Screen("elder_pairing")

    // ── Main graph (after login) ───────────────────────────────
    data object Dashboard      : Screen("dashboard")
    data object Incidents      : Screen("incidents")
    data object IncidentDetail : Screen("incident_detail/{incidentId}") {
        fun createRoute(incidentId: String) = "incident_detail/$incidentId"
    }
    data object Alerts         : Screen("alerts")
    data object DeepCheck      : Screen("deepcheck")
    data object Graph          : Screen("graph")
    data object Plans          : Screen("plans")
    data object Settings       : Screen("settings")
    data object Notifications  : Screen("notifications")
    data object About          : Screen("about")
    data object Help           : Screen("help")
    data object Feedback       : Screen("feedback")
}
