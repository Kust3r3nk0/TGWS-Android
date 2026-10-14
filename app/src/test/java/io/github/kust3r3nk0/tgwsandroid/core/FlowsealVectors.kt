package io.github.kust3r3nk0.tgwsandroid.core

import java.util.Properties

/** Fixtures are produced by executing the pinned upstream Python functions, independently of Kotlin. */
internal object FlowsealVectors {
    private val values = Properties().apply {
        val stream = checkNotNull(FlowsealVectors::class.java.getResourceAsStream("/flowseal-v1.11.2.properties"))
        stream.use { load(it) }
    }
    fun value(key: String): String = checkNotNull(values.getProperty(key)) { "Missing fixture property: $key" }
    fun bytes(key: String): ByteArray = value(key).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    fun numbers(key: String): List<Int> = value(key).split(',').map(String::toInt)
}
