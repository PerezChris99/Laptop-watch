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
import com.example.data.LaptopRepository
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
    val isRecordingVoice: Boolean = false,
    val recordingDurationSec: Int = 0,
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
    val recentDiagnostics: List<DiagnosticEvent> = emptyList()
)

class LaptopMonitorViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val repository = LaptopRepository(database.laptopDao(), database.intruderLogDao())
    private val voiceRecorder = VoiceRecorderHelper(application)

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

    private var heartbeatJob: Job? = null
    private var recordingTimerJob: Job? = null
    private var motionCooldownJob: Job? = null

    init {
        viewModelScope.launch(coroutineExceptionHandler) {
            repository.initializeDefaultsIfNeeded()
            recordDiagnostic(
                tag = "SYSTEM",
                status = "HEALTHY",
                message = "Laptop Security Engine started. Room DB verified."
            )
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

    fun updateConfig(name: String, ip: String, port: Int, pin: String) {
        viewModelScope.launch(coroutineExceptionHandler) {
            val current = laptopConfig.value ?: LaptopConfigEntity()
            val updated = current.copy(
                laptopName = name.trim(),
                ipAddress = ip.trim(),
                port = port,
                pin = pin.trim()
            )
            repository.updateConfig(updated)
            _uiState.value = _uiState.value.copy(toastFeedback = "Laptop connection settings saved")
            recordDiagnostic(
                tag = "CONFIG",
                status = "HEALTHY",
                message = "Updated laptop link config: $ip:$port"
            )
            testConnection()
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
                repository.syncMotionConfigToLaptop(
                    config.ipAddress, config.port, config.pin,
                    armed = newArmed, sensitivity = config.motionSensitivity, autoLock = config.autoLockOnMotion
                )
            }
        }
    }

    fun setMotionSensitivity(sensitivity: String) {
        val config = laptopConfig.value ?: return
        viewModelScope.launch(coroutineExceptionHandler) {
            repository.setMotionSensitivity(sensitivity)
            _uiState.value = _uiState.value.copy(toastFeedback = "Motion sensitivity: $sensitivity")
            if (!config.isDemoMode) {
                repository.syncMotionConfigToLaptop(
                    config.ipAddress, config.port, config.pin,
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

            // Record incident into Room database
            repository.recordEvent(
                eventType = "Motion Detected",
                description = "Suspicious movement detected in front of laptop webcam (88% intensity)",
                warningIssued = if (config.autoTtsOnMotion) config.ttsWarningPhrase else "Motion Alert",
                wasLocked = _uiState.value.isLocked,
                severity = "ALERT"
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
                statusMessage = "Demo Laptop Connected (Ready)"
            )
            return
        }

        viewModelScope.launch(coroutineExceptionHandler) {
            _uiState.value = _uiState.value.copy(
                connectionState = ConnectionState.CONNECTING,
                statusMessage = "Connecting to ${config.ipAddress}:${config.port}..."
            )
            val result = repository.checkLaptopStatus(config.ipAddress, config.port, config.pin)
            if (result.isSuccess) {
                val res = result.getOrNull()!!
                _uiState.value = _uiState.value.copy(
                    connectionState = ConnectionState.CONNECTED,
                    statusMessage = "Connected to ${res.hostname}",
                    batteryLevel = res.battery,
                    isLocked = res.isLocked,
                    isCameraActive = res.cameraActive,
                    motionAlertActive = res.motionDetected,
                    motionIntensity = res.motionIntensity,
                    threatLevel = res.threatLevel,
                    lastMotionTime = res.lastMotionTime,
                    toastFeedback = "Connected to laptop successfully!"
                )
                repository.setLockStatus(res.isLocked)
                recordDiagnostic(
                    tag = "NETWORK",
                    status = "HEALTHY",
                    message = "Ping successful to ${config.ipAddress}. Laptop is online."
                )
            } else {
                val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Connection failed"
                _uiState.value = _uiState.value.copy(
                    connectionState = ConnectionState.ERROR,
                    statusMessage = "Offline ($errorMsg)",
                    toastFeedback = "Could not reach laptop. Make sure companion script is running."
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
                repository.recordEvent(
                    eventType = "Screen Locked",
                    description = "Remote lockdown sent from Tecno Camon 12 Air",
                    warningIssued = "Screen locked immediately",
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

            val result = repository.lockLaptop(config.ipAddress, config.port, config.pin)
            _uiState.value = _uiState.value.copy(isSendingAction = false)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLocked = true,
                    toastFeedback = "🔒 Laptop screen LOCKED successfully!"
                )
                repository.setLockStatus(true)
                repository.recordEvent(
                    eventType = "Screen Locked",
                    description = "Remote lock command executed",
                    wasLocked = true,
                    severity = "ALERT"
                )
                recordDiagnostic(
                    tag = "LOCKDOWN",
                    status = "HEALTHY",
                    message = "Real lock command confirmed by laptop OS"
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

            val result = repository.sendTTSWarning(config.ipAddress, config.port, config.pin, message)
            _uiState.value = _uiState.value.copy(isSendingAction = false)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    toastFeedback = "📢 Spoken through laptop: \"$message\""
                )
                repository.recordEvent(
                    eventType = "Voice Warning",
                    description = "Voice warning broadcasted",
                    warningIssued = message,
                    wasLocked = _uiState.value.isLocked,
                    severity = "WARNING"
                )
                recordDiagnostic(
                    tag = "AUDIO",
                    status = "HEALTHY",
                    message = "Spoken warning sent to laptop speakers: $message"
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
                repository.recordEvent(
                    eventType = "Alarm Fired",
                    description = "High-decibel anti-theft siren triggered",
                    warningIssued = "Siren Alert",
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

            val result = repository.triggerLaptopAlarm(config.ipAddress, config.port, config.pin)
            _uiState.value = _uiState.value.copy(isSendingAction = false)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    toastFeedback = "🚨 Anti-theft siren triggered on laptop!"
                )
                repository.recordEvent(
                    eventType = "Alarm Fired",
                    description = "Anti-theft deterrent siren sounded",
                    wasLocked = true,
                    severity = "ALERT"
                )
                recordDiagnostic(
                    tag = "ALARM",
                    status = "HEALTHY",
                    message = "Alarm trigger delivered to laptop"
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

            val result = repository.sendAudioWarning(config.ipAddress, config.port, config.pin, recordedFile)
            _uiState.value = _uiState.value.copy(isSendingAction = false)
            try { recordedFile.delete() } catch (_: Exception) {}

            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    toastFeedback = "🎙️ Voice transmitted & played on laptop!"
                )
                repository.recordEvent(
                    eventType = "Live Voice Mic",
                    description = "Microphone voice message played on laptop",
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

    fun refreshCameraFrame() {
        _uiState.value = _uiState.value.copy(cameraRefreshTrigger = System.currentTimeMillis())
    }

    fun captureManualSnapshot() {
        viewModelScope.launch(coroutineExceptionHandler) {
            vibrateDevice(false)
            _uiState.value = _uiState.value.copy(
                toastFeedback = "📸 Snapshot saved to Intruder Log"
            )
            repository.recordEvent(
                eventType = "Manual Snapshot",
                description = "Photo captured by laptop webcam",
                warningIssued = "Surveillance Snapshot",
                wasLocked = _uiState.value.isLocked,
                severity = "INFO"
            )
        }
    }

    fun deleteLog(id: Long) {
        viewModelScope.launch(coroutineExceptionHandler) {
            repository.deleteLog(id)
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
        return repository.getCameraSnapshotUrl(config.ipAddress, config.port, config.pin)
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
                        val result = repository.checkLaptopStatus(config.ipAddress, config.port, config.pin)
                        if (result.isSuccess) {
                            val status = result.getOrNull()!!
                            val wasMotion = _uiState.value.motionAlertActive
                            val newMotion = status.motionDetected

                            // If motion newly triggered from real laptop:
                            if (!wasMotion && newMotion && config.isMotionArmed) {
                                vibrateDevice(true)
                                if (config.autoLockOnMotion && !status.isLocked) {
                                    lockLaptopRemotely()
                                }
                                repository.recordEvent(
                                    eventType = "Motion Detected",
                                    description = "Webcam detected movement in front of laptop",
                                    warningIssued = "Motion Alarm",
                                    wasLocked = status.isLocked,
                                    severity = "ALERT"
                                )
                            }

                            _uiState.value = _uiState.value.copy(
                                connectionState = ConnectionState.CONNECTED,
                                batteryLevel = status.battery,
                                isLocked = status.isLocked,
                                isCameraActive = status.cameraActive,
                                motionAlertActive = newMotion,
                                motionIntensity = status.motionIntensity,
                                threatLevel = status.threatLevel,
                                lastMotionTime = status.lastMotionTime
                            )
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
        voiceRecorder.cancelRecording()
    }
}
