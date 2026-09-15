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

    suspend fun getConfigDirect(): LaptopConfigEntity? = laptopDao.getLaptopConfigDirect()

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
        severity: String = "WARNING",
        category: String = "SECURITY"
    ): Long {
        val entity = IntruderLogEntity(
            timestamp = System.currentTimeMillis(),
            eventType = eventType,
            description = description,
            snapshotUrl = snapshotUrl,
            warningIssued = warningIssued,
            wasLocked = wasLocked,
            severity = severity,
            category = category
        )
        return intruderLogDao.insertLog(entity)
    }

    suspend fun recordUserAction(
        action: String,
        details: String,
        wasLocked: Boolean = false,
        severity: String = "INFO"
    ): Long {
        return recordEvent(
            eventType = action,
            description = details,
            wasLocked = wasLocked,
            severity = severity,
            category = "USER_ACTION"
        )
    }

    suspend fun recordSystemEvent(
        event: String,
        details: String,
        severity: String = "INFO"
    ): Long {
        return recordEvent(
            eventType = event,
            description = details,
            severity = severity,
            category = "SYSTEM"
        )
    }

    suspend fun deleteLog(id: Long) {
        intruderLogDao.deleteLogById(id)
    }

    suspend fun clearLogs() {
        intruderLogDao.clearAllLogs()
    }

    suspend fun setAwayMode(isAway: Boolean) {
        laptopDao.updateAwayMode(isAway)
    }

    suspend fun setAwaySensitivity(sensitivity: String) {
        laptopDao.updateAwaySensitivity(sensitivity)
    }

    suspend fun setConnectionMode(mode: String) {
        laptopDao.updateConnectionMode(mode)
    }

    suspend fun setActiveTransport(transport: String) {
        laptopDao.updateActiveTransport(transport)
    }

    suspend fun setRemoteWebUrl(url: String) {
        laptopDao.updateRemoteWebUrl(url)
    }

    suspend fun resolveActiveTarget(config: LaptopConfigEntity): Pair<String, String> {
        // Returns Pair(resolvedTargetHostOrUrl, transportType: "LAN" | "WEB")
        val lanTarget = "${config.ipAddress}:${config.port}"
        val webTarget = config.remoteWebUrl.trim()

        return when (config.connectionMode) {
            "LAN" -> Pair(lanTarget, "LAN")
            "WEB" -> Pair(if (webTarget.isNotEmpty()) webTarget else lanTarget, "WEB")
            else -> {
                // AUTO: Test LAN first
                val lanReachable = apiClient.pingEndpoint(lanTarget, config.pin, timeoutSecs = 2)
                if (lanReachable) {
                    if (config.activeTransport != "LAN") {
                        laptopDao.updateActiveTransport("LAN")
                    }
                    Pair(lanTarget, "LAN")
                } else if (webTarget.isNotEmpty()) {
                    val webReachable = apiClient.pingEndpoint(webTarget, config.pin, timeoutSecs = 3)
                    if (webReachable) {
                        if (config.activeTransport != "WEB") {
                            laptopDao.updateActiveTransport("WEB")
                        }
                        Pair(webTarget, "WEB")
                    } else {
                        Pair(lanTarget, "LAN")
                    }
                } else {
                    Pair(lanTarget, "LAN")
                }
            }
        }
    }

    // Network calls to laptop
    suspend fun checkLaptopStatus(ipOrUrl: String, port: Int, pin: String): Result<LaptopStatusResponse> {
        return apiClient.getStatus(ipOrUrl, port, pin)
    }

    suspend fun lockLaptop(ipOrUrl: String, port: Int, pin: String): Result<Boolean> {
        return apiClient.lockScreen(ipOrUrl, port, pin)
    }

    suspend fun sendTTSWarning(ipOrUrl: String, port: Int, pin: String, message: String): Result<Boolean> {
        return apiClient.sendTTSWarning(ipOrUrl, port, pin, message)
    }

    suspend fun sendAudioWarning(ipOrUrl: String, port: Int, pin: String, file: File): Result<Boolean> {
        return apiClient.sendAudioWarning(ipOrUrl, port, pin, file)
    }

    suspend fun triggerLaptopAlarm(ipOrUrl: String, port: Int, pin: String): Result<Boolean> {
        return apiClient.triggerAlarm(ipOrUrl, port, pin)
    }

    suspend fun fetchRemoteLogs(ipOrUrl: String, port: Int, pin: String) {
        val result = apiClient.fetchIntruders(ipOrUrl, port, pin)
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

    fun getCameraSnapshotUrl(ipOrUrl: String, port: Int, pin: String): String {
        return apiClient.getCameraSnapshotUrl(ipOrUrl, port, pin)
    }
}
