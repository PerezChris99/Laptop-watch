package com.example.network

 import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class LaptopStatusResponse(
    val online: Boolean,
    val hostname: String = "Laptop",
    val battery: String = "100%",
    val isLocked: Boolean = false,
    val cameraActive: Boolean = true,
    val motionDetected: Boolean = false,
    val motionIntensity: Int = 0,
    val lastMotionTime: Long = 0L,
    val threatLevel: String = "SECURE",
    val message: String = "Connected",
    // GPS Hardware Telemetry
    val latitude: Double? = null,
    val longitude: Double? = null,
    val gpsAccuracy: Float? = null,
    val gpsSpeed: Float? = null,
    val gpsAltitude: Double? = null,
    val gpsProvider: String? = null,
    val lastGpsFixTime: Long? = null,
    // Active Face / Subject detection telemetry
    val subjectCount: Int = 0,
    val subjectIdentified: String? = null,
    val subjectConfidence: Float = 0f
)

data class RemoteLogItem(
    val id: String,
    val timestamp: Long,
    val eventType: String,
    val description: String,
    val snapshotPath: String?
)

/**
 * Disabled migration adapter. The former Flask endpoint used a shared PIN in
 * HTTP headers/URL query parameters; it is not a secure remote-control scheme.
 * All real commands now use SecureLaptopGateway (Ed25519 + QR-pinned TLS).
 *
 * Keep method signatures temporarily for the remaining legacy UI code, but
 * NEVER permit any network request or legacy PIN URL to be constructed.
 */
class LaptopApiClient {
    private fun <T> disabled(): Result<T> = Result.failure(
        SecurityException("Legacy PIN/HTTP access is disabled. Scan the secure laptop QR.")
    )

    fun resolveBaseUrl(ipOrUrl: String, port: Int = 5000): String {
        throw SecurityException("Legacy base URLs are disabled; only QR-pinned HTTPS is supported")
    }

    fun getCameraSnapshotUrl(
        ipOrUrl: String, port: Int, pin: String, quality: Int = 75, scale: Float = 1.0f
    ): String = ""

    fun getCameraStreamUrl(ipOrUrl: String, port: Int, pin: String): String = ""

    suspend fun pingEndpoint(targetUrl: String, pin: String, timeoutSecs: Long = 2): Boolean = false

    suspend fun getStatus(ip: String, port: Int, pin: String): Result<LaptopStatusResponse> = disabled()

    suspend fun setMotionConfig(
        ip: String, port: Int, pin: String,
        armed: Boolean, sensitivity: String, autoLock: Boolean
    ): Result<Boolean> = disabled()

    suspend fun lockScreen(ip: String, port: Int, pin: String): Result<Boolean> = disabled()

    suspend fun sendTTSWarning(
        ip: String, port: Int, pin: String, message: String
    ): Result<Boolean> = disabled()

    suspend fun sendAudioWarning(
        ip: String, port: Int, pin: String, audioFile: File
    ): Result<Boolean> = disabled()

    suspend fun triggerAlarm(ip: String, port: Int, pin: String): Result<Boolean> = disabled()

    suspend fun fetchIntruders(
        ip: String, port: Int, pin: String
    ): Result<List<RemoteLogItem>> = disabled()

    suspend fun pingLatencyMs(targetUrl: String, pin: String): Long? = null

    suspend fun captureSnapshot(ip: String, port: Int, pin: String): Result<String> = disabled()

    suspend fun startRecording(ip: String, port: Int, pin: String): Result<Boolean> = disabled()

    suspend fun stopRecording(ip: String, port: Int, pin: String): Result<String> = disabled()

    suspend fun getLiveLocation(ip: String, port: Int, pin: String): Result<JSONObject> = disabled()

    suspend fun getSubjectProfiles(ip: String, port: Int, pin: String): Result<JSONArray> = disabled()
}
