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
import com.example.security.SecureLaptopGateway
import com.example.security.LiveVideoSession
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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
    val networkLatencyMs: Long? = null,
    val isAwayMode: Boolean = false,
    val awaySensitivity: String = "HIGH", // "HIGH" (5%) or "ULTRA" (2%)
    val connectionMode: String = "AUTO", // "AUTO", "LAN", "WEB"
    val streamQualityPreset: String = "BALANCED", // "ECO" (360p), "BALANCED" (540p), "ULTRA" (720p/1080p)
    val isBackgroundServiceActive: Boolean = false,
    val recentDiagnostics: List<DiagnosticEvent> = emptyList(),
    // GPS Location Tracking Telemetry (OpenStreetMap)
    val currentLatitude: Double = 0.0,
    val currentLongitude: Double = 0.0,
    val gpsAccuracyMeters: Float = 0.0f,
    val gpsAltitudeMeters: Double = 0.0,
    val gpsSpeedKmh: Float = 0.0f,
    val gpsProvider: String = "UNAVAILABLE",
    val lastGpsFixTime: Long = 0L,
    val isLocationTrackingActive: Boolean = false,
    val locationAddress: String = "No verified laptop location available",
    val hasLocationCoordinates: Boolean = false,
    val isLocationDemo: Boolean = false,
    // Geofencing & Hardware Tamper
    val isGeofenceArmed: Boolean = false,
    val geofenceRadiusMeters: Float = 60.0f,
    val isOutsideGeofence: Boolean = false,
    val isLidClosedTamperDetected: Boolean = false,
    val isPowerDisconnectTamperDetected: Boolean = false,
    val isPeripheralTamperDetected: Boolean = false,
    // Active Biometric Detection Trail
    val activeSubjectName: String = "No verified subject",
    val activeSubjectTag: String = "",
    val activeSubjectConfidence: Float = 0.0f,
    val activeSubjectCategory: String = "UNKNOWN", // "AUTHORIZED", "INVESTIGATE", "FLAGGED_THREAT"
    val securePairingStatus: String = "NOT_PAIRED",
    val secureArmed: Boolean = false,
    val secureEvidenceId: String? = null,
    val secureRelayUrl: String = "",
    val webRtcStatus: String = "STOPPED"

)

