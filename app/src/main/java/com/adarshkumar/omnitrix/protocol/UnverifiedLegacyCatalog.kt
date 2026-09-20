package com.adarshkumar.omnitrix.protocol

/**
 * Every protocol assumption carried by the ORIGINAL experimental code (pre-audit),
 * preserved as DATA — never emitted — and explicitly labeled [EvidenceLevel.UNKNOWN]
 * / UNVERIFIED until proven by the actual watch.
 *
 * The diagnostic app compares these against the discovered GATT database and reports
 * PRESENT vs ABSENT; absence is informational, never an error. The app always uses
 * whatever the watch actually exposes.
 */
object UnverifiedLegacyCatalog {

    data class LegacyUuid(
        val uuid: String,
        val role: String,
        val note: String,
    )

    data class LegacyPacket(
        val name: String,
        val bytesHex: String,
        val note: String,
    )

    /** Hard-coded in the pre-audit MainActivity. NOT SIG-standard base UUIDs. */
    val uuidAssumptions = listOf(
        LegacyUuid(
            "16186f00-0000-1000-8000-00807f9b34fb", "service",
            "old code's 'ColorFit service' — unverified, non-standard base UUID"
        ),
        LegacyUuid(
            "16186f01-0000-1000-8000-00807f9b34fb", "notify characteristic",
            "old code's notification channel — unverified"
        ),
        LegacyUuid(
            "16186f02-0000-1000-8000-00807f9b34fb", "write characteristic",
            "old code's command channel — unverified"
        ),
    )

    /** Guessed packets from the pre-audit code, archived for comparison only. */
    val packetAssumptions = listOf(
        LegacyPacket("PING", "00 00 00 00 01 00", "guessed keep-alive — never validated"),
        LegacyPacket("ACK_OK", "00 00 01 01 00 00", "guessed ACK — never validated"),
        LegacyPacket("ACK_END", "00 00 01 00 00 00", "guessed ACK terminator — never validated"),
        LegacyPacket(
            "frame(0xA1 'find watch')", "01 00 08 A1",
            "guessed find-watch opcode — never validated"
        ),
        LegacyPacket(
            "session-init burst (8 frames)", "(varies)",
            "guessed protobuf registration/session sequence — archived, not re-implemented"
        ),
    )

    fun isLegacyUuid(uuid: String): Boolean =
        uuidAssumptions.any { it.uuid.equals(uuid, ignoreCase = true) }

    fun legacyNote(uuid: String): String? =
        uuidAssumptions.firstOrNull { it.uuid.equals(uuid, ignoreCase = true) }?.let {
            "LEGACY ASSUMPTION (UNVERIFIED): ${it.role} — ${it.note}"
        }
}
