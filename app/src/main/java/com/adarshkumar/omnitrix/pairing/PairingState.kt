package com.adarshkumar.omnitrix.pairing

/**
 * Explicit pairing progress state for the diagnostic flow.
 *
 *   NO_DATA → QR_CAPTURED → BLE_DEVICE_FOUND → GATT_CONNECTED → SERVICES_EXPLORED
 *
 * Transition rules are intentionally boring and pure so they're unit-testable.
 * Importantly: reaching QR_CAPTURED never implies any BLE action — the QR is just data.
 */
enum class PairingState {
    NO_DATA,
    QR_CAPTURED,
    BLE_DEVICE_FOUND,
    GATT_CONNECTED,
    SERVICES_EXPLORED,
}

object PairingStateMachine {

    fun transition(state: PairingState, event: Event): PairingState = when (event) {
        Event.QR_CAPTURED -> PairingState.QR_CAPTURED
        Event.DEVICE_FOUND ->
            if (state == PairingState.NO_DATA) PairingState.BLE_DEVICE_FOUND else state
        Event.GATT_CONNECTED -> PairingState.GATT_CONNECTED
        Event.SERVICES_EXPLORED ->
            if (state == PairingState.GATT_CONNECTED)
                PairingState.SERVICES_EXPLORED else state
        Event.GATT_DISCONNECTED ->
            when (state) {
                PairingState.GATT_CONNECTED, PairingState.SERVICES_EXPLORED ->
                    if (hadQr(state)) PairingState.BLE_DEVICE_FOUND else state
                else -> state
            }
        Event.RESET -> PairingState.NO_DATA
    }

    enum class Event {
        QR_CAPTURED,
        DEVICE_FOUND,
        GATT_CONNECTED,
        SERVICES_EXPLORED,
        GATT_DISCONNECTED,
        RESET,
    }

    /** Whether a QR has been captured at any point (NO_DATA/QR_CAPTURED carry that info). */
    private fun hadQr(state: PairingState): Boolean = state != PairingState.NO_DATA
}
