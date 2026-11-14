package com.kc.laptopwatch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Base64
import android.content.Context
import com.kc.laptopwatch.mobile.MainActivity
import okhttp3.OkHttpClient
import okhttp3.Request
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Owner-opt-in Android connectedDevice foreground service. No camera, audio,
 * action command or unsanctioned FCM. Only authenticated signed status polling.
 * The private seed exists in process RAM only; no sticky restart on process
 * death. The UI can opt back in after unlocking the phone.
 */
class LaptopWatchForegroundService : Service() {
    data class Session(
        val host: String, val port: Int, val pin: String,
        val deviceId: String, val seed: ByteArray
    ) {
        fun zeroize() = seed.fill(0)
    }
    companion object {
        const val CHANNEL = "laptopwatch-owner-connection"
        private const val NOTICE_ID = 6054
        @Volatile var pendingSession: Session? = null
        const val STOP = "com.kc.laptopwatch.STOP_CONNECTION"
    }

    private val active = AtomicBoolean(false)
    @Volatile private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL, "LaptopWatch paired connection", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Visible owner-requested signed connection to a paired laptop" })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val session = pendingSession
        if (session == null || session.seed.size != 32) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTICE_ID, notification("Starting secure connection…"))
        if (!active.compareAndSet(false, true)) return START_NOT_STICKY
        val task = Thread({ monitor(session) }, "LaptopWatch-SignedStatus")
        task.isDaemon = true
        worker = task
        task.start()
        return START_NOT_STICKY
    }

    private fun notification(message: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val launch = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("LaptopWatch — owner protection")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(launch)
            .setOngoing(true).build()
    }

    private fun postNotice(message: String) {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTICE_ID, notification(message))
    }

    private fun monitor(session: Session) {
        try {
            val client = pinnedClient(session)
            while (active.get()) {
                try {
                    val nonceBytes = ByteArray(16)
                    SecureRandom().nextBytes(nonceBytes)
                    val nonce = Base64.encodeToString(
                        nonceBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                    )
                    val timestamp = System.currentTimeMillis() / 1_000
                    val transcript = ("LW1\nGET\n/api/status\n" +
                        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855\n" +
                        "$timestamp\n$nonce\n${session.deviceId}\n").toByteArray(Charsets.US_ASCII)
                    val signer = Ed25519Signer()
                    signer.init(true, Ed25519PrivateKeyParameters(session.seed, 0))
                    signer.update(transcript, 0, transcript.size)
                    val signature = Base64.encodeToString(
                        signer.generateSignature(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                    )
                    val request = Request.Builder()
                        .url("https://${session.host}:${session.port}/api/status")
                        .header("X-LaptopWatch-Device", session.deviceId)
                        .header("X-LaptopWatch-Timestamp", timestamp.toString())
                        .header("X-LaptopWatch-Nonce", nonce)
                        .header("X-LaptopWatch-Signature", signature)
                        .get().build()
                    client.newCall(request).execute().use { response ->
                        if (response.code == 401 || response.code == 403) {
                            // Revoked or untrusted identity. No repeated auth probes.
                            active.set(false)
                            return
                        }
                        if (response.isSuccessful) {
                            val body = response.body?.byteStream()?.use { input ->
                                val output = ByteArrayOutputStream(1024)
                                val buffer = ByteArray(1024)
                                while (true) {
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    if (output.size() + n > 4096) {
                                        throw IllegalStateException("Signed status exceeded response limit")
                                    }
                                    output.write(buffer, 0, n)
                                }
                                if (output.size() == 0) null
                                else output.toString(Charsets.UTF_8.name())
                            }
                            val payload = body?.let { JSONObject(it) }
                            // HTTP success alone is not an authenticated active state.
                            if (payload == null || payload.optBoolean("online", false) != true ||
                                payload.optBoolean("motion_armed", false) != true) {
                                active.set(false)
                                return
                            }
                            postNotice("Paired laptop armed — signed connection active")
                        } else {
                            postNotice("Laptop unreachable — no commands queued")
                        }
                    }
                } catch (_: Exception) {
                    postNotice("Laptop unreachable — no commands queued")
                }
                try { Thread.sleep(12_000) } catch (_: InterruptedException) { break }
            }
        } finally {
            active.set(false)
            if (pendingSession === session) pendingSession = null
            session.zeroize()
            stopSelf()
        }
    }

    private fun pinnedClient(session: Session): OkHttpClient {
        val expected = ByteArray(32) { i ->
            session.pin.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                throw CertificateException("Client TLS not supported")
            }
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                if (chain.isEmpty()) throw CertificateException("Missing pinned certificate")
                chain[0].checkValidity()
                val digest = MessageDigest.getInstance("SHA-256").digest(chain[0].encoded)
                if (!MessageDigest.isEqual(digest, expected)) {
                    throw CertificateException("Certificate does not match QR")
                }
            }
        }
        val tls = SSLContext.getInstance("TLS")
        tls.init(null, arrayOf<TrustManager>(trust), SecureRandom())
        return OkHttpClient.Builder()
            .sslSocketFactory(tls.socketFactory, trust)
            .hostnameVerifier { name, _ -> name == session.host }
            .followRedirects(false).followSslRedirects(false)
            .connectTimeout(4, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    override fun onDestroy() {
        active.set(false)
        worker?.interrupt()
        // The worker erases its key after returning. Do not mutate the key
        // concurrently while an Ed25519 signing operation is running.
        super.onDestroy()
    }
}
