package com.adarshkumar.omnitrix.analysis

import com.adarshkumar.omnitrix.pairing.QrPayload
import java.util.Locale

/**
 * Pure correlation engine answering the Phase-7 question: *is the QR actually part of
 * the BLE pairing protocol?* Compares the scanned QR against observed BLE data and
 * emits labeled findings. No mutation, no assumptions — just evidence.
 */
object QrBleCorrelation {

    enum class Verdict { MATCH, NO_MATCH, NO_DATA }

    data class Finding(
        val label: String,
        val verdict: Verdict,
        val detail: String,
    )

    /** Everything we may know about a BLE device from scanning + GATT. */
    data class BleObservation(
        val address: String?,
        val name: String?,
        val manufacturerDataHexList: List<String>, // lower-case, no spaces
        val advertisedServiceUuids: List<String>,
        val gattServiceUuids: List<String>,
    )

    fun correlate(qr: QrPayload?, ble: BleObservation): List<Finding> {
        val findings = ArrayList<Finding>()
        if (qr == null || qr.raw.isBlank()) {
            findings += Finding("QR payload", Verdict.NO_DATA, "No QR captured yet — scan the watch QR first.")
        }

        // 1) QR MAC vs BLE address
        if (qr != null && qr.macCandidates.isNotEmpty()) {
            val addr = ble.address?.uppercase()
            findings += when {
                addr == null -> Finding(
                    "QR MAC vs BLE address", Verdict.NO_DATA,
                    "QR contains ${qr.macCandidates.joinToString()}, but no BLE address to compare."
                )
                qr.macCandidates.any { it.equals(addr, ignoreCase = true) } -> Finding(
                    "QR MAC vs BLE address", Verdict.MATCH,
                    "QR MAC ${qr.macCandidates.joinToString()} == BLE address $addr. The QR most likely carries the device address."
                )
                else -> Finding(
                    "QR MAC vs BLE address", Verdict.NO_MATCH,
                    "QR MAC ${qr.macCandidates.joinToString()} ≠ BLE address $addr. The QR may hold a different identifier (serial/token)."
                )
            }
        }

        // 2) QR UUID candidates vs advertised/discovered services
        if (qr != null && qr.uuidCandidates.isNotEmpty()) {
            val allUuids = (ble.advertisedServiceUuids + ble.gattServiceUuids)
                .map { it.lowercase(Locale.US) }
            val hit = qr.uuidCandidates.firstOrNull { it.lowercase(Locale.US) in allUuids }
            findings += when {
                allUuids.isEmpty() -> Finding("QR UUID vs services", Verdict.NO_DATA,
                    "QR carries UUID(s): ${qr.uuidCandidates.joinToString()}, but no service UUIDs observed.")
                hit != null -> Finding("QR UUID vs services", Verdict.MATCH,
                    "QR UUID $hit appears in BLE services. The QR may reference the GATT profile.")
                else -> Finding("QR UUID vs services", Verdict.NO_MATCH,
                    "QR UUID(s) ${qr.uuidCandidates.joinToString()} not seen in BLE services.")
            }
        }

        // 3) Raw QR (normalized) embedded in manufacturer/service data
        if (qr != null) {
            val normalized = qr.raw.replace(Regex("[^0-9A-Za-z]"), "").lowercase(Locale.US)
            val observed = ble.manufacturerDataHexList
            findings += when {
                observed.isEmpty() || normalized.length < 6 -> Finding(
                    "QR vs manufacturer data", Verdict.NO_DATA,
                    if (observed.isEmpty()) "No manufacturer data captured from advertisements."
                    else "QR too short/ambiguous to search inside manufacturer bytes."
                )
                else -> {
                    val hit = observed.any { hex ->
                        hexToPrintable(hex).contains(normalized) ||
                            normalizedHex(normalized).let { it.isNotEmpty() && hex.contains(it) }
                    }
                    if (hit) Finding("QR vs manufacturer data", Verdict.MATCH,
                        "QR content (or its hex rendering) appears inside manufacturer data.")
                    else Finding("QR vs manufacturer data", Verdict.NO_MATCH,
                        "QR content not found inside manufacturer data bytes.")
                }
            }
        }

        // 4) Device-id hints (name contains device identifier)
        if (ble.name != null) {
            val n = ble.name.lowercase(Locale.US)
            val hints = listOfNotNull(
                if (n.contains("caliber")) "\"Caliber\" in name" else null,
                if (n.contains("noise")) "\"Noise\" in name" else null,
                if (n.contains("2881")) "\"2881\" in name" else null,
            )
            if (hints.isNotEmpty()) findings += Finding(
                "Device-name hints", Verdict.MATCH, "Advertised name \"${ble.name}\" contains: ${hints.joinToString()}."
            )
        }

        if (findings.isEmpty()) {
            findings += Finding("Correlation", Verdict.NO_DATA, "Nothing to correlate yet.")
        }
        return findings
    }

    private fun normalizedHex(s: String): String =
        s.filter { it in "0123456789abcdefABCDEF" }.lowercase(Locale.US)

    private fun hexToPrintable(hex: String): String {
        if (hex.length % 2 != 0) return ""
        val sb = StringBuilder()
        var i = 0
        while (i + 1 < hex.length + 1 && i + 2 <= hex.length) {
            val b = hex.substring(i, i + 2).toIntOrNull(16) ?: return ""
            sb.append(if (b in 32..126) b.toChar() else '.')
            i += 2
        }
        return sb.toString()
    }
}
