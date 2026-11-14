package com.example.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import android.net.Uri
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class PairingCryptoTest {
    private val seed = ByteArray(32) { (it * 3 + 5).toByte() }
    private val pub = PairingCrypto.publicKey(seed)

    @Test fun ed25519RoundTripAndTamper() {
        val data = "LaptopWatch signed request".toByteArray()
        val signature = PairingCrypto.sign(seed, data)
        assertEquals(64, signature.size)
        assertTrue(PairingCrypto.verify(pub, data, signature))
        assertFalse(PairingCrypto.verify(pub, "tampered".toByteArray(), signature))
    }

    @Test fun signedRequestCanonicalMatchesPythonSpec() {
        val body = "{\"armed\":true}".toByteArray()
        val deviceId = "8f3ddba0-93c2-498e-98ee-a1e352ad5141"
        val nonce = PairingCrypto.b64(ByteArray(16) { it.toByte() })
        val transcript = PairingCrypto.requestTranscript(
            deviceId, "POST", "/api/config/armed", body, 1700000000L, nonce
        ).toString(Charsets.US_ASCII)
        val expected = "LW1\nPOST\n/api/config/armed\n" +
            PairingCrypto.hex(PairingCrypto.sha256(body)) +
            "\n1700000000\n" + nonce + "\n" + deviceId + "\n"
        assertEquals(expected, transcript)
        assertTrue(PairingCrypto.verify(pub, transcript.toByteArray(),
            PairingCrypto.sign(seed, transcript.toByteArray())))
    }

    @Test fun signedQrAcceptedButTamperingOrExpiryDenied() {
        val now = System.currentTimeMillis() / 1000
        val invitation = PairingInvitation(
            desktopId = UUID.randomUUID().toString(),
            host = "192.168.10.30",
            port = 8765,
            desktopPubkey = PairingCrypto.b64(pub),
            tlsFingerprint = "a".repeat(64),
            challenge = PairingCrypto.b64(ByteArray(16) { it.toByte() }),
            expiresSeconds = now + 90L
        )
        val signature = PairingCrypto.b64(PairingCrypto.sign(
            seed, PairingCrypto.qrTranscript(invitation)))
        val url = Uri.parse("laptopwatch://pair").buildUpon()
            .appendQueryParameter("v", "1")
            .appendQueryParameter("id", invitation.desktopId)
            .appendQueryParameter("host", invitation.host)
            .appendQueryParameter("port", invitation.port.toString())
            .appendQueryParameter("pk", invitation.desktopPubkey)
            .appendQueryParameter("fp", invitation.tlsFingerprint)
            .appendQueryParameter("nonce", invitation.challenge)
            .appendQueryParameter("exp", invitation.expiresSeconds.toString())
            .appendQueryParameter("sig", signature)
            .build().toString()
        assertEquals(invitation, PairingInvitation.parseAndVerify(url))
        assertThrows(IllegalArgumentException::class.java) {
            PairingInvitation.parseAndVerify(url.replace("192.168.10.30", "192.168.10.31"))
        }
        val expired = invitation.copy(expiresSeconds = now - 500L)
        val oldSignature = PairingCrypto.b64(PairingCrypto.sign(
            seed, PairingCrypto.qrTranscript(expired)))
        assertThrows(IllegalArgumentException::class.java) {
            PairingInvitation.parseAndVerify(
                url.replace(invitation.expiresSeconds.toString(), expired.expiresSeconds.toString())
                    .replace(signature, oldSignature)
            )
        }
    }

    @Test fun rejectPublicAndReservedHosts() {
        assertTrue(PairingCrypto.isPrivateIPv4("10.1.20.4"))
        assertTrue(PairingCrypto.isPrivateIPv4("192.168.1.25"))
        assertTrue(PairingCrypto.isPrivateIPv4("172.31.255.2"))
        assertFalse(PairingCrypto.isPrivateIPv4("8.8.8.8"))
        assertFalse(PairingCrypto.isPrivateIPv4("224.0.0.1"))
        assertFalse(PairingCrypto.isPrivateIPv4("192.168.1.025"))
        assertFalse(PairingCrypto.isPrivateIPv4("0.0.0.0"))
    }
}
