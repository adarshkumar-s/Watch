package com.adarshkumar.omnitrix.ble

/**
 * Pure, documented Bluetooth LE Advertisement Data (AD) structure parser.
 * Parses the raw bytes Android exposes via ScanRecord.getBytes() — the actual on-air
 * payload — into typed AD fields. Malformed/truncated payloads never throw; parsing
 * stops at the first bad structure and reports what was recovered.
 *
 * AD format reference (public, Bluetooth Core Spec — CSS):
 *   [ len ][ AD type ][ AD data… ]  repeated; len counts type+data (not itself).
 */
object AdvertisementParser {

    // AD type codes (Bluetooth SIG assigned numbers — publicly documented)
    const val AD_FLAGS = 0x01
    const val AD_INCOMPLETE_UUID16 = 0x02
    const val AD_COMPLETE_UUID16 = 0x03
    const val AD_INCOMPLETE_UUID32 = 0x04
    const val AD_COMPLETE_UUID32 = 0x05
    const val AD_INCOMPLETE_UUID128 = 0x06
    const val AD_COMPLETE_UUID128 = 0x07
    const val AD_SHORTENED_NAME = 0x08
    const val AD_COMPLETE_NAME = 0x09
    const val AD_TX_POWER = 0x0A
    const val AD_SERVICE_DATA_16 = 0x16
    const val AD_SERVICE_DATA_32 = 0x20
    const val AD_SERVICE_DATA_128 = 0x21
    const val AD_MANUFACTURER = 0xFF

    data class Field(val type: Int, val data: ByteArray) {
        val typeName: String
            get() = when (type) {
                AD_FLAGS -> "Flags"
                AD_INCOMPLETE_UUID16 -> "Incomplete UUID16"
                AD_COMPLETE_UUID16 -> "Complete UUID16"
                AD_INCOMPLETE_UUID32 -> "Incomplete UUID32"
                AD_COMPLETE_UUID32 -> "Complete UUID32"
                AD_INCOMPLETE_UUID128 -> "Incomplete UUID128"
                AD_COMPLETE_UUID128 -> "Complete UUID128"
                AD_SHORTENED_NAME -> "Shortened Name"
                AD_COMPLETE_NAME -> "Complete Name"
                AD_TX_POWER -> "Tx Power"
                AD_SERVICE_DATA_16 -> "Service Data (UUID16)"
                AD_SERVICE_DATA_32 -> "Service Data (UUID32)"
                AD_SERVICE_DATA_128 -> "Service Data (UUID128)"
                AD_MANUFACTURER -> "Manufacturer Specific"
                else -> "Type 0x%02X".format(type)
            }
    }

    data class Result(
        val fields: List<Field>,
        val truncatedAt: Int?,   // index where a malformed structure stopped parsing
    ) {
        val ok: Boolean get() = truncatedAt == null
        fun fieldsOfType(type: Int): List<Field> = fields.filter { it.type == type }
    }

    /** Parses a raw advertisement payload. Never throws. */
    fun parse(bytes: ByteArray): Result {
        val fields = ArrayList<Field>()
        var i = 0
        while (i < bytes.size) {
            val len = bytes[i].toInt() and 0xFF
            if (len == 0) break                       // standard terminator
            // len counts (type + data); full structure spans [i, i + len]
            if (i + 1 > bytes.size - 1) return Result(fields, truncatedAt = i)
            val dataEnd = i + 1 + len                  // exclusive: first byte past structure
            if (dataEnd > bytes.size) return Result(fields, truncatedAt = i)
            val type = bytes[i + 1].toInt() and 0xFF
            fields += Field(type, bytes.copyOfRange(i + 2, dataEnd))
            i = dataEnd
        }
        return Result(fields, null)
    }

    /** Human-readable rendering of a parsed payload for the diagnostics screens. */
    fun render(bytes: ByteArray): String {
        val r = parse(bytes)
        val sb = StringBuilder()
        for (f in r.fields) {
            sb.append("  ${f.typeName} (0x%02X, ${f.data.size}B)".format(f.type))
            val text = when (f.type) {
                AD_SHORTENED_NAME, AD_COMPLETE_NAME -> " \"${String(f.data, Charsets.UTF_8)}\""
                AD_MANUFACTURER -> {
                    if (f.data.size >= 2) {
                        val company = (f.data[1].toInt() and 0xFF shl 8) or
                            (f.data[0].toInt() and 0xFF)
                        val payload = f.data.copyOfRange(2, f.data.size)
                        // Some vendors embed plain text here — show it next to the hex.
                        val printable =
                            if (payload.isNotEmpty() && payload.all { (it.toInt() and 0xFF) in 32..126 })
                                " \"${String(payload, Charsets.UTF_8)}\""
                            else ""
                        " company=0x%04X data=%s%s".format(company, hex(payload), printable)
                    } else " data=${hex(f.data)}"
                }
                AD_TX_POWER -> if (f.data.isNotEmpty()) " ${f.data[0]} dBm" else ""
                else -> " ${hex(f.data)}"
            }
            sb.append(text).append('\n')
        }
        r.truncatedAt?.let { sb.append("  <truncated/malformed AD structure at byte $it>\n") }
        return sb.toString().trimEnd()
    }

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02X".format(it) }
}
