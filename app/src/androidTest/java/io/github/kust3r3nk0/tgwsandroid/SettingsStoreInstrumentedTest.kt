package io.github.kust3r3nk0.tgwsandroid

import android.content.Context
import android.security.keystore.KeyInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyStore
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsStoreInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val alias = "tgws-secret-aes-v1"
    private val sample = "0123456789abcdef0123456789abcdef"
    private fun keystore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Before fun cleanBefore() { reset() }
    @After fun cleanAfter() { reset() }
    private fun reset() {
        prefs.edit().clear().commit()
        keystore().deleteEntry(alias)
    }

    @Test fun firstRunCreatesAes256KeyAndOnlyEncryptedSecret() {
        val first = SettingsStore(context).load()
        assertEquals(1443, first.port)
        assertTrue(SettingsValidation.validSecret(first.secret))
        assertEquals(first, SettingsStore(context).load())
        val encrypted = prefs.getString("secret_encrypted", null)
        assertNotNull(encrypted)
        assertEquals(80, encrypted!!.length)
        assertFalse(prefs.all.values.contains(first.secret))
        val key = keystore().getKey(alias, null) as SecretKey
        val info = SecretKeyFactory.getInstance("AES", "AndroidKeyStore").getKeySpec(key, KeyInfo::class.java) as KeyInfo
        assertEquals(256, info.keySize)
        assertArrayEquals(arrayOf("GCM"), info.blockModes)
    }

    @Test fun relayIsEncryptedRetainedAcrossEditsAndNeverSilentlyFallsBackAfterKeyLoss() {
        val store = SettingsStore(context)
        val url = "wss://relay.example/test-key/apiws"
        store.save(2443, sample, url)
        assertEquals(StoredSettings(2443, sample, url), store.load())
        assertFalse(prefs.all.values.contains(url))
        val encrypted = prefs.getString("relay_encrypted", null)
        assertNotNull(encrypted)
        store.save(3443, sample)
        assertEquals(url, store.load().relayUrl)
        keystore().deleteEntry(alias)
        assertThrows(SettingsException::class.java) { store.load() }
        assertThrows(SettingsException::class.java) { store.save(3443, sample, "") }
        assertNotNull(prefs.getString("relay_encrypted", null))
        store.recover(3443, sample, url)
        assertEquals(url, store.load().relayUrl)
        store.save(3443, sample, "")
        assertEquals("", store.load().relayUrl)
        assertFalse(prefs.contains("relay_encrypted"))
    }

    @Test fun corruptedRelayDoesNotChangeSecretOrSwitchToDirect() {
        val store = SettingsStore(context)
        store.save(1443, sample, "wss://relay.example")
        val secretRecord = prefs.getString("secret_encrypted", null)
        prefs.edit().putString("relay_encrypted", "corrupted").commit()
        assertThrows(SettingsException::class.java) { store.load() }
        assertThrows(SettingsException::class.java) { store.save(1443, sample, "") }
        assertEquals(secretRecord, prefs.getString("secret_encrypted", null))
        assertEquals("corrupted", prefs.getString("relay_encrypted", null))
    }

    @Test fun missingKeystoreAliasPreservesCiphertextAndRequiresExplicitRecovery() {
        val store = SettingsStore(context)
        store.save(1443, sample)
        val saved = prefs.getString("secret_encrypted", null)
        keystore().deleteEntry(alias)
        val error = assertThrows(SettingsException::class.java) { store.load() }
        assertEquals(SettingsIssue.SECRET_UNAVAILABLE, error.issue)
        assertEquals(saved, prefs.getString("secret_encrypted", null))
        assertFalse(keystore().containsAlias(alias))
        assertThrows(SettingsException::class.java) { store.save(1443, "fedcba9876543210fedcba9876543210") }
        assertEquals(saved, prefs.getString("secret_encrypted", null))
        val replacement = "fedcba9876543210fedcba9876543210"
        store.recover(2443, replacement)
        assertEquals(StoredSettings(2443, replacement), store.load())
    }

    @Test fun corruptedAndWrongTypeRecordsAreNotAutomaticallyReplaced() {
        val store = SettingsStore(context)
        store.save(1443, sample)
        prefs.edit().putString("secret_encrypted", "invalid").commit()
        assertThrows(SettingsException::class.java) { store.load() }
        assertEquals("invalid", prefs.getString("secret_encrypted", null))
        prefs.edit().putInt("secret_encrypted", 3).commit()
        assertThrows(SettingsException::class.java) { store.load() }
        assertEquals(3, prefs.getInt("secret_encrypted", -1))
        store.recover(1443, sample)
        assertEquals(sample, store.load().secret)
    }

    @Test fun invalidConfigurationDoesNotOverwritePreviousEncryptedRecord() {
        val store = SettingsStore(context)
        store.save(1443, sample)
        val previous = prefs.all
        assertThrows(SettingsException::class.java) { store.save(0, sample) }
        assertThrows(SettingsException::class.java) { store.save(1443, "invalid") }
        assertEquals(previous, prefs.all)
    }

    @Test fun missingSecretInExistingInstallationNeverGeneratesOrSilentlySavesReplacement() {
        val store = SettingsStore(context)
        store.save(8443, sample)
        prefs.edit().remove("secret_encrypted").commit()
        val remaining = prefs.all.toMap()
        assertEquals(SettingsIssue.SECRET_UNAVAILABLE, assertThrows(SettingsException::class.java) { store.load() }.issue)
        assertEquals(SettingsIssue.SECRET_UNAVAILABLE, assertThrows(SettingsException::class.java) { store.save(8443, sample) }.issue)
        assertEquals(remaining, prefs.all)
        // Even if the preference file is lost entirely, the old key is evidence of prior use.
        prefs.edit().clear().commit()
        assertThrows(SettingsException::class.java) { store.load() }
        assertTrue(prefs.all.isEmpty())
        store.recover(8443, sample)
        assertEquals(sample, store.load().secret)
    }

    @Test fun legacyEncryptedSettingsSurviveRepeatedLoadsWithoutReencryptionOrSecretRotation() {
        val store = SettingsStore(context)
        store.save(8443, sample, "wss://relay.example/test-key/apiws")
        // Pre-0.3 files have no transport record. Migration is in memory, with no secret write.
        prefs.edit().remove("transport_encrypted").commit()
        val previous = prefs.all.toMap()
        repeat(20) {
            val saved = SettingsStore(context).load()
            assertEquals(sample, saved.secret)
            assertEquals(8443, saved.port)
            assertEquals("wss://relay.example/test-key/apiws", saved.relayUrl)
        }
        assertEquals(previous, prefs.all)
    }
}
