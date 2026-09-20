package com.adarshkumar.omnitrix.pairing

import android.content.Context
import com.adarshkumar.omnitrix.diagnostics.DiagnosticLog
import com.adarshkumar.omnitrix.diagnostics.LogEvent
import com.adarshkumar.omnitrix.protocol.Direction

/**
 * Owns the scanned-QR side of pairing and the explicit [PairingState] machine.
 * CRITICAL SAFETY INVARIANT: this manager never auto-connects, never derives an
 * address silently and never sends anything to the watch. It records, parses and
 * exposes state; the user decides.
 */
class PairingManager(context: Context) {

    private val store = QrStore(context)

    /** Last payload handled this session (in-memory). */
    var lastPayload: QrPayload? = null
        private set

    /** Current pairing progress (process-wide). */
    fun state(): PairingState = Companion.state

    /** Called by the scanner UI when the user confirms the capture. */
    fun record(payload: QrPayload) {
        lastPayload = payload
        store.save(payload)
        DiagnosticLog.log(
            Direction.LOCAL, LogEvent.QR,
            "QR captured: ${payload.formats.joinToString(", ") { it.label }} | len=${payload.raw.length} | payload logged verbatim"
        )
        DiagnosticLog.log(Direction.LOCAL, LogEvent.QR, "RAW: ${payload.raw}")
        emit(PairingStateMachine.Event.QR_CAPTURED)
    }

    fun history(): List<QrStore.StoredPayload> = store.all()

    companion object {
        @Volatile var state: PairingState = PairingState.NO_DATA
            private set

        /** Single choke-point for pairing-state transitions (also tests). */
        fun emit(event: PairingStateMachine.Event): PairingState {
            val next = PairingStateMachine.transition(state, event)
            if (next != state) {
                state = next
                DiagnosticLog.info(LogEvent.STATE, "pairing state → $next")
            }
            return state
        }
    }
}
