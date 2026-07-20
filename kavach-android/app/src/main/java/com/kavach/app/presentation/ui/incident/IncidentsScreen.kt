package com.kavach.app.presentation.ui.incident

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kavach.app.presentation.ui.components.*
import com.kavach.app.presentation.viewmodel.IncidentListState
import com.kavach.app.presentation.viewmodel.IncidentViewModel

@Composable
fun IncidentsScreen(
    onIncidentClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: IncidentViewModel = hiltViewModel(),
) {
    val state by viewModel.incidents.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { KavachTopBar(title = "Incidents", onBack = onBack) }
    ) { padding ->
        when (val s = state) {
            is IncidentListState.Loading ->
                KavachLoadingView(modifier = Modifier.padding(padding))

            is IncidentListState.Error ->
                KavachErrorView(message = s.message, modifier = Modifier.padding(padding))

            is IncidentListState.Success -> {
                if (s.items.isEmpty()) {
                    KavachEmptyView(
                        message  = "No incidents recorded yet.",
                        modifier = Modifier.padding(padding),
                    )
                } else {
                    LazyColumn(
                        modifier       = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(s.items, key = { it.id }) { incident ->
                            IncidentCard(
                                id        = incident.id,
                                status    = incident.status.value,
                                riskScore = incident.riskScore,
                                startedAt = incident.startedAt,
                                onClick   = { onIncidentClick(incident.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
