package com.example.viewmodel

import android.app.Application
import android.os.Vibrator
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.VoiceRecorderHelper
import com.example.data.AppDatabase
import com.example.data.DiagnosticEvent
import com.example.data.IntruderLogEntity
import com.example.data.LaptopConfigEntity
import com.example.data.LaptopLocationEntity
import com.example.data.LaptopRepository
import com.example.data.SubjectProfileEntity
import com.example.util.NotificationHelper
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

enum class ConnectionState {
    CONNECTED,
    CONNECTING,
    DISCONNECTED,
    DEMO_MODE,
    ERROR
}

enum class MonitorTab {
    DASHBOARD, // Central Alert & Defense Dashboard
    CAMERA,    // Live Webcam & Motion Box
    TRACKER,   // GPS Location Map & History (OpenStreetMap)
    INTERCOM,  // Mic & Voice Warnings
    LOGS,      // Incident History & Snapshots
    SETUP      // Pairing & Diagnostics
}

data class MonitorUiState(
    val connectionState: ConnectionState = ConnectionState.DEMO_MODE,
    val statusMessage: String = "Operating in Safe Demo Mode",
    val batteryLevel: String = "88%",
    val isLocked: Boolean = false,
    val isCameraActive: Boolean = true,
    val cameraRefreshTrigger: Long = System.currentTimeMillis(),
    val isAutoStreamingEnabled: Boolean = false,
    val isRecordingVoice: Boolean = false,
    val recordingDurationSec: Int = 0,
    val isSubjectRecordingActive: Boolean = false,
    val subjectRecordingDurationSec: Int = 0,
    val lastCapturedPhotoUrl: String? = null,
    val recentSnapshots: List<String> = emptyList(),
    val subjectDetected: Boolean = false,
    val isSendingAction: Boolean = false,
    val activeTab: MonitorTab = MonitorTab.DASHBOARD,
    val toastFeedback: String? = null,
    val motionAlertActive: Boolean = false,
    val motionIntensity: Int = 0,
    val threatLevel: String = "SECURE", // "SECURE", "ELEVATED", "ALERT"
    val lastMotionTime: Long = 0L,
    val lastSnapshotTaken: String? = null,
    val systemHealthStatus: String = "100% OPERATIONAL",
    val caughtErrorsCount: Int = 0,
    val activeTransport: String = "LAN", // "LAN" or "WEB"
    val networkLatencyMs: Long? = 28L,
    val isAwayMode: Boolean = false,
    val awaySensitivity: String = "HIGH", // "HIGH" (5%) or "ULTRA" (2%)
    val connectionMode: String = "AUTO", // "AUTO", "LAN", "WEB"
    val streamQualityPreset: String = "BALANCED", // "ECO" (360p), "BALANCED" (540p), "ULTRA" (720p/1080p)
    val isBackgroundServiceActive: Boolean = true,
    val recentDiagnostics: List<DiagnosticEvent> = emptyList(),
    // GPS Location Tracking Telemetry (OpenStreetMap)
    val currentLatitude: Double = 40.7128902,
    val currentLongitude: Double = -74.0060804,
    val gpsAccuracyMeters: Float = 1.0f,
    val gpsAltitudeMeters: Double = 15.4,
    val gpsSpeedKmh: Float = 0.0f,
    val gpsProvider: String = "GPS_HARDWARE (RTK 1-METER)",
    val lastGpsFixTime: Long = System.currentTimeMillis() - 1000 * 60 * 5,
    val isLocationTrackingActive: Boolean = true,
    val locationAddress: String = "Workstation Floor 3 • Desk 4B (±1.0m Pinpoint)",
    // Geofencing & Hardware Tamper
    val isGeofenceArmed: Boolean = true,
    val geofenceRadiusMeters: Float = 60.0f,
    val isOutsideGeofence: Boolean = false,
    val isLidClosedTamperDetected: Boolean = false,
    val isPowerDisconnectTamperDetected: Boolean = false,
    val isPeripheralTamperDetected: Boolean = false,
    // Active Biometric Detection Trail
    val activeSubjectName: String = "Authorized Owner",
    val activeSubjectTag: String = "PROFILE-01",
    val activeSubjectConfidence: Float = 0.98f,
    val activeSubjectCategory: String = "AUTHORIZED" // "AUTHORIZED", "INVESTIGATE", "FLAGGED_THREAT"
)

class LaptopMonitorViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val repository = LaptopRepository(
        laptopDao = database.laptopDao(),
        intruderLogDao = database.intruderLogDao(),
        locationDao = database.locationDao(),
        subjectProfileDao = database.subjectProfileDao()
    )
    private val voiceRecorder = VoiceRecorderHelper(application)
    private val notificationHelper = NotificationHelper(application)

    // Global Safe Coroutine Exception Handler to catch and log any unhandled coroutine errors proactively
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("LaptopMonitorVM", "Caught coroutine exception: ${throwable.message}", throwable)
        recordDiagnostic(
            tag = "WATCHDOG",
            status = "RECOVERED",
            message = "Safely caught and neutralized error: ${throwable.localizedMessage ?: "Unknown"}"
        )
    }

    private val _uiState = MutableStateFlow(MonitorUiState())
    val uiState: StateFlow<MonitorUiState> = _uiState.asStateFlow()

    val laptopConfig: StateFlow<LaptopConfigEntity?> = repository.laptopConfig.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val intruderLogs: StateFlow<List<IntruderLogEntity>> = repository.intruderLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val locationHistory: StateFlow<List<LaptopLocationEntity>> = repository.laptopLocations.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val latestLocation: StateFlow<LaptopLocationEntity?> = repository.latestLocation.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val subjectProfiles: StateFlow<List<SubjectProfileEntity>> = repository.subjectProfiles.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private var heartbeatJob: Job? = null
    private var recordingTimerJob: Job? = null
    private var motionCooldownJob: Job? = null
    private var autoStreamJob: Job? = null
    private var subjectRecordingJob: Job? = null

    init {
        viewModelScope.launch(coroutineExceptionHandler) {
            repository.initializeDefaultsIfNeeded()
            val initialConfig = repository.getConfigDirect()
            if (initialConfig != null) {
                _uiState.value = _uiState.value.copy(
                    isAwayMode = initialConfig.isAwayMode,
                    awaySensitivity = initialConfig.awayMotionSensitivity,
                    connectionMode = initialConfig.connectionMode,
                    activeTransport = initialConfig.activeTransport
                )
            }
            recordDiagnostic(
                tag = "SYSTEM",
                status = "HEALTHY",
                message = "Laptop Security Engine started. Ready for LAN & Web connections."
            )
            try {
                com.example.service.LaptopMonitorService.start(application)
            } catch (e: Exception) {
                Log.w("LaptopMonitorVM", "Foreground service init notice: ${e.message}")
            }
            startHeartbeat()
        }
    }

    fun switchTab(tab: MonitorTab) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(toastFeedback = null)
    }

    fun recordDiagnostic(tag: String, status: String, message: String) {
        val event = DiagnosticEvent(tag = tag, status = status, message = message)
        val current = _uiState.value.recentDiagnostics.toMutableList()
        current.add(0, event)
        val trimmed = if (current.size > 25) current.subList(0, 25) else current
        val errorsCount = if (status == "RECOVERED" || status == "WARNING") {
            _uiState.value.caughtErrorsCount + 1
        } else {
            _uiState.value.caughtErrorsCount
        }
        _uiState.value = _uiState.value.copy(
            recentDiagnostics = trimmed,
            caughtErrorsCount = errorsCount
        )
    }

    fun toggleDemoMode() {
        val config = laptopConfig.value ?: return
        val newDemoState = !config.isDemoMode
        viewModelScope.launch(coroutineExceptionHandler) {
            repository.setDemoMode(newDemoState)
            if (newDemoState) {
                _uiState.value = _uiState.value.copy(
                    connectionState = ConnectionState.DEMO_MODE,
                    statusMessage = "Switched to Safe Demo Mode"
                )
                recordDiagnostic(
                    tag = "NETWORK",
                    status = "HEALTHY",
                    message = "Switched into safe demo simulation mode"
                )
            } else {
                testConnection()
            }
        }
    }

    fun updateConfig(name: String, ip: String, port: Int, pin: String, webUrl: String = "", mode: String = "AUTO") {
        viewModelScope.launch(coroutineExceptionHandler) {
            val current = laptopConfig.value ?: LaptopConfigEntity()
            val updated = current.copy(
                laptopName = name.trim(),
                ipAddress = ip.trim(),
                port = port,
                pin = pin.trim(),
                remoteWebUrl = webUrl.trim(),
                connectionMode = mode
            )
            repository.updateConfig(updated)
            _uiState.value = _uiState.value.copy(
                toastFeedback = "Configuration saved. Auto-detecting route...",
                connectionMode = mode
            )
            recordDiagnostic(
                tag = "CONFIG",
                status = "HEALTHY",
                message = "Updated connection settings (Mode: $mode, LAN: $ip:$port, Web: $webUrl)"
            )
            testConnection()
        }
    }

    fun setConnectionMode(mode: String) {
        val config = laptopConfig.value ?: return
        viewModelScope.launch(coroutineExceptionHandler) {
            repository.setConnectionMode(mode)
            _uiState.value = _uiState.value.copy(
                connectionMode = mode,
                toastFeedback = "Connection Mode: $mode"
            )
            testConnection()
        }
    }

    // Away Mode with heightened motion sensitivity
    fun toggleAwayMode() {
        val config = laptopConfig.value ?: return
        val newAway = !config.isAwayMode
        viewModelScope.launch(coroutineExceptionHandler) {
            repository.setAwayMode(newAway)
            if (newAway) {
                // When activating Away Mode:
                repository.setMotionArmed(true)
                _uiState.value = _uiState.value.copy(
                    isAwayMode = true,
                    threatLevel = "SECURE",
                    toastFeedback = "🏃 Away Mode ACTIVATED! High-sensitivity radar & auto-lock armed."
                )
                recordDiagnostic(
                    tag = "AWAY_MODE",
                    status = "HEALTHY",
                    message = "Away Mode enabled with ${config.awayMotionSensitivity} sensitivity"
                )
                repository.recordEvent(
                    eventType = "Away Guard Armed",
                    description = "Away Mode active: Radar armed to ${config.awayMotionSensitivity} sensitivity.",
                    warningIssued = "Away Protocol Active",
                    wasLocked = _uiState.value.isLocked,
                    severity = "INFO"
                )
                if (!config.isDemoMode) {
                    val (target, _) = repository.resolveActiveTarget(config)
                    repository.syncMotionConfigToLaptop(
                        target, config.port, config.pin,
                        armed = true, sensitivity = config.awayMotionSensitivity, autoLock = true
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    isAwayMode = false,
                    toastFeedback = "Away Mode deactivated. Normal radar restored."
                )
                recordDiagnostic(
                    tag = "AWAY_MODE",
                    status = "HEALTHY",
                    message = "Away Mode deactivated. Normal sensitivity restored."
                )
                if (!config.isDemoMode) {
                    val (target, _) = repository.resolveActiveTarget(config)
                    repository.syncMotionConfigToLaptop(
                        target, config.port, config.pin,
                        armed = config.isMotionArmed, sensitivity = config.motionSensitivity, autoLock = config.autoLockOnMotion
                    )
                }
            }
        }
    }

    fun setAwaySensitivity(sensitivity: String) {
        val config = laptopConfig.value ?: return
        viewModelScope.launch(coroutineExceptionHandler) {
            repository.setAwaySensitivity(sensitivity)
            _uiState.value = _uiState.value.copy(
                awaySensitivity = sensitivity,
                toastFeedback = "Away sensitivity: $sensitivity"
            )
            if (config.isAwayMode && !config.isDemoMode) {
                val (target, _) = repository.resolveActiveTarget(config)
                repository.syncMotionConfigToLaptop(
                    target, config.port, config.pin,
                    armed = true, sensitivity = sensitivity, autoLock = true
                )
            }
        }
    }

    // Motion Detection Defense Settings
    fun toggleMotionArmed() {
        val config = laptopConfig.value ?: return
        val newArmed = !config.isMotionArmed
        viewModelScope.launch(coroutineExceptionHandler) {
            repository.setMotionArmed(newArmed)
            val msg = if (newArmed) "🛡️ Motion Detection ARMED" else "⚠️ Motion Detection DISARMED"
            _uiState.value = _uiState.value.copy(
                toastFeedback = msg,
                threatLevel = if (newArmed) "SECURE" else "DISARMED"
            )
            recordDiagnostic(
                tag = "SECURITY",
                status = if (newArmed) "HEALTHY" else "WARNING",
                message = "Motion sensor arm state set to $newArmed"
            )
            if (!config.isDemoMode) {
                val (target, _) = repository.resolveActiveTarget(config)
                val effectiveSens = if (config.isAwayMode) config.awayMotionSensitivity else config.motionSensitivity
                repository.syncMotionConfigToLaptop(
                    target, config.port, config.pin,
                    armed = newArmed, sensitivity = effectiveSens, autoLock = config.autoLockOnMotion
                )
            }
        }
    }

    fun setMotionSensitivity(sensitivity: String) {
        val config = laptopConfig.value ?: return
        viewModelScope.launch(coroutineExceptionHandler) {
            repository.setMotionSensitivity(sensitivity)
            _uiState.value = _uiState.value.copy(toastFeedback = "Normal sensitivity: $sensitivity")
            if (!config.isDemoMode && !config.isAwayMode) {
                val (target, _) = repository.resolveActiveTarget(config)
                repository.syncMotionConfigToLaptop(
                    target, config.port, config.pin,
                    armed = config.isMotionArmed, sensitivity = sensitivity, autoLock = config.autoLockOnMotion
                )
            }
        }
    }

    fun toggleAutoLockOnMotion() {
        val config = laptopConfig.value ?: return
        viewModelScope.launch(coroutineExceptionHandler) {
            val newState = !config.autoLockOnMotion
            repository.setAutoLockOnMotion(newState)
            _uiState.value = _uiState.value.copy(
                toastFeedback = if (newState) "Auto-Lock ON motion enabled" else "Auto-Lock ON motion disabled"
            )
        }
    }

    fun toggleAutoAlarmOnMotion() {
        val config = laptopConfig.value ?: return
        viewModelScope.launch(coroutineExceptionHandler) {
            val newState = !config.autoAlarmOnMotion
            repository.setAutoAlarmOnMotion(newState)
            _uiState.value = _uiState.value.copy(
                toastFeedback = if (newState) "Siren ON motion enabled" else "Siren ON motion disabled"
            )
        }
    }

    fun toggleAutoSnapOnMotion() {
        val config = laptopConfig.value ?: return
        viewModelScope.launch(coroutineExceptionHandler) {
            val newState = !config.autoSnapOnMotion
            repository.setAutoSnapOnMotion(newState)
            _uiState.value = _uiState.value.copy(
                toastFeedback = if (newState) "Auto-Snapshot ON motion enabled" else "Auto-Snapshot disabled"
            )
        }
    }

    fun toggleAutoRecordOnMotion() {
        val config = laptopConfig.value ?: return
        viewModelScope.launch(coroutineExceptionHandler) {
            val newState = !config.autoRecordOnMotion
            repository.setAutoRecordOnMotion(newState)
            _uiState.value = _uiState.value.copy(
                toastFeedback = if (newState) "Auto-Record Subject ON motion enabled" else "Auto-Record Subject disabled"
            )
        }
    }

    fun toggleAutoTtsOnMotion() {
        val config = laptopConfig.value ?: return
        viewModelScope.launch(coroutineExceptionHandler) {
            val newState = !config.autoTtsOnMotion
            repository.setAutoTtsOnMotion(newState)
            _uiState.value = _uiState.value.copy(
                toastFeedback = if (newState) "Voice warning ON motion enabled" else "Voice warning disabled"
            )
        }
    }

    // Motion Detection Simulation Trigger (For instant testing without physical laptop)
    fun simulateIntruderMotion() {
        val config = laptopConfig.value ?: LaptopConfigEntity()
        if (!config.isMotionArmed) {
            _uiState.value = _uiState.value.copy(toastFeedback = "Motion detection is currently DISARMED! Arm it first.")
            return
        }

        viewModelScope.launch(coroutineExceptionHandler) {
            vibrateDevice(true)
            val now = System.currentTimeMillis()
            _uiState.value = _uiState.value.copy(
                motionAlertActive = true,
                motionIntensity = (75..95).random(),
                threatLevel = "ALERT",
                lastMotionTime = now,
                toastFeedback = "⚠️ MOTION DETECTED! Intruder movement spotted!"
            )

            recordDiagnostic(
                tag = "SECURITY",
                status = "WARNING",
                message = "Intruder motion detected near laptop keyboard area"
            )

            // Automated responses based on configured rules
            if (config.autoLockOnMotion && !_uiState.value.isLocked) {
                _uiState.value = _uiState.value.copy(isLocked = true)
                repository.setLockStatus(true)
                recordDiagnostic(
                    tag = "SECURITY",
                    status = "HEALTHY",
                    message = "Auto-Lock executed: screen locked instantly"
                )
            }

            if (config.autoAlarmOnMotion) {
                triggerDeterrentAlarm()
            }

            if (config.autoTtsOnMotion) {
                sendTTSWarning(config.ttsWarningPhrase)
            }

            if (config.autoSnapOnMotion) {
                captureAutoMotionSnapshot(config)
            }

            if (config.autoRecordOnMotion) {
                startSubjectRecording(isAuto = true)
            }

            // Trigger Push Notification immediately
            notificationHelper.showMotionAlertNotification(
                title = if (_uiState.value.isAwayMode) "🚨 Intruder Detected (Away Radar)" else "🚨 Intruder Movement Detected!",
                message = "Webcam spotted movement near your laptop workspace.",
                isAwayMode = _uiState.value.isAwayMode
            )

            // Record incident into Room database
            repository.recordEvent(
                eventType = "Motion Detected",
                description = "Suspicious movement detected in front of laptop webcam (${_uiState.value.motionIntensity}% intensity)",
                warningIssued = if (config.autoTtsOnMotion) config.ttsWarningPhrase else "Motion Alert",
                wasLocked = _uiState.value.isLocked,
                severity = "ALERT",
                category = "SECURITY"
            )

            // Start cooldown to restore threat level to ELEVATED after 8s
            motionCooldownJob?.cancel()
            motionCooldownJob = viewModelScope.launch {
                delay(8000)
                if (_uiState.value.threatLevel == "ALERT") {
                    _uiState.value = _uiState.value.copy(
                        motionAlertActive = false,
                        motionIntensity = 0,
                        threatLevel = "ELEVATED"
                    )
                }
            }
        }
    }

    fun dismissMotionAlert() {
        motionCooldownJob?.cancel()
        _uiState.value = _uiState.value.copy(
            motionAlertActive = false,
            motionIntensity = 0,
            threatLevel = "SECURE",
            toastFeedback = "Alert dismissed"
        )
    }

    fun testConnection() {
        val config = laptopConfig.value ?: return
        if (config.isDemoMode) {
            _uiState.value = _uiState.value.copy(
                connectionState = ConnectionState.DEMO_MODE,
                statusMessage = "Demo Laptop Connected (Ready)",
                networkLatencyMs = (18..32).random().toLong()
            )
            return
        }

        viewModelScope.launch(coroutineExceptionHandler) {
            _uiState.value = _uiState.value.copy(
                connectionState = ConnectionState.CONNECTING,
                statusMessage = "Auto-detecting link (LAN / Web)..."
            )
            val (activeTarget, transport) = repository.resolveActiveTarget(config)
            val latency = repository.pingLatencyMs(activeTarget, config.pin) ?: 28L
            val result = repository.checkLaptopStatus(activeTarget, config.port, config.pin)
            if (result.isSuccess) {
                val res = result.getOrNull()!!
                val routeLabel = if (transport == "WEB") "Web Cloud" else "LAN Direct"
                _uiState.value = _uiState.value.copy(
                    connectionState = ConnectionState.CONNECTED,
                    statusMessage = "$routeLabel: ${res.hostname}",
                    activeTransport = transport,
                    networkLatencyMs = latency,
                    batteryLevel = res.battery,
                    isLocked = res.isLocked,
                    isCameraActive = res.cameraActive,
                    motionAlertActive = res.motionDetected,
                    motionIntensity = res.motionIntensity,
                    threatLevel = res.threatLevel,
                    lastMotionTime = res.lastMotionTime,
                    toastFeedback = "Connected via $routeLabel!"
                )
                repository.setLockStatus(res.isLocked)
                recordDiagnostic(
                    tag = "NETWORK",
                    status = "HEALTHY",
                    message = "Active route: $routeLabel ($activeTarget). Ping OK."
                )
            } else {
                val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Connection failed"
                _uiState.value = _uiState.value.copy(
                    connectionState = ConnectionState.ERROR,
                    statusMessage = "Offline ($errorMsg)",
                    toastFeedback = "Could not reach laptop via LAN or Web URL."
                )
                recordDiagnostic(
                    tag = "NETWORK",
                    status = "RECOVERED",
                    message = "Connection timeout caught: $errorMsg (App running normally)"
                )
            }
        }
    }

    fun lockLaptopRemotely() {
        val config = laptopConfig.value ?: return
        viewModelScope.launch(coroutineExceptionHandler) {
            _uiState.value = _uiState.value.copy(isSendingAction = true)
            vibrateDevice(false)

            if (config.isDemoMode) {
                delay(600)
                _uiState.value = _uiState.value.copy(
                    isLocked = true,
                    isSendingAction = false,
                    toastFeedback = "🔒 Laptop screen LOCKED remotely!"
                )
                repository.setLockStatus(true)
                notificationHelper.showRemoteLockNotification(isLocked = true)
                repository.recordUserAction(
                    action = "Screen Locked Remotely",
                    details = "Remote lock command executed (Demo Simulation)",
                    wasLocked = true,
                    severity = "ALERT"
                )
                recordDiagnostic(
                    tag = "LOCKDOWN",
                    status = "HEALTHY",
                    message = "Remote lock simulation executed"
                )
                return@launch
            }

            val (activeTarget, transport) = repository.resolveActiveTarget(config)
            val result = repository.lockLaptop(activeTarget, config.port, config.pin)
            _uiState.value = _uiState.value.copy(isSendingAction = false, activeTransport = transport)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLocked = true,
                    toastFeedback = "🔒 Laptop screen LOCKED successfully!"
                )
                repository.setLockStatus(true)
                notificationHelper.showRemoteLockNotification(isLocked = true)
                repository.recordUserAction(
                    action = "Screen Locked Remotely",
                    details = "Remote lock command executed (via $transport)",
                    wasLocked = true,
                    severity = "ALERT"
                )
                recordDiagnostic(
                    tag = "LOCKDOWN",
                    status = "HEALTHY",
                    message = "Real lock command confirmed via $transport"
                )
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Unknown error"
                _uiState.value = _uiState.value.copy(
                    toastFeedback = "Failed to lock: $err"
                )
                recordDiagnostic(
                    tag = "LOCKDOWN",
                    status = "RECOVERED",
                    message = "Lock request failed ($err). Error caught safely."
                )
            }
        }
    }

    fun unlockLaptopSimulation() {
        viewModelScope.launch(coroutineExceptionHandler) {
            _uiState.value = _uiState.value.copy(
                isLocked = false,
                toastFeedback = "Laptop unlocked"
            )
            repository.setLockStatus(false)
            notificationHelper.showRemoteLockNotification(isLocked = false)
            repository.recordUserAction(
                action = "Screen Unlocked",
                details = "Workstation display unlocked simulation confirmed",
                wasLocked = false,
                severity = "INFO"
            )
            recordDiagnostic(
                tag = "LOCKDOWN",
                status = "HEALTHY",
                message = "Laptop unlock state updated"
            )
        }
    }

    fun sendTTSWarning(message: String) {
        if (message.isBlank()) return
        val config = laptopConfig.value ?: return

        viewModelScope.launch(coroutineExceptionHandler) {
            _uiState.value = _uiState.value.copy(isSendingAction = true)
            vibrateDevice(false)

            if (config.isDemoMode) {
                delay(800)
                _uiState.value = _uiState.value.copy(
                    isSendingAction = false,
                    toastFeedback = "📢 Spoken through laptop: \"$message\""
                )
                repository.recordEvent(
                    eventType = "Voice Warning",
                    description = "TTS announcement broadcasted to laptop speakers",
                    warningIssued = message,
                    wasLocked = _uiState.value.isLocked,
                    severity = "WARNING"
                )
                recordDiagnostic(
                    tag = "AUDIO",
                    status = "HEALTHY",
                    message = "TTS simulated: \"$message\""
                )
                return@launch
            }

            val (target, transport) = repository.resolveActiveTarget(config)
            val result = repository.sendTTSWarning(target, config.port, config.pin, message)
            _uiState.value = _uiState.value.copy(isSendingAction = false, activeTransport = transport)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    toastFeedback = "📢 Spoken through laptop: \"$message\""
                )
                repository.recordEvent(
                    eventType = "Voice Warning",
                    description = "Voice warning broadcasted (via $transport)",
                    warningIssued = message,
                    wasLocked = _uiState.value.isLocked,
                    severity = "WARNING"
                )
                recordDiagnostic(
                    tag = "AUDIO",
                    status = "HEALTHY",
                    message = "Spoken warning sent to laptop speakers via $transport: $message"
                )
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Unknown"
                _uiState.value = _uiState.value.copy(
                    toastFeedback = "Failed to transmit voice warning"
                )
                recordDiagnostic(
                    tag = "AUDIO",
                    status = "RECOVERED",
                    message = "TTS dispatch failed ($err). Error handled."
                )
            }
        }
    }

    fun triggerDeterrentAlarm() {
        val config = laptopConfig.value ?: return
        viewModelScope.launch(coroutineExceptionHandler) {
            _uiState.value = _uiState.value.copy(isSendingAction = true)
            vibrateDevice(true)

            if (config.isDemoMode) {
                delay(700)
                _uiState.value = _uiState.value.copy(
                    isSendingAction = false,
                    toastFeedback = "🚨 Deterrent alarm sounding on laptop!"
                )
                notificationHelper.showAlarmNotification("High-decibel anti-theft siren triggered.")
                repository.recordUserAction(
                    action = "Deterrent Siren Fired",
                    details = "Audible anti-theft siren triggered (Demo Simulation)",
                    wasLocked = true,
                    severity = "ALERT"
                )
                recordDiagnostic(
                    tag = "ALARM",
                    status = "HEALTHY",
                    message = "High-pitch siren alarm simulated"
                )
                return@launch
            }

            val (target, transport) = repository.resolveActiveTarget(config)
            val result = repository.triggerLaptopAlarm(target, config.port, config.pin)
            _uiState.value = _uiState.value.copy(isSendingAction = false, activeTransport = transport)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    toastFeedback = "🚨 Anti-theft siren triggered on laptop!"
                )
                notificationHelper.showAlarmNotification("Anti-theft deterrent siren sounded on laptop speakers.")
                repository.recordUserAction(
                    action = "Deterrent Siren Fired",
                    details = "Audible anti-theft siren sounded (via $transport)",
                    wasLocked = true,
                    severity = "ALERT"
                )
                recordDiagnostic(
                    tag = "ALARM",
                    status = "HEALTHY",
                    message = "Alarm trigger delivered to laptop via $transport"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    toastFeedback = "Alarm request failed"
                )
                recordDiagnostic(
                    tag = "ALARM",
                    status = "RECOVERED",
                    message = "Alarm trigger failed. Error handled."
                )
            }
        }
    }

    fun startVoiceRecording() {
        val file = voiceRecorder.startRecording()
        if (file != null) {
            _uiState.value = _uiState.value.copy(
                isRecordingVoice = true,
                recordingDurationSec = 0
            )
            startRecordingTimer()
        } else {
            _uiState.value = _uiState.value.copy(
                toastFeedback = "Could not access microphone"
            )
            recordDiagnostic(
                tag = "AUDIO",
                status = "RECOVERED",
                message = "Microphone initialization failed. Safely handled without crash."
            )
        }
    }

    fun stopAndSendVoiceRecording() {
        stopRecordingTimer()
        val recordedFile = voiceRecorder.stopRecording()
        _uiState.value = _uiState.value.copy(isRecordingVoice = false)

        if (recordedFile == null || !recordedFile.exists()) {
            _uiState.value = _uiState.value.copy(toastFeedback = "No audio captured")
            return
        }

        val config = laptopConfig.value ?: return
        viewModelScope.launch(coroutineExceptionHandler) {
            _uiState.value = _uiState.value.copy(isSendingAction = true)
            if (config.isDemoMode) {
                delay(800)
                _uiState.value = _uiState.value.copy(
                    isSendingAction = false,
                    toastFeedback = "🎙️ Voice transmitted & played on laptop!"
                )
                repository.recordEvent(
                    eventType = "Live Voice Mic",
                    description = "Live microphone audio played out loud on laptop",
                    warningIssued = "Voice transmission",
                    wasLocked = _uiState.value.isLocked,
                    severity = "WARNING"
                )
                try { recordedFile.delete() } catch (_: Exception) {}
                return@launch
            }

            val (target, transport) = repository.resolveActiveTarget(config)
            val result = repository.sendAudioWarning(target, config.port, config.pin, recordedFile)
            _uiState.value = _uiState.value.copy(isSendingAction = false, activeTransport = transport)
            try { recordedFile.delete() } catch (_: Exception) {}

            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    toastFeedback = "🎙️ Voice transmitted & played on laptop!"
                )
                repository.recordEvent(
                    eventType = "Live Voice Mic",
                    description = "Microphone voice message played on laptop (via $transport)",
                    warningIssued = "Voice transmission",
                    wasLocked = _uiState.value.isLocked,
                    severity = "WARNING"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    toastFeedback = "Failed to send audio to laptop"
                )
                recordDiagnostic(
                    tag = "AUDIO",
                    status = "RECOVERED",
                    message = "Audio transmission failed. File cleaned up safely."
                )
            }
        }
    }

    fun cancelVoiceRecording() {
        stopRecordingTimer()
        voiceRecorder.cancelRecording()
        _uiState.value = _uiState.value.copy(isRecordingVoice = false)
    }

    fun toggleLockscreen() {
        if (_uiState.value.isLocked) {
            unlockLaptopSimulation()
        } else {
            lockLaptopRemotely()
        }
    }

    fun refreshCameraFrame() {
        _uiState.value = _uiState.value.copy(cameraRefreshTrigger = System.currentTimeMillis())
    }

    fun toggleAutoStreaming() {
        val willEnable = !_uiState.value.isAutoStreamingEnabled
        _uiState.value = _uiState.value.copy(
            isAutoStreamingEnabled = willEnable,
            toastFeedback = if (willEnable) "🟢 Live auto-stream preview active" else "Stream paused (Manual mode)"
        )
        autoStreamJob?.cancel()
        if (willEnable) {
            autoStreamJob = viewModelScope.launch {
                while (isActive) {
                    delay(1200)
                    _uiState.value = _uiState.value.copy(cameraRefreshTrigger = System.currentTimeMillis())
                }
            }
        }
    }

    fun captureManualSnapshot() {
        val config = laptopConfig.value ?: return
        viewModelScope.launch(coroutineExceptionHandler) {
            vibrateDevice(false)
            val now = System.currentTimeMillis()
            val snapshotUrl: String?
            if (config.isDemoMode) {
                snapshotUrl = "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?auto=format&fit=crop&w=600&q=80"
            } else {
                val (target, _) = repository.resolveActiveTarget(config)
                val snapResult = repository.captureSnapshot(target, config.port, config.pin)
                snapshotUrl = snapResult.getOrNull() ?: repository.getCameraSnapshotUrl(target, config.port, config.pin)
            }
            val updatedList = listOfNotNull(snapshotUrl) + _uiState.value.recentSnapshots.take(7)
            _uiState.value = _uiState.value.copy(
                toastFeedback = "📸 Snapshot captured & saved to log!",
                lastCapturedPhotoUrl = snapshotUrl,
                recentSnapshots = updatedList
            )
            repository.recordEvent(
                eventType = "Manual Snapshot",
                description = "High-definition photo captured by laptop webcam",
                snapshotUrl = snapshotUrl,
                warningIssued = "Manual Shutter",
                wasLocked = _uiState.value.isLocked,
                severity = "INFO",
                category = "USER_ACTION"
            )
        }
    }

    fun captureAutoMotionSnapshot(config: LaptopConfigEntity) {
        viewModelScope.launch(coroutineExceptionHandler) {
            val snapshotUrl: String?
            if (config.isDemoMode) {
                snapshotUrl = "https://images.unsplash.com/photo-1550751827-4bd374c3f58b?auto=format&fit=crop&w=600&q=80"
            } else {
                val (target, _) = repository.resolveActiveTarget(config)
                val snapResult = repository.captureSnapshot(target, config.port, config.pin)
                snapshotUrl = snapResult.getOrNull() ?: repository.getCameraSnapshotUrl(target, config.port, config.pin)
            }
            val updatedList = listOfNotNull(snapshotUrl) + _uiState.value.recentSnapshots.take(7)
            _uiState.value = _uiState.value.copy(
                lastCapturedPhotoUrl = snapshotUrl,
                recentSnapshots = updatedList,
                subjectDetected = true
            )
            repository.recordEvent(
                eventType = "Auto Snapshot",
                description = "Subject auto-photographed upon motion trigger",
                snapshotUrl = snapshotUrl,
                warningIssued = "Intruder Auto-Snap",
                wasLocked = _uiState.value.isLocked,
                severity = "ALERT",
                category = "SECURITY"
            )
        }
    }

    fun toggleSubjectRecording() {
        if (_uiState.value.isSubjectRecordingActive) {
            stopSubjectRecording(isAuto = false)
        } else {
            startSubjectRecording(isAuto = false)
        }
    }

    fun startSubjectRecording(isAuto: Boolean = false) {
        val config = laptopConfig.value ?: return
        if (_uiState.value.isSubjectRecordingActive) return

        viewModelScope.launch(coroutineExceptionHandler) {
            _uiState.value = _uiState.value.copy(
                isSubjectRecordingActive = true,
                subjectRecordingDurationSec = 0,
                subjectDetected = true,
                toastFeedback = if (isAuto) "🔴 AUTO-RECORDING Subject in progress..." else "🔴 Recording Subject video started..."
            )
            vibrateDevice(true)
            if (!config.isDemoMode) {
                val (target, _) = repository.resolveActiveTarget(config)
                repository.startRecording(target, config.port, config.pin)
            }
            // Start recording duration timer
            subjectRecordingJob?.cancel()
            subjectRecordingJob = viewModelScope.launch {
                var seconds = 0
                while (isActive && _uiState.value.isSubjectRecordingActive) {
                    delay(1000)
                    seconds++
                    _uiState.value = _uiState.value.copy(subjectRecordingDurationSec = seconds)
                    if (isAuto && seconds >= 12) {
                        // Auto-recorded clip ends after 12 seconds
                        stopSubjectRecording(isAuto = true)
                        break
                    }
                }
            }
        }
    }

    fun stopSubjectRecording(isAuto: Boolean = false) {
        val config = laptopConfig.value ?: return
        subjectRecordingJob?.cancel()
        subjectRecordingJob = null
        val duration = _uiState.value.subjectRecordingDurationSec
        _uiState.value = _uiState.value.copy(isSubjectRecordingActive = false)

        viewModelScope.launch(coroutineExceptionHandler) {
            var filename = "subject_recording_${System.currentTimeMillis()}.mp4"
            if (!config.isDemoMode) {
                val (target, _) = repository.resolveActiveTarget(config)
                val stopRes = repository.stopRecording(target, config.port, config.pin)
                filename = stopRes.getOrNull() ?: filename
            }
            _uiState.value = _uiState.value.copy(
                toastFeedback = "💾 Subject recording saved (${duration}s, $filename)"
            )
            repository.recordEvent(
                eventType = if (isAuto) "Auto Subject Recording" else "Subject Video Recording",
                description = "Recorded ${duration}s video clip of subject saved to laptop storage ($filename)",
                warningIssued = if (isAuto) "Auto Surveillance Video" else "Manual Video Record",
                wasLocked = _uiState.value.isLocked,
                severity = if (isAuto) "ALERT" else "INFO",
                category = if (isAuto) "SECURITY" else "USER_ACTION"
            )
        }
    }

    fun deleteLog(id: Long) {
        viewModelScope.launch(coroutineExceptionHandler) {
            repository.deleteLog(id)
        }
    }

    // =========================================================================
    //  GPS LOCATION TRACKING & OPENSTREETMAP COORDINATES (Live & Last Seen)
    // =========================================================================

    fun fetchLiveGpsCoordinates() {
        val config = laptopConfig.value ?: return
        viewModelScope.launch(coroutineExceptionHandler) {
            _uiState.value = _uiState.value.copy(
                isLocationTrackingActive = true,
                toastFeedback = "Querying laptop GPS hardware..."
            )

            if (config.isDemoMode) {
                delay(700)
                // Strict 1-meter pinpoint high-precision GPS coordinate lock
                val baseLat = 40.7128902
                val baseLng = -74.0060804
                // 0.000009 degrees corresponds to exactly ~1.0 meter
                val driftLat = baseLat + ((1..5).random() - 3) * 0.000009
                val driftLng = baseLng + ((1..5).random() - 3) * 0.000009
                val accuracy = 1.0f // Exactly 1-meter pinpoint precision
                val speed = 0.0f
                val now = System.currentTimeMillis()

                val loc = LaptopLocationEntity(
                    timestamp = now,
                    latitude = driftLat,
                    longitude = driftLng,
                    accuracyMeters = accuracy,
                    altitudeMeters = 15.6,
                    speedKmh = speed,
                    provider = "GPS_HARDWARE (RTK 1-METER)",
                    addressEstimate = "Workstation Floor 3 • Desk 4B (±1.0m Pinpoint Fix)",
                    transport = _uiState.value.activeTransport,
                    isLiveFix = true
                )
                repository.recordLocation(loc)

                // Log event directly into Activity Timeline
                repository.recordEvent(
                    eventType = "GPS Calibrated (±1.0m)",
                    description = "High-precision satellite fix locked: Lat %.7f, Lng %.7f (±1m RTK)".format(driftLat, driftLng),
                    wasLocked = _uiState.value.isLocked,
                    severity = "INFO",
                    category = "GPS",
                    latitude = driftLat,
                    longitude = driftLng,
                    accuracyMeters = 1.0f,
                    locationName = "Workstation Floor 3 • Desk 4B"
                )

                _uiState.value = _uiState.value.copy(
                    currentLatitude = driftLat,
                    currentLongitude = driftLng,
                    gpsAccuracyMeters = accuracy,
                    gpsSpeedKmh = speed,
                    lastGpsFixTime = now,
                    locationAddress = loc.addressEstimate,
                    toastFeedback = "📍 Pinpoint GPS Fix Locked (Accuracy: ±1.0m)"
                )
                recordDiagnostic(
                    tag = "GPS",
                    status = "HEALTHY",
                    message = "GPS Coordinate fix: $driftLat, $driftLng (±1.0m RTK Calibrated)"
                )
                checkGeofenceBreach(driftLat, driftLng)
                return@launch
            }

            val (activeTarget, transport) = repository.resolveActiveTarget(config)
            val result = repository.fetchLiveLocationFromLaptop(activeTarget, config.port, config.pin)
            if (result.isSuccess) {
                val loc = result.getOrNull()!!
                _uiState.value = _uiState.value.copy(
                    currentLatitude = loc.latitude,
                    currentLongitude = loc.longitude,
                    gpsAccuracyMeters = loc.accuracyMeters,
                    gpsAltitudeMeters = loc.altitudeMeters,
                    gpsSpeedKmh = loc.speedKmh,
                    gpsProvider = loc.provider,
                    lastGpsFixTime = loc.timestamp,
                    locationAddress = loc.addressEstimate,
                    toastFeedback = "📍 GPS updated via $transport"
                )
                recordDiagnostic(
                    tag = "GPS",
                    status = "HEALTHY",
                    message = "Received GPS lock from laptop: ${loc.latitude}, ${loc.longitude} via $transport"
                )
                checkGeofenceBreach(loc.latitude, loc.longitude)
            } else {
                val err = result.exceptionOrNull()?.message ?: "GPS hardware unavailable"
                _uiState.value = _uiState.value.copy(
                    toastFeedback = "Failed to fetch GPS: $err"
                )
                recordDiagnostic(
                    tag = "GPS",
                    status = "WARNING",
                    message = "Could not fetch GPS fix: $err"
                )
            }
        }
    }

    private fun checkGeofenceBreach(currentLat: Double, currentLng: Double) {
        if (!_uiState.value.isGeofenceArmed) return
        val anchorLat = 40.712890
        val anchorLng = -74.006080
        val latDist = (currentLat - anchorLat) * 111139.0
        val lngDist = (currentLng - anchorLng) * 111139.0 * Math.cos(Math.toRadians(anchorLat))
        val distanceMeters = Math.sqrt(latDist * latDist + lngDist * lngDist)

        val breached = distanceMeters > _uiState.value.geofenceRadiusMeters
        if (breached && !_uiState.value.isOutsideGeofence) {
            _uiState.value = _uiState.value.copy(
                isOutsideGeofence = true,
                threatLevel = "ALERT",
                toastFeedback = "🚨 GEOFENCE BREACH: Laptop moved beyond ${_uiState.value.geofenceRadiusMeters}m perimeter!"
            )
            vibrateDevice(true)
            notificationHelper.showMotionAlertNotification(
                title = "🚨 GEOFENCE PERIMETER BREACH!",
                message = "Laptop is moving outside the safe boundary (${distanceMeters.toInt()}m from base).",
                isAwayMode = _uiState.value.isAwayMode
            )
            val config = laptopConfig.value
            if (config?.autoLockOnMotion == true && !_uiState.value.isLocked) {
                lockLaptopRemotely()
            }
            viewModelScope.launch {
                repository.recordEvent(
                    eventType = "Geofence Perimeter Breach",
                    description = "Laptop coordinates moved ${distanceMeters.toInt()}m from anchored workstation area.",
                    warningIssued = "Geofence Alarm",
                    wasLocked = _uiState.value.isLocked,
                    severity = "ALERT",
                    category = "SECURITY"
                )
            }
        } else if (!breached) {
            _uiState.value = _uiState.value.copy(isOutsideGeofence = false)
        }
    }

    fun toggleGeofence() {
        val newState = !_uiState.value.isGeofenceArmed
        _uiState.value = _uiState.value.copy(
            isGeofenceArmed = newState,
            toastFeedback = if (newState) "🛡️ GPS Geofence boundary ARMED" else "Geofence boundary disarmed"
        )
    }

    fun updateSubjectClassification(profileId: Long, category: String, notes: String) {
        viewModelScope.launch(coroutineExceptionHandler) {
            repository.updateSubjectClassification(profileId, category, notes)
            _uiState.value = _uiState.value.copy(
                toastFeedback = "Biometric classification updated to $category"
            )
        }
    }

    fun triggerTamperSimulation(tamperType: String) {
        viewModelScope.launch(coroutineExceptionHandler) {
            when (tamperType) {
                "LID_TAMPER" -> {
                    _uiState.value = _uiState.value.copy(
                        isLidClosedTamperDetected = true,
                        threatLevel = "ALERT",
                        toastFeedback = "⚠️ Tamper Warning: Laptop lid manipulation detected!"
                    )
                    vibrateDevice(true)
                    repository.recordEvent(
                        eventType = "Hardware Tamper: Lid Closed/Moved",
                        description = "Physical lid sensor triggered while in Away Sentinel mode.",
                        warningIssued = "Tamper Lockout",
                        wasLocked = true,
                        severity = "ALERT"
                    )
                    lockLaptopRemotely()
                }
                "POWER_TAMPER" -> {
                    _uiState.value = _uiState.value.copy(
                        isPowerDisconnectTamperDetected = true,
                        threatLevel = "ALERT",
                        toastFeedback = "⚠️ Tamper Warning: AC Power Cable abruptly unplugged!"
                    )
                    vibrateDevice(true)
                    repository.recordEvent(
                        eventType = "Hardware Tamper: AC Power Severed",
                        description = "AC adapter was abruptly disconnected, switching to emergency battery.",
                        warningIssued = "Power Tamper Alert",
                        wasLocked = _uiState.value.isLocked,
                        severity = "ALERT"
                    )
                }
                "PERIPHERAL_TAMPER" -> {
                    _uiState.value = _uiState.value.copy(
                        isPeripheralTamperDetected = true,
                        threatLevel = "ALERT",
                        toastFeedback = "⚠️ Tamper Warning: Unauthorized USB device attached/detached!"
                    )
                    vibrateDevice(true)
                    repository.recordEvent(
                        eventType = "Hardware Tamper: USB Peripheral Intrusion",
                        description = "Unidentified USB device or external drive attached while locked.",
                        warningIssued = "Port Lockout Alert",
                        wasLocked = _uiState.value.isLocked,
                        severity = "ALERT"
                    )
                }
            }
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch(coroutineExceptionHandler) {
            repository.clearLogs()
            _uiState.value = _uiState.value.copy(toastFeedback = "Intruder history cleared")
        }
    }

    fun getCameraSnapshotUrl(): String? {
        val config = laptopConfig.value ?: return null
        if (config.isDemoMode) return null
        val target = if (config.activeTransport == "WEB" && config.remoteWebUrl.isNotBlank()) {
            config.remoteWebUrl
        } else {
            "${config.ipAddress}:${config.port}"
        }
        val (quality, scale) = when (_uiState.value.streamQualityPreset) {
            "ECO" -> Pair(40, 0.5f)
            "ULTRA" -> Pair(90, 1.0f)
            else -> Pair(70, 0.75f)
        }
        return repository.getCameraSnapshotUrl(target, config.port, config.pin, quality, scale)
    }

    fun setStreamQuality(preset: String) {
        _uiState.value = _uiState.value.copy(
            streamQualityPreset = preset,
            toastFeedback = "Webcam stream quality: $preset"
        )
    }

    fun toggleBackgroundService() {
        val newState = !_uiState.value.isBackgroundServiceActive
        _uiState.value = _uiState.value.copy(
            isBackgroundServiceActive = newState,
            toastFeedback = if (newState) "Background surveillance service ACTIVE" else "Background service STOPPED"
        )
        val app = getApplication<Application>()
        if (newState) {
            com.example.service.LaptopMonitorService.start(app)
        } else {
            com.example.service.LaptopMonitorService.stop(app)
        }
    }

    private fun startRecordingTimer() {
        recordingTimerJob?.cancel()
        recordingTimerJob = viewModelScope.launch {
            var seconds = 0
            while (isActive && _uiState.value.isRecordingVoice) {
                delay(1000)
                seconds++
                _uiState.value = _uiState.value.copy(recordingDurationSec = seconds)
            }
        }
    }

    private fun stopRecordingTimer() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch(coroutineExceptionHandler) {
            while (isActive) {
                delay(8000)
                val config = laptopConfig.value
                if (config != null && !config.isDemoMode) {
                    try {
                        val (activeTarget, transport) = repository.resolveActiveTarget(config)
                        val result = repository.checkLaptopStatus(activeTarget, config.port, config.pin)
                        if (result.isSuccess) {
                            val status = result.getOrNull()!!
                            val wasMotion = _uiState.value.motionAlertActive
                            val newMotion = status.motionDetected

                            // If motion newly triggered from real laptop:
                            if (!wasMotion && newMotion && config.isMotionArmed) {
                                vibrateDevice(true)
                                notificationHelper.showMotionAlertNotification(
                                    title = if (config.isAwayMode) "🚨 Intruder Movement (Away Mode)!" else "🚨 Intruder Motion Detected!",
                                    message = "Motion detected near your laptop webcam (via $transport).",
                                    isAwayMode = config.isAwayMode
                                )
                                if (config.autoLockOnMotion && !status.isLocked) {
                                    lockLaptopRemotely()
                                }
                                if (config.autoSnapOnMotion) {
                                    captureAutoMotionSnapshot(config)
                                }
                                if (config.autoRecordOnMotion) {
                                    startSubjectRecording(isAuto = true)
                                }
                                repository.recordEvent(
                                    eventType = "Motion Detected",
                                    description = "Webcam detected movement in front of laptop (via $transport)",
                                    warningIssued = "Motion Alarm",
                                    wasLocked = status.isLocked,
                                    severity = "ALERT",
                                    category = "SECURITY"
                                )
                            }

                            _uiState.value = _uiState.value.copy(
                                connectionState = ConnectionState.CONNECTED,
                                activeTransport = transport,
                                batteryLevel = status.battery,
                                isLocked = status.isLocked,
                                isCameraActive = status.cameraActive,
                                motionAlertActive = newMotion,
                                motionIntensity = status.motionIntensity,
                                threatLevel = status.threatLevel,
                                lastMotionTime = status.lastMotionTime,
                                currentLatitude = status.latitude ?: _uiState.value.currentLatitude,
                                currentLongitude = status.longitude ?: _uiState.value.currentLongitude,
                                gpsAccuracyMeters = status.gpsAccuracy ?: _uiState.value.gpsAccuracyMeters,
                                gpsSpeedKmh = status.gpsSpeed ?: _uiState.value.gpsSpeedKmh,
                                gpsAltitudeMeters = status.gpsAltitude ?: _uiState.value.gpsAltitudeMeters,
                                gpsProvider = status.gpsProvider ?: _uiState.value.gpsProvider,
                                lastGpsFixTime = status.lastGpsFixTime ?: _uiState.value.lastGpsFixTime,
                                activeSubjectName = status.subjectIdentified ?: _uiState.value.activeSubjectName,
                                activeSubjectConfidence = if (status.subjectConfidence > 0) status.subjectConfidence else _uiState.value.activeSubjectConfidence
                            )
                            if (status.latitude != null && status.longitude != null) {
                                checkGeofenceBreach(status.latitude, status.longitude)
                            }
                        } else {
                            _uiState.value = _uiState.value.copy(
                                connectionState = ConnectionState.ERROR
                            )
                        }
                    } catch (e: Exception) {
                        recordDiagnostic(
                            tag = "HEARTBEAT",
                            status = "RECOVERED",
                            message = "Heartbeat ping exception caught: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrateDevice(isAlert: Boolean) {
        try {
            val vibrator = getApplication<Application>().getSystemService(Application.VIBRATOR_SERVICE) as? Vibrator
            if (isAlert) {
                vibrator?.vibrate(longArrayOf(0, 150, 100, 250), -1)
            } else {
                vibrator?.vibrate(60)
            }
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        heartbeatJob?.cancel()
        recordingTimerJob?.cancel()
        motionCooldownJob?.cancel()
        autoStreamJob?.cancel()
        subjectRecordingJob?.cancel()
        voiceRecorder.cancelRecording()
    }
}
