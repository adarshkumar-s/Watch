package com.adarshkumar.omnitrix.pairing

import android.content.Context
import com.adarshkumar.omnitrix.diag.DiagnosticLog
import com.adarshkumar.omnitrix.protocol.Direction

/**
 * Owns the scanned-QR side of pairing. CRITICAL SAFETY INVARIANT:
 * this manager never auto-connects, never derives an address silently and never
 * sends anything to the watch. It records, parses and exposes data; the user decides.
 */
class PairingManager(context: Context) {

    private val store = QrStore(context)

    /** Last payload handled this session (in-memory). */
    var lastPayload: QrPayload? = null
        private set

    /** Called by the scanner UI when the user confirms the capture. */
    fun record(payload: QrPayload) {
        lastPayload = payload
        store.save(payload)
        DiagnosticLog.log(
            Direction.LOCAL, "QR",
            "QR captured: ${payload.formats.joinToString(\", \") { it.label }} | len=${payload.raw.length} | payload logged verbatim"
        )
        DiagnosticLog.log(Direction.LOCAL, "QR", "RAW: ${payload.raw}")
    }

    fun history(): List<QrStore.StoredPayload> = store.all()
}
