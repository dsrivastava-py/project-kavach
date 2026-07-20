package com.kavach.app.presentation.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavach.app.domain.model.PairingCode
import com.kavach.app.presentation.ui.components.KavachButton
import com.kavach.app.presentation.ui.components.KavachErrorView
import com.kavach.app.presentation.ui.components.KavachLoadingView
import com.kavach.app.presentation.ui.components.KavachOutlinedButton
import com.kavach.app.presentation.ui.components.KavachTopBar
import com.kavach.app.presentation.viewmodel.AuthUiState
import com.kavach.app.presentation.viewmodel.AuthViewModel
import kotlinx.coroutines.delay

/**
 * Elder screen — generates a 6-digit pairing code.
 * Calls POST /api/v1/guardians/generate-pairing-code (requires elder JWT).
 * Shows a live countdown timer (5 minutes TTL from backend).
 */
@Composable
fun ElderPairingScreen(
    onBack: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.codeState.collectAsStateWithLifecycle()
    var secondsLeft by remember { mutableIntStateOf(0) }

    val pairingCode = (state as? AuthUiState.Success<*>)?.data as? PairingCode

    LaunchedEffect(pairingCode) {
        if (pairingCode != null) {
            secondsLeft = pairingCode.expiresInSeconds
            while (secondsLeft > 0) {
                delay(1_000)
                secondsLeft--
            }
            viewModel.resetCodeState()
        }
    }

    Scaffold(
        topBar = { KavachTopBar(title = "Generate Pairing Code", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AnimatedContent(
                targetState = state,
                label       = "codeState",
            ) { s ->
                when (s) {
                    is AuthUiState.Idle    -> IdleContent(onGenerate = { viewModel.generatePairingCode() })
                    is AuthUiState.Loading -> KavachLoadingView(message = "Generating…")
                    is AuthUiState.Success<*> -> CodeContent(
                        code        = (s.data as? PairingCode)?.code ?: "",
                        secondsLeft = secondsLeft,
                        onRegenerate = { viewModel.generatePairingCode() },
                    )
                    is AuthUiState.Error   -> KavachErrorView(
                        message = s.message,
                        onRetry = { viewModel.generatePairingCode() },
                    )
                }
            }
        }
    }
}

// ── Private sub-composables ───────────────────────────────────────────────

@Composable
private fun IdleContent(onGenerate: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text      = "Tap below to generate a one-time pairing code.\nShare it with your guardian.",
            style     = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        KavachButton(
            text     = "Generate Code",
            onClick  = onGenerate,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CodeContent(
    code: String,
    secondsLeft: Int,
    onRegenerate: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Your Pairing Code", style = MaterialTheme.typography.titleLarge)

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 40.dp, vertical = 24.dp),
        ) {
            Text(
                text          = code.chunked(3).joinToString("  "),
                fontSize      = 44.sp,
                fontFamily    = FontFamily.Monospace,
                color         = MaterialTheme.colorScheme.onPrimaryContainer,
                letterSpacing = 4.sp,
            )
        }

        Text(
            text  = if (secondsLeft > 0) "Expires in ${secondsLeft}s" else "Code expired",
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                secondsLeft <= 0  -> MaterialTheme.colorScheme.error
                secondsLeft < 30  -> MaterialTheme.colorScheme.error
                else              -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        KavachOutlinedButton(
            text     = "Generate New Code",
            onClick  = onRegenerate,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
