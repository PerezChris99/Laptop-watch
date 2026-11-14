package com.kc.laptopwatch

import android.util.Base64
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Intentionally small Android-only signed-network bridge.
 *
 * Never use a general-purpose JS fetch here. A self-signed desktop certificate
 * is trusted ONLY if its DER SHA-256 equals the fingerprint scanned in person.
 * The bridge is not registered until the React Native native project is built.
 */
class LaptopWatchPinnedTransport(context: ReactApplicationContext) :
    ReactContextBaseJavaModule(context) {
    private val executor = ThreadPoolExecutor(
        2, 2, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(16),
        ThreadPoolExecutor.AbortPolicy()
    )

    override fun getName(): String = "LaptopWatchPinnedTransport"

    @ReactMethod
    fun sendPinned(params: ReadableMap, promise: Promise) {
        try {
            executor.execute {
                try {
                    val host = params.getString("host") ?: error("Missing host")
                    val port = params.getInt("port")
                    val pin = params.getString("tlsFingerprint") ?: error("Missing TLS pin")
                    val method = params.getString("method") ?: error("Missing method")
                    val path = params.getString("path") ?: error("Missing path")
                    val encoded = params.getString("bodyBase64") ?: ""
                    val timeout = params.getInt("timeoutMs").coerceIn(1_000, 30_000)
                    val maximum = params.getInt("maxResponseBytes").coerceIn(1, 157_286_400)
                    require(isPrivateIPv4(host) && port in 1024..65535) { "Private LAN only" }
                    require(pin.matches(Regex("[0-9a-f]{64}"))) { "Missing SHA-256 certificate pin" }
                    require((path == "/pair/request" && method == "POST") ||
                        ((method == "GET" || method == "POST") &&
                        path.matches(Regex("/api/[A-Za-z0-9/_-]{1,180}")) &&
                        !path.contains("//") && !path.contains(".."))) { "Invalid gateway route" }
                    require(encoded.length <= 90_000) { "Body exceeded gateway limit" }
                    val payload = Base64.decode(encoded, Base64.DEFAULT)
                    require(payload.size <= 65_536) { "Body exceeded gateway limit" }
                    val expected = hexBytes(pin)
                    val trust = object : X509TrustManager {
                        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                            throw CertificateException("Client authentication not supported")
                        }
                        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                            if (chain.isEmpty()) throw CertificateException("Missing desktop certificate")
                            chain[0].checkValidity()
                            val actual = MessageDigest.getInstance("SHA-256").digest(chain[0].encoded)
                            if (!MessageDigest.isEqual(actual, expected)) {
                                throw CertificateException("Desktop certificate pin mismatch")
                            }
                        }
                    }
                    val ssl = SSLContext.getInstance("TLS")
                    ssl.init(null, arrayOf<TrustManager>(trust), SecureRandom())
                    val client = OkHttpClient.Builder()
                        .sslSocketFactory(ssl.socketFactory, trust)
                        .hostnameVerifier { candidate, _ -> candidate == host }
                        .connectTimeout(4, TimeUnit.SECONDS)
                        .readTimeout(15, TimeUnit.SECONDS)
                        .callTimeout(timeout.toLong(), TimeUnit.MILLISECONDS)
                        .followRedirects(false).followSslRedirects(false).build()
                    val request = Request.Builder().url("https://$host:$port$path")
                    if (params.hasKey("headers") && !params.isNull("headers")) {
                        val headers = params.getMap("headers") ?: error("Invalid signed headers")
                        val keys = headers.keySetIterator()
                        while (keys.hasNextKey()) {
                            val name = keys.nextKey()
                            require(name == "Content-Type" || name in setOf(
                                "X-LaptopWatch-Device", "X-LaptopWatch-Nonce",
                                "X-LaptopWatch-Timestamp", "X-LaptopWatch-Signature"
                            )) { "Header not allowed" }
                            val value = headers.getString(name) ?: error("Invalid signed header")
                            require(value.length <= 256 && value.all { it.code in 32..126 }) {
                                "Invalid header characters"
                            }
                            request.header(name, value)
                        }
                    }
                    if (method == "GET") require(payload.isEmpty()) { "GET body not supported" }
                    if (method == "GET") request.get()
                    else request.post(payload.toRequestBody("application/json".toMediaType()))
                    client.newCall(request.build()).execute().use { response ->
                        val stream = response.body?.byteStream()
                        val out = ByteArrayOutputStream()
                        if (stream != null) {
                            val buffer = ByteArray(16_384)
                            while (true) {
                                val n = stream.read(buffer)
                                if (n < 0) break
                                require(out.size().toLong() + n.toLong() <= maximum.toLong()) {
                                    "Response exceeded configured maximum"
                                }
                                out.write(buffer, 0, n)
                            }
                        }
                        val obj = Arguments.createMap()
                        obj.putInt("status", response.code)
                        obj.putString("contentType", response.header("Content-Type") ?: "")
                        obj.putString("bodyBase64", Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
                        promise.resolve(obj)
                    }
                } catch (_: Exception) {
                    // Generic failure only: no IP, keys, PIN, signature or payload in logs.
                    promise.reject("PINNED_REQUEST_FAILED", "Desktop unavailable or pinned TLS request refused")
                }
            }
        } catch (_: Exception) {
            promise.reject("TRANSPORT_BUSY", "Pinned transport queue is full or unavailable")
        }
    }

    private fun hexBytes(hex: String): ByteArray = ByteArray(hex.length / 2) { i ->
        hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }

    private fun isPrivateIPv4(host: String): Boolean {
        val parts = host.split(".")
        if (parts.size != 4 || parts.any { it.isEmpty() || (it.length > 1 && it[0] == '0') }) return false
        val a = parts.map { it.toIntOrNull()?.takeIf { n -> n in 0..255 } ?: return false }
        return a[0] == 10 || (a[0] == 172 && a[1] in 16..31) ||
            (a[0] == 192 && a[1] == 168) || (a == listOf(127, 0, 0, 1))
    }
}
