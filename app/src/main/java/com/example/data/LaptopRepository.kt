package com.example.data

import com.example.network.LaptopApiClient
import com.example.network.LaptopStatusResponse
import kotlinx.coroutines.flow.Flow
import java.io.File

class LaptopRepository(
    private val laptopDao: LaptopDao,
    private val intruderLogDao: IntruderLogDao,
    private val locationDao: LocationDao? = null,
    private val subjectProfileDao: SubjectProfileDao? = null,
    private val apiClient: LaptopApiClient = LaptopApiClient()
) {
    val laptopConfig: Flow<LaptopConfigEntity?> = laptopDao.getLaptopConfig()
    val intruderLogs: Flow<List<IntruderLogEntity>> = intruderLogDao.getAllLogs()
    val laptopLocations: Flow<List<LaptopLocationEntity>> = locationDao?.getAllLocations() ?: kotlinx.coroutines.flow.flowOf(emptyList())
    val latestLocation: Flow<LaptopLocationEntity?> = locationDao?.getLatestLocation() ?: kotlinx.coroutines.flow.flowOf(null)
    val subjectProfiles: Flow<List<SubjectProfileEntity>> = subjectProfileDao?.getAllProfiles() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun recordLocation(location: LaptopLocationEntity): Long {
        return locationDao?.insertLocation(location) ?: 0L
    }

    suspend fun getLatestLocationDirect(): LaptopLocationEntity? {
        return locationDao?.getLatestLocationDirect()
    }

    suspend fun recordSubjectProfile(profile: SubjectProfileEntity): Long {
        return subjectProfileDao?.insertProfile(profile) ?: 0L
    }

    suspend fun updateSubjectClassification(id: Long, category: String, notes: String) {
        subjectProfileDao?.updateClassification(id, category, notes)
    }

    suspend fun clearLocations() {
        locationDao?.clearAllLocations()
    }

    suspend fun clearProfiles() {
        subjectProfileDao?.clearAllProfiles()
    }

    suspend fun fetchLiveLocationFromLaptop(ipOrUrl: String, port: Int, pin: String): Result<LaptopLocationEntity> {
        val res = apiClient.getLiveLocation(ipOrUrl, port, pin)
        if (res.isFailure) return Result.failure(res.exceptionOrNull() ?: Exception("GPS fetch failed"))
        val json = res.getOrNull() ?: return Result.failure(Exception("Empty location payload"))
        val lat = json.optDouble("latitude", Double.NaN)
        val lng = json.optDouble("longitude", Double.NaN)
        val accuracy = json.optDouble("accuracy_meters", Double.NaN)
        if (!json.optBoolean("is_live_fix", false)
            || !lat.isFinite() || lat !in -90.0..90.0
            || !lng.isFinite() || lng !in -180.0..180.0
            || !accuracy.isFinite() || accuracy <= 0.0 || accuracy > 100_000.0
        ) {
            return Result.failure(IllegalStateException("No valid, verified laptop location fix available"))
        }
        val loc = LaptopLocationEntity(
            latitude = lat,
            longitude = lng,
            accuracyMeters = accuracy.toFloat(),
            altitudeMeters = json.optDouble("altitude_meters", 0.0),
            speedKmh = json.optDouble("speed_kmh", 0.0).toFloat(),
            provider = json.optString("provider", "UNKNOWN"),
            addressEstimate = json.optString("address_estimate", "Latitude: $lat, Longitude: $lng"),
            isLiveFix = true
        )
        locationDao?.insertLocation(loc)
        return Result.success(loc)
    }

    suspend fun getConfigDirect(): LaptopConfigEntity? = laptopDao.getLaptopConfigDirect()

    suspend fun initializeDefaultsIfNeeded() {
        if (laptopDao.getLaptopConfigDirect() == null) {
            // Never put fabricated incidents, GPS coordinates or face dossiers
            // in the persistent forensic database. Demo data stays in UI state.
            laptopDao.insertOrUpdateConfig(
                LaptopConfigEntity(
                    laptopName = "My Laptop",
                    ipAddress = "",
                    port = 5000,
                    pin = "",
                    isDemoMode = true,
                    isMotionArmed = false,
                    autoLockOnMotion = false
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

    suspend fun setAutoRecordOnMotion(autoRecord: Boolean) {
        laptopDao.updateAutoRecordOnMotion(autoRecord)
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
        category: String = "SECURITY",
        latitude: Double? = 40.7128902,
        longitude: Double? = -74.0060804,
        accuracyMeters: Float? = 1.0f,
        locationName: String? = "Workstation Floor 3 • Desk 4B"
    ): Long {
        val entity = IntruderLogEntity(
            timestamp = System.currentTimeMillis(),
            eventType = eventType,
            description = description,
            snapshotUrl = snapshotUrl,
            warningIssued = warningIssued,
            wasLocked = wasLocked,
            severity = severity,
            category = category,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            locationName = locationName
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

    fun getCameraSnapshotUrl(ipOrUrl: String, port: Int, pin: String, quality: Int = 75, scale: Float = 1.0f): String {
        return apiClient.getCameraSnapshotUrl(ipOrUrl, port, pin, quality, scale)
    }

    suspend fun pingLatencyMs(ipOrUrl: String, pin: String): Long? {
        return apiClient.pingLatencyMs(ipOrUrl, pin)
    }

    suspend fun captureSnapshot(ipOrUrl: String, port: Int, pin: String): Result<String> {
        return apiClient.captureSnapshot(ipOrUrl, port, pin)
    }

    suspend fun startRecording(ipOrUrl: String, port: Int, pin: String): Result<Boolean> {
        return apiClient.startRecording(ipOrUrl, port, pin)
    }

    suspend fun stopRecording(ipOrUrl: String, port: Int, pin: String): Result<String> {
        return apiClient.stopRecording(ipOrUrl, port, pin)
    }
}
