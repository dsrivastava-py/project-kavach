package com.kavach.app.presentation.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavach.app.data.remote.websocket.WsState
import com.kavach.app.presentation.navigation.Screen
import com.kavach.app.presentation.ui.components.*
import com.kavach.app.presentation.viewmodel.AlertViewModel
import com.kavach.app.presentation.viewmodel.IncidentListState
import com.kavach.app.presentation.viewmodel.IncidentViewModel

/**
 * Main dashboard — overview of active incidents and quick-access feature grid.
 * WebSocket connection state is shown as a live pulsing dot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateTo: (String) -> Unit,
    incidentViewModel: IncidentViewModel = hiltViewModel(),
    alertViewModel: AlertViewModel = hiltViewModel(),
) {
    val incidentState by incidentViewModel.incidents.collectAsStateWithLifecycle()
    val wsState       by alertViewModel.wsState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Kavach", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(8.dp))
                        PulsingDot(
                            color = if (wsState is WsState.Connected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigateTo(Screen.Settings.route) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(padding),
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── WebSocket status banner ─────────────────────
            if (wsState !is WsState.Connected) {
                item(key = "ws_banner") {
                    WsBanner(state = wsState, onReconnect = { alertViewModel.reconnect() })
                }
            }

            // ── Feature grid ────────────────────────────────
            item(key = "feature_header") { SectionHeader("Features") }
            item(key = "feature_grid")   { QuickAccessGrid(onNavigateTo) }

            // ── Active incidents ────────────────────────────
            item(key = "incidents_header") { SectionHeader("Active Incidents") }

            when (val s = incidentState) {
                is IncidentListState.Loading -> item(key = "inc_loading") {
                    KavachLoadingView(modifier = Modifier.fillMaxWidth().height(120.dp))
                }

                is IncidentListState.Error -> item(key = "inc_error") {
                    KavachErrorView(
                        message  = s.message,
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                    )
                }

                is IncidentListState.Success -> {
                    val active = s.items.filter { it.status.isActive }
                    if (active.isEmpty()) {
                        item(key = "inc_empty") {
                            KavachEmptyView(
                                message  = "No active incidents",
                                modifier = Modifier.fillMaxWidth().height(120.dp),
                            )
                        }
                    } else {
                        items(items = active, key = { it.id }) { inc ->
                            IncidentCard(
                                id        = inc.id,
                                status    = inc.status.value,
                                riskScore = inc.riskScore,
                                startedAt = inc.startedAt,
                                onClick   = { onNavigateTo(Screen.IncidentDetail.createRoute(inc.id)) },
                            )
                        }
                        item(key = "view_all") {
                            TextButton(
                                onClick  = { onNavigateTo(Screen.Incidents.route) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("View all incidents") }
                        }
                    }
                }
            }
        }
    }
}

// ── Private composables ───────────────────────────────────────────────────

@Composable
private fun WsBanner(state: WsState, onReconnect: () -> Unit) {
    val msg = when (state) {
        WsState.Connecting   -> "Connecting to alert stream…"
        WsState.Reconnecting -> "Reconnecting…"
        WsState.AuthError    -> "Session expired. Please log in again."
        is WsState.Error     -> "Alert stream error: ${state.message}"
        else                 -> "Alert stream disconnected"
    }
    Card(
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text     = msg,
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            if (state is WsState.Disconnected || state is WsState.Error) {
                TextButton(onClick = onReconnect) { Text("Retry") }
            }
        }
    }
}

@Composable
private fun QuickAccessGrid(onNavigateTo: (String) -> Unit) {
    val gridItems = listOf(
        Triple("Incidents",   Icons.Default.Warning,           Screen.Incidents.route),
        Triple("Alerts",      Icons.Default.Notifications,     Screen.Alerts.route),
        Triple("Deep Check",  Icons.Default.RecordVoiceOver,   Screen.DeepCheck.route),
        Triple("Plans",       Icons.Default.Star,              Screen.Plans.route),
        Triple("Graph",       Icons.Default.AccountTree,       Screen.Graph.route),
        Triple("Settings",    Icons.Default.Settings,          Screen.Settings.route),
    )
    // 3-column grid rendered as two rows of three
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        gridItems.chunked(3).forEach { rowItems ->
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { (label, icon, route) ->
                    QuickAccessCard(
                        label    = label,
                        icon     = icon,
                        onClick  = { onNavigateTo(route) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Pad last row if fewer than 3 items
                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun QuickAccessCard(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick  = onClick,
        modifier = modifier,
    ) {
        Column(
            modifier            = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
            Text(
                text      = label,
                style     = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines  = 2,
            )
        }
    }
}
