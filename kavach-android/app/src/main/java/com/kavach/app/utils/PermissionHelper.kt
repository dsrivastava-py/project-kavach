package com.kavach.app.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Centralised runtime permission queries.
 *
 * All permission decisions go through here — no scattered
 * [ContextCompat.checkSelfPermission] calls in screens.
 */
object PermissionHelper {

    // ── Permission groups ─────────────────────────────────────

    /** Permissions needed by the elder-side signal monitoring. */
    val SIGNAL_PERMISSIONS: List<String> = buildList {
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_CALL_LOG)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Permissions needed for deep-check audio recording. */
    val AUDIO_PERMISSIONS: List<String> = listOf(
        Manifest.permission.RECORD_AUDIO,
    )

    /** Permissions needed for location-based signals. */
    val LOCATION_PERMISSIONS: List<String> = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    /** Notification permission (Android 13+). */
    val NOTIFICATION_PERMISSION: String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.POST_NOTIFICATIONS
        else null

    // ── Check helpers ─────────────────────────────────────────

    /** Returns true if ALL permissions in [permissions] are granted. */
    fun allGranted(context: Context, permissions: List<String>): Boolean =
        permissions.all { isGranted(context, it) }

    /** Returns true if the given permission is granted. */
    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED

    /** Returns the subset of [permissions] that are NOT yet granted. */
    fun missing(context: Context, permissions: List<String>): List<String> =
        permissions.filter { !isGranted(context, it) }

    fun isMicrophoneGranted(context: Context): Boolean =
        isGranted(context, Manifest.permission.RECORD_AUDIO)

    fun isNotificationGranted(context: Context): Boolean =
        NOTIFICATION_PERMISSION?.let { isGranted(context, it) } ?: true

    // ── Navigation ────────────────────────────────────────────

    /** Opens the app's system settings page so the user can grant permissions manually. */
    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
