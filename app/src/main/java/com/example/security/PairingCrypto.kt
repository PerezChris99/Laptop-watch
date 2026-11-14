package com.example.security

import android.net.Uri
import android.util.Base64
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.net.Inet4Address
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

internal object PairingCrypto {
    private val random = SecureRandom()

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also(random::nextBytes)
    fun b64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    fun fromB64(input: String, length: Int): ByteArray {
        require(input.matches(Regex("[A-Za-z0-9_-]+")) && input.length <= length * 2)
        val decoded = Base64.decode(input, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        require(decoded.size == length && b64(decoded) == input) { "Invalid key encoding" }
        return decoded
    }

    fun hex(data: ByteArray): String =
        data.joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun fromHex(value: String): ByteArray {
        require(value.length % 2 == 0 && value.matches(Regex("[0-9a-f]+")))
        return ByteArray(value.length / 2) { i ->
            value.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    fun publicKey(seed: ByteArray): ByteArray {
        require(seed.size == 32)
        return Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().encoded
    }

    fun sign(seed: ByteArray, data: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(seed, 0))
        signer.update(data, 0, data.size)
        return signer.generateSignature()
    }

    fun verify(public: ByteArray, data: ByteArray, signature: ByteArray): Boolean {
        if (public.size != 32 || signature.size != 64) return false
        val signer = Ed25519Signer()
        signer.init(false, Ed25519PublicKeyParameters(public, 0))
        signer.update(data, 0, data.size)
        return signer.verifySignature(signature)
    }

    fun qrTranscript(invitation: PairingInvitation): ByteArray =
        ("LWPAIR1\n" + invitation.desktopId + "\n" + invitation.host + "\n" +
                invitation.port + "\n" + invitation.desktopPubkey + "\n" +
                invitation.tlsFingerprint + "\n" + invitation.challenge + "\n" +
                invitation.expiresSeconds + "\n").toByteArray(Charsets.US_ASCII)

    fun phoneProof(deviceId: String, publicKey: String, nonce: String): ByteArray =
        ("LWPAIR-PROOF1\n" + deviceId + "\n" + publicKey + "\n" + nonce + "\n")
            .toByteArray(Charsets.US_ASCII)

    fun requestTranscript(
        deviceId: String, method: String, path: String, body: ByteArray,
        timestamp: Long, nonce: String
    ): ByteArray =
        ("LW1\n" + method + "\n" + path + "\n" + hex(sha256(body)) + "\n" +
                timestamp + "\n" + nonce + "\n" + deviceId + "\n")
            .toByteArray(Charsets.US_ASCII)

    fun isPrivateIPv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        val values = parts.map { p ->
            if (p.isEmpty() || (p.length > 1 && p.startsWith('0'))) return false
            p.toIntOrNull()?.takeIf { it in 0..255 } ?: return false
        }
        val a = values[0]
        val b = values[1]
        return a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168) ||
                (a == 127 && values.drop(1) == listOf(0, 0, 1))
    }
}

/** Signed QR contains the desktop's public identity, pinned TLS fingerprint
 * and a short-lived proof-of-possession challenge. It is scanned in person. */
data class PairingInvitation(
    val desktopId: String, val host: String, val port: Int, val desktopPubkey: String,
    val tlsFingerprint: String, val challenge: String, val expiresSeconds: Long
) {
    companion object {
        fun parseAndVerify(raw: String): PairingInvitation {
            require(raw.length in 40..4096) { "Invalid QR size" }
            val uri = Uri.parse(raw)
            require(uri.scheme == "laptopwatch" && uri.authority == "pair") {
                "Not a LaptopWatch pairing QR"
            }
            val expected = setOf("v", "id", "host", "port", "pk", "fp", "nonce", "exp", "sig")
            require(uri.queryParameterNames == expected &&
                    expected.all { uri.getQueryParameters(it).size == 1 }) {
                "Invalid QR fields"
            }
            fun field(name: String): String =
                uri.getQueryParameter(name) ?: throw IllegalArgumentException("Missing QR field")
            require(field("v") == "1")
            val id = field("id")
            UUID.fromString(id)
            val host = field("host")
            require(PairingCrypto.isPrivateIPv4(host)) { "Pair only over a private LAN" }
            val port = field("port").toInt()
            require(port in 1024..65535)
            val pk = field("pk")
            val fp = field("fp")
            require(fp.matches(Regex("[0-9a-f]{64}")))
            val nonce = field("nonce")
            val expires = field("exp").toLong()
            val now = System.currentTimeMillis() / 1000L
            require(expires >= now - 30L && expires <= now + 180L) { "Pairing QR expired" }
            val invitation = PairingInvitation(id, host, port, pk, fp, nonce, expires)
            val public = PairingCrypto.fromB64(pk, 32)
            PairingCrypto.fromB64(nonce, 16)
            val signature = PairingCrypto.fromB64(field("sig"), 64)
            require(PairingCrypto.verify(public, PairingCrypto.qrTranscript(invitation), signature)) {
                "Invalid desktop signature"
            }
            return invitation
        }
    }
}
