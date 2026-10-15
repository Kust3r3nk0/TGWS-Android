package io.github.kust3r3nk0.tgwsandroid

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

enum class SettingsIssue { INVALID_CONFIGURATION, SECRET_UNAVAILABLE, WRITE_FAILED }
class SettingsException(val issue: SettingsIssue) : Exception(issue.name)
data class StoredSettings(val port: Int, val secret: String, val relayUrl: String = "")

/** Only an encrypted secret enters preferences. Reads never replace an unreadable record. */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val alias = "tgws-secret-aes-v1"

    var port: Int
        get() = try {
            prefs.getInt("port", SettingsValidation.DEFAULT_PORT).also {
                if (!SettingsValidation.validPort(it)) throw SettingsException(SettingsIssue.INVALID_CONFIGURATION)
            }
        } catch (_: ClassCastException) {
            throw SettingsException(SettingsIssue.INVALID_CONFIGURATION)
        }
        set(value) {
            if (!SettingsValidation.validPort(value)) throw SettingsException(SettingsIssue.INVALID_CONFIGURATION)
            if (!prefs.edit().putInt("port", value).commit()) throw SettingsException(SettingsIssue.WRITE_FAILED)
        }

    var secret: String
        get() = load().secret
        set(value) = save(port, value)

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun existingKey(): SecretKey? = keyStore().getKey(alias, null) as? SecretKey

    private fun newKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return generator.generateKey()
    }

    private fun record(): String? = try {
        prefs.getString("secret_encrypted", null)
    } catch (_: ClassCastException) {
        throw SettingsException(SettingsIssue.SECRET_UNAVAILABLE)
    }

    private fun decrypt(saved: String): String = try {
        val key = existingKey() ?: throw SettingsException(SettingsIssue.SECRET_UNAVAILABLE)
        SecretRecordCodec.decrypt(key, saved)
    } catch (_: Exception) {
        // Do not emit exception messages, which may contain sensitive provider data.
        throw SettingsException(SettingsIssue.SECRET_UNAVAILABLE)
    }

    fun load(): StoredSettings = synchronized(lock) {
        val localPort = port
        val relay = loadRelay()
        val saved = record()
        if (saved != null) return@synchronized StoredSettings(localPort, decrypt(saved), relay)
        val generated = SettingsValidation.generateSecret()
        persist(localPort, generated, recovery = false, relayUrl = relay)
        StoredSettings(localPort, generated, relay)
    }

    private fun loadRelay(): String = try {
        val saved = prefs.getString("relay_encrypted", null)
        if (saved == null) "" else RelayRecordCodec.decrypt(existingKey() ?: error("Key unavailable"), saved)
    } catch (_: Exception) { throw SettingsException(SettingsIssue.SECRET_UNAVAILABLE) }

    fun save(port: Int, secret: String, relayUrl: String? = null) = synchronized(lock) {
        val relay = relayUrl ?: loadRelay()
        validate(port, secret, relay)
        // A UI edit cannot silently overwrite settings after loss of the Keystore key.
        record()?.let(::decrypt)
        loadRelay() // An unreadable relay must never silently switch to direct routes.
        persist(port, secret, recovery = false, relayUrl = relay)
    }

    /** Called only after the user explicitly asks to replace the unavailable secret. */
    fun recover(port: Int, secret: String, relayUrl: String = "") = synchronized(lock) {
        validate(port, secret, relayUrl)
        persist(port, secret, recovery = true, relayUrl = relayUrl)
    }

    private fun persist(port: Int, secret: String, recovery: Boolean, relayUrl: String = "") {
        val encrypted = try {
            val key = try {
                existingKey() ?: if (recovery || !prefs.contains("secret_encrypted")) newKey()
                    else throw SettingsException(SettingsIssue.SECRET_UNAVAILABLE)
            } catch (error: Exception) {
                if (!recovery) throw error
                keyStore().deleteEntry(alias)
                newKey()
            }
            try {
                SecretRecordCodec.encrypt(key, secret)
            } catch (error: Exception) {
                if (!recovery) throw error
                keyStore().deleteEntry(alias)
                SecretRecordCodec.encrypt(newKey(), secret)
            }
        } catch (_: Exception) {
            throw SettingsException(SettingsIssue.WRITE_FAILED)
        }
        val relayEncrypted = try {
            if (relayUrl.isEmpty()) null else RelayRecordCodec.encrypt(existingKey() ?: error("Key unavailable"), relayUrl)
        } catch (_: Exception) { throw SettingsException(SettingsIssue.WRITE_FAILED) }
        if (!prefs.edit().putInt("port", port).putString("secret_encrypted", encrypted)
                .putString("relay_encrypted", relayEncrypted).commit()) {
            throw SettingsException(SettingsIssue.WRITE_FAILED)
        }
    }

    private fun validate(port: Int, secret: String, relayUrl: String) {
        try { io.github.kust3r3nk0.tgwsandroid.core.RelayEndpoint.parse(relayUrl) }
        catch (_: IllegalArgumentException) { throw SettingsException(SettingsIssue.INVALID_CONFIGURATION) }
        if (!SettingsValidation.validPort(port) || !SettingsValidation.validSecret(secret)) {
            throw SettingsException(SettingsIssue.INVALID_CONFIGURATION)
        }
    }

    companion object { private val lock = Any() }
}
