package com.example.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

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
    val message: String = "Connected"
)

data class RemoteLogItem(
    val id: String,
    val timestamp: Long,
    val eventType: String,
    val description: String,
    val snapshotPath: String?
)

class LaptopApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun resolveBaseUrl(ipOrUrl: String, port: Int = 5000): String {
        val trimmed = ipOrUrl.trim().removeSuffix("/")
        if (trimmed.isEmpty()) return "http://127.0.0.1:$port"
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        val cleanHost = trimmed.removePrefix("http://").removePrefix("https://")
        return "http://$cleanHost:$port"
    }

    private fun getBaseUrl(ip: String, port: Int): String {
        return resolveBaseUrl(ip, port)
    }

    fun getCameraSnapshotUrl(ipOrUrl: String, port: Int, pin: String): String {
        return "${resolveBaseUrl(ipOrUrl, port)}/api/camera/frame?pin=$pin&t=${System.currentTimeMillis()}"
    }

    fun getCameraStreamUrl(ipOrUrl: String, port: Int, pin: String): String {
        return "${resolveBaseUrl(ipOrUrl, port)}/api/camera/stream?pin=$pin"
    }

    suspend fun pingEndpoint(targetUrl: String, pin: String, timeoutSecs: Long = 2): Boolean = withContext(Dispatchers.IO) {
        try {
            val quickClient = client.newBuilder()
                .connectTimeout(timeoutSecs, TimeUnit.SECONDS)
                .readTimeout(timeoutSecs, TimeUnit.SECONDS)
                .build()

            val url = "${resolveBaseUrl(targetUrl)}/api/status"
            val request = Request.Builder()
                .url(url)
                .addHeader("X-Auth-Token", pin)
                .get()
                .build()

            quickClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getStatus(ip: String, port: Int, pin: String): Result<LaptopStatusResponse> = withContext(Dispatchers.IO) {
        try {
            val url = "${getBaseUrl(ip, port)}/api/status"
            val request = Request.Builder()
                .url(url)
                .addHeader("X-Auth-Token", pin)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
                val body = response.body?.string() ?: "{}"
                val json = JSONObject(body)
                val status = LaptopStatusResponse(
                    online = json.optBoolean("online", true),
                    hostname = json.optString("hostname", "Laptop"),
                    battery = json.optString("battery", "Unknown"),
                    isLocked = json.optBoolean("is_locked", false),
                    cameraActive = json.optBoolean("camera_active", true),
                    motionDetected = json.optBoolean("motion_detected", false),
                    motionIntensity = json.optInt("motion_intensity", 0),
                    lastMotionTime = json.optLong("last_motion_time", 0L),
                    threatLevel = json.optString("threat_level", "SECURE"),
                    message = json.optString("message", "OK")
                )
                Result.success(status)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setMotionConfig(
        ip: String,
        port: Int,
        pin: String,
        armed: Boolean,
        sensitivity: String,
        autoLock: Boolean
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "${getBaseUrl(ip, port)}/api/motion/config"
            val jsonPayload = JSONObject().apply {
                put("armed", armed)
                put("sensitivity", sensitivity)
                put("auto_lock", autoLock)
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("X-Auth-Token", pin)
                .post(jsonPayload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("Motion config failed: HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun lockScreen(ip: String, port: Int, pin: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "${getBaseUrl(ip, port)}/api/lock"
            val jsonPayload = JSONObject().apply {
                put("action", "lock")
                put("timestamp", System.currentTimeMillis())
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("X-Auth-Token", pin)
                .post(jsonPayload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("Lock failed: HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendTTSWarning(ip: String, port: Int, pin: String, message: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "${getBaseUrl(ip, port)}/api/warning/tts"
            val jsonPayload = JSONObject().apply {
                put("message", message)
                put("volume", 100)
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("X-Auth-Token", pin)
                .post(jsonPayload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("Warning failed: HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendAudioWarning(ip: String, port: Int, pin: String, audioFile: File): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "${getBaseUrl(ip, port)}/api/warning/audio"
            val audioMediaType = "audio/mp4".toMediaType()
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "audio",
                    audioFile.name,
                    audioFile.asRequestBody(audioMediaType)
                )
                .build()

            val request = Request.Builder()
                .url(url)
                .addHeader("X-Auth-Token", pin)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("Audio transmission failed: HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun triggerAlarm(ip: String, port: Int, pin: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "${getBaseUrl(ip, port)}/api/alarm"
            val jsonPayload = JSONObject().apply {
                put("duration_seconds", 5)
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("X-Auth-Token", pin)
                .post(jsonPayload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("Alarm failed: HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchIntruders(ip: String, port: Int, pin: String): Result<List<RemoteLogItem>> = withContext(Dispatchers.IO) {
        try {
            val url = "${getBaseUrl(ip, port)}/api/logs"
            val request = Request.Builder()
                .url(url)
                .addHeader("X-Auth-Token", pin)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Logs fetch failed: HTTP ${response.code}"))
                }
                val body = response.body?.string() ?: "[]"
                val jsonArray = JSONArray(body)
                val list = mutableListOf<RemoteLogItem>()
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    list.add(
                        RemoteLogItem(
                            id = item.optString("id", i.toString()),
                            timestamp = item.optLong("timestamp", System.currentTimeMillis()),
                            eventType = item.optString("event_type", "Motion Detected"),
                            description = item.optString("description", "Camera motion recorded"),
                            snapshotPath = item.optString("snapshot_url", null)
                        )
                    )
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
