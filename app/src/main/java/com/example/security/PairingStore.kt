package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Pairing keys are stored only as AES-GCM ciphertext wrapped by Android Keystore.
 * Private pairing material is never included in backups or application logs. */
internal data class PairedLaptop(
    val deviceId: String,
    val desktopId: String,
    val host: String,
    val port: Int,
    val desktopPubkey: String,
    val tlsFingerprint: String,
    val privateSeed: ByteArray,
    val approved: Boolean
) {
    fun toJson(): JSONObject = JSONObject()
        .put("device", deviceId)
        .put("desktop", desktopId)
        .put("host", host)
        .put("port", port)
        .put("pubkey", desktopPubkey)
        .put("fingerprint", tlsFingerprint)
        .put("seed", PairingCrypto.b64(privateSeed))
        .put("approved", approved)

    companion object {
        fun fromJson(j: JSONObject) = PairedLaptop(
            j.getString("device"), j.getString("desktop"), j.getString("host"),
            j.getInt("port"), j.getString("pubkey"), j.getString("fingerprint"),
            PairingCrypto.fromB64(j.getString("seed"), 32), j.getBoolean("approved")
        )
    }
}

internal class PairingStore(context: Context) {
    private val prefs = context.getSharedPreferences("laptopwatch_secure_pairing", Context.MODE_PRIVATE)
    private val keyAlias = "laptopwatch_pairing_wrap_v1"
    private val aad = "LaptopWatch pairing 1".toByteArray(Charsets.UTF_8)

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val current = store.getKey(keyAlias, null) as? SecretKey
        if (current != null) return current
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    @Synchronized
    fun save(pair: PairedLaptop) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        cipher.updateAAD(aad)
        val encoded = cipher.iv + cipher.doFinal(pair.toJson().toString().toByteArray(Charsets.UTF_8))
        val text = Base64.encodeToString(encoded, Base64.NO_WRAP)
        check(prefs.edit().putString("v1", text).commit()) { "Unable to save encrypted pairing" }
    }

    @Synchronized
    fun load(): PairedLaptop? {
        val text = prefs.getString("v1", null) ?: return null
        val bytes = Base64.decode(text, Base64.NO_WRAP)
        require(bytes.size > 28) { "Stored pairing corrupted" }
        val iv = bytes.copyOfRange(0, 12)
        val cipherText = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        // Decryption/authentication failure deliberately propagates; never
        // silently reset or downgrade to unsigned legacy connection.
        val decoded = cipher.doFinal(cipherText).toString(Charsets.UTF_8)
        return PairedLaptop.fromJson(JSONObject(decoded))
    }

    @Synchronized
    fun clear() {
        check(prefs.edit().remove("v1").commit())
    }
}
