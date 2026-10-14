package io.github.kust3r3nk0.tgwsandroid.core

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

internal data class Handshake(val dc: Int, val media: Boolean, val tag: ByteArray,
    val originalSignedDc: Int, val clientKeyMaterial: ByteArray)

internal class CryptoContext(
    val fromClient: Cipher, val toClient: Cipher,
    val toTelegram: Cipher, val fromTelegram: Cipher
)

/** Literal port of Flowseal's MTProto obfuscated2 crypto setup, not MTProto E2EE. */
internal object MtprotoCrypto {
    private val random = SecureRandom()
    private val tags = listOf(0xef, 0xee, 0xdd)
    private fun digest(vararg parts: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256")
        .apply { parts.forEach(::update) }.digest()
    private fun ctr(key: ByteArray, iv: ByteArray): Cipher = Cipher.getInstance("AES/CTR/NoPadding").apply {
        init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
    }
    private fun ByteArray.part(start: Int, end: Int) = copyOfRange(start, end)
    private fun forbidden(bytes: ByteArray): Boolean {
        val prefix = String(bytes, 0, 4, Charsets.ISO_8859_1)
        return (bytes[0].toInt() and 255) == 0xef ||
            prefix in setOf("HEAD", "POST", "GET ", "OPTI") ||
            bytes.part(0, 4).all { (it.toInt() and 255) == 0xee } ||
            bytes.part(0, 4).all { (it.toInt() and 255) == 0xdd } ||
            bytes.part(0, 4).contentEquals(byteArrayOf(0x16, 0x03, 0x01, 0x02)) ||
            (bytes[4] == 0.toByte() && bytes[5] == 0.toByte() &&
             bytes[6] == 0.toByte() && bytes[7] == 0.toByte())
    }
    fun parseClientInit(header: ByteArray, secret: ByteArray): Handshake? {
        if (header.size != 64 || secret.size != 16 || forbidden(header)) return null
        val keyMaterial = header.part(8, 56)
        val dec = ctr(digest(keyMaterial.part(0, 32), secret), keyMaterial.part(32, 48))
        val plain = dec.update(header)
        val tag = plain.part(56, 60)
        val prefix = tag[0].toInt() and 255
        if (prefix !in tags || !tag.all { (it.toInt() and 255) == prefix }) return null
        val signed = ((plain[61].toInt() shl 8) or (plain[60].toInt() and 255)).toShort().toInt()
        val dc = abs(signed)
        if (dc !in 1..5 && dc != 203) return null
        return Handshake(dc, signed < 0, tag, signed, keyMaterial)
    }
    private fun validateHandshake(handshake: Handshake) {
        require(handshake.dc in 1..5 || handshake.dc == 203) { "Unsupported Telegram DC" }
        require(handshake.originalSignedDc == if (handshake.media) -handshake.dc else handshake.dc) {
            "Inconsistent Telegram DC"
        }
        require(handshake.clientKeyMaterial.size == 48) { "Invalid client key material length" }
        require(handshake.tag.size == 4 && tags.any { value ->
            handshake.tag.all { (it.toInt() and 255) == value }
        }) { "Unsupported MTProto transport" }
    }
    private fun secureBytes(size: Int) = ByteArray(size).also(random::nextBytes)

    /** Injectable randomness permits byte-for-byte comparison with the pinned Python reference. */
    fun createRelayInit(handshake: Handshake, randomBytes: (Int) -> ByteArray = ::secureBytes): ByteArray {
        validateHandshake(handshake)
        var result: ByteArray? = null
        for (attempt in 0 until 128) {
            val candidate = randomBytes(64)
            require(candidate.size == 64) { "Invalid relay randomness length" }
            if (!forbidden(candidate)) {
                result = candidate.copyOf()
                break
            }
        }
        val relay = checkNotNull(result) { "Unable to generate a valid relay initialization" }
        val cipher = ctr(relay.part(8, 40), relay.part(40, 56))
        val encrypted = cipher.update(relay)
        val padding = randomBytes(2)
        require(padding.size == 2) { "Invalid relay padding length" }
        val tail = handshake.tag + byteArrayOf(handshake.originalSignedDc.toByte(),
            (handshake.originalSignedDc shr 8).toByte()) + padding
        for (i in 0..7) relay[56 + i] = (tail[i].toInt() xor encrypted[56 + i].toInt()
            xor relay[56 + i].toInt()).toByte()
        return relay
    }
    fun buildContext(handshake: Handshake, secret: ByteArray, relay: ByteArray): CryptoContext {
        validateHandshake(handshake)
        require(secret.size == 16) { "Invalid MTProto secret length" }
        require(relay.size == 64 && !forbidden(relay)) { "Invalid relay initialization" }
        val relayPlain = ctr(relay.part(8, 40), relay.part(40, 56)).update(relay)
        require(relayPlain.part(56, 60).contentEquals(handshake.tag) &&
            relayPlain[60] == handshake.originalSignedDc.toByte() &&
            relayPlain[61] == (handshake.originalSignedDc shr 8).toByte()) {
            "Inconsistent relay initialization"
        }
        val material = handshake.clientKeyMaterial
        val backwards = material.reversedArray()
        val fromClient = ctr(digest(material.part(0, 32), secret), material.part(32, 48))
        val toClient = ctr(digest(backwards.part(0, 32), secret), backwards.part(32, 48))
        val toTelegram = ctr(relay.part(8, 40), relay.part(40, 56))
        val fromTelegramMaterial = relay.part(8, 56).reversedArray()
        val fromTelegram = ctr(fromTelegramMaterial.part(0, 32), fromTelegramMaterial.part(32, 48))
        fromClient.update(ByteArray(64))
        toTelegram.update(ByteArray(64))
        return CryptoContext(fromClient, toClient, toTelegram, fromTelegram)
    }
}
