package com.kc.laptopwatch

import android.util.Base64
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.uimanager.ViewManager
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** Direct-to-app-private evidence. No large media payload is passed through JS.
 * Unauthenticated URLs, redirects and user-selected paths are forbidden.
 */
class LaptopWatchEvidenceModule(private val app: ReactApplicationContext)
    : ReactContextBaseJavaModule(app) {
    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()

    override fun getName() = "LaptopWatchEvidence"

    @ReactMethod
    fun downloadPinned(input: ReadableMap, promise: Promise) {
        executor.execute {
            var temp: File? = null
            try {
                val host = input.getString("host") ?: error("Missing host")
                val a = host.split(".").map {
                    require(it.matches(Regex("0|[1-9][0-9]{0,2}")))
                    it.toInt().also { n -> require(n in 0..255) }
                }
                require(a.size == 4 && (a[0] == 10 ||
                    (a[0] == 172 && a[1] in 16..31) ||
                    (a[0] == 192 && a[1] == 168))) { "Private LAN only" }
                val port = input.getInt("port")
                require(port in 1024..65535)
                val pin = input.getString("tlsFingerprint") ?: error("Missing pin")
                require(pin.matches(Regex("[a-f0-9]{64}")))
                val path = input.getString("path") ?: error("Missing evidence")
                val match = Regex("^/api/evidence/(photo|video)/([a-f0-9]{32})$").matchEntire(path)
                    ?: error("Invalid signed evidence path")
                require(input.getString("method") == "GET")
                require(input.getString("bodyBase64") == "")
                val headers = input.getMap("headers") ?: error("Missing signed headers")
                val fields = listOf("X-LaptopWatch-Device", "X-LaptopWatch-Nonce",
                    "X-LaptopWatch-Timestamp", "X-LaptopWatch-Signature")
                require(fields.all { !headers.isNull(it) && (headers.getString(it)?.length ?: 0) in 1..256 })
                val expected = ByteArray(32) { i ->
                    pin.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                }
                val trust = object : X509TrustManager {
                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                        throw CertificateException("Not supported")
                    }
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                        if (chain.isEmpty()) throw CertificateException("No desktop certificate")
                        chain[0].checkValidity()
                        if (!MessageDigest.isEqual(
                            MessageDigest.getInstance("SHA-256").digest(chain[0].encoded), expected
                        )) throw CertificateException("Owner certificate mismatch")
                    }
                }
                val tls = SSLContext.getInstance("TLS")
                tls.init(null, arrayOf<TrustManager>(trust), SecureRandom())
                val client = OkHttpClient.Builder()
                    .sslSocketFactory(tls.socketFactory, trust)
                    .hostnameVerifier { candidate, _ -> candidate == host }
                    .connectTimeout(4, TimeUnit.SECONDS)
                    .callTimeout(45, TimeUnit.SECONDS)
                    .followRedirects(false).followSslRedirects(false)
                    .build()
                val builder = Request.Builder().url("https://$host:$port$path")
                for (field in fields) {
                    val value = headers.getString(field) ?: error("Unsigned request")
                    require(value.all { it.code in 32..126 })
                    builder.header(field, value)
                }
                val directory = File(app.filesDir, "evidence")
                require(directory.isDirectory || directory.mkdirs()) { "Private storage not available" }
                val kind = match.groupValues[1]
                val id = match.groupValues[2]
                val file = File(directory, "$kind-$id.${if (kind == "photo") "jpg" else "mp4"}")
                val maximum = if (kind == "photo") 16L * 1024 * 1024 else 128L * 1024 * 1024
                val tempFile = File(directory, ".pending-${UUID.randomUUID()}")
                temp = tempFile
                client.newCall(builder.get().build()).execute().use { response ->
                    require(response.code == 200) { "Desktop evidence unavailable" }
                    val contentType = response.header("Content-Type") ?: ""
                    require(contentType.startsWith(if (kind == "photo") "image/jpeg" else "video/mp4"))
                    val body = response.body ?: error("Empty evidence")
                    var length = 0L
                    body.byteStream().use { inputStream ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(16_384)
                            while (true) {
                                val n = inputStream.read(buffer)
                                if (n < 0) break
                                length += n
                                require(length <= maximum) { "Evidence exceeds size limit" }
                                output.write(buffer, 0, n)
                            }
                            output.fd.sync()
                        }
                    }
                    require(length > 0) { "Evidence is empty" }
                    // Never let a symlink redirect app-private file writes.
                    require(!file.isSymbolicLink())
                    Files.move(tempFile.toPath(), file.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                    val result = Arguments.createMap()
                    result.putString("uri", "file://${file.absolutePath}")
                    result.putDouble("sizeBytes", length.toDouble())
                    result.putString("kind", kind)
                    result.putString("id", id)
                    promise.resolve(result)
                }
            } catch (_: Exception) {
                promise.reject("EVIDENCE_UNAVAILABLE", "Signed private evidence download failed")
            } finally {
                temp?.delete()
            }
        }
    }

    private fun File.isSymbolicLink(): Boolean = Files.isSymbolicLink(toPath())
}

class LaptopWatchEvidencePackage : ReactPackage {
    override fun createNativeModules(context: ReactApplicationContext): List<NativeModule> =
        listOf(LaptopWatchEvidenceModule(context))

    override fun createViewManagers(context: ReactApplicationContext): List<ViewManager<*, *>> =
        emptyList()
}
