package com.example.data

import com.example.network.LaptopApiClient
import com.example.network.LaptopStatusResponse
import kotlinx.coroutines.flow.Flow
import java.io.File

class LaptopRepository(
    private val laptopDao: LaptopDao,
    private val intruderLogDao: IntruderLogDao,
    private val apiClient: LaptopApiClient = LaptopApiClient()
) {
    val laptopConfig: Flow<LaptopConfigEntity?> = laptopDao.getLaptopConfig()
    val intruderLogs: Flow<List<IntruderLogEntity>> = intruderLogDao.getAllLogs()

    suspend fun initializeDefaultsIfNeeded() {
        val current = laptopDao.getLaptopConfigDirect()
        if (current == null) {
            val defaultConfig = LaptopConfigEntity(
                laptopName = "Workstation Laptop",
                ipAddress = "192.168.1.105",
                port = 5000,
                pin = "7890",
                isDemoMode = true,
                isLocked = false
            )
            laptopDao.insertOrUpdateConfig(defaultConfig)

            // Seed initial sample security incident logs for realistic experience
            val now = System.currentTimeMillis()
            intruderLogDao.insertLog(
                IntruderLogEntity(
                    timestamp = now - 1000 * 60 * 42,
                    eventType = "Motion Detected",
                    description = "Webcam detected movement within 1.5m of keyboard area",
                    warningIssued = "Automated Chime Played",
                    wasLocked = false,
                    severity = "WARNING"
                )
            )
            intruderLogDao.insertLog(
                IntruderLogEntity(
                    timestamp = now - 1000 * 60 * 18,
                    eventType = "Lock Triggered",
                    description = "Screen locked remotely via Tecno Camon 12 Air",
                    warningIssued = "Step away from this computer!",
                    wasLocked = true,
                    severity = "ALERT"
                )
            )
        }
    }

    suspend fun updateConfig(config: LaptopConfigEntity) {
        laptopDao.insertOrUpdateConfig(config)
    }

    suspend fun setLockStatus(locked: Boolean) {
        laptopDao.updateLockStatus(locked)
    }

    suspend fun setDemoMode(isDemo: Boolean) {
        laptopDao.updateDemoMode(isDemo)
    }

    suspend fun setMotionArmed(armed: Boolean) {
        laptopDao.updateMotionArmed(armed)
    }

    suspend fun setMotionSensitivity(sensitivity: String) {
        laptopDao.updateMotionSensitivity(sensitivity)
    }

    suspend fun setAutoLockOnMotion(autoLock: Boolean) {
        laptopDao.updateAutoLockOnMotion(autoLock)
    }

    suspend fun setAutoAlarmOnMotion(autoAlarm: Boolean) {
        laptopDao.updateAutoAlarmOnMotion(autoAlarm)
    }

    suspend fun setAutoSnapOnMotion(autoSnap: Boolean) {
        laptopDao.updateAutoSnapOnMotion(autoSnap)
    }

    suspend fun setAutoTtsOnMotion(autoTts: Boolean) {
        laptopDao.updateAutoTtsOnMotion(autoTts)
    }

    suspend fun syncMotionConfigToLaptop(ip: String, port: Int, pin: String, armed: Boolean, sensitivity: String, autoLock: Boolean): Result<Boolean> {
        return apiClient.setMotionConfig(ip, port, pin, armed, sensitivity, autoLock)
    }

    suspend fun recordEvent(
        eventType: String,
        description: String,
        snapshotUrl: String? = null,
        warningIssued: String? = null,
        wasLocked: Boolean = false,
        severity: String = "WARNING"
    ): Long {
        val entity = IntruderLogEntity(
            timestamp = System.currentTimeMillis(),
            eventType = eventType,
            description = description,
            snapshotUrl = snapshotUrl,
            warningIssued = warningIssued,
            wasLocked = wasLocked,
            severity = severity
        )
        return intruderLogDao.insertLog(entity)
    }

    suspend fun deleteLog(id: Long) {
        intruderLogDao.deleteLogById(id)
    }

    suspend fun clearLogs() {
        intruderLogDao.clearAllLogs()
    }

    // Network calls to laptop
    suspend fun checkLaptopStatus(ip: String, port: Int, pin: String): Result<LaptopStatusResponse> {
        return apiClient.getStatus(ip, port, pin)
    }

    suspend fun lockLaptop(ip: String, port: Int, pin: String): Result<Boolean> {
        return apiClient.lockScreen(ip, port, pin)
    }

    suspend fun sendTTSWarning(ip: String, port: Int, pin: String, message: String): Result<Boolean> {
        return apiClient.sendTTSWarning(ip, port, pin, message)
    }

    suspend fun sendAudioWarning(ip: String, port: Int, pin: String, file: File): Result<Boolean> {
        return apiClient.sendAudioWarning(ip, port, pin, file)
    }

    suspend fun triggerLaptopAlarm(ip: String, port: Int, pin: String): Result<Boolean> {
        return apiClient.triggerAlarm(ip, port, pin)
    }

    suspend fun fetchRemoteLogs(ip: String, port: Int, pin: String) {
        val result = apiClient.fetchIntruders(ip, port, pin)
        if (result.isSuccess) {
            val logs = result.getOrNull() ?: emptyList()
            for (remote in logs) {
                intruderLogDao.insertLog(
                    IntruderLogEntity(
                        timestamp = remote.timestamp,
                        eventType = remote.eventType,
                        description = remote.description,
                        snapshotUrl = remote.snapshotPath,
                        severity = "ALERT"
                    )
                )
            }
        }
    }

    fun getCameraSnapshotUrl(ip: String, port: Int, pin: String): String {
        return apiClient.getCameraSnapshotUrl(ip, port, pin)
    }
}
