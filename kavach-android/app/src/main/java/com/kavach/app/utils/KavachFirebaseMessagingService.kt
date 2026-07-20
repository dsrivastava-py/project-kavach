package com.kavach.app.utils

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kavach.app.R
import com.kavach.app.data.local.SessionManager
import com.kavach.app.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Firebase Cloud Messaging handler.
 *
 * Foreground: posts a high-priority notification immediately.
 * Background: Android handles the notification automatically using the
 *             default_notification_channel_id in AndroidManifest.
 *
 * Notification click navigates to the relevant incident via deep link:
 *   kavach://incident/{incident_id}
 */
@AndroidEntryPoint
class KavachFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var sessionManager: SessionManager

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            sessionManager.saveFcmToken(token)
            // TODO: POST the new FCM token to the backend device registration endpoint
            // when that endpoint is added in a future phase.
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title      = message.notification?.title ?: message.data["title"] ?: "Kavach Alert"
        val body       = message.notification?.body  ?: message.data["body"]  ?: "A safety event was detected."
        val incidentId = message.data["incident_id"]

        showNotification(title, body, incidentId)
    }

    private fun showNotification(title: String, body: String, incidentId: String?) {
        val notificationId = System.currentTimeMillis().toInt()

        // Deep-link intent — taps open IncidentDetailScreen
        val intent = if (incidentId != null) {
            Intent(Intent.ACTION_VIEW, Uri.parse("kavach://incident/$incidentId"), this, MainActivity::class.java)
        } else {
            Intent(this, MainActivity::class.java)
        }.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, Constants.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, notification)
    }
}
