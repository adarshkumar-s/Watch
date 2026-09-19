package com.adarshkumar.omnitrix.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionStateMachineTest {

    private fun run(seq: List<ConnectionEvent>): ConnectionState {
        var s = ConnectionState.IDLE
        for (e in seq) s = ConnectionStateMachine.transition(s, e)
        return s
    }

    @Test
    fun `happy path reaches READY`() {
        val s = run(
            listOf(
                ConnectionEvent.SCAN_STARTED, ConnectionEvent.SCAN_FINISHED,
                ConnectionEvent.CONNECT_REQUESTED, ConnectionEvent.GATT_CONNECTED,
                ConnectionEvent.SERVICES_DISCOVERED,
            )
        )
        assertEquals(ConnectionState.READY, s)
        assertTrue(ConnectionStateMachine.isOperational(s))
    }

    @Test
    fun `cannot discover before connecting`() {
        val s = run(listOf(ConnectionEvent.GATT_CONNECTED))
        assertEquals(ConnectionState.IDLE, s)
    }

    @Test
    fun `discovery failure lands in DISCONNECTED`() {
        val s = run(
            listOf(
                ConnectionEvent.CONNECT_REQUESTED, ConnectionEvent.GATT_CONNECTED,
                ConnectionEvent.SERVICES_DISCOVERY_FAILED,
            )
        )
        assertEquals(ConnectionState.DISCONNECTED, s)
    }

    @Test
    fun `disconnect from anywhere lands in DISCONNECTED`() {
        val s = run(
            listOf(
                ConnectionEvent.CONNECT_REQUESTED, ConnectionEvent.GATT_CONNECTED,
                ConnectionEvent.SERVICES_DISCOVERED, ConnectionEvent.GATT_DISCONNECTED,
            )
        )
        assertEquals(ConnectionState.DISCONNECTED, s)
    }

    @Test
    fun `user initiated disconnect transitions through DISCONNECTING`() {
        var s = run(
            listOf(
                ConnectionEvent.CONNECT_REQUESTED, ConnectionEvent.GATT_CONNECTED,
                ConnectionEvent.SERVICES_DISCOVERED,
            )
        )
        s = ConnectionStateMachine.transition(s, ConnectionEvent.DISCONNECT_REQUESTED)
        assertEquals(ConnectionState.DISCONNECTING, s)
        s = ConnectionStateMachine.transition(s, ConnectionEvent.GATT_DISCONNECTED)
        assertEquals(ConnectionState.DISCONNECTED, s)
    }

    @Test
    fun `scan while connected is ignored`() {
        val s = run(
            listOf(
                ConnectionEvent.CONNECT_REQUESTED, ConnectionEvent.SCAN_STARTED,
            )
        )
        assertEquals(ConnectionState.CONNECTING, s)
    }

    @Test
    fun `fatal anywhere lands in ERROR and stays until reconnect request`() {
        var s = run(listOf(ConnectionEvent.FATAL))
        assertEquals(ConnectionState.ERROR, s)
        assertFalse(ConnectionStateMachine.isOperational(s))
        s = ConnectionStateMachine.transition(s, ConnectionEvent.CONNECT_REQUESTED)
        assertEquals(ConnectionState.CONNECTING, s)
    }
}
