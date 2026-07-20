package com.kavach.app.presentation.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavach.app.BuildConfig
import com.kavach.app.presentation.navigation.Screen
import com.kavach.app.presentation.ui.components.KavachTopBar
import com.kavach.app.presentation.ui.components.SectionHeader
import com.kavach.app.presentation.viewmodel.SettingsViewModel

/**
 * Settings screen — theme, notifications, language, about, logout.
 */
@Composable
fun SettingsScreen(
    onNavigateTo: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val isDark               by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val language             by viewModel.languagePref.collectAsStateWithLifecycle()
    val role                 by viewModel.userRole.collectAsStateWithLifecycle()
    var showLogoutDialog     by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { KavachTopBar(title = "Settings", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Account
            SectionHeader("Account")
            role?.let { r ->
                SettingInfoRow("Role", r.replace("_", " ").replaceFirstChar { it.uppercase() })
            }

            // Appearance
            SectionHeader("Appearance")
            SettingSwitchRow(
                title   = "Dark Theme",
                icon    = Icons.Default.DarkMode,
                checked = isDark,
                onCheckedChange = { viewModel.setDarkTheme(it) },
            )

            // Notifications
            SectionHeader("Notifications")
            SettingSwitchRow(
                title   = "Enable Notifications",
                icon    = Icons.Default.Notifications,
                checked = notificationsEnabled,
                onCheckedChange = { viewModel.setNotificationsEnabled(it) },
            )
            SettingNavRow("Notification Preferences", Icons.Default.Tune) {
                onNavigateTo(Screen.Notifications.route)
            }

            // Language
            SectionHeader("Language")
            SettingInfoRow("Current Language", language.uppercase())

            // Info
            SectionHeader("Information")
            SettingNavRow("About Kavach", Icons.Default.Info)    { onNavigateTo(Screen.About.route) }
            SettingNavRow("Help & FAQ",   Icons.Default.Help)    { onNavigateTo(Screen.Help.route) }
            SettingNavRow("Send Feedback",Icons.Default.Feedback){ onNavigateTo(Screen.Feedback.route) }
            SettingInfoRow("App Version", BuildConfig.VERSION_NAME)

            Spacer(Modifier.height(24.dp))

            OutlinedButton(
                onClick  = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Log Out")
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title            = { Text("Log Out") },
            text             = { Text("Are you sure you want to log out? Your session will end.") },
            confirmButton    = {
                TextButton(onClick = { showLogoutDialog = false; viewModel.logout() }) {
                    Text("Log Out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton    = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent  = { Icon(icon, contentDescription = null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
}

@Composable
private fun SettingNavRow(title: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent  = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
        modifier        = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
}

@Composable
private fun SettingInfoRow(label: String, value: String) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
        },
    )
    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
}
