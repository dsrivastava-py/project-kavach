package com.kavach.app.utils

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kavach.app.R
import com.kavach.app.data.local.SessionManager
import com.kavach.app.domain.repository.SignalRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that drives periodic signal batch uploads.
 *
 * Every [Constants.SIGNAL_BATCH_INTERVAL_MS] it calls [SignalRepository.flushPendingEvents].
 * Signal events are written to the local Room queue by device listeners
 * (call state, accessibility events) — this service only manages the upload loop.
 *
 * Start this service when an elder session is active.
 * Stop it on logout via [stopSelf] / explicit stop intent.
 */
@AndroidEntryPoint
class SignalMonitorService : Service() {

    @Inject lateinit var signalRepository: SignalRepository
    @Inject lateinit var sessionManager: SessionManager

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(Constants.NOTIFICATION_ID_SIGNAL_MONITOR, buildNotification())
        startUploadLoop()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startUploadLoop() {
        serviceScope.launch {
            while (true) {
                delay(Constants.SIGNAL_BATCH_INTERVAL_MS)
                try {
                    val deviceId = sessionManager.getDeviceIdOnce()  ?: continue
                    val elderId  = sessionManager.elderId.first()     ?: continue
                    signalRepository.flushPendingEvents(deviceId, elderId)
                } catch (_: Exception) {
                    // Non-fatal — retry next cycle
                }
            }
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, Constants.CHANNEL_SIGNAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Kavach Monitoring")
            .setContentText("Device safety monitoring is active")
            .setOngoing(true)
            .setSilent(true)
            .build()
}
