package com.kavach.app.presentation.ui.incident

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavach.app.presentation.ui.components.*
import com.kavach.app.presentation.viewmodel.*

/**
 * Incident detail screen.
 * Guardian can resolve or mark false positive, and generate an evidence PDF.
 *
 * Backend endpoints used:
 *   POST /api/v1/incidents/{id}/resolve
 *   POST /api/v1/incidents/{id}/evidence
 */
@Composable
fun IncidentDetailScreen(
    incidentId: String,
    onBack: () -> Unit,
    viewModel: IncidentViewModel = hiltViewModel(),
) {
    val incidentsState by viewModel.incidents.collectAsStateWithLifecycle()
    val actionState    by viewModel.actionState.collectAsStateWithLifecycle()
    val context        = LocalContext.current

    var showResolveDialog by remember { mutableStateOf(false) }
    var showFpDialog      by remember { mutableStateOf(false) }
    var resolutionNote    by remember { mutableStateOf("") }

    val incident = (incidentsState as? IncidentListState.Success)
        ?.items?.firstOrNull { it.id == incidentId }

    // Open evidence PDF in browser when download URL is available
    LaunchedEffect(actionState) {
        if (actionState is IncidentActionState.Evidence) {
            val url = (actionState as IncidentActionState.Evidence).result.downloadUrl
            if (!url.isNullOrBlank()) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
            viewModel.resetActionState()
        }
    }

    Scaffold(
        topBar = { KavachTopBar(title = "Incident Detail", onBack = onBack) }
    ) { padding ->
        if (incident == null) {
            KavachLoadingView(modifier = Modifier.padding(padding).fillMaxSize())
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Header card ───────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text("Incident", style = MaterialTheme.typography.titleLarge)
                        RiskBadge(incident.riskScore)
                    }
                    DetailRow("ID",      incident.id)
                    DetailRow("Status",  incident.status.value.replace("_", " ")
                        .replaceFirstChar { it.uppercase() })
                    DetailRow("Started", incident.startedAt.take(19).replace("T", " "))
                    incident.resolvedAt?.let {
                        DetailRow("Resolved", it.take(19).replace("T", " "))
                    }
                    incident.resolutionNote?.let {
                        DetailRow("Note", it)
                    }
                }
            }

            // ── Error banner ──────────────────────────────
            if (actionState is IncidentActionState.Error) {
                Card(
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text     = (actionState as IncidentActionState.Error).message,
                        modifier = Modifier.padding(12.dp),
                        color    = MaterialTheme.colorScheme.onErrorContainer,
                        style    = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // ── Success banner ────────────────────────────
            if (actionState is IncidentActionState.Resolved) {
                Card(
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text     = "Incident marked as: ${(actionState as IncidentActionState.Resolved).result.status}",
                        modifier = Modifier.padding(12.dp),
                        color    = MaterialTheme.colorScheme.onPrimaryContainer,
                        style    = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // ── Action buttons ────────────────────────────
            if (incident.status.isActive) {
                SectionHeader("Actions")

                ActionButton(
                    text    = "Resolve Incident",
                    icon    = Icons.Default.CheckCircle,
                    loading = actionState is IncidentActionState.Loading,
                    onClick = { showResolveDialog = true },
                )

                ActionButton(
                    text      = "Mark as False Positive",
                    icon      = Icons.Default.Close,
                    outlined  = true,
                    onClick   = { showFpDialog = true },
                )
            }

            ActionButton(
                text     = "Generate Evidence PDF",
                icon     = Icons.Default.Description,
                outlined = true,
                loading  = actionState is IncidentActionState.Loading,
                onClick  = { viewModel.generateEvidence(incidentId) },
            )
        }
    }

    // ── Dialogs ───────────────────────────────────────────────

    if (showResolveDialog) {
        AlertDialog(
            onDismissRequest = { showResolveDialog = false },
            title            = { Text("Resolve Incident") },
            text             = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Add an optional note:")
                    OutlinedTextField(
                        value         = resolutionNote,
                        onValueChange = { resolutionNote = it },
                        placeholder   = { Text("e.g. spoke with elder, confirmed safe") },
                        singleLine    = false,
                        maxLines      = 3,
                        modifier      = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showResolveDialog = false
                    viewModel.resolve(incidentId, "resolved",
                        resolutionNote.takeIf { it.isNotBlank() })
                }) { Text("Resolve") }
            },
            dismissButton = {
                TextButton(onClick = { showResolveDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showFpDialog) {
        AlertDialog(
            onDismissRequest = { showFpDialog = false },
            title            = { Text("Mark as False Positive") },
            text             = { Text("Are you sure this alert was a false alarm?") },
            confirmButton    = {
                TextButton(onClick = {
                    showFpDialog = false
                    viewModel.resolve(incidentId, "false_positive",
                        resolutionNote.takeIf { it.isNotBlank() })
                }) { Text("Confirm") }
            },
            dismissButton    = {
                TextButton(onClick = { showFpDialog = false }) { Text("Cancel") }
            },
        )
    }
}

// ── Private components ────────────────────────────────────────────────────

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text  = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    outlined: Boolean = false,
) {
    if (outlined) {
        OutlinedButton(
            onClick  = onClick,
            modifier = modifier.fillMaxWidth().height(52.dp),
            enabled  = !loading,
            shape    = RoundedCornerShape(12.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(text, style = MaterialTheme.typography.labelLarge)
            }
        }
    } else {
        Button(
            onClick  = onClick,
            modifier = modifier.fillMaxWidth().height(52.dp),
            enabled  = !loading,
            shape    = RoundedCornerShape(12.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(20.dp),
                    color       = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(text, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
