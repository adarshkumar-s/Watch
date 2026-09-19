package com.adarshkumar.omnitrix.protocol

/**
 * Pure connection state machine — the authoritative definition of the GATT lifecycle
 * the app implements. Android-free for JVM unit tests.
 *
 *   IDLE → SCANNING → IDLE
 *   IDLE → CONNECTING → DISCOVERING → READY → DISCONNECTING → IDLE
 *                        └ on failure ───────────→ DISCONNECTED
 */
enum class ConnectionState {
    IDLE,
    SCANNING,
    CONNECTING,
    DISCOVERING_SERVICES,
    READY,
    DISCONNECTING,
    DISCONNECTED,
    ERROR,
}

enum class ConnectionEvent {
    SCAN_STARTED,
    SCAN_FINISHED,
    CONNECT_REQUESTED,
    GATT_CONNECTED,
    SERVICES_DISCOVERY_STARTED,
    SERVICES_DISCOVERED,
    SERVICES_DISCOVERY_FAILED,
    GATT_DISCONNECTED,
    DISCONNECT_REQUESTED,
    FATAL,
}

object ConnectionStateMachine {

    fun transition(state: ConnectionState, event: ConnectionEvent): ConnectionState =
        when (event) {
            ConnectionEvent.SCAN_STARTED ->
                if (state in setOf(ConnectionState.IDLE, ConnectionState.DISCONNECTED, ConnectionState.ERROR))
                    ConnectionState.SCANNING else state

            ConnectionEvent.SCAN_FINISHED ->
                if (state == ConnectionState.SCANNING) ConnectionState.IDLE else state

            ConnectionEvent.CONNECT_REQUESTED ->
                when (state) {
                    ConnectionState.IDLE, ConnectionState.SCANNING,
                    ConnectionState.DISCONNECTED, ConnectionState.ERROR -> ConnectionState.CONNECTING
                    else -> state
                }

            ConnectionEvent.GATT_CONNECTED ->
                if (state == ConnectionState.CONNECTING) ConnectionState.DISCOVERING_SERVICES else state

            ConnectionEvent.SERVICES_DISCOVERY_STARTED ->
                if (state == ConnectionState.DISCOVERING_SERVICES) state else state

            ConnectionEvent.SERVICES_DISCOVERED ->
                if (state == ConnectionState.DISCOVERING_SERVICES) ConnectionState.READY else state

            ConnectionEvent.SERVICES_DISCOVERY_FAILED ->
                ConnectionState.DISCONNECTED

            ConnectionEvent.GATT_DISCONNECTED ->
                when (state) {
                    ConnectionState.IDLE, ConnectionState.DISCONNECTED -> ConnectionState.DISCONNECTED
                    else -> ConnectionState.DISCONNECTED // any drop lands here
                }

            ConnectionEvent.DISCONNECT_REQUESTED ->
                when (state) {
                    ConnectionState.CONNECTING, ConnectionState.DISCOVERING_SERVICES,
                    ConnectionState.READY -> ConnectionState.DISCONNECTING
                    else -> state
                }

            ConnectionEvent.FATAL -> ConnectionState.ERROR
        }

    /** States in which the explorer UI should treat the GATT database as accessible. */
    fun isOperational(state: ConnectionState): Boolean = state == ConnectionState.READY
}
