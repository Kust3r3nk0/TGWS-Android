package io.github.kust3r3nk0.tgwsandroid.core

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class MtprotoCryptoTest {
    @Test fun parsesEncryptedObfuscated2ClientHandshake() {
        val secret = ByteArray(16) { it.toByte() }
        val header = ByteArray(64) { (3 * it + 17).toByte() }
        val key = MessageDigest.getInstance("SHA-256").digest(header.copyOfRange(8, 40) + secret)
        val aes = Cipher.getInstance("AES/CTR/NoPadding")
        aes.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(header.copyOfRange(40, 56)))
        val stream = aes.update(header)
        val tail = byteArrayOf(-17, -17, -17, -17, 2, 0, 9, 8)
        for (i in tail.indices) header[56 + i] = (tail[i].toInt() xor stream[56 + i].toInt() xor header[56 + i].toInt()).toByte()
        val parsed = MtprotoCrypto.parseClientInit(header, secret)
        assertNotNull(parsed)
        assertEquals(2, parsed!!.dc)
        assertFalse(parsed.media)
        val relay = MtprotoCrypto.createRelayInit(parsed)
        assertEquals(64, relay.size)
        val context = MtprotoCrypto.buildContext(parsed, secret, relay)
        assertEquals(3, context.fromClient.update(byteArrayOf(1, 2, 3)).size)
    }
}
