package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.Vibrator
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.LaptopRepository
import com.example.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LaptopMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var repository: LaptopRepository
    private var pollingJob: Job? = null
    private var lastReportedMotion = false

    companion object {
        private const val TAG = "LaptopMonitorService"

        fun start(context: Context) {
            val intent = Intent(context, LaptopMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LaptopMonitorService::class.java).apply {
                action = NotificationHelper.ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        val database = AppDatabase.getInstance(this)
        repository = LaptopRepository(database.laptopDao(), database.intruderLogDao())

        // Acquire partial wake lock so surveillance loop continues if device screen sleeps
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LaptopMonitor::SurveillanceWakeLock")?.apply {
            setReferenceCounted(false)
            acquire(24 * 60 * 60 * 1000L) // 24h max safeguard
        }

        val initialNotification = notificationHelper.buildForegroundNotification(
            statusText = "Guarding Laptop",
            isArmed = true,
            isLocked = false,
            routeText = "Connecting..."
        )
        startForeground(NotificationHelper.NOTIFICATION_ID_FOREGROUND, initialNotification)

        startSurveillanceLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action != null) {
            handleAction(action)
        }
        return START_STICKY
    }

    private fun handleAction(action: String) {
        serviceScope.launch {
            val config = repository.getConfigDirect() ?: return@launch
            val (target, transport) = repository.resolveActiveTarget(config)

            when (action) {
                NotificationHelper.ACTION_LOCK_NOW -> {
                    Log.i(TAG, "Executing quick action: Lock Laptop")
                    vibrateQuick()
                    if (config.isDemoMode) {
                        repository.setLockStatus(true)
                        notificationHelper.showRemoteLockNotification(isLocked = true)
                    } else {
                        val result = repository.lockLaptop(target, config.port, config.pin)
                        if (result.isSuccess) {
                            repository.setLockStatus(true)
                            notificationHelper.showRemoteLockNotification(isLocked = true)
                            repository.recordUserAction(
                                action = "Screen Locked via Notification",
                                details = "Executed 1-tap remote lock from notification shade",
                                wasLocked = true,
                                severity = "ALERT"
                            )
                        }
                    }
                    updateNotification(status = "Screen Locked", isArmed = config.isMotionArmed, isLocked = true, route = transport)
                }

                NotificationHelper.ACTION_TRIGGER_SIREN -> {
                    Log.i(TAG, "Executing quick action: Trigger Siren")
                    vibrateAlert()
                    if (!config.isDemoMode) {
                        repository.triggerLaptopAlarm(target, config.port, config.pin)
                    }
                    notificationHelper.showAlarmNotification("High-decibel anti-theft siren triggered from notification bar.")
                    repository.recordUserAction(
                        action = "Siren Triggered via Notification",
                        details = "Deterrent siren sounded from notification quick action",
                        wasLocked = config.isLocked,
                        severity = "ALERT"
                    )
                }

                NotificationHelper.ACTION_STOP_SERVICE -> {
                    Log.i(TAG, "Stopping foreground surveillance service")
                    stopSelf()
                }
            }
        }
    }

    private fun startSurveillanceLoop() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            while (isActive) {
                delay(7000)
                try {
                    val config = repository.getConfigDirect() ?: continue
                    if (config.isDemoMode) {
                        updateNotification(
                            status = "Demo Mode Active",
                            isArmed = config.isMotionArmed,
                            isLocked = config.isLocked,
                            route = "Simulation"
                        )
                        continue
                    }

                    val (target, transport) = repository.resolveActiveTarget(config)
                    val statusResult = repository.checkLaptopStatus(target, config.port, config.pin)

                    if (statusResult.isSuccess) {
                        val status = statusResult.getOrNull()!!
                        val motionDetected = status.motionDetected

                        if (!lastReportedMotion && motionDetected && config.isMotionArmed) {
                            vibrateAlert()
                            notificationHelper.showMotionAlertNotification(
                                title = if (config.isAwayMode) "🚨 Intruder Detected (Away Radar)" else "🚨 Intruder Motion Detected!",
                                message = "Webcam spotted movement near your laptop ($transport).",
                                isAwayMode = config.isAwayMode
                            )

                            if (config.autoLockOnMotion && !status.isLocked) {
                                repository.lockLaptop(target, config.port, config.pin)
                                repository.setLockStatus(true)
                            }
                            if (config.autoSnapOnMotion) {
                                val snapResult = repository.captureSnapshot(target, config.port, config.pin)
                                val snapUrl = snapResult.getOrNull() ?: repository.getCameraSnapshotUrl(target, config.port, config.pin)
                                repository.recordEvent(
                                    eventType = "Auto Snapshot",
                                    description = "Subject auto-photographed on motion ($transport)",
                                    snapshotUrl = snapUrl,
                                    wasLocked = status.isLocked,
                                    severity = "ALERT",
                                    category = "SECURITY"
                                )
                            }
                            if (config.autoRecordOnMotion) {
                                repository.startRecording(target, config.port, config.pin)
                            }
                        }

                        lastReportedMotion = motionDetected

                        val statusLabel = when {
                            motionDetected -> "⚠️ MOTION SPOTTED!"
                            config.isAwayMode -> "Away Radar Armed"
                            config.isMotionArmed -> "Monitoring"
                            else -> "Disarmed"
                        }

                        updateNotification(
                            status = statusLabel,
                            isArmed = config.isMotionArmed,
                            isLocked = status.isLocked,
                            route = transport
                        )
                    } else {
                        updateNotification(
                            status = "Offline (Reconnecting)",
                            isArmed = config.isMotionArmed,
                            isLocked = config.isLocked,
                            route = transport
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Surveillance loop error: ${e.message}")
                }
            }
        }
    }

    private fun updateNotification(status: String, isArmed: Boolean, isLocked: Boolean, route: String) {
        val updated = notificationHelper.buildForegroundNotification(
            statusText = status,
            isArmed = isArmed,
            isLocked = isLocked,
            routeText = route
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        manager?.notify(NotificationHelper.NOTIFICATION_ID_FOREGROUND, updated)
    }

    @Suppress("DEPRECATION")
    private fun vibrateQuick() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(75)
        } catch (_: Exception) {}
    }

    @Suppress("DEPRECATION")
    private fun vibrateAlert() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(longArrayOf(0, 200, 150, 300), -1)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
        serviceScope.cancel()
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        Log.i(TAG, "LaptopMonitorService destroyed cleanly")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
