package com.adarshkumar.omnitrix.pairing

import com.adarshkumar.omnitrix.pairing.PairingStateMachine.Event
import org.junit.Assert.assertEquals
import org.junit.Test

class PairingStateMachineTest {

    private fun run(vararg events: Event): PairingState {
        var s = PairingState.NO_DATA
        for (e in events) s = PairingStateMachine.transition(s, e)
        return s
    }

    @Test
    fun `qr capture alone never implies a connection`() {
        assertEquals(PairingState.QR_CAPTURED, run(Event.QR_CAPTURED))
    }

    @Test
    fun `typical diagnostic path`() {
        assertEquals(
            PairingState.SERVICES_EXPLORED,
            run(Event.QR_CAPTURED, Event.DEVICE_FOUND, Event.GATT_CONNECTED, Event.SERVICES_EXPLORED)
        )
    }

    @Test
    fun `device found without qr is tracked from no data`() {
        assertEquals(PairingState.BLE_DEVICE_FOUND, run(Event.DEVICE_FOUND))
    }

    @Test
    fun `disconnect after exploration falls back to device found when qr existed`() {
        assertEquals(
            PairingState.BLE_DEVICE_FOUND,
            run(Event.QR_CAPTURED, Event.GATT_CONNECTED, Event.SERVICES_EXPLORED, Event.GATT_DISCONNECTED)
        )
    }

    @Test
    fun `disconnect from unknown start is a no op`() {
        assertEquals(PairingState.NO_DATA, run(Event.GATT_DISCONNECTED))
    }

    @Test
    fun `services explored cannot be claimed before connection`() {
        assertEquals(PairingState.NO_DATA, run(Event.SERVICES_EXPLORED))
    }

    @Test
    fun `reset clears`() {
        assertEquals(PairingState.NO_DATA, run(Event.QR_CAPTURED, Event.RESET))
    }
}
