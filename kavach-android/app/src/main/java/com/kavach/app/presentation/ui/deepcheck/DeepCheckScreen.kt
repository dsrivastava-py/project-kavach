package com.kavach.app.presentation.ui.deepcheck

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavach.app.domain.model.DeepCheckSession
import com.kavach.app.presentation.ui.components.KavachButton
import com.kavach.app.presentation.ui.components.KavachErrorView
import com.kavach.app.presentation.ui.components.KavachLoadingView
import com.kavach.app.presentation.ui.components.KavachTopBar
import com.kavach.app.presentation.ui.components.RiskBadge
import com.kavach.app.presentation.ui.components.SectionHeader
import com.kavach.app.presentation.viewmodel.DeepCheckUiState
import com.kavach.app.presentation.viewmodel.DeepCheckViewModel
import java.io.File

/**
 * Deep-check screen — opt-in audio spoof detection.
 *
 * Flow: user selects audio → POST /api/v1/deepcheck/sessions → poll GET until done.
 *
 * HARD RULE: [DeepCheckSession.spoofScore] must ALWAYS be displayed alongside
 * [DeepCheckSession.spoofDisclaimer]. Never present it as a definitive verdict.
 */
@Composable
fun DeepCheckScreen(
    onBack: () -> Unit,
    viewModel: DeepCheckViewModel = hiltViewModel(),
) {
    val state   by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasAudioPermission = granted }

    Scaffold(
        topBar = { KavachTopBar(title = "Deep Check", onBack = onBack) }
    ) { padding ->
        AnimatedContent(
            targetState = state,
            label       = "deepcheck",
            modifier    = Modifier.fillMaxSize().padding(padding),
        ) { s ->
            when (s) {
                DeepCheckUiState.Idle ->
                    IdleContent(
                        hasPermission = hasAudioPermission,
                        onRequestPerm = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        onPickAudio   = { file -> viewModel.startDeepCheck(file) },
                    )

                DeepCheckUiState.Uploading ->
                    KavachLoadingView(message = "Uploading audio to server…")

                is DeepCheckUiState.Polling ->
                    PollingContent(s.session)

                is DeepCheckUiState.Done ->
                    ResultContent(s.session, onReset = { viewModel.reset() })

                is DeepCheckUiState.Error ->
                    KavachErrorView(
                        message = s.message,
                        onRetry = { viewModel.reset() },
                    )
            }
        }
    }
}

// ── Private sub-composables ───────────────────────────────────────────────

@Composable
private fun IdleContent(
    hasPermission: Boolean,
    onRequestPerm: () -> Unit,
    onPickAudio: (File) -> Unit,
) {
    val context = LocalContext.current

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val stream = context.contentResolver.openInputStream(uri)
            ?: return@rememberLauncherForActivityResult
        val tmp = File.createTempFile("deepcheck_", ".ogg", context.cacheDir)
        tmp.outputStream().use { out -> stream.copyTo(out) }
        onPickAudio(tmp)
    }

    Column(
        modifier            = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector        = Icons.Default.RecordVoiceOver,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.primary,
            modifier           = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text      = "Audio Deep Check",
            style     = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Upload a voice recording to analyse for scam patterns " +
                "and voice spoofing. Opt-in only — results are assistive, not definitive.",
            style     = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        if (!hasPermission) {
            OutlinedButton(
                onClick  = onRequestPerm,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.Mic, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Grant Microphone Permission")
            }
            Spacer(Modifier.height(12.dp))
        }

        KavachButton(
            text     = "Select Audio File",
            onClick  = { fileLauncher.launch("audio/*") },
            icon     = Icons.Default.Mic,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PollingContent(session: DeepCheckSession) {
    Column(
        modifier            = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(24.dp))
        Text("Analysing audio…", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text  = "Session: ${session.sessionId.take(8)}…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResultContent(session: DeepCheckSession, onReset: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionHeader("Analysis Results")

        // ── Transcript ───────────────────────────────────
        if (!session.transcript.isNullOrBlank()) {
            ResultCard(title = "Transcript") {
                Text(session.transcript, style = MaterialTheme.typography.bodySmall)
            }
        }

        // ── Summary ──────────────────────────────────────
        if (!session.summary.isNullOrBlank()) {
            ResultCard(title = "Summary") {
                Text(session.summary, style = MaterialTheme.typography.bodyMedium)
            }
        }

        // ── Red Flags ────────────────────────────────────
        if (session.redFlags.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Red Flags",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    session.redFlags.forEach { flag ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning, null,
                                Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                flag,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }
        }

        // ── Spoof Score — ALWAYS with disclaimer ─────────
        if (session.spoofScore != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text("Spoof Score", style = MaterialTheme.typography.labelLarge)
                        RiskBadge(session.spoofScore)
                    }
                    LinearProgressIndicator(
                        progress = { session.spoofScore.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                        color    = when {
                            session.spoofScore >= 0.7 -> MaterialTheme.colorScheme.error
                            session.spoofScore >= 0.4 -> MaterialTheme.colorScheme.tertiary
                            else                      -> MaterialTheme.colorScheme.primary
                        },
                    )
                    // Mandatory disclaimer — never omit
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Row(
                            modifier          = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                Icons.Default.Info, null,
                                Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text  = session.spoofDisclaimer
                                    ?: "Assistive only. Do not use as definitive evidence.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
            }
        }

        KavachButton(
            text     = "New Analysis",
            onClick  = onReset,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ResultCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            content()
        }
    }
}
