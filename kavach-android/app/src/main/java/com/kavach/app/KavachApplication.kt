package com.kavach.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.kavach.app.utils.Constants
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Hilt injects the dependency graph here.
 * Notification channels are created once at startup (Android 8+).
 */
@HiltAndroidApp
class KavachApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // High-priority channel for real-time scam alerts
            val alertsChannel = NotificationChannel(
                Constants.CHANNEL_ALERTS,
                getString(R.string.notification_channel_alerts),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_alerts_desc)
                enableVibration(true)
                enableLights(true)
            }

            // Low-priority channel for background signal monitoring service
            val signalChannel = NotificationChannel(
                Constants.CHANNEL_SIGNAL,
                getString(R.string.notification_channel_signal),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_signal_desc)
            }

            manager.createNotificationChannels(listOf(alertsChannel, signalChannel))
        }
    }
}
