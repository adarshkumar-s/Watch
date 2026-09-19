package com.adarshkumar.omnitrix.pairing

import com.adarshkumar.omnitrix.protocol.HexCodec
import java.util.regex.Pattern

/**
 * Describes a scanned QR payload WITHOUT executing anything in it.
 *
 * Detection is descriptive ("looks like a MAC", "looks like JSON") and every extracted
 * candidate is surfaced to the user verbatim. Malformed input never crashes the scanner —
 * worst case it is classified as PLAIN_TEXT.
 */
object QrParser {

    private val MAC_COLON: Pattern = Pattern.compile("(?i)([0-9a-f]{2}[:\\-]){5}[0-9a-f]{2}")
    private val UUID_RE: Pattern = Pattern.compile(
        "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    )
    private val URL_RE: Pattern = Pattern.compile("^(https?|ble|noise|intent)://.+", Pattern.CASE_INSENSITIVE)
    private val JWT_RE: Pattern = Pattern.compile("^[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+$")
    private val KV_RE: Pattern = Pattern.compile("^[^=\\s&]+=[^=\\s&]*(?:[&;][^=\\s&]+=[^=\\s&]*)*$")

    var now: () -> Long = { System.currentTimeMillis() }

    fun parse(raw: String?): QrPayload {
        val value = raw ?: ""
        if (value.isBlank()) {
            return QrPayload(
                raw = value,
                formats = listOf(QrPayload.Format.EMPTY),
                macCandidates = emptyList(),
                uuidCandidates = emptyList(),
                urlValue = null,
                jsonKeys = emptyList(),
                scannedAtEpochMillis = now(),
            )
        }

        val formats = LinkedHashSet<QrPayload.Format>()
        val macs = LinkedHashSet<String>()
        val uuids = LinkedHashSet<String>()
        var urlValue: String? = null
        var jsonKeys = emptyList<String>()

        // --- MAC candidates (also found inside larger payloads) ---
        val macMatcher = MAC_COLON.matcher(value)
        while (macMatcher.find()) macs += macMatcher.group().uppercase()
        // 12 contiguous hex digits treated as a MAC only when the whole payload is exactly that,
        // to avoid matching random hex blobs.
        if (value.matches(Regex("[0-9A-Fa-f]{12}"))) {
            val grouped = value.uppercase().chunked(2).joinToString(":")
            macs += grouped
        }
        if (macs.isNotEmpty()) formats += QrPayload.Format.MAC

        // --- UUID candidates ---
        val uuidMatcher = UUID_RE.matcher(value)
        while (uuidMatcher.find()) uuids += uuidMatcher.group()
        if (uuids.isNotEmpty()) formats += QrPayload.Format.UUID

        // --- URL ---
        if (URL_RE.matcher(value).matches()) {
            formats += QrPayload.Format.URL
            urlValue = value
        }

        // --- JSON (structural signal only; keys listed, values not auto-evaluated) ---
        val trimmed = value.trim()
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]"))
        ) {
            formats += QrPayload.Format.JSON
            jsonKeys = extractJsonKeys(trimmed)
        }

        // --- JWT ---
        if (JWT_RE.matcher(value).matches()) formats += QrPayload.Format.JWT

        // --- Key=value ---
        if (KV_RE.matcher(value).matches() && value.contains('=')) {
            formats += QrPayload.Format.KEY_VALUE
        }

        // --- Hex / Base64 (whole-payload encodings) ---
        if (HexCodec.looksLikeHex(value)) formats += QrPayload.Format.HEX
        if (looksLikeBase64(value)) formats += QrPayload.Format.BASE64

        if (formats.isEmpty()) formats += QrPayload.Format.PLAIN_TEXT

        return QrPayload(
            raw = value,
            formats = formats.toList(),
            macCandidates = macs.toList(),
            uuidCandidates = uuids.toList(),
            urlValue = urlValue,
            jsonKeys = jsonKeys,
            scannedAtEpochMillis = now(),
        )
    }

    private fun looksLikeBase64(s: String): Boolean {
        if (s.length < 8 || s.length % 4 != 0) return false
        if (!s.matches(Regex("[A-Za-z0-9+/]+={0,2}"))) return false
        // Round-trip check: valid Base64 that decodes to non-trivial bytes.
        // java.util.Base64 keeps this parser unit-testable on the JVM (minSdk is 26).
        return try {
            val decoded = java.util.Base64.getDecoder().decode(s)
            decoded.isNotEmpty() && decoded.size >= 6
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    private fun extractJsonKeys(json: String): List<String> {
        val keys = ArrayList<String>()
        val re = Pattern.compile("\"([^\"\\\\]{1,64})\"\\s*:")
        val m = re.matcher(json)
        var guard = 0
        while (m.find() && guard++ < 64) keys += m.group(1) ?: continue
        return keys
    }
}
