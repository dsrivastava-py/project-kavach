package com.kavach.app.presentation.ui.feedback

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kavach.app.presentation.ui.components.KavachButton
import com.kavach.app.presentation.ui.components.KavachTextField
import com.kavach.app.presentation.ui.components.KavachTopBar

/** Simple feedback form — no backend endpoint exists yet; shows confirmation locally. */
@Composable
fun FeedbackScreen(onBack: () -> Unit) {
    var message  by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { KavachTopBar(title = "Send Feedback", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (submitted) {
                Text(
                    "Thank you for your feedback! We'll review it shortly.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text("We'd love to hear from you.", style = MaterialTheme.typography.bodyLarge)

                KavachTextField(
                    value         = message,
                    onValueChange = { message = it },
                    label         = "Your feedback",
                    placeholder   = "Tell us what's working or what could be improved…",
                    singleLine    = false,
                    modifier      = Modifier.fillMaxWidth().height(160.dp),
                )

                Spacer(Modifier.weight(1f))

                KavachButton(
                    text     = "Submit",
                    icon     = Icons.Default.Send,
                    enabled  = message.isNotBlank(),
                    onClick  = { submitted = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
