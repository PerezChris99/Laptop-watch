package com.kc.laptopwatch

import android.util.Base64
import android.content.Context
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.uimanager.ViewManager
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.kc.laptopwatch.mobile.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Normal OS-CA-validated HTTPS relay, completely separate from self-signed
 * pinned LAN control. No redirects, URLs with credentials, alternate ports,
 * HTTP fallback, arbitrary paths or unsanctioned offline action queue.
 */
class LaptopWatchRelayModule(private val context: ReactApplicationContext) :
    ReactContextBaseJavaModule(context) {
    private val executor = Executors.newSingleThreadExecutor()

    override fun getName() = "LaptopWatchRelay"

    @ReactMethod
    fun sendSignedRelay(params: ReadableMap, promise: Promise) {
        executor.execute {
            try {
                val raw = params.getString("relayUrl") ?: error("Missing HTTPS relay")
                val uri = URI(raw)
                require(uri.scheme == "https" && uri.userInfo == null &&
                    uri.query == null && uri.fragment == null &&
                    (uri.port == -1 || uri.port == 443) &&
                    (uri.path.isNullOrEmpty() || uri.path == "/")) { "Invalid relay URL" }
                val host = uri.host ?: error("Relay hostname required")
                require(host.length in 5..220 && host.contains(".") &&
                    !host.matches(Regex("[0-9.]+")) &&
                    host.matches(Regex("[A-Za-z0-9][A-Za-z0-9.-]*[A-Za-z0-9]"))) {
                    "Relay requires normal public DNS hostname"
                }
                val method = params.getString("method") ?: error("No method")
                val path = params.getString("path") ?: error("No path")
                require(path in setOf(
                    "/api/push/register", "/api/push/unregister", "/api/push/status",
                    "/api/incidents/query", "/api/location/latest"
                ) && method in setOf("POST", "GET")) { "Unsupported signed relay endpoint" }
                val encoded = params.getString("bodyBase64") ?: ""
                require(encoded.length <= 6000)
                val body = Base64.decode(encoded, Base64.DEFAULT)
                require(body.size <= 4096)
                val signed = params.getMap("headers") ?: error("Unsigned request")
                val keys = listOf("X-LaptopWatch-Device", "X-LaptopWatch-Nonce",
                    "X-LaptopWatch-Timestamp", "X-LaptopWatch-Signature")
                require(keys.all { !signed.isNull(it) && (signed.getString(it)?.length ?: 0) in 1..256 })
                val request = Request.Builder().url("https://$host$path")
                for (key in keys) {
                    val value = signed.getString(key) ?: error("Signature missing")
                    require(value.all { it.code in 32..126 })
                    request.header(key, value)
                }
                if (method == "GET") {
                    require(body.isEmpty())
                    request.get()
                } else {
                    request.post(body.toRequestBody("application/json".toMediaType()))
                }
                val client = OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .callTimeout(15, TimeUnit.SECONDS)
                    .followRedirects(false).followSslRedirects(false)
                    .build()
                client.newCall(request.build()).execute().use { response ->
                    require(response.code != 301 && response.code != 302 &&
                        response.code != 307 && response.code != 308)
                    val output = ByteArrayOutputStream()
                    val stream = response.body?.byteStream()
                    if (stream != null) stream.use {
                        val chunk = ByteArray(8192)
                        while (true) {
                            val count = it.read(chunk)
                            if (count < 0) break
                            require(output.size() + count <= 1024 * 1024) {
                                "Relay response exceeded size policy"
                            }
                            output.write(chunk, 0, count)
                        }
                    }
                    val result = Arguments.createMap()
                    result.putInt("status", response.code)
                    result.putString("bodyBase64", Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP))
                    promise.resolve(result)
                }
            } catch (_: Exception) {
                promise.reject("SIGNED_RELAY_FAILED", "Owner-specified HTTPS relay unreachable or rejected signed request")
            }
        }
    }
}

/** Optional Firebase is OFF in BuildConfig until owner provisioning/review. */
class LaptopWatchPushModule(private val context: ReactApplicationContext) :
    ReactContextBaseJavaModule(context) {
    override fun getName() = "LaptopWatchPush"

    @ReactMethod
    fun available(promise: Promise) {
        promise.resolve(BuildConfig.LAPTOPWATCH_FCM_ENABLED &&
            FirebaseApp.getApps(context).isNotEmpty())
    }

    /** A token rotation does not enroll by itself. An approved foreground
     * client must re-sign the relay request with the owner pairing key. */
    @ReactMethod
    fun needsTokenSync(promise: Promise) {
        promise.resolve(BuildConfig.LAPTOPWATCH_FCM_ENABLED &&
            context.getSharedPreferences("laptopwatch-fcm", Context.MODE_PRIVATE)
                .getBoolean("token_sync_pending", false))
    }

    @ReactMethod
    fun acknowledgeTokenSync(promise: Promise) {
        context.getSharedPreferences("laptopwatch-fcm", Context.MODE_PRIVATE)
            .edit().putBoolean("token_sync_pending", false).apply()
        promise.resolve(true)
    }

    @ReactMethod
    fun getToken(promise: Promise) {
        if (!BuildConfig.LAPTOPWATCH_FCM_ENABLED ||
            FirebaseApp.getApps(context).isEmpty()) {
            promise.reject("FCM_DISABLED", "Firebase is not provisioned by the owner")
            return
        }
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful && !task.result.isNullOrBlank()) promise.resolve(task.result)
            else promise.reject("FCM_UNAVAILABLE", "Push registration unavailable")
        }
    }

    @ReactMethod
    fun deleteToken(promise: Promise) {
        if (!BuildConfig.LAPTOPWATCH_FCM_ENABLED ||
            FirebaseApp.getApps(context).isEmpty()) {
            promise.resolve(true)
            return
        }
        FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener { task ->
            if (task.isSuccessful) promise.resolve(true)
            else promise.reject("FCM_DELETE_FAILED", "FCM token deletion failed")
        }
    }
}

class LaptopWatchRelayPackage : ReactPackage {
    override fun createNativeModules(context: ReactApplicationContext): List<NativeModule> =
        listOf(LaptopWatchRelayModule(context), LaptopWatchPushModule(context))

    override fun createViewManagers(context: ReactApplicationContext): List<ViewManager<*, *>> =
        emptyList()
}
