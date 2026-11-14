package com.kc.laptopwatch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kc.laptopwatch.mobile.BuildConfig
import com.kc.laptopwatch.mobile.MainActivity
import java.util.UUID

/**
 * Only an owner-approved optional hint channel.
 *
 * FCM payloads are NOT signed forensic evidence and must never trigger
 * lock, alarm, camera, person identity assertions or record operations.
 * Foreground signed incident query verifies any event after app opening.
 * No Firebase backend is provisioned in source.
 */
class LaptopWatchFcmService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        if (!BuildConfig.LAPTOPWATCH_FCM_ENABLED) return
        val data = message.data
        if (data["kind"] != "verified_person_hint") return
        val desktopId = data["desktop_id"] ?: return
        val eventId = data["event_id"]?.toLongOrNull() ?: return
        if (eventId <= 0 || runCatching { UUID.fromString(desktopId) }.isFailure) return
        // Generic notification deliberately makes NO verified-person claim.
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = "laptopwatch-owner-incidents"
        manager.createNotificationChannel(NotificationChannel(
            channel, "LaptopWatch security notices", NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Open to inspect authenticated laptop incidents" })
        val intent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("LaptopWatch — laptop event")
            .setContentText("Your paired laptop reported an event. Open the app to verify.")
            .setContentIntent(intent)
            .setAutoCancel(true).build()
        try { manager.notify(eventId.toInt() and 0x7fffffff, notification) }
        catch (_: SecurityException) { /* User denied notifications */ }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Never log/persist this bearer token. Registration must occur only
        // after explicit owner opt-in with the configured signed relay.
        // Deferred external Firebase/relay provisioning remains BLOCKED.
    }
}
