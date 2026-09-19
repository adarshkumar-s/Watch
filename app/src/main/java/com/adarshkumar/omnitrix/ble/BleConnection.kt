package com.adarshkumar.omnitrix.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.adarshkumar.omnitrix.devices.DeviceDriver
import com.adarshkumar.omnitrix.diag.DiagnosticLog
import com.adarshkumar.omnitrix.protocol.ConnectionEvent
import com.adarshkumar.omnitrix.protocol.ConnectionState
import com.adarshkumar.omnitrix.protocol.ConnectionStateMachine
import com.adarshkumar.omnitrix.protocol.Direction
import com.adarshkumar.omnitrix.protocol.HexCodec.toHex
import com.adarshkumar.omnitrix.protocol.PacketDecoder

/**
 * GATT client for the diagnostic milestone.
 *
 * ⚠ SAFETY INVARIANT (Phase 2/8):
 * There is INTENTIONALLY NO characteristic-value write API here. The only mutation this
 * class can ever perform against the watch is a CCCD subscribe/unsubscribe, and only
 * when the user explicitly toggles notifications on a characteristic. Session-init
 * frames, PINGs, ACKs, opcodes — none of that exists in this codebase.
 *
 * All traffic is logged with PHONE→WATCH / WATCH→PHONE direction tags.
 */
