package com.kavach.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.local.SessionManager
import com.kavach.app.data.local.entity.IncidentEntity
import com.kavach.app.data.local.dao.IncidentCacheDao
import com.kavach.app.data.remote.websocket.AlertEventParser
import com.kavach.app.data.remote.websocket.GuardianWebSocketClient
import com.kavach.app.data.remote.websocket.WsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Manages the real-time WebSocket connection to the guardian alert stream.
 *
 * On every incoming alert frame:
 * 1. The raw JSON is added to [alertMessages] for display in [AlertsScreen].
 * 2. The frame is parsed by [AlertEventParser] and — if it contains incident
 *    data — the incident is upserted into the local Room cache so [IncidentsScreen]
 *    and [DashboardScreen] update reactively without a manual refresh.
 *
 * Connects on init, disconnects on [onCleared].
 */
@HiltViewModel
class AlertViewModel @Inject constructor(
    private val wsClient: GuardianWebSocketClient,
    private val sessionManager: SessionManager,
    private val alertEventParser: AlertEventParser,
    private val incidentCacheDao: IncidentCacheDao,
) : ViewModel() {

    /** Live WebSocket connection state. */
    val wsState: StateFlow<WsState> = wsClient.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WsState.Disconnected)

    /** Last 50 raw JSON alert frames for the Alerts screen. */
    private val _alertMessages = MutableStateFlow<List<String>>(emptyList())
    val alertMessages: StateFlow<List<String>> = _alertMessages.asStateFlow()

    init {
        connectIfLoggedIn()
        collectAndProcessMessages()
    }

    // ── Connection ─────────────────────────────────────────────────────────

    private fun connectIfLoggedIn() {
        viewModelScope.launch {
            val guardianId = sessionManager.guardianId.first() ?: return@launch
            wsClient.connect(guardianId)
        }
    }

    /** Manually trigger a reconnect (e.g. after user taps "Retry"). */
    fun reconnect() {
        viewModelScope.launch {
            val guardianId = sessionManager.guardianId.first() ?: return@launch
            wsClient.connect(guardianId)
        }
    }

    // ── Message processing ─────────────────────────────────────────────────

    private fun collectAndProcessMessages() {
        viewModelScope.launch {
            wsClient.messages.collect { raw ->
                // 1. Append to display list (capped at 50)
                _alertMessages.value = (_alertMessages.value + raw).takeLast(50)

                // 2. Parse and cache the incident if the frame carries one
                val frame = alertEventParser.parse(raw) ?: return@collect
                val incidentId = frame.incidentId ?: return@collect
                val elderId    = frame.elderId    ?: return@collect
                val status     = frame.status     ?: return@collect
                val riskScore  = frame.riskScore  ?: 0.0
                val startedAt  = frame.startedAt  ?: return@collect

                incidentCacheDao.upsert(
                    IncidentEntity(
                        id        = incidentId,
                        elderId   = elderId,
                        status    = status,
                        riskScore = riskScore,
                        startedAt = startedAt,
                    )
                )
            }
        }
    }

    // ── Utilities ──────────────────────────────────────────────────────────

    fun clearMessages() { _alertMessages.value = emptyList() }

    override fun onCleared() {
        wsClient.disconnect()
        super.onCleared()
    }
}
