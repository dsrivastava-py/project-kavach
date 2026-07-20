package com.kavach.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.local.SessionManager
import com.kavach.app.domain.usecases.auth.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val logoutUseCase: LogoutUseCase,
) : ViewModel() {

    val isDarkTheme = sessionManager.isDarkTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val notificationsEnabled = sessionManager.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val languagePref = sessionManager.languagePref
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "en")

    val userRole = sessionManager.userRole
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { sessionManager.setDarkTheme(enabled) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { sessionManager.setNotificationsEnabled(enabled) }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch { sessionManager.setLanguagePref(lang) }
    }

    fun logout() {
        viewModelScope.launch { logoutUseCase() }
    }
}
