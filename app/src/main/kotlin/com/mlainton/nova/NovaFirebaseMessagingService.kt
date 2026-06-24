package com.mlainton.nova

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.concurrent.atomic.AtomicInteger

class NovaFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        createPushNotificationChannel()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val title = message.notification?.title?.takeIf { it.isNotBlank() } ?: DEFAULT_TITLE
        val body = message.notification?.body?.takeIf { it.isNotBlank() } ?: DEFAULT_BODY
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, PUSH_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this).notify(notificationIds.incrementAndGet(), notification)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Thread {
            val result = NovaApiClient.registerPushToken(token)
            if (result.ok) {
                android.util.Log.d("NOVA_PUSH", "Refreshed FCM token registered (length=${token.length})")
            } else {
                android.util.Log.w("NOVA_PUSH", "Refreshed FCM token registration failed")
            }
        }.start()
    }

    private fun createPushNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PUSH_CHANNEL_ID,
                "Nova push notifications",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Updates and alerts from Nova"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val PUSH_CHANNEL_ID = "nova_push"
        private const val DEFAULT_TITLE = "Nova"
        private const val DEFAULT_BODY = "You have a new Nova notification."
        private val notificationIds = AtomicInteger()
    }
}
