package com.kavach.app.presentation.ui.notifications

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavach.app.presentation.ui.components.KavachTopBar
import com.kavach.app.presentation.ui.components.SectionHeader
import com.kavach.app.presentation.viewmodel.SettingsViewModel

/** Notification preferences screen. Persisted in DataStore. */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { KavachTopBar(title = "Notifications", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeader("Alert Preferences")

            Card(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent  = { Text("Push Notifications") },
                    supportingContent = { Text("Receive real-time scam alerts on this device") },
                    leadingContent   = {
                        Icon(
                            if (notificationsEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                            contentDescription = null,
                        )
                    },
                    trailingContent  = {
                        Switch(
                            checked         = notificationsEnabled,
                            onCheckedChange = { viewModel.setNotificationsEnabled(it) },
                        )
                    },
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "You will always receive critical safety alerts regardless of this setting.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
