package io.github.kust3r3nk0.tgwsandroid.core

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class MtprotoCryptoTest {
    private fun vector(index: Int, name: String) = FlowsealVectors.bytes("handshake.$index.$name")
    private fun parsed(index: Int = 0) = checkNotNull(MtprotoCrypto.parseClientInit(
        vector(index, "header"), vector(index, "secret")))
    private fun cipherChunks(cipher: Cipher, data: ByteArray): ByteArray {
        var offset = 0
        var result = ByteArray(0)
        for (size in listOf(1, 15, 17, 64, data.size - 97)) {
            result += cipher.update(data.copyOfRange(offset, offset + size))
            offset += size
        }
        return result
    }

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

    @Test fun `handshake relay and all crypto directions match pinned Flowseal vectors`() {
        assertEquals("307a384ab7f522e34bb1a045884d0bd90f40bee5", FlowsealVectors.value("upstream.commit"))
        assertEquals(36, FlowsealVectors.value("handshake.count").toInt())
        for (index in 0 until FlowsealVectors.value("handshake.count").toInt()) {
            val handshake = parsed(index)
            assertEquals(FlowsealVectors.value("handshake.$index.dc").toInt(), handshake.dc)
            assertEquals(FlowsealVectors.value("handshake.$index.media").toBoolean(), handshake.media)
            assertEquals(FlowsealVectors.value("handshake.$index.signedDc").toInt(), handshake.originalSignedDc)
            assertArrayEquals(vector(index, "tag"), handshake.tag)
            assertArrayEquals(vector(index, "material"), handshake.clientKeyMaterial)
            val inputs = listOf(vector(index, "relayRandom"), vector(index, "relaySuffix")).iterator()
            val relay = MtprotoCrypto.createRelayInit(handshake) { size ->
                inputs.next().also { assertEquals(size, it.size) }
            }
            assertFalse(inputs.hasNext())
            assertArrayEquals(vector(index, "relay"), relay)
            val ctx = MtprotoCrypto.buildContext(handshake, vector(index, "secret"), relay)
            val payload = vector(index, "payload")
            assertArrayEquals(payload, cipherChunks(ctx.fromClient, vector(index, "clientCiphertext")))
            assertArrayEquals(payload, cipherChunks(ctx.fromTelegram, vector(index, "telegramCiphertext")))
            assertArrayEquals(vector(index, "toClient"), cipherChunks(ctx.toClient, payload))
            assertArrayEquals(vector(index, "toTelegram"), cipherChunks(ctx.toTelegram, payload))
        }
    }

    @Test fun `cipher states belong to individual sessions`() {
        val a = MtprotoCrypto.buildContext(parsed(0), vector(0, "secret"), vector(0, "relay"))
        val b = MtprotoCrypto.buildContext(parsed(1), vector(1, "secret"), vector(1, "relay"))
        val inputA = vector(0, "clientCiphertext")
        val inputB = vector(1, "clientCiphertext")
        val firstA = a.fromClient.update(inputA.copyOfRange(0, 7))
        val allB = b.fromClient.update(inputB)
        val remainingA = a.fromClient.update(inputA.copyOfRange(7, inputA.size))
        assertArrayEquals(vector(0, "payload"), firstA + remainingA)
        assertArrayEquals(vector(1, "payload"), allB)
        // Moving the opposite direction does not move an inbound cipher.
        assertArrayEquals(vector(0, "toClient"), a.toClient.update(vector(0, "payload")))
    }

    @Test fun `rejects malformed headers secrets tags and unsupported DCs`() {
        val header = vector(0, "header")
        val secret = vector(0, "secret")
        for (size in listOf(0, 1, 63, 65)) assertNull(MtprotoCrypto.parseClientInit(header.copyOf(size), secret))
        for (size in listOf(0, 15, 17)) assertNull(MtprotoCrypto.parseClientInit(header, secret.copyOf(size)))
        assertNull(MtprotoCrypto.parseClientInit(header, secret.copyOf().apply { this[0] = 127 }))
        assertNull(MtprotoCrypto.parseClientInit(header.copyOf().apply { this[56] = (this[56].toInt() xor 1).toByte() }, secret))
        assertNull(MtprotoCrypto.parseClientInit(header.copyOf().apply { this[8] = (this[8].toInt() xor 1).toByte() }, secret))
        for (dc in listOf(0, 6, 999, 10001, -10005, -32768)) {
            val mutated = header.copyOf()
            val original = parsed().originalSignedDc
            mutated[60] = (mutated[60].toInt() xor (original and 255) xor (dc and 255)).toByte()
            mutated[61] = (mutated[61].toInt() xor ((original shr 8) and 255) xor ((dc shr 8) and 255)).toByte()
            assertNull("DC $dc", MtprotoCrypto.parseClientInit(mutated, secret))
        }
    }

    @Test fun `reserved relay prefixes are retried and rejected on incoming headers`() {
        val prefixes = listOf(byteArrayOf(0xef.toByte()), "HEAD".toByteArray(), "POST".toByteArray(),
            "GET ".toByteArray(), "OPTI".toByteArray(), ByteArray(4) { 0xee.toByte() },
            ByteArray(4) { 0xdd.toByte() }, byteArrayOf(0x16, 0x03, 0x01, 0x02))
        val badCandidates = prefixes.map { prefix -> vector(0, "relayRandom").apply { prefix.copyInto(this) } } +
            listOf(vector(0, "relayRandom").apply { fill(0, 4, 8) })
        for (candidate in badCandidates) {
            val input = candidate.copyOf()
            val inputs = listOf(candidate, vector(0, "relayRandom"), vector(0, "relaySuffix")).iterator()
            assertArrayEquals(vector(0, "relay"), MtprotoCrypto.createRelayInit(parsed()) { size ->
                inputs.next().also { assertEquals(size, it.size) }
            })
            assertFalse(inputs.hasNext())
            assertArrayEquals(input, candidate)
        }
        for (prefix in prefixes) {
            val incoming = vector(0, "header").apply { prefix.copyInto(this) }
            assertNull(MtprotoCrypto.parseClientInit(incoming, vector(0, "secret")))
        }
        assertNull(MtprotoCrypto.parseClientInit(vector(0, "header").apply { fill(0, 4, 8) }, vector(0, "secret")))
    }

    @Test fun `invalid context and random sources fail without exposing material`() {
        val handshake = parsed()
        val secret = vector(0, "secret")
        val relay = vector(0, "relay")
        assertThrows(IllegalArgumentException::class.java) { MtprotoCrypto.buildContext(handshake, secret.copyOf(15), relay) }
        assertThrows(IllegalArgumentException::class.java) { MtprotoCrypto.buildContext(handshake, secret, relay.copyOf(63)) }
        assertThrows(IllegalArgumentException::class.java) { MtprotoCrypto.buildContext(handshake, secret, relay.copyOf().apply { this[56] = (this[56].toInt() xor 1).toByte() }) }
        for (invalid in listOf(handshake.copy(clientKeyMaterial = ByteArray(47)), handshake.copy(tag = ByteArray(4)),
            handshake.copy(originalSignedDc = -handshake.dc), handshake.copy(dc = 10001))) {
            assertThrows(IllegalArgumentException::class.java) { MtprotoCrypto.buildContext(invalid, secret, relay) }
            assertThrows(IllegalArgumentException::class.java) { MtprotoCrypto.createRelayInit(invalid) }
        }
        assertThrows(IllegalArgumentException::class.java) { MtprotoCrypto.createRelayInit(handshake) { ByteArray(63) } }
        assertThrows(IllegalArgumentException::class.java) {
            MtprotoCrypto.createRelayInit(handshake) { size -> if (size == 64) vector(0, "relayRandom") else ByteArray(1) }
        }
        var attempts = 0
        assertThrows(IllegalStateException::class.java) { MtprotoCrypto.createRelayInit(handshake) { ByteArray(it).also { attempts++ } } }
        assertEquals(128, attempts)
    }

    @Test fun `AES 256 CTR matches NIST SP800 38A known answer`() {
        fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val key = hex("603deb1015ca71be2b73aef0857d77811f352c073b6108d72d9810a30914dff4")
        val iv = hex("f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff")
        val plain = hex("6bc1bee22e409f96e93d7e117393172aae2d8a571e03ac9c9eb76fac45af8e51" +
            "30c81c46a35ce411e5fbc1191a0a52eff69f2445df4f9b17ad2b417be66c3710")
        val expected = hex("601ec313775789a5b7a7f504bbf3d228f443e3ca4d62b59aca84e990cacaf5c5" +
            "2b0930daa23de94ce87017ba2d84988ddfc9c58db67aada613c2dd08457941a6")
        val cipher = Cipher.getInstance("AES/CTR/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        }
        assertArrayEquals(expected, cipher.update(plain))
    }
}
