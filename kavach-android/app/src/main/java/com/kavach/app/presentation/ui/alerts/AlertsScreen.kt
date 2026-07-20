package com.kavach.app.presentation.ui.alerts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavach.app.data.remote.websocket.WsState
import com.kavach.app.presentation.ui.components.*
import com.kavach.app.presentation.viewmodel.AlertViewModel

/**
 * Live alert stream screen.
 * Subscribes to WS /ws/guardian/{guardian_id} and renders raw JSON alert frames.
 */
@Composable
fun AlertsScreen(
    onBack: () -> Unit,
    viewModel: AlertViewModel = hiltViewModel(),
) {
    val messages by viewModel.alertMessages.collectAsStateWithLifecycle()
    val wsState  by viewModel.wsState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            KavachTopBar(
                title   = "Live Alerts",
                onBack  = onBack,
                actions = {
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearMessages() }) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = "Clear")
                        }
                    }
                },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Connection status chip
            Row(
                modifier            = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val (label, color) = when (wsState) {
                    WsState.Connected    -> "Connected" to MaterialTheme.colorScheme.primary
                    WsState.Connecting,
                    WsState.Reconnecting -> "Connecting…" to MaterialTheme.colorScheme.tertiary
                    WsState.AuthError    -> "Auth Error" to MaterialTheme.colorScheme.error
                    else                 -> "Disconnected" to MaterialTheme.colorScheme.error
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulsingDot(color = color)
                    Spacer(Modifier.width(6.dp))
                    Text(label, style = MaterialTheme.typography.labelMedium, color = color)
                }
                if (wsState is WsState.Disconnected || wsState is WsState.Error) {
                    TextButton(onClick = { viewModel.reconnect() }) { Text("Reconnect") }
                }
            }

            HorizontalDivider()

            if (messages.isEmpty()) {
                KavachEmptyView("Waiting for alerts…\nMake sure the elder's device is active.")
            } else {
                LazyColumn(
                    modifier       = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    reverseLayout  = true,
                ) {
                    items(messages.reversed()) { raw ->
                        AlertMessageCard(raw)
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertMessageCard(rawJson: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(10.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint     = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Alert Received",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                rawJson,
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSecondaryContainer,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
        }
    }
}
