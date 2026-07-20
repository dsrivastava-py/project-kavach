package com.kavach.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.kavach.app.presentation.ui.about.AboutScreen
import com.kavach.app.presentation.ui.alerts.AlertsScreen
import com.kavach.app.presentation.ui.auth.AuthScreen
import com.kavach.app.presentation.ui.auth.ElderPairingScreen
import com.kavach.app.presentation.ui.auth.PairGuardianScreen
import com.kavach.app.presentation.ui.billing.PlansScreen
import com.kavach.app.presentation.ui.dashboard.DashboardScreen
import com.kavach.app.presentation.ui.deepcheck.DeepCheckScreen
import com.kavach.app.presentation.ui.feedback.FeedbackScreen
import com.kavach.app.presentation.ui.graph.GraphScreen
import com.kavach.app.presentation.ui.help.HelpScreen
import com.kavach.app.presentation.ui.incident.IncidentDetailScreen
import com.kavach.app.presentation.ui.incident.IncidentsScreen
import com.kavach.app.presentation.ui.notifications.NotificationsScreen
import com.kavach.app.presentation.ui.settings.SettingsScreen
import com.kavach.app.presentation.viewmodel.AuthViewModel
import com.kavach.app.utils.Constants

/**
 * Root navigation graph.
 *
 * Auth state is observed from [AuthViewModel].
 * When [isLoggedIn] flips to false (JWT cleared by [TokenExpiryInterceptor] or logout),
 * the graph immediately pops to Auth without any individual screen needing to handle it.
 *
 * Deep link: kavach://incident/{incidentId} — navigated from FCM notification taps.
 */
@Composable
fun KavachNavGraph(
    navController: NavHostController,
    startDestination: String,
) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val isLoggedIn by authViewModel.isLoggedIn.collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn == false) {
            navController.navigate(Screen.Auth.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(
        navController    = navController,
        startDestination = startDestination,
    ) {

        // ── Auth ───────────────────────────────────────────────────────────
        composable(Screen.Auth.route) {
            AuthScreen(
                onNavigateToPairGuardian = { navController.navigate(Screen.PairGuardian.route) },
                onNavigateToElderPairing = { navController.navigate(Screen.ElderPairing.route) },
            )
        }

        composable(Screen.PairGuardian.route) {
            PairGuardianScreen(
                onPaired = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.ElderPairing.route) {
            ElderPairingScreen(onBack = { navController.popBackStack() })
        }

        // ── Main app ───────────────────────────────────────────────────────
        composable(Screen.Dashboard.route) {
            DashboardScreen(onNavigateTo = { route -> navController.navigate(route) })
        }

        composable(Screen.Incidents.route) {
            IncidentsScreen(
                onIncidentClick = { id ->
                    navController.navigate(Screen.IncidentDetail.createRoute(id))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route     = Screen.IncidentDetail.route,
            arguments = listOf(
                navArgument("incidentId") { type = NavType.StringType }
            ),
            // Deep link from FCM notification tap: kavach://incident/{incidentId}
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = "${Constants.DEEP_LINK_SCHEME}://incident/{incidentId}"
                }
            ),
        ) { backStack ->
            IncidentDetailScreen(
                incidentId = backStack.arguments?.getString("incidentId") ?: "",
                onBack     = { navController.popBackStack() },
            )
        }

        composable(Screen.Alerts.route) {
            AlertsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.DeepCheck.route) {
            DeepCheckScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Graph.route) {
            GraphScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Plans.route) {
            PlansScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateTo = { route -> navController.navigate(route) },
                onBack       = { navController.popBackStack() },
            )
        }

        composable(Screen.Notifications.route) {
            NotificationsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.About.route) {
            AboutScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Help.route) {
            HelpScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Feedback.route) {
            FeedbackScreen(onBack = { navController.popBackStack() })
        }
    }
}