@SuppressLint("MissingPermission") // every entry point checks BlePermissions first
class BleConnection(
    private val context: Context,
    private val driver: DeviceDriver,
) {

    interface Listener {
        fun onStateChanged(state: ConnectionState, note: String)
        fun onSnapshot(snapshot: GattSnapshot)
        fun onCharacteristicRead(charUuid: String, bytes: ByteArray)
        fun onNotification(charUuid: String, bytes: ByteArray)
        fun onSubscriptionChanged(charUuid: String, enabled: Boolean)
        fun onRssi(rssi: Int)
    }

    var listener: Listener? = null

    var state: ConnectionState = ConnectionState.IDLE
        private set

    var snapshot: GattSnapshot? = null
        private set

    private var gatt: BluetoothGatt? = null
    private var device: BluetoothDevice? = null
    private val main = Handler(Looper.getMainLooper())

    // ------------------------------------------------------------------ op queue

    private sealed class Op(val tag: String) {
        class ReadCharacteristic(val uuid: String) : Op("READ $uuid")
        class SetNotification(val uuid: String, val enable: Boolean) :
            Op((if (enable) "SUBSCRIBE " else "UNSUBSCRIBE ") + uuid)
        object ReadRssi : Op("READ RSSI")
    }

    private val opQueue = ArrayDeque<Op>()
    private var activeOp: Op? = null
    private var opWatchdog: Runnable? = null

    // ------------------------------------------------------------- public API

    fun connect(target: BluetoothDevice): Boolean {
        if (!BlePermissions.hasConnectPermission(context)) {
            DiagnosticLog.info("GATT", "connect refused: BLUETOOTH_CONNECT missing")
            return false
        }
        dispatch(ConnectionEvent.CONNECT_REQUESTED, "connect requested")
        closeGattOnly()
        device = target
        DeviceRegistry.lastConnectedAddress = runCatching { target.address }.getOrNull()
        val name = runCatching { target.name }.getOrNull() ?: "(no name)"
        DiagnosticLog.info("GATT", "Connecting to $name [${target.address}] (read/discovery mode)")
        gatt = target.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        notifyState("connecting to $name")
        return true
    }

    fun disconnect() {
        dispatch(ConnectionEvent.DISCONNECT_REQUESTED, "user disconnect")
        val g = gatt ?: return
        try {
            g.disconnect()
        } catch (se: SecurityException) {
            DiagnosticLog.info("GATT", "disconnect SecurityException: ${se.message}")
        }
    }

    fun close() {
        opQueue.clear(); cancelWatchdog(); activeOp = null
        closeGattOnly()
        state = ConnectionState.IDLE
    }

    /** True while a GATT link exists. */
    fun isConnected(): Boolean =
        state == ConnectionState.READY || state == ConnectionState.DISCOVERING_SERVICES

    /** Enqueue a READ — only allowed on characteristics that advertise READ. */
    fun readCharacteristic(uuid: String) {
        val g = gatt ?: return
        val characteristic = findCharacteristic(uuid) ?: run {
            DiagnosticLog.info("GATT", "read refused: characteristic $uuid not found")
            return
        }
        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ == 0) {
            DiagnosticLog.info("GATT", "read refused: $uuid has no READ property")
            return
        }
        g.let { enqueue(Op.ReadCharacteristic(uuid)) }
    }

    /** Enqueue a CCCD subscribe/unsubscribe — the ONLY watch-destined write, user-gated. */
    fun setNotifications(uuid: String, enable: Boolean) {
        val characteristic = findCharacteristic(uuid) ?: return
        val props = characteristic.properties
        val supports = props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 ||
            props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
        if (!supports) {
            DiagnosticLog.info("GATT", "subscription refused: $uuid is not notifiable")
            return
        }
        enqueue(Op.SetNotification(uuid, enable))
    }

    fun readRemoteRssi() = enqueue(Op.ReadRssi)

    // --------------------------------------------------------------- internals

    private fun findCharacteristic(uuid: String): BluetoothGattCharacteristic? {
        val g = gatt ?: return null
        val target = runCatching { java.util.UUID.fromString(uuid) }.getOrNull() ?: return null
        for (service in runCatching { g.services }.getOrNull() ?: emptyList()) {
            service.getCharacteristic(target)?.let { return it }
        }
        return null
    }

    private fun enqueue(op: Op) {
        if (!isConnected()) {
            DiagnosticLog.info("GATT", "op dropped (not connected): ${op.tag}")
            return
        }
        opQueue.addLast(op)
        pump()
    }

    private fun pump() {
        if (activeOp != null) return
        val op = opQueue.removeFirstOrNull() ?: return
        val g = gatt ?: return
        activeOp = op
        val started = when (op) {
            is Op.ReadCharacteristic -> {
                val c = findCharacteristic(op.uuid)
                if (c == null) { false } else {
                    DiagnosticLog.tx("GATT", "PHONE → WATCH: read request for ${op.uuid}")
                    @Suppress("DEPRECATION")
                    g.readCharacteristic(c)
                }
            }
            is Op.SetNotification -> {
                val c = findCharacteristic(op.uuid)
                val d = c?.getDescriptor(GattExplorer.CCCD_UUID)
                if (c == null || d == null) {
                    false
                } else {
                    val okLocal = g.setCharacteristicNotification(c, op.enable)
                    if (!okLocal) {
                        false
                    } else {
                        val value = if (op.enable)
                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                        DiagnosticLog.tx(
                            "GATT",
                            "PHONE → WATCH: CCCD ${if (op.enable) "subscribe" else "unsubscribe"} on ${op.uuid} (user-requested)"
                        )
                        if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, value)
                        else {
                            @Suppress("DEPRECATION")
                            d.value = value
                            @Suppress("DEPRECATION")
                            g.writeDescriptor(d)
                        }
                    }
                }
            }
            is Op.ReadRssi -> {
                DiagnosticLog.tx("GATT", "PHONE → WATCH: read remote RSSI")
                g.readRemoteRssi()
            }
        }
        if (!started) {
            DiagnosticLog.info("GATT", "op failed to start: ${op.tag}")
            activeOp = null
            pump()
            return
        }
        armWatchdog(op)
    }

    private fun completeActiveOp() {
        cancelWatchdog()
        activeOp = null
        main.post { pump() }
    }

    private fun armWatchdog(op: Op) {
        cancelWatchdog()
        val r = Runnable {
            DiagnosticLog.info("GATT", "op timed out after 8s: ${op.tag}")
            activeOp = null
            pump()
        }
        opWatchdog = r
        main.postDelayed(r, 8000)
    }

    private fun cancelWatchdog() {
        opWatchdog?.let { main.removeCallbacks(it) }
        opWatchdog = null
    }

    // ---------------------------------------------------------------- callback

    private val callback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    DiagnosticLog.info(
                        "GATT",
                        "GATT connected (status=$status) — starting service discovery"
                    )
                    dispatch(ConnectionEvent.GATT_CONNECTED, "connected")
                    notifyState("connected — discovering services")
                    if (!g.discoverServices()) {
                        DiagnosticLog.info("GATT", "discoverServices() failed to start")
                        dispatch(ConnectionEvent.SERVICES_DISCOVERY_FAILED, "discovery failed to start")
                    } else {
                        ConnectionStateMachine.transition(state, ConnectionEvent.SERVICES_DISCOVERY_STARTED)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    DiagnosticLog.info("GATT", "GATT disconnected (status=$status)")
                    dispatch(ConnectionEvent.GATT_DISCONNECTED, "disconnected (status=$status)")
                    opQueue.clear(); cancelWatchdog(); activeOp = null
                    closeGattOnly()
                    main.post { listener?.onStateChanged(state, "disconnected") }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                DiagnosticLog.info("GATT", "service discovery FAILED (status=$status)")
                dispatch(ConnectionEvent.SERVICES_DISCOVERY_FAILED, "discovery failed ($status)")
                return
            }
            val snap = GattExplorer.snapshot(
                g,
                deviceName = runCatching { g.device.name }.getOrNull(),
                deviceAddress = runCatching { g.device.address }.getOrNull(),
            )
            snapshot = snap
            DeviceRegistry.setSnapshot(snap)
            DeviceRegistry.resetConnectionFacts()
            DiagnosticLog.info("GATT", "Service discovery complete: ${snap.services.size} service(s)")
            for (s in snap.services) {
                val label = GattExplorer.standardServiceName(s.uuid)?.let { " <$it>" } ?: ""
                DiagnosticLog.info("GATT", "Service ${s.uuid}$label (${s.characteristics.size} chars)")
                for (c in s.characteristics) {
                    DiagnosticLog.info("GATT", "  Char ${c.uuid} [${c.properties.joinToString(",")}]")
                }
            }
            driver.matchByGatt(snap)?.let {
                DiagnosticLog.info("DRIVER", "identification: ${it.reason} [${it.confidence}]")
            }
            dispatch(ConnectionEvent.SERVICES_DISCOVERED, "ready")
            main.post {
                listener?.onStateChanged(state, "service discovery complete")
                listener?.onSnapshot(snap)
            }
        }

        // ---- reads (API <33 and >=33 signatures both forward to one handler) ----

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onCharacteristicRead(
            g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int,
        ) {
            @Suppress("DEPRECATION")
            handleRead(characteristic.uuid.toString(), characteristic.value, status)
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt, characteristic: BluetoothGattCharacteristic,
            value: ByteArray, status: Int,
        ) = handleRead(characteristic.uuid.toString(), value, status)

        private fun handleRead(uuid: String, value: ByteArray?, status: Int) {
            val op = activeOp as? Op.ReadCharacteristic
            if (op == null || !op.uuid.equals(uuid, ignoreCase = true)) {
                // duplicate/stale callback (both API signatures) — ignore
                return
            }
            completeActiveOp()
            val bytes = value ?: byteArrayOf()
            val hex = bytes.toHex()
            val interpretation = driver.interpretRead(uuid, bytes)
            DiagnosticLog.rx(
                "GATT",
                "WATCH → PHONE: read response $uuid status=$status value=[$hex]" +
                    (interpretation?.let { " | ${it.entries.joinToString()}" } ?: "")
            )
            interpretation?.forEach { (k, v) -> DeviceRegistry.putDeviceInfo(k, v) }
            updateSnapshot(uuid, bytes)
            main.post { listener?.onCharacteristicRead(uuid, bytes) }
        }

        // ---- notifications ----

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(
            g: BluetoothGatt, characteristic: BluetoothGattCharacteristic,
        ) {
            @Suppress("DEPRECATION")
            handleNotification(characteristic.uuid.toString(), characteristic.value)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray,
        ) = handleNotification(characteristic.uuid.toString(), value)

        private fun handleNotification(uuid: String, value: ByteArray?) {
            val bytes = value ?: return
            DiagnosticLog.rx(
                "NOTIFY",
                "WATCH → PHONE: $uuid → ${driver.interpretNotification(uuid, bytes)} | raw=[${bytes.toHex()}]"
            )
            main.post { listener?.onNotification(uuid, bytes) }
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int,
        ) {
            val op = activeOp as? Op.SetNotification ?: return
            completeActiveOp()
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val charUuid = descriptor.characteristic.uuid.toString()
                if (op.enable) {
                    DiagnosticLog.info("GATT", "Notification enabled on $charUuid")
                } else {
                    DiagnosticLog.info("GATT", "Notification disabled on $charUuid")
                }
                updateSnapshot(charUuid, null, notifyEnabled = op.enable)
                main.post { listener?.onSubscriptionChanged(charUuid, op.enable) }
            } else {
                DiagnosticLog.info("GATT", "CCCD write failed for ${op.uuid} (status=$status)")
            }
        }

        override fun onReadRemoteRssi(g: BluetoothGatt, rssi: Int, status: Int) {
            if (activeOp !is Op.ReadRssi) return
            completeActiveOp()
            DeviceRegistry.lastRssi = rssi
            DiagnosticLog.rx("GATT", "WATCH → PHONE: RSSI = $rssi dBm")
            main.post { listener?.onRssi(rssi) }
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun updateSnapshot(uuid: String, value: ByteArray?, notifyEnabled: Boolean? = null) {
        val current = snapshot ?: return
        val svc = current.services.firstOrNull { s ->
            s.characteristics.any { it.uuid.equals(uuid, ignoreCase = true) }
        } ?: return
        val model = svc.characteristics.first { it.uuid.equals(uuid, ignoreCase = true) }
        if (value != null) {
            model.lastReadHex = value.toHex()
            model.lastReadText = String(value, Charsets.UTF_8).trim(Char(0))
                .takeIf { it.isNotEmpty() && it.all { ch -> ch.isLetterOrDigit() || ch in " .-_:%" } }
        }
        notifyEnabled?.let { model.notificationsEnabled = it }
        DeviceRegistry.setSnapshot(current)
    }

    private fun dispatch(event: ConnectionEvent, note: String) {
        val next = ConnectionStateMachine.transition(state, event)
        if (next != state) {
            DiagnosticLog.log(Direction.LOCAL, "STATE", "${state} → ${next} ($note)")
            state = next
        }
    }

    private fun notifyState(note: String) {
        main.post { listener?.onStateChanged(state, note) }
    }

    private fun closeGattOnly() {
        try {
            gatt?.close()
        } catch (se: SecurityException) {
            DiagnosticLog.info("GATT", "gatt.close SecurityException: ${se.message}")
        }
        gatt = null
    }
}
