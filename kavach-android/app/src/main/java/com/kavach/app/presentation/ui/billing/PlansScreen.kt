package com.kavach.app.presentation.ui.billing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavach.app.domain.model.Plan
import com.kavach.app.domain.model.isFree
import com.kavach.app.presentation.ui.components.*
import com.kavach.app.presentation.viewmodel.BillingViewModel
import com.kavach.app.presentation.viewmodel.PlansUiState

/**
 * Billing plans screen.
 * Fetches from GET /api/v1/billing/plans — cache-first.
 * Note: This build has no live payment flow (stub only).
 */
@Composable
fun PlansScreen(
    onBack: () -> Unit,
    viewModel: BillingViewModel = hiltViewModel(),
) {
    val state        by viewModel.plansState.collectAsStateWithLifecycle()
    val refreshError by viewModel.refreshError.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { KavachTopBar(title = "Subscription Plans", onBack = onBack) }
    ) { padding ->
        when (val s = state) {
            PlansUiState.Loading ->
                KavachLoadingView(modifier = Modifier.padding(padding))

            is PlansUiState.Error ->
                KavachErrorView(message = s.message, onRetry = { viewModel.refresh() }, modifier = Modifier.padding(padding))

            is PlansUiState.Success -> {
                LazyColumn(
                    modifier       = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (refreshError != null) {
                        item {
                            Text(
                                "Showing cached plans (refresh failed: $refreshError)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    item { SectionHeader("Choose a plan") }
                    items(s.plans) { plan ->
                        PlanCard(plan)
                    }
                    item {
                        Text(
                            "Billing integration is in progress. No charges will be made.",
                            style     = MaterialTheme.typography.bodySmall,
                            color     = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier  = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanCard(plan: Plan) {
    val isHighlighted = plan.priceInr == 99

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        border   = if (isHighlighted) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isHighlighted) 6.dp else 2.dp),
        colors   = if (isHighlighted) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier            = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment   = Alignment.Top,
            ) {
                Column {
                    Text(plan.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (plan.billingCycle != null) {
                        Text(
                            "₹${plan.priceInr} / ${plan.billingCycle}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Text("Free", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (isHighlighted) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            "Popular",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }

            HorizontalDivider()

            plan.features.forEach { feature ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(feature, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (!plan.isFree) {
                KavachButton(
                    text     = "Select Plan",
                    onClick  = { /* Billing not live — stub */ },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
