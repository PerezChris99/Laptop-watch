package com.example.security

import android.content.Context
import android.os.Build
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Controls must always be signed with a per-phone key and sent over TLS
 * authenticated by the exact certificate fingerprint scanned in person. */
class SecureLaptopGateway(context: Context) {
    private val app = context.applicationContext
    private val store = PairingStore(app)
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val relayPrefs = app.getSharedPreferences("laptopwatch_relay_settings", Context.MODE_PRIVATE)
    private val maxDownload = 150L * 1024L * 1024L

    fun hasPairing(): Boolean = store.load() != null
    fun isApproved(): Boolean = store.load()?.approved == true
    fun forgetDevice() = store.clear()

    private fun client(host: String, fingerprint: String): OkHttpClient {
        require(PairingCrypto.isPrivateIPv4(host))
        require(fingerprint.matches(Regex("[a-f0-9]{64}")))
        val expected = PairingCrypto.fromHex(fingerprint)
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(c: Array<out X509Certificate>?, a: String?) {
                throw CertificateException("Client-authentication certificates are unsupported")
            }
            override fun checkServerTrusted(c: Array<out X509Certificate>?, a: String?) {
                if (c.isNullOrEmpty()) throw CertificateException("Missing laptop certificate")
                c[0].checkValidity()
                val actual = PairingCrypto.sha256(c[0].encoded)
                if (!MessageDigest.isEqual(actual, expected)) {
                    throw CertificateException("Desktop certificate differs from scanned pairing QR")
                }
            }
        }
        val tls = SSLContext.getInstance("TLS")
        tls.init(null, arrayOf<TrustManager>(trust), SecureRandom())
        return OkHttpClient.Builder()
            .sslSocketFactory(tls.socketFactory, trust)
            // The pinned self-signed certificate itself, not an external CA,
            // authenticates this exact QR-specified private LAN host.
            .hostnameVerifier { peerHost, _ -> peerHost == host }
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(22, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false)
            .build()
    }

    private fun signed(
        method: String, path: String, body: ByteArray = byteArrayOf(),
        allowPending: Boolean = false,
        hostOverride: String? = null
    ): okhttp3.Response {
        val pair = store.load() ?: error("Scan the QR on your laptop before connecting")
        require(allowPending || pair.approved) { "Approve this phone on the laptop first" }
        require(path.matches(Regex("/api/[A-Za-z0-9/_-]{1,180}")) &&
            !path.contains("//") && !path.contains(".."))
        val nonce = PairingCrypto.b64(PairingCrypto.randomBytes(16))
        val timestamp = System.currentTimeMillis() / 1000
        val transcript = PairingCrypto.requestTranscript(
            pair.deviceId, method, path, body, timestamp, nonce
        )
        val auth = PairingCrypto.b64(PairingCrypto.sign(pair.privateSeed, transcript))
        val targetHost = hostOverride ?: pair.host
        require(PairingCrypto.isPrivateIPv4(targetHost))
        val url = "https://" + targetHost + ":" + pair.port + path
        val request = Request.Builder().url(url)
            .header("X-LaptopWatch-Device", pair.deviceId)
            .header("X-LaptopWatch-Nonce", nonce)
            .header("X-LaptopWatch-Timestamp", timestamp.toString())
            .header("X-LaptopWatch-Signature", auth)
        if (method == "GET") request.get()
        else if (method == "POST") request.post(body.toRequestBody(jsonMedia))
        else error("Unsupported signed method")
        return client(targetHost, pair.tlsFingerprint).newCall(request.build()).execute()
    }

    /** A QR alone does not authorize the phone: it must prove its Ed25519
     * private key and wait for the laptop owner's visible approval. */
    suspend fun requestPairing(rawQr: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(store.load() == null) { "Unpair the previous device first" }
            val invitation = PairingInvitation.parseAndVerify(rawQr)
            val seed = PairingCrypto.randomBytes(32)
            val phoneId = UUID.randomUUID().toString()
            val pub = PairingCrypto.b64(PairingCrypto.publicKey(seed))
            val proof = PairingCrypto.b64(
                PairingCrypto.sign(seed, PairingCrypto.phoneProof(phoneId, pub, invitation.challenge))
            )
            val label = Build.MODEL.filter { it.code in 32..126 }.take(48)
                .ifEmpty { "Android phone" }
            val json = JSONObject().put("device_id", phoneId)
                .put("pubkey", pub).put("nonce", invitation.challenge)
                .put("proof", proof).put("label", label)
            val url = "https://" + invitation.host + ":" + invitation.port + "/pair/request"
            val req = Request.Builder().url(url).post(json.toString().toRequestBody(jsonMedia)).build()
            client(invitation.host, invitation.tlsFingerprint).newCall(req).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Pairing denied (HTTP " + response.code + ")")
                val pendingId = JSONObject(response.body?.string() ?: "{}").getString("pending_id")
                UUID.fromString(pendingId)
                store.save(PairedLaptop(
                    phoneId, invitation.desktopId, invitation.host, invitation.port,
                    invitation.desktopPubkey, invitation.tlsFingerprint, seed, false
                ))
                pendingId
            }
        }
    }

    suspend fun verifyApproval(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            signed("GET", "/api/status", allowPending = true).use {
                if (!it.isSuccessful) throw IOException("Awaiting local desktop approval")
            }
            val pair = store.load() ?: error("Pairing not found")
            store.save(pair.copy(approved = true))
        }
    }

    fun configuredRelayUrl(): String =
        relayPrefs.getString("relay_https_url", "") ?: ""

    fun setRelayUrl(value: String) {
        val trimmed = value.trim().removeSuffix("/")
        if (trimmed.isNotEmpty()) {
            val url = java.net.URI(trimmed)
            require(url.scheme == "https" && url.host != null &&
                (url.port == -1 || url.port == 443) &&
                (url.path == null || url.path.isEmpty() || url.path == "/") &&
                url.rawQuery == null && url.rawFragment == null &&
                url.userInfo == null && trimmed.length <= 256) {
                "Enter a normal HTTPS relay domain, without paths or credentials"
            }
            require(!url.host.endsWith(".invalid")) { "Placeholder relay domain not allowed" }
        }
        check(relayPrefs.edit().putString("relay_https_url", trimmed).commit())
    }

    /** Relay enrollment uses public keys ONLY. The private seed never leaves Keystore storage. */
    fun enrollmentInfo(): Pair<String, String>? {
        val pair = store.load()?.takeIf { it.approved } ?: return null
        return pair.deviceId to PairingCrypto.b64(PairingCrypto.publicKey(pair.privateSeed))
    }

    suspend fun remoteLastSeen(): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            val pair = store.load()?.takeIf { it.approved }
                ?: error("Approve the phone before retrieving remote records")
            val base = configuredRelayUrl()
            require(base.isNotEmpty()) { "Configure your trusted remote HTTPS relay first" }
            val path = "/api/location/latest"
            val nonce = PairingCrypto.b64(PairingCrypto.randomBytes(16))
            val stamp = System.currentTimeMillis() / 1000L
            val transcript = PairingCrypto.requestTranscript(
                pair.deviceId, "GET", path, byteArrayOf(), stamp, nonce
            )
            val request = Request.Builder().url(base + path)
                .header("X-LaptopWatch-Device", pair.deviceId)
                .header("X-LaptopWatch-Nonce", nonce)
                .header("X-LaptopWatch-Timestamp", stamp.toString())
                .header("X-LaptopWatch-Signature", PairingCrypto.b64(
                    PairingCrypto.sign(pair.privateSeed, transcript)))
                .get().build()
            // Standard publicly trusted TLS. Never permit a self-signed or
            // unpinned fallback for this public Internet endpoint.
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .callTimeout(15, TimeUnit.SECONDS)
                .followRedirects(false).followSslRedirects(false)
                .build().newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException(
                        "Remote last-seen unavailable (HTTP " + response.code + ")"
                    )
                    JSONObject(response.body?.string() ?: "{}")
                }
        }
    }

    suspend fun rediscoverTrustedLaptop(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val previous = store.load() ?: error("Pair laptop first")
            require(previous.approved) { "Approve phone on desktop before finding it" }
            val candidate = TrustedLaptopDiscovery(app).find(previous)
                ?: throw IOException("No matching paired desktop advertised over LAN")
            // A forged mDNS TXT record is insufficient: verify that the candidate
            // holds the QR-pinned desktop certificate and accepts a signed request.
            signed("GET", "/api/status", hostOverride = candidate).use { response ->
                if (!response.isSuccessful) throw IOException(
                    "Discovered laptop rejected signed identity test (HTTP " + response.code + ")"
                )
            }
            store.save(previous.copy(host = candidate))
            candidate
        }
    }

    suspend fun json(
        method: String, path: String, payload: JSONObject? = null
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            val data = payload?.toString()?.toByteArray(Charsets.UTF_8) ?: byteArrayOf()
            signed(method, path, data).use { response ->
                if (!response.isSuccessful) throw IOException(
                    "Desktop rejected " + path + " (HTTP " + response.code + ")"
                )
                JSONObject(response.body?.string() ?: "{}")
            }
        }
    }

    suspend fun arm(armed: Boolean) = json("POST", "/api/config/armed", JSONObject().put("armed", armed))
    suspend fun lock() = json("POST", "/api/lock")
    suspend fun alarm() = json("POST", "/api/alarm")
    suspend fun speak(message: String): Result<JSONObject> {
        require(message.length in 1..400) { "Text warning must be 1–400 characters" }
        return json("POST", "/api/warning/tts", JSONObject().put("message", message))
    }
    suspend fun photo() = json("POST", "/api/camera/photo")
    suspend fun startRecording() = json("POST", "/api/camera/record/start")
    suspend fun stopRecording() = json("POST", "/api/camera/record/stop")
    suspend fun status() = json("GET", "/api/status")
    suspend fun lastSeen() = json("GET", "/api/location/last-seen")
    suspend fun refreshLocation() = json("POST", "/api/location/refresh")
    /** Fetch only verified person events since the phone's durable cursor.
     * This is authenticated foreground sync, not a replacement for FCM push. */
    suspend fun incidents(afterId: Long, limit: Int = 100): Result<JSONObject> {
        require(afterId >= 0 && limit in 1..100)
        return json("POST", "/api/incidents/query",
            JSONObject().put("after_id", afterId).put("limit", limit))
    }

    suspend fun clearIncidents(): Result<JSONObject> =
        json("POST", "/api/incidents/clear")

    fun pairedDesktopId(): String? = store.load()?.takeIf { it.approved }?.desktopId


    suspend fun download(kind: String, id: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(kind in setOf("photo", "video") && id.matches(Regex("[a-f0-9]{32}")))
            val extension = if (kind == "photo") "jpg" else "mp4"
            val directory = File(app.filesDir, "laptopwatch-evidence")
            check(directory.isDirectory || directory.mkdirs()) { "Cannot create private evidence store" }
            val filename = kind + "-" + id + "." + extension
            val target = File(directory, filename)
            val temporary = File(directory, filename + ".part")
            try {
                signed("GET", "/api/evidence/" + kind + "/" + id).use { response ->
                    if (!response.isSuccessful) throw IOException(
                        "Evidence download denied (HTTP " + response.code + ")"
                    )
                    val body = response.body ?: throw IOException("No evidence response")
                    if (body.contentLength() > maxDownload) throw IOException("Evidence too large")
                    var count = 0L
                    body.byteStream().use { source ->
                        temporary.outputStream().buffered().use { sink ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val n = source.read(buffer)
                                if (n < 0) break
                                count += n
                                if (count > maxDownload) throw IOException("Evidence exceeded size limit")
                                sink.write(buffer, 0, n)
                            }
                        }
                    }
                    if (count == 0L) throw IOException("Evidence was empty")
                }
                if (target.exists() && !target.delete()) throw IOException("Cannot replace old evidence")
                if (!temporary.renameTo(target)) throw IOException("Cannot finalize evidence file")
                target
            } finally {
                temporary.delete()
            }
        }
    }
}
