package com.adarshkumar.omnitrix.protocol

/**
 * Pure hex utilities used by the diagnostic log, QR parser and packet codecs.
 * No Android dependencies — unit-testable on the JVM.
 */
object HexCodec {

    private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

    fun ByteArray.toHex(separator: String = " "): String {
        if (isEmpty()) return ""
        val sb = StringBuilder(size * (2 + separator.length))
        for ((i, b) in withIndex()) {
            if (i > 0 && separator.isNotEmpty()) sb.append(separator)
            val v = b.toInt() and 0xFF
            sb.append(HEX_CHARS[v ushr 4]).append(HEX_CHARS[v and 0x0F])
        }
        return sb.toString()
    }

    /** Parses "AA:BB:.." / "AA BB" / "AABB" into bytes. Returns null on any invalid input. */
    fun parse(input: String): ByteArray? {
        val cleaned = input.replace(Regex("[:\\-\\s]"), "")
        if (cleaned.isEmpty() || cleaned.length % 2 != 0) return null
        if (!cleaned.matches(Regex("[0-9A-Fa-f]+"))) return null
        return ByteArray(cleaned.length / 2) { i ->
            cleaned.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    /** True when [s] is a non-empty, even-length run of hex digits (optionally :/- separated). */
    fun looksLikeHex(s: String): Boolean {
        val cleaned = s.replace(Regex("[:\\-\\s]"), "")
        return cleaned.isNotEmpty() && cleaned.length >= 4 && cleaned.length % 2 == 0 &&
            cleaned.matches(Regex("[0-9A-Fa-f]+"))
    }
}
