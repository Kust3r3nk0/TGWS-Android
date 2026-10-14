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
            (bytes[4] == 0.toByte() && bytes[5] == 0.toByte() &&
             bytes[6] == 0.toByte() && bytes[7] == 0.toByte())
    }
    fun parseClientInit(header: ByteArray, secret: ByteArray): Handshake? {
        if (header.size != 64 || secret.size != 16) return null
        val keyMaterial = header.part(8, 56)
        val dec = ctr(digest(keyMaterial.part(0, 32), secret), keyMaterial.part(32, 48))
        val plain = dec.update(header)
        val tag = plain.part(56, 60)
        val prefix = tag[0].toInt() and 255
        if (prefix !in tags || !tag.all { (it.toInt() and 255) == prefix }) return null
        val signed = ((plain[61].toInt() shl 8) or (plain[60].toInt() and 255)).toShort().toInt()
        val dc = abs(signed)
        if (dc !in 1..5 && dc != 203 && dc !in 10001..10005) return null
        return Handshake(dc, signed < 0, tag, signed, keyMaterial)
    }
    fun createRelayInit(handshake: Handshake): ByteArray {
        var result: ByteArray
        do { result = ByteArray(64).also(random::nextBytes) } while (forbidden(result))
        val cipher = ctr(result.part(8, 40), result.part(40, 56))
        val encrypted = cipher.update(result)
        val tail = handshake.tag + byteArrayOf(handshake.originalSignedDc.toByte(),
            (handshake.originalSignedDc shr 8).toByte()) + ByteArray(2).also(random::nextBytes)
        for (i in 0..7) result[56 + i] = (tail[i].toInt() xor encrypted[56 + i].toInt()
            xor result[56 + i].toInt()).toByte()
        return result
    }
    fun buildContext(handshake: Handshake, secret: ByteArray, relay: ByteArray): CryptoContext {
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