class LaptopMonitorViewModel(application: Application) : AndroidViewModel(application) {
    // Geofence anchors must come from a verified owner's position, never a
    // hard-coded coordinate or the laptop's first post-theft observation.
    // Not persisted until an authenticated, consented anchor schema exists.
    private var geofenceAnchor: Pair<Double, Double>? = null
    private val database = AppDatabase.getInstance(application)
    private val repository = LaptopRepository(
        laptopDao = database.laptopDao(),
        intruderLogDao = database.intruderLogDao(),
        locationDao = database.locationDao(),
        subjectProfileDao = database.subjectProfileDao()
    )
    private val secureGateway = SecureLaptopGateway(application)
    private val liveVideo = LiveVideoSession(application, secureGateway)
    val liveVideoTrack = liveVideo.track
    fun videoEglContext() = liveVideo.eglContext()
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
        val pairingState = runCatching {
            when {
                secureGateway.isApproved() -> "PAIRED"
                secureGateway.hasPairing() -> "PENDING_APPROVAL"
                else -> "NOT_PAIRED"
            }
        }.getOrDefault("ERROR")
        _uiState.value = _uiState.value.copy(
            securePairingStatus = pairingState,
            secureRelayUrl = secureGateway.configuredRelayUrl()
        )
        viewModelScope.launch {
            liveVideo.state.collect { mediaState ->
                _uiState.value = _uiState.value.copy(
                    webRtcStatus = mediaState,
                    isAutoStreamingEnabled = mediaState == "CONNECTED"
                )
            }
        }
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
            // The old foreground dataSync service polls the insecure legacy
            // HTTP/PIN interface and cannot run indefinitely on Android 15+.
            // Do not auto-start it or its heartbeat. The paired gateway is
            // explicitly owner-operated while the app is foregrounded.
        }
    }

    fun scanLaptopQr(rawQr: String) {
        viewModelScope.launch(coroutineExceptionHandler) {
            _uiState.value = _uiState.value.copy(isSendingAction = true)
            val result = secureGateway.requestPairing(rawQr)
            _uiState.value = _uiState.value.copy(
                isSendingAction = false,
                securePairingStatus = if (result.isSuccess) "PENDING_APPROVAL" else "NOT_PAIRED",
                toastFeedback = if (result.isSuccess) {
                    "Phone request sent. Approve it on the laptop, then tap Verify."
                } else "Pairing failed: " + (result.exceptionOrNull()?.message ?: "Invalid QR")
            )
        }
    }

    fun verifySecurePairing() {
        viewModelScope.launch(coroutineExceptionHandler) {
            val result = secureGateway.verifyApproval()
            _uiState.value = _uiState.value.copy(
                securePairingStatus = if (result.isSuccess) "PAIRED" else "PENDING_APPROVAL",
                toastFeedback = if (result.isSuccess) "Secure laptop pairing verified!"
                    else (result.exceptionOrNull()?.message ?: "Approval pending")
            )
            if (result.isSuccess) {
                repository.setDemoMode(false)
                val status = secureGateway.status().getOrNull()
                _uiState.value = _uiState.value.copy(
                    secureArmed = status?.optBoolean("motion_armed", false) == true,
                    connectionState = ConnectionState.CONNECTED,
                    activeTransport = "SIGNED_TLS",
                    statusMessage = "Paired encrypted connection"
                )
            }
        }
    }

    fun startLiveVideo() {
        if (!secureGateway.isApproved() || !_uiState.value.secureArmed) {
            _uiState.value = _uiState.value.copy(
                toastFeedback = "Pair your laptop and arm the camera before starting live video"
            )
            return
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            val started = liveVideo.connect()
            if (started.isFailure) {
                _uiState.value = _uiState.value.copy(
                    toastFeedback = "Encrypted video negotiation failed. Try an authenticated photo: " +
                        (started.exceptionOrNull()?.message ?: "Transport unavailable")
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    activeTransport = "WEBRTC",
                    toastFeedback = "WebRTC negotiated; waiting for first live frame"
                )
            }
        }
    }

    fun stopLiveVideo() {
        viewModelScope.launch(coroutineExceptionHandler) {
            liveVideo.disconnect()
            _uiState.value = _uiState.value.copy(
                webRtcStatus = "STOPPED",
                isAutoStreamingEnabled = false,
                toastFeedback = "Encrypted live camera session stopped"
            )
        }
    }

    fun rediscoverSecureLaptop() {
        viewModelScope.launch(coroutineExceptionHandler) {
            val discovered = secureGateway.rediscoverTrustedLaptop()
            _uiState.value = _uiState.value.copy(
                toastFeedback = if (discovered.isSuccess)
                    "Verified paired laptop on trusted LAN: " + discovered.getOrThrow()
                else "Paired laptop not found: " +
                    (discovered.exceptionOrNull()?.message ?: "Check Wi-Fi and desktop gateway")
            )
            if (discovered.isSuccess) testConnection()
        }
    }

    fun forgetSecurePairing() {
        // User chooses to forget this desktop identity. Revoke the phone
        // from the laptop as well to prevent stale remote authorization.
        secureGateway.forgetDevice()
        _uiState.value = _uiState.value.copy(
            securePairingStatus = "NOT_PAIRED",
            secureArmed = false,
            connectionState = ConnectionState.DISCONNECTED,
            toastFeedback = "Phone pairing forgotten. Revoke it on the laptop too."
        )
    }

    fun setSecureCameraArmed(armed: Boolean) {
        viewModelScope.launch(coroutineExceptionHandler) {
            val result = secureGateway.arm(armed)
            if (result.isSuccess) repository.setMotionArmed(armed)
            _uiState.value = _uiState.value.copy(
                secureArmed = if (result.isSuccess) armed else _uiState.value.secureArmed,
                toastFeedback = if (result.isSuccess) {
                    if (armed) "Laptop camera armed; visible capture permitted" else "Laptop camera disarmed"
                } else "Arm command failed: " + (result.exceptionOrNull()?.message ?: "Disconnected")
            )
        }
    }

    fun configureRemoteRelay(url: String) {
        val result = runCatching { secureGateway.setRelayUrl(url) }
        _uiState.value = _uiState.value.copy(
            secureRelayUrl = secureGateway.configuredRelayUrl(),
            toastFeedback = if (result.isSuccess) "Remote location endpoint configured"
            else "Invalid relay URL: " + (result.exceptionOrNull()?.message ?: "Invalid HTTPS endpoint")
        )
    }

    fun getRemoteEnrollmentText(): String {
        val info = secureGateway.enrollmentInfo()
        return if (info == null) "Approve and verify this phone first"
        else "PHONE DEVICE ID: " + info.first + "\nPUBLIC KEY (base64url): " + info.second
    }

    fun refreshSecureLastSeen() {
        viewModelScope.launch(coroutineExceptionHandler) {
            val local = secureGateway.lastSeen()
            val remote = if (local.isFailure && secureGateway.configuredRelayUrl().isNotBlank()) {
                secureGateway.remoteLastSeen()
            } else null
            val json = (remote ?: local).getOrNull()
            if (json == null) {
                _uiState.value = _uiState.value.copy(
                    toastFeedback = "No verified last-seen record available: " +
                        ((remote ?: local).exceptionOrNull()?.message ?: "Receiver unavailable")
                )
                return@launch
            }
            val lat = json.optDouble("latitude", Double.NaN)
            val lon = json.optDouble("longitude", Double.NaN)
            val accuracy = json.optDouble("accuracy_meters", Double.NaN)
            val seen = json.optLong("observed_at_ms", 0L)
            if (!lat.isFinite() || !lon.isFinite() || !accuracy.isFinite() ||
                lat !in -90.0..90.0 || lon !in -180.0..180.0 ||
                accuracy <= 0.0 || seen <= 0L || seen > System.currentTimeMillis() + 30_000L) {
                _uiState.value = _uiState.value.copy(toastFeedback = "Unverified location data rejected")
                return@launch
            }
            val ageMinutes = ((System.currentTimeMillis() - seen).coerceAtLeast(0L) / 60_000L)
            _uiState.value = _uiState.value.copy(
                currentLatitude = lat, currentLongitude = lon,
                hasLocationCoordinates = true, isLocationDemo = false,
                isLocationTrackingActive = false, gpsAccuracyMeters = accuracy.toFloat(),
                lastGpsFixTime = seen,
                gpsProvider = json.optString("provider", "UNKNOWN"),
                locationAddress = (if (remote != null) "Remote-delivered last seen " else "Local laptop last seen ") +
                    ageMinutes + " min ago (±" + accuracy.toInt() + "m reported); not live",
                toastFeedback = "Last seen loaded; laptop must be reachable for this query"
            )
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
                _uiState.value = _uiState.value.copy(
                    hasLocationCoordinates = false,
                    isLocationDemo = false,
                    isLocationTrackingActive = false,
                    locationAddress = "No verified laptop location available",
                    gpsAccuracyMeters = 0.0f
                )
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
        val newState = !config.isMotionArmed
        viewModelScope.launch(coroutineExceptionHandler) {
            if (!config.isDemoMode) {
                if (!secureGateway.isApproved()) {
                    _uiState.value = _uiState.value.copy(
                        toastFeedback = "Pair and approve a secure laptop before arming the camera"
                    )
                    return@launch
                }
                val remote = secureGateway.arm(newState)
                if (remote.isFailure) {
                    _uiState.value = _uiState.value.copy(
                        toastFeedback = "Laptop did not change arm state: " +
                            (remote.exceptionOrNull()?.message ?: "Network offline")
                    )
                    return@launch
                }
            }
            repository.setMotionArmed(newState)
            _uiState.value = _uiState.value.copy(
                secureArmed = newState,
                toastFeedback = if (config.isDemoMode) "Demo arming simulated"
                    else if (newState) "Laptop camera armed (person alerts not configured)"
                    else "Laptop camera disarmed",
                threatLevel = if (newState) "SECURE" else "DISARMED"
            )
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
                statusMessage = "Demo only; no real laptop connection",
                networkLatencyMs = null
            )
            return
        }
        if (!secureGateway.isApproved()) {
            _uiState.value = _uiState.value.copy(
                connectionState = ConnectionState.DISCONNECTED,
                statusMessage = "Scan QR, approve phone on laptop, then verify pairing",
                networkLatencyMs = null
            )
            return
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            _uiState.value = _uiState.value.copy(
                connectionState = ConnectionState.CONNECTING,
                statusMessage = "Checking signed HTTPS laptop gateway"
            )
            val startedAt = android.os.SystemClock.elapsedRealtime()
            val result = secureGateway.status()
            val latency = android.os.SystemClock.elapsedRealtime() - startedAt
            val json = result.getOrNull()
            _uiState.value = _uiState.value.copy(
                connectionState = if (json != null) ConnectionState.CONNECTED else ConnectionState.ERROR,
                statusMessage = if (json != null) "LaptopWatch paired gateway online"
                    else "Laptop unreachable: " + (result.exceptionOrNull()?.message ?: "Unknown error"),
                activeTransport = "SIGNED_TLS",
                networkLatencyMs = if (json != null) latency else null,
                secureArmed = json?.optBoolean("motion_armed", false) ?: _uiState.value.secureArmed,
                toastFeedback = if (json != null) "Signed laptop connection verified"
                    else "Laptop did not respond; retry while desktop gateway is active"
            )
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

            val transport = "SIGNED_TLS"
            val result: Result<Boolean> = if (secureGateway.isApproved()) {
                secureGateway.lock().map { true }
            } else Result.failure(IllegalStateException("Secure laptop pairing required"))
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

            val transport = "SIGNED_TLS"
            val result: Result<Boolean> = if (secureGateway.isApproved()) {
                secureGateway.alarm().map { true }
            } else Result.failure(IllegalStateException("Secure laptop pairing required"))
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
        if (secureGateway.isApproved() && _uiState.value.secureArmed) {
            captureManualSnapshot()
        } else {
            _uiState.value = _uiState.value.copy(
                toastFeedback = "Pair and arm the laptop to capture an authenticated camera frame"
            )
        }
    }

    fun toggleAutoStreaming() {
        // A periodic JPEG refresh is not encrypted real-time WebRTC, and
        // displaying a 'LIVE' badge would be a false security promise.
        _uiState.value = _uiState.value.copy(
            isAutoStreamingEnabled = false,
            toastFeedback = "Live WebRTC streaming not active. Capture an authenticated photo instead."
        )
        autoStreamJob?.cancel()
    }

    fun captureManualSnapshot() {
        val config = laptopConfig.value ?: return
        viewModelScope.launch(coroutineExceptionHandler) {
            if (config.isDemoMode) {
                _uiState.value = _uiState.value.copy(
                    toastFeedback = "Demo mode: no real image captured. Pair your laptop to save evidence."
                )
                return@launch
            }
            if (!secureGateway.isApproved()) {
                _uiState.value = _uiState.value.copy(toastFeedback = "Pair and approve this phone before taking photos")
                return@launch
            }
            val capture = secureGateway.photo()
            val identifier = capture.getOrNull()?.optString("id", "") ?: ""
            if (capture.isFailure || identifier.isEmpty()) {
                _uiState.value = _uiState.value.copy(toastFeedback = "Photo failed: " +
                    (capture.exceptionOrNull()?.message ?: "Camera is disarmed or unavailable"))
                return@launch
            }
            val saved = secureGateway.download("photo", identifier)
            if (saved.isFailure) {
                _uiState.value = _uiState.value.copy(toastFeedback =
                    "Photo saved on laptop but download failed: " + saved.exceptionOrNull()?.message)
                return@launch
            }
            val uri = android.net.Uri.fromFile(saved.getOrThrow()).toString()
            _uiState.value = _uiState.value.copy(
                lastCapturedPhotoUrl = uri,
                secureEvidenceId = identifier,
                recentSnapshots = listOf(uri) + _uiState.value.recentSnapshots.take(7),
                cameraRefreshTrigger = System.currentTimeMillis(),
                toastFeedback = "Photo saved securely on both laptop and phone"
            )
            repository.recordEvent(
                eventType = "LaptopWatch Photo",
                description = "Owner captured photo on explicitly armed laptop (evidence " + identifier + ")",
                snapshotUrl = uri,
                warningIssued = "Owner camera action",
                wasLocked = _uiState.value.isLocked,
                severity = "INFO",
                category = "USER_ACTION"
            )
        }
    }

    fun captureAutoMotionSnapshot(config: LaptopConfigEntity) {
        // A motion-difference threshold is not proof of an intruder.
        // Person-class verified auto-capture is not yet enabled.
        if (!config.isDemoMode) {
            _uiState.value = _uiState.value.copy(
                toastFeedback = "Automatic security photo requires verified person detection"
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
            if (config.isDemoMode) {
                _uiState.value = _uiState.value.copy(
                    toastFeedback = "Demo only; no actual laptop video recording"
                )
                return@launch
            }
            if (!secureGateway.isApproved()) {
                _uiState.value = _uiState.value.copy(toastFeedback = "Pair the laptop before recording")
                return@launch
            }
            val started = secureGateway.startRecording()
            val id = started.getOrNull()?.optString("id", "") ?: ""
            if (started.isFailure || id.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    toastFeedback = "Video could not start: " +
                        (started.exceptionOrNull()?.message ?: "Camera disarmed or unavailable")
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                isSubjectRecordingActive = true,
                subjectRecordingDurationSec = 0,
                secureEvidenceId = id,
                toastFeedback = "Recording MP4 on laptop. Camera indicator is active."
            )
            subjectRecordingJob?.cancel()
            subjectRecordingJob = viewModelScope.launch {
                var elapsed = 0
                while (isActive && _uiState.value.isSubjectRecordingActive) {
                    delay(1000)
                    elapsed++
                    _uiState.value = _uiState.value.copy(subjectRecordingDurationSec = elapsed)
                    if ((isAuto && elapsed >= 12) || elapsed >= 180) {
                        stopSubjectRecording(isAuto = isAuto)
                        break
                    }
                }
            }
        }
    }

    fun stopSubjectRecording(isAuto: Boolean = false) {
        if (!_uiState.value.isSubjectRecordingActive) return
        viewModelScope.launch(coroutineExceptionHandler) {
            val duration = _uiState.value.subjectRecordingDurationSec
            val result = secureGateway.stopRecording()
            val record = result.getOrNull()
            val id = record?.optString("id", "") ?: ""
            if (result.isFailure || id.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    toastFeedback = "Video stop/finalize failed: " +
                        (result.exceptionOrNull()?.message ?: "Recording unavailable"),
                    isSubjectRecordingActive = false
                )
                subjectRecordingJob?.cancel()
                return@launch
            }
            val downloaded = secureGateway.download("video", id)
            subjectRecordingJob?.cancel()
            subjectRecordingJob = null
            _uiState.value = _uiState.value.copy(
                isSubjectRecordingActive = false,
                secureEvidenceId = id,
                toastFeedback = if (downloaded.isSuccess)
                    "MP4 saved on laptop and phone (" + duration + " seconds)"
                else "MP4 saved on laptop; phone download unavailable"
            )
            repository.recordEvent(
                eventType = "LaptopWatch Video",
                description = "Owner recorded MP4 on armed laptop, evidence " + id +
                    " (" + duration + " seconds)",
                wasLocked = _uiState.value.isLocked,
                severity = "INFO",
                category = "USER_ACTION"
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
                delay(300)
                _uiState.value = _uiState.value.copy(
                    currentLatitude = 40.7128902,
                    currentLongitude = -74.0060804,
                    hasLocationCoordinates = true,
                    isLocationDemo = true,
                    isLocationTrackingActive = false,
                    gpsAccuracyMeters = 0.0f,
                    gpsProvider = "SIMULATION",
                    lastGpsFixTime = 0L,
                    locationAddress = "Simulated demo position — not a GPS fix",
                    toastFeedback = "Demo position only; no real laptop GPS"
                )
                return@launch
            }

            val (activeTarget, transport) = repository.resolveActiveTarget(config)
            val result = repository.fetchLiveLocationFromLaptop(activeTarget, config.port, config.pin)
            if (result.isSuccess) {
                val loc = result.getOrNull()!!
                _uiState.value = _uiState.value.copy(
                    currentLatitude = loc.latitude,
                    currentLongitude = loc.longitude,
                    hasLocationCoordinates = true,
                    isLocationDemo = false,
                    isLocationTrackingActive = true,
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
                // Old demo records were written to Room as GPS_HARDWARE.
                // Never use legacy unverified records as stolen-device evidence.
                val prior = repository.getLatestLocationDirect()?.takeIf {
                    it.provider == "gpsd-gnss" &&
                    it.accuracyMeters > 0f &&
                    it.latitude in -90.0..90.0 &&
                    it.longitude in -180.0..180.0
                }
                _uiState.value = _uiState.value.copy(
                    toastFeedback = if (prior != null) "Offline; showing the last verified GNSS fix" else "No verified laptop location: $err",
                    currentLatitude = prior?.latitude ?: 0.0,
                    currentLongitude = prior?.longitude ?: 0.0,
                    hasLocationCoordinates = prior != null,
                    isLocationDemo = false,
                    isLocationTrackingActive = false,
                    locationAddress = prior?.let {
                        "Last seen ${java.util.Date(it.timestamp)} (reported ±${it.accuracyMeters}m); device offline"
                    } ?: "No verified laptop location available",
                    gpsProvider = prior?.provider ?: "UNAVAILABLE",
                    lastGpsFixTime = prior?.timestamp ?: 0L,
                    gpsAccuracyMeters = prior?.accuracyMeters ?: 0.0f
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
        if (!_uiState.value.isGeofenceArmed || !_uiState.value.isLocationTrackingActive || _uiState.value.isLocationDemo) return
        val anchor = geofenceAnchor ?: return
        val (anchorLat, anchorLng) = anchor
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
        val state = _uiState.value
        if (newState && (!state.hasLocationCoordinates || state.isLocationDemo || !state.isLocationTrackingActive || state.gpsAccuracyMeters <= 0f)) {
            _uiState.value = state.copy(
                toastFeedback = "A verified live laptop location is required before arming a geofence"
            )
            return
        }
        geofenceAnchor = if (newState) state.currentLatitude to state.currentLongitude else null
        _uiState.value = state.copy(
            isGeofenceArmed = newState,
            isOutsideGeofence = false,
            toastFeedback = if (newState) "Geofence anchored to current verified position" else "Geofence boundary disarmed"
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
        // Never construct legacy HTTP URLs containing PIN query parameters.
        // Coil previews a previously authenticated JPEG from app-private files.
        if (laptopConfig.value?.isDemoMode == true) return null
        return _uiState.value.lastCapturedPhotoUrl
    }

    fun setStreamQuality(preset: String) {
        _uiState.value = _uiState.value.copy(
            streamQualityPreset = preset,
            toastFeedback = "Webcam stream quality: $preset"
        )
    }

    fun toggleBackgroundService() {
        // The legacy sticky dataSync FGS performs unsigned PIN polling and
        // is time-limited by newer Android platform policy. Do not start it.
        com.example.service.LaptopMonitorService.stop(getApplication<Application>())
        _uiState.value = _uiState.value.copy(
            isBackgroundServiceActive = false,
            toastFeedback = "Background monitoring requires signed push delivery; legacy polling is disabled"
        )
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
        liveVideo.release()
    }
}
