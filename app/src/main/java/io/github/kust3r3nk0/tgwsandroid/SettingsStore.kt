package io.github.kust3r3nk0.tgwsandroid

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Port stored in preferences; secret encrypted using an Android Keystore AES key. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val alias = "tgws-secret-aes-v1"
    var port: Int
        get() = prefs.getInt("port", 1443)
        set(value) { require(value in 1..65535); prefs.edit().putInt("port", value).apply() }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun decrypt(data: String): String {
        val bytes = Base64.decode(data, Base64.NO_WRAP)
        require(bytes.size >= 13)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }

    var secret: String
        get() {
            val saved = prefs.getString("secret_encrypted", null)
            if (saved != null) {
                try { return decrypt(saved) } catch (_: Exception) { /* re-key if needed */ }
            }
            val generated = ByteArray(16).also(SecureRandom()::nextBytes)
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            secret = generated
            return generated
        }
        set(value) {
            require(value.matches(Regex("[0-9a-fA-F]{32}")))
            prefs.edit().putString("secret_encrypted", encrypt(value.lowercase())).apply()
        }
}
