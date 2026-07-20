package com.kavach.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** DataStore instance scoped to the Application context. */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kavach_session")

/**
 * Secure preferences storage for JWT, role, user ID, and app settings.
 *
 * JWT is stored in DataStore (encrypted at-rest by Android Keystore on
 * supported hardware via the EncryptedSharedPreferences layer — we keep
 * DataStore here for simplicity and type-safety, and rely on Android's
 * app-sandbox isolation + file-based encryption for security).
 *
 * Sensitive values are never logged.
 */
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object Keys {
        val JWT_TOKEN         = stringPreferencesKey("jwt_token")
        val USER_ID           = stringPreferencesKey("user_id")
        val USER_ROLE         = stringPreferencesKey("user_role")
        val GUARDIAN_ID       = stringPreferencesKey("guardian_id")
        val ELDER_ID          = stringPreferencesKey("elder_id")
        val DEVICE_ID         = stringPreferencesKey("device_id")
        val DEVICE_API_KEY    = stringPreferencesKey("device_api_key")
        val FCM_TOKEN         = stringPreferencesKey("fcm_token")
        val DARK_THEME        = booleanPreferencesKey("dark_theme")
        val NOTIFICATIONS_ON  = booleanPreferencesKey("notifications_on")
        val LANGUAGE_PREF     = stringPreferencesKey("language_pref")
        val ONBOARDING_DONE   = booleanPreferencesKey("onboarding_done")
    }

    // ── Read flows ──────────────────────────────────────────────

    val jwtToken: Flow<String?> = context.dataStore.data.map { it[JWT_TOKEN] }
    val userId:   Flow<String?> = context.dataStore.data.map { it[USER_ID] }
    val userRole: Flow<String?> = context.dataStore.data.map { it[USER_ROLE] }
    val guardianId: Flow<String?> = context.dataStore.data.map { it[GUARDIAN_ID] }
    val elderId:  Flow<String?> = context.dataStore.data.map { it[ELDER_ID] }
    val deviceId: Flow<String?> = context.dataStore.data.map { it[DEVICE_ID] }
    val deviceApiKey: Flow<String?> = context.dataStore.data.map { it[DEVICE_API_KEY] }
    val isDarkTheme: Flow<Boolean> = context.dataStore.data.map { it[DARK_THEME] ?: false }
    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[NOTIFICATIONS_ON] ?: true }
    val languagePref: Flow<String> = context.dataStore.data.map { it[LANGUAGE_PREF] ?: "en" }
    val isOnboardingDone: Flow<Boolean> = context.dataStore.data.map { it[ONBOARDING_DONE] ?: false }
    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { it[JWT_TOKEN] != null }

    // ── Synchronous reads (use sparingly — prefer flows) ────────

    suspend fun getJwtTokenOnce(): String? =
        context.dataStore.data.first()[JWT_TOKEN]

    suspend fun getDeviceApiKeyOnce(): String? =
        context.dataStore.data.first()[DEVICE_API_KEY]

    // ── Writes ─────────────────────────────────────────────────

    /** Called after a successful guardian pairing. */
    suspend fun saveGuardianSession(
        token: String,
        guardianId: String,
        userId: String,
    ) {
        context.dataStore.edit { prefs ->
            prefs[JWT_TOKEN]    = token
            prefs[USER_ID]      = userId
            prefs[USER_ROLE]    = "guardian"
            prefs[GUARDIAN_ID]  = guardianId
        }
    }

    /** Called after elder generates a pairing code (elder is logged in). */
    suspend fun saveElderSession(token: String, userId: String, elderId: String) {
        context.dataStore.edit { prefs ->
            prefs[JWT_TOKEN]  = token
            prefs[USER_ID]    = userId
            prefs[USER_ROLE]  = "elder"
            prefs[ELDER_ID]   = elderId
        }
    }

    /** Persist device API key received during device registration. */
    suspend fun saveDeviceApiKey(apiKey: String) {
        context.dataStore.edit { it[DEVICE_API_KEY] = apiKey }
    }

    /** Persist device ID (UUID) assigned at first registration. */
    suspend fun saveDeviceId(id: String) {
        context.dataStore.edit { it[DEVICE_ID] = id }
    }

    suspend fun getDeviceIdOnce(): String? =
        context.dataStore.data.first()[DEVICE_ID]

    suspend fun saveFcmToken(token: String) {
        context.dataStore.edit { it[FCM_TOKEN] = token }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[DARK_THEME] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[NOTIFICATIONS_ON] = enabled }
    }

    suspend fun setLanguagePref(lang: String) {
        context.dataStore.edit { it[LANGUAGE_PREF] = lang }
    }

    suspend fun setOnboardingDone() {
        context.dataStore.edit { it[ONBOARDING_DONE] = true }
    }

    /** Full logout — clears all auth data but preserves theme settings. */
    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(JWT_TOKEN)
            prefs.remove(USER_ID)
            prefs.remove(USER_ROLE)
            prefs.remove(GUARDIAN_ID)
            prefs.remove(ELDER_ID)
            prefs.remove(DEVICE_ID)
            prefs.remove(DEVICE_API_KEY)
        }
    }
}
