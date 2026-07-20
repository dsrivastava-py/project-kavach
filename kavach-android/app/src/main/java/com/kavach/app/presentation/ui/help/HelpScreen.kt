package com.kavach.app.presentation.ui.help

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kavach.app.presentation.ui.components.KavachTopBar
import com.kavach.app.presentation.ui.components.SectionHeader

private val FAQ = listOf(
    "What is a pairing code?" to
        "A 6-digit code generated on the elder's device. Share it with a guardian to pair the accounts. It expires in 5 minutes.",
    "How does signal monitoring work?" to
        "The Kavach app on the elder's device detects events like unknown calls, screen sharing, and banking apps opening. These are sent securely to the backend for risk analysis.",
    "What is Deep Check?" to
        "An opt-in feature that analyses a voice recording for scam patterns and voice spoofing. Results are assistive only — not a definitive verdict.",
    "What does 'graduated' incident status mean?" to
        "graduated_1 through graduated_4 indicate escalating risk levels before full resolution. Higher graduation = higher risk score.",
    "Can I have more than one guardian?" to
        "Yes. Up to 2 guardians can be paired per elder.",
    "What happens when a JWT expires?" to
        "You are automatically logged out after 60 minutes. Re-pair using a new pairing code to regain access.",
)

@Composable
fun HelpScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = { KavachTopBar(title = "Help & FAQ", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeader("Frequently Asked Questions")
            FAQ.forEach { (q, a) -> FaqItem(q, a) }
        }
    }
}

@Composable
private fun FaqItem(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick  = { expanded = !expanded },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(question, style = MaterialTheme.typography.titleSmall)
            if (expanded) {
                Text(answer, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
