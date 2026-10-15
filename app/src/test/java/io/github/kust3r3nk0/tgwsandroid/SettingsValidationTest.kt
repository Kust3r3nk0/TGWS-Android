package io.github.kust3r3nk0.tgwsandroid

import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test

class SettingsValidationTest {
    private val secret = "0123456789abcdef0123456789abcdef"
    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")

    @Test fun relayUrlIsAuthenticatedEncryptedAndCannotBeSubstitutedForSecretRecord() {
        val url = "wss://relay.example/private-key/apiws"
        val encrypted = RelayRecordCodec.encrypt(key, url)
        assertEquals(url, RelayRecordCodec.decrypt(key, encrypted))
        assertNotEquals(encrypted, RelayRecordCodec.encrypt(key, url))
        assertFalse(encrypted.contains("private-key"))
        val corrupt = Base64.getDecoder().decode(encrypted).also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertThrows(AEADBadTagException::class.java) { RelayRecordCodec.decrypt(key, Base64.getEncoder().encodeToString(corrupt)) }
        assertThrows(AEADBadTagException::class.java) { RelayRecordCodec.decrypt(key, SecretRecordCodec.encrypt(key, secret)) }
        assertThrows(IllegalArgumentException::class.java) { RelayRecordCodec.decrypt(key, "x".repeat(1405)) }
        assertThrows(IllegalArgumentException::class.java) { RelayRecordCodec.encrypt(key, "ws://relay.example") }
    }

    @Test fun localPortBoundsAreCheckedBeforeProducingLink() {
        assertFalse(SettingsValidation.validPort(0))
        assertFalse(SettingsValidation.validPort(65536))
        assertTrue(SettingsValidation.validPort(1))
        assertTrue(SettingsValidation.validPort(65535))
        assertThrows(IllegalArgumentException::class.java) { SettingsValidation.telegramLink(0, secret) }
    }

    @Test fun linkUsesLoopbackAndPaddedSecretWithCanonicalHex() {
        assertEquals("tg://proxy?server=127.0.0.1&port=1443&secret=dd$secret",
            SettingsValidation.telegramLink(1443, secret.uppercase()))
    }

    @Test fun malformedOrInjectedSecretsCannotBecomeLinks() {
        listOf("", secret.drop(1), secret + "0", secret.dropLast(1) + "g", secret.dropLast(1) + "&", secret + "\n")
            .forEach { invalid ->
                assertFalse(SettingsValidation.validSecret(invalid))
                assertThrows(IllegalArgumentException::class.java) { SettingsValidation.telegramLink(1443, invalid) }
            }
    }

    @Test fun generatedSecretHasSupportedFormat() {
        repeat(8) { assertTrue(SettingsValidation.validSecret(SettingsValidation.generateSecret())) }
    }

    @Test fun aes256GcmRecordPreservesExistingFormatAndCanonicalSecret() {
        val encrypted = SecretRecordCodec.encrypt(key, secret.uppercase())
        assertEquals(80, encrypted.length)
        assertEquals(60, Base64.getDecoder().decode(encrypted).size)
        assertEquals(secret, SecretRecordCodec.decrypt(key, encrypted))
        assertNotEquals(encrypted, SecretRecordCodec.encrypt(key, secret))
    }

    @Test fun gcmRejectsTamperingAndWrongKey() {
        val encrypted = SecretRecordCodec.encrypt(key, secret)
        val modified = Base64.getDecoder().decode(encrypted).apply { this[20] = (this[20].toInt() xor 1).toByte() }
        assertThrows(AEADBadTagException::class.java) {
            SecretRecordCodec.decrypt(key, Base64.getEncoder().encodeToString(modified))
        }
        val otherKey = SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "AES")
        assertThrows(AEADBadTagException::class.java) { SecretRecordCodec.decrypt(otherKey, encrypted) }
    }

    @Test fun encryptedRecordRejectsInvalidEncodingAndUnboundedInput() {
        listOf("", "?".repeat(80), "A".repeat(76), "A".repeat(84), "A".repeat(1_000_000)).forEach {
            assertThrows(IllegalArgumentException::class.java) { SecretRecordCodec.decrypt(key, it) }
        }
    }
}
