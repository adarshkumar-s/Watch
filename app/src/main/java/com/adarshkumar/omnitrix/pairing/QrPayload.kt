package com.adarshkumar.omnitrix.pairing

/**
 * A QR payload exactly as scanned — the app stores and displays it VERBATIM.
 * Parsing only *describes* it; it never triggers any action.
 */
data class QrPayload(
    val raw: String,
    val formats: List<Format>,
    val macCandidates: List<String>,
    val uuidCandidates: List<String>,
    val urlValue: String?,
    val jsonKeys: List<String>,
    val scannedAtEpochMillis: Long,
) {
    enum class Format(val label: String) {
        URL("URL"),
        JSON("JSON"),
        JWT("JWT token"),
        MAC("MAC address"),
        UUID("UUID"),
        HEX("Hex data"),
        BASE64("Base64 data"),
        KEY_VALUE("Key=value pairs"),
        TEXT("Text"),
        UNKNOWN("Unknown"),
        EMPTY("Empty"),
    }

    fun primaryFormat(): Format = formats.firstOrNull() ?: Format.TEXT
}
