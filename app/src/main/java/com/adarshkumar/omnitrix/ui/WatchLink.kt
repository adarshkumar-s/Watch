package com.adarshkumar.omnitrix.ui

import com.adarshkumar.omnitrix.ble.BleConnection
import com.adarshkumar.omnitrix.protocol.ConnectionState

/**
 * Holds the currently live GATT session across screens. Activities never own cross-
 * screen state directly; the owner (GattExplorerActivity) attaches/detaches here.
 */
object WatchLink {

    var connection: BleConnection? = null
        private set

    var deviceLabel: String? = null
        private set

    /** True when a REAL device is connected and its GATT database is ready. */
    val online: Boolean
        get() = connection?.state == ConnectionState.READY

    fun attach(connection: BleConnection, deviceLabel: String?) {
        connection // detach previous safely
        detach()
        this.connection = connection
        this.deviceLabel = deviceLabel
    }

    fun detach() {
        runCatching { connection?.close() }
        connection = null
        deviceLabel = null
    }
}
