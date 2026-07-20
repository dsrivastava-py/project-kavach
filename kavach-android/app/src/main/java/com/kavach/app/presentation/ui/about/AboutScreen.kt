package com.kavach.app.presentation.ui.about

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kavach.app.BuildConfig
import com.kavach.app.presentation.ui.components.KavachTopBar

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = { KavachTopBar(title = "About Kavach", onBack = onBack) }
    ) { padding ->
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Icon(
                Icons.Default.Shield,
                contentDescription = null,
                tint     = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(80.dp),
            )
            Text("Kavach", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            HorizontalDivider()

            Text(
                "Kavach is an AI-powered elder safety platform that detects scam calls, " +
                "monitors device activity, and alerts guardians in real time.\n\n" +
                "Features:\n" +
                "• WhatsApp scam detection (basic & advanced)\n" +
                "• Android device signal monitoring\n" +
                "• Real-time guardian alerts via WebSocket\n" +
                "• Opt-in audio deep-check (voice spoof detection)\n" +
                "• Fraud ring graph for investigators\n" +
                "• Evidence PDF with Section 65B certificate",
                style     = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Start,
                color     = MaterialTheme.colorScheme.onSurface,
            )

            HorizontalDivider()
            Text(
                "© 2024–2026 Kavach. All rights reserved.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
