package io.github.kust3r3nk0.tgwsandroid

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Android-independent configuration validation, shared by UI, service and tests. */
object SettingsValidation {
    const val DEFAULT_PORT = 1443
    const val LOOPBACK = "127.0.0.1"
    private val secretPattern = Regex("[0-9a-fA-F]{32}")

    fun validPort(port: Int) = port in 1..65535
    fun validSecret(secret: String) = secretPattern.matches(secret)
    fun normalizeSecret(secret: String): String {
        require(validSecret(secret)) { "Invalid secret format" }
        return secret.lowercase(java.util.Locale.ROOT)
    }

    fun generateSecret(): String = ByteArray(16).also(SecureRandom()::nextBytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    fun telegramLink(port: Int, secret: String): String {
        require(validPort(port)) { "Invalid local port" }
        return "tg://proxy?server=$LOOPBACK&port=$port&secret=dd${normalizeSecret(secret)}"
    }
}

/** Existing v1 record: 12-byte GCM IV + 32 ASCII hex bytes + 16-byte tag. */
internal object SecretRecordCodec {
    private const val IV_BYTES = 12
    private const val RECORD_BYTES = 60
    private const val BASE64_CHARS = 80

    fun encrypt(key: SecretKey, secret: String): String {
        val plain = SettingsValidation.normalizeSecret(secret).toByteArray(Charsets.US_ASCII)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        require(cipher.iv.size == IV_BYTES) { "Invalid GCM IV size" }
        return Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(plain))
    }

    fun decrypt(key: SecretKey, encoded: String): String {
        require(encoded.length == BASE64_CHARS) { "Invalid encrypted record size" }
        val record = Base64.getDecoder().decode(encoded)
        require(record.size == RECORD_BYTES) { "Invalid encrypted record size" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, record.copyOfRange(0, IV_BYTES)))
        return SettingsValidation.normalizeSecret(
            cipher.doFinal(record.copyOfRange(IV_BYTES, record.size)).toString(Charsets.US_ASCII)
        )
    }
}
