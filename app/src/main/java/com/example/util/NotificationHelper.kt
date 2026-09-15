package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID_ALERTS = "laptop_security_alerts"
        const val CHANNEL_ID_ACTIVITY = "laptop_activity_events"
        private const val NOTIFICATION_ID_MOTION = 1001
        private const val NOTIFICATION_ID_LOCK = 1002
        private const val NOTIFICATION_ID_ALARM = 1003
        private const val NOTIFICATION_ID_ACTIVITY = 1004
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            // High Priority Intruder & Security Alert Channel
            val alertChannel = NotificationChannel(
                CHANNEL_ID_ALERTS,
                "Laptop Security Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alerts for intruder motion, workstation lockouts, and siren deterrence."
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 400)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(alertChannel)

            // Standard Activity & System Events Channel
            val activityChannel = NotificationChannel(
                CHANNEL_ID_ACTIVITY,
                "Activity & Remote Actions",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Updates on remote lock, intercom messages, and system connectivity changes."
            }
            notificationManager.createNotificationChannel(activityChannel)
        }
    }

    private fun canPostNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    private fun getPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showMotionAlertNotification(
        title: String = "🚨 Intruder Detected Near Laptop!",
        message: String = "Movement detected in front of your workstation webcam.",
        isAwayMode: Boolean = false
    ) {
        if (!canPostNotifications()) return

        val awayPrefix = if (isAwayMode) "[AWAY RADAR] " else ""
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("$awayPrefix$title")
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$message\nTap to view live camera feed and engage voice deterrence.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(getPendingIntent())
            .setVibrate(longArrayOf(0, 350, 200, 350))

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_MOTION, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun showRemoteLockNotification(isLocked: Boolean) {
        if (!canPostNotifications()) return

        val title = if (isLocked) "🔒 Workstation Locked Remotely" else "🔓 Workstation Unlocked"
        val message = if (isLocked) {
            "Laptop display locked. Unauthorized access prevented."
        } else {
            "Laptop screen unlocked simulation confirmed."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_ACTIVITY)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(getPendingIntent())

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_LOCK, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun showAlarmNotification(message: String = "Deterrent siren sounded on laptop speakers.") {
        if (!canPostNotifications()) return

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("📢 Deterrent Siren Triggered")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(getPendingIntent())

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_ALARM, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun showActivityNotification(title: String, message: String) {
        if (!canPostNotifications()) return

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_ACTIVITY)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(getPendingIntent())

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_ACTIVITY, builder.build())
        } catch (_: SecurityException) {
        }
    }
}
