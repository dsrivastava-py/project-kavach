package com.kavach.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.local.SessionManager
import com.kavach.app.domain.model.GuardianSession
import com.kavach.app.domain.model.PairingCode
import com.kavach.app.domain.usecases.auth.GeneratePairingCodeUseCase
import com.kavach.app.domain.usecases.auth.LogoutUseCase
import com.kavach.app.domain.usecases.auth.PairGuardianUseCase
import com.kavach.app.utils.NetworkResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UI state ──────────────────────────────────────────────────────────────
sealed class AuthUiState {
    data object Idle    : AuthUiState()
    data object Loading : AuthUiState()
    data class  Success<T>(val data: T) : AuthUiState()
    data class  Error(val message: String) : AuthUiState()
}

/**
 * Shared auth ViewModel. Exposes:
 * - [isLoggedIn] — observed by [NavGraph] for auto-logout routing
 * - [pairGuardian] — guardian login via pairing code
 * - [generatePairingCode] — elder creates a code to share
 * - [logout] — clears session
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val pairGuardianUseCase: PairGuardianUseCase,
    private val generatePairingCodeUseCase: GeneratePairingCodeUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val isLoggedIn = sessionManager.isLoggedIn.distinctUntilChanged()
    val userRole   = sessionManager.userRole

    private val _pairState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val pairState: StateFlow<AuthUiState> = _pairState.asStateFlow()

    private val _codeState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val codeState: StateFlow<AuthUiState> = _codeState.asStateFlow()

    fun pairGuardian(pairingCode: String, phone: String) {
        viewModelScope.launch {
            _pairState.value = AuthUiState.Loading
            _pairState.value = when (val result = pairGuardianUseCase(pairingCode, phone)) {
                is NetworkResult.Success -> AuthUiState.Success<GuardianSession>(result.data)
                is NetworkResult.Error   -> AuthUiState.Error(result.message)
                NetworkResult.Loading    -> AuthUiState.Loading
            }
        }
    }

    fun generatePairingCode() {
        viewModelScope.launch {
            _codeState.value = AuthUiState.Loading
            _codeState.value = when (val result = generatePairingCodeUseCase()) {
                is NetworkResult.Success -> AuthUiState.Success<PairingCode>(result.data)
                is NetworkResult.Error   -> AuthUiState.Error(result.message)
                NetworkResult.Loading    -> AuthUiState.Loading
            }
        }
    }

    fun logout() {
        viewModelScope.launch { logoutUseCase() }
    }

    fun resetPairState() { _pairState.value = AuthUiState.Idle }
    fun resetCodeState() { _codeState.value = AuthUiState.Idle }
}
