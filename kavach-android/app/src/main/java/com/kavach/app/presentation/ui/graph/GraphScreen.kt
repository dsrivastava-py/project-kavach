package com.kavach.app.presentation.ui.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavach.app.domain.model.RingSubgraph
import com.kavach.app.presentation.ui.components.KavachButton
import com.kavach.app.presentation.ui.components.KavachEmptyView
import com.kavach.app.presentation.ui.components.KavachErrorView
import com.kavach.app.presentation.ui.components.KavachLoadingView
import com.kavach.app.presentation.ui.components.KavachTextField
import com.kavach.app.presentation.ui.components.KavachTopBar
import com.kavach.app.presentation.viewmodel.GraphUiState
import com.kavach.app.presentation.viewmodel.GraphViewModel
import kotlin.math.cos
import kotlin.math.sin

/**
 * Fraud-ring graph screen — investigator role only.
 * Calls GET /api/v1/graph/ring/{phone}?depth={d}
 *
 * Renders a circular force-layout graph using Compose Canvas.
 */
@Composable
fun GraphScreen(
    onBack: () -> Unit,
    viewModel: GraphViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var phone by remember { mutableStateOf("") }
    var depth by remember { mutableIntStateOf(3) }

    Scaffold(
        topBar = { KavachTopBar(title = "Fraud Ring Graph", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ── Search bar ────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                KavachTextField(
                    value           = phone,
                    onValueChange   = { phone = it },
                    label           = "Phone (E.164)",
                    placeholder     = "+919876543210",
                    leadingIcon     = Icons.Default.Search,
                    modifier        = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction    = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = { viewModel.search(phone, depth) },
                    ),
                )
                KavachButton(
                    text     = "Search",
                    loading  = state is GraphUiState.Loading,
                    onClick  = { viewModel.search(phone, depth) },
                    modifier = Modifier.widthIn(min = 88.dp),
                )
            }

            // ── Depth slider (1–6) ────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier          = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text     = "Depth: $depth",
                    style    = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(64.dp),
                )
                Slider(
                    value         = depth.toFloat(),
                    onValueChange = { depth = it.toInt() },
                    valueRange    = 1f..6f,
                    steps         = 4,
                    modifier      = Modifier.weight(1f),
                )
            }

            // ── Content ───────────────────────────────────
            when (val s = state) {
                GraphUiState.Idle ->
                    KavachEmptyView("Enter a phone number to explore the fraud ring.")

                GraphUiState.Loading ->
                    KavachLoadingView()

                is GraphUiState.Error ->
                    KavachErrorView(
                        message = s.message,
                        onRetry = { viewModel.search(phone, depth) },
                    )

                is GraphUiState.Success ->
                    GraphCanvas(
                        graph    = s.graph,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
            }
        }
    }
}

// ── Canvas graph renderer ─────────────────────────────────────────────────

@Composable
private fun GraphCanvas(graph: RingSubgraph, modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Column(modifier = modifier) {
        Text(
            text  = "${graph.nodeCount} nodes · ${graph.edgeCount} edges",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (graph.nodes.isEmpty()) return@Canvas

            val cx     = size.width  / 2f
            val cy     = size.height / 2f
            val radius = minOf(cx, cy) * 0.75f

            // Assign circular positions
            val positions = graph.nodes.mapIndexed { i, node ->
                val angle = (2 * Math.PI * i / graph.nodes.size).toFloat()
                node.id to Offset(
                    x = cx + radius * cos(angle.toDouble()).toFloat(),
                    y = cy + radius * sin(angle.toDouble()).toFloat(),
                )
            }.toMap()

            // Draw edges first (behind nodes)
            graph.edges.forEach { edge ->
                val from = positions[edge.source] ?: return@forEach
                val to   = positions[edge.target] ?: return@forEach
                drawLine(
                    color       = Color.Gray.copy(alpha = 0.4f),
                    start       = from,
                    end         = to,
                    strokeWidth = 2f,
                )
            }

            // Draw nodes
            graph.nodes.forEachIndexed { i, node ->
                val pos = positions[node.id] ?: return@forEachIndexed
                val nodeColor = when (node.group) {
                    "phone"   -> primaryColor
                    "account" -> Color(0xFFF57F17)
                    else      -> Color(0xFF607D8B)
                }
                drawCircle(color = nodeColor,         radius = 22f, center = pos)
                drawCircle(color = Color.White,        radius = 22f, center = pos, style = Stroke(2f))
            }
        }

        // Legend
        Row(
            modifier              = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            listOf(
                "phone"   to primaryColor,
                "account" to Color(0xFFF57F17),
                "other"   to Color(0xFF607D8B),
            ).forEach { (label, color) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier          = Modifier.padding(horizontal = 10.dp),
                ) {
                    Canvas(Modifier.size(10.dp)) { drawCircle(color) }
                    Spacer(Modifier.width(4.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
