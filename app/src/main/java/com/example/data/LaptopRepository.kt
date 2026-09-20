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
        return if (res.isSuccess) {
            val json = res.getOrNull()!!
            val lat = json.optDouble("latitude", 0.0)
            val lng = json.optDouble("longitude", 0.0)
            val loc = LaptopLocationEntity(
                latitude = lat,
                longitude = lng,
                accuracyMeters = json.optDouble("accuracy", 4.5).toFloat(),
                altitudeMeters = json.optDouble("altitude", 12.0),
                speedKmh = json.optDouble("speed", 0.0).toFloat(),
                provider = json.optString("provider", "GPS_HARDWARE"),
                addressEstimate = json.optString("address", "Latitude: $lat, Longitude: $lng"),
                isLiveFix = true
            )
            locationDao?.insertLocation(loc)
            Result.success(loc)
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("GPS fetch failed"))
        }
    }

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
                    timestamp = now - 1000 * 60 * 55,
                    eventType = "GPS Hardware Lock",
                    description = "Pinpoint RTK satellite fix acquired at desk workstation (Accuracy: ±1.0m)",
                    warningIssued = null,
                    wasLocked = false,
                    severity = "INFO",
                    category = "GPS",
                    latitude = 40.7128902,
                    longitude = -74.0060804,
                    accuracyMeters = 1.0f,
                    locationName = "Workstation Floor 3 • Desk 4B"
                )
            )
            intruderLogDao.insertLog(
                IntruderLogEntity(
                    timestamp = now - 1000 * 60 * 42,
                    eventType = "Motion Detected",
                    description = "Webcam detected movement within 1.0m zone of keyboard",
                    warningIssued = "Automated Chime Played",
                    wasLocked = false,
                    severity = "WARNING",
                    category = "SECURITY",
                    latitude = 40.7128902,
                    longitude = -74.0060804,
                    accuracyMeters = 1.0f,
                    locationName = "Workstation Floor 3 • Desk 4B"
                )
            )
            intruderLogDao.insertLog(
                IntruderLogEntity(
                    timestamp = now - 1000 * 60 * 28,
                    eventType = "Face Sighted (Authorized)",
                    description = "Biometric scan verified: Primary Operator (Owner). Confidence 98%.",
                    warningIssued = null,
                    wasLocked = false,
                    severity = "INFO",
                    category = "SECURITY",
                    latitude = 40.7128902,
                    longitude = -74.0060804,
                    accuracyMeters = 1.0f,
                    locationName = "Workstation Floor 3 • Desk 4B"
                )
            )
            intruderLogDao.insertLog(
                IntruderLogEntity(
                    timestamp = now - 1000 * 60 * 18,
                    eventType = "Lock Triggered",
                    description = "Screen locked remotely via Android sentinel applet",
                    warningIssued = "Step away from this computer!",
                    wasLocked = true,
                    severity = "ALERT",
                    category = "SECURITY",
                    latitude = 40.7128902,
                    longitude = -74.0060804,
                    accuracyMeters = 1.0f,
                    locationName = "Workstation Floor 3 • Desk 4B"
                )
            )
            intruderLogDao.insertLog(
                IntruderLogEntity(
                    timestamp = now - 1000 * 60 * 6,
                    eventType = "Perimeter Geofence Armed",
                    description = "Virtual boundary active (±60m). GPS 1-meter tracking armed.",
                    warningIssued = null,
                    wasLocked = true,
                    severity = "INFO",
                    category = "GPS",
                    latitude = 40.7128902,
                    longitude = -74.0060804,
                    accuracyMeters = 1.0f,
                    locationName = "Workstation Floor 3 • Desk 4B"
                )
            )

            // Seed initial realistic GPS coordinates and historical trail
            locationDao?.let { locDao ->
                locDao.insertLocation(
                    LaptopLocationEntity(
                        timestamp = now - 1000 * 60 * 25,
                        latitude = 40.7128812,
                        longitude = -74.0060714,
                        accuracyMeters = 1.0f,
                        altitudeMeters = 15.0,
                        speedKmh = 0.0f,
                        provider = "GPS_HARDWARE",
                        addressEstimate = "Office Workstation • Desk 4B (±1.0m Pinpoint)",
                        transport = "LAN",
                        isLiveFix = false
                    )
                )
                locDao.insertLocation(
                    LaptopLocationEntity(
                        timestamp = now - 1000 * 60 * 5,
                        latitude = 40.7128902,
                        longitude = -74.0060804,
                        accuracyMeters = 1.0f,
                        altitudeMeters = 15.4,
                        speedKmh = 0.0f,
                        provider = "GPS_HARDWARE",
                        addressEstimate = "Office Floor 3 • Near Window Dock (±1.0m Pinpoint)",
                        transport = "WEB",
                        isLiveFix = true
                    )
                )
            }

            // Seed initial Biometric Subject Dossiers & Faces
            subjectProfileDao?.let { profileDao ->
                profileDao.insertProfile(
                    SubjectProfileEntity(
                        subjectTag = "PROFILE-01",
                        displayName = "Authorized Owner (Primary)",
                        firstSeenTimestamp = now - 1000L * 3600 * 48,
                        lastSeenTimestamp = now - 1000L * 60 * 15,
                        encounterCount = 142,
                        securityCategory = "AUTHORIZED",
                        behaviorNotes = "Verified owner. High recognition consistency (98.4%). Regular daytime access pattern.",
                        dwellDurationMinutes = 340,
                        confidenceScore = 0.98f
                    )
                )
                profileDao.insertProfile(
                    SubjectProfileEntity(
                        subjectTag = "PROFILE-02",
                        displayName = "Unrecognized Visitor #4",
                        firstSeenTimestamp = now - 1000L * 60 * 95,
                        lastSeenTimestamp = now - 1000L * 60 * 35,
                        encounterCount = 3,
                        securityCategory = "INVESTIGATE",
                        behaviorNotes = "Approached laptop while screen locked. Lingered for 2 mins within 1m radius.",
                        dwellDurationMinutes = 4,
                        confidenceScore = 0.82f
                    )
                )
                profileDao.insertProfile(
                    SubjectProfileEntity(
                        subjectTag = "PROFILE-03",
                        displayName = "Flagged Subject (After-Hours)",
                        firstSeenTimestamp = now - 1000L * 3600 * 12,
                        lastSeenTimestamp = now - 1000L * 3600 * 11,
                        encounterCount = 1,
                        securityCategory = "FLAGGED_THREAT",
                        behaviorNotes = "Nighttime encounter (02:14 AM). Motion triggered auto-alarm and voice deterrent warning.",
                        dwellDurationMinutes = 1,
                        confidenceScore = 0.89f
                    )
                )
            }
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
