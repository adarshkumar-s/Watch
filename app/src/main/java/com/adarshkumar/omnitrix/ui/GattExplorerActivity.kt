package com.adarshkumar.omnitrix.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.adarshkumar.omnitrix.R
import com.adarshkumar.omnitrix.ble.BleConnection
import com.adarshkumar.omnitrix.ble.BlePermissions
import com.adarshkumar.omnitrix.ble.DeviceRegistry
import com.adarshkumar.omnitrix.ble.GattCharacteristicModel
import com.adarshkumar.omnitrix.ble.GattExplorer
import com.adarshkumar.omnitrix.ble.GattServiceModel
import com.adarshkumar.omnitrix.ble.GattSnapshot
import com.adarshkumar.omnitrix.devices.FakeCaliberDevice
import com.adarshkumar.omnitrix.devices.NoiseColorFitCaliber2881Driver
import com.adarshkumar.omnitrix.diag.DiagnosticLog
import com.adarshkumar.omnitrix.protocol.ConnectionState
import com.adarshkumar.omnitrix.protocol.HexCodec.toHex
import com.google.android.material.button.MaterialButton

/**
 * GATT explorer: connect to a device, list its services/characteristics/descriptors,
 * perform USER-INITIATED READs and explicit NOTIFY toggles only. No characteristic
 * writes exist here — this is a read/discovery surface.
 */
class GattExplorerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_DEMO = "demo"
    }

    private lateinit var title: TextView
    private lateinit var sub: TextView
    private lateinit var statePill: TextView
    private lateinit var rssiView: TextView
    private lateinit var list: RecyclerView
    private lateinit var adapter: GattTreeAdapter

    private var demo = false
    private var connection: BleConnection? = null
    private var createdLink = false
    private var address: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gatt_explorer)

        title = findViewById(R.id.gattTitle)
        sub = findViewById(R.id.gattSub)
        statePill = findViewById(R.id.gattStatePill)
        rssiView = findViewById(R.id.gattRssi)
        list = findViewById(R.id.gattList)
        list.layoutManager = LinearLayoutManager(this)

        address = intent.getStringExtra(EXTRA_ADDRESS)
        demo = intent.getBooleanExtra(EXTRA_DEMO, false)

        adapter = GattTreeAdapter(
            onRead = { uuid -> onReadClicked(uuid) },
            onToggleNotify = { uuid, enable -> onNotifyToggled(uuid, enable) },
            onRowInfo = { uuid -> toastDescribe(uuid) },
        )
        list.adapter = adapter

        findViewById<MaterialButton>(R.id.gattBack).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.gattReadRssi).setOnClickListener {
            connection?.readRemoteRssi() ?: toast("Not connected")
        }
        findViewById<MaterialButton>(R.id.gattDisconnect).setOnClickListener {
            connection?.disconnect()
        }

        if (demo || address == FakeCaliberDevice.FAKE_ADDRESS) {
            demo = true
            startDemo()
        } else {
            startReal()
        }
    }

    // ---------------------------------------------------------------- demo mode

    private fun startDemo() {
        val snap = FakeCaliberDevice.snapshot()
        DeviceRegistry.setSnapshot(snap)
        NoiseColorFitCaliber2881Driver.matchByGatt(snap)?.let {
            DiagnosticLog.info("DRIVER", "(demo) identification: ${it.reason} [${it.confidence}]")
        }
        title.text = snap.deviceName ?: "Simulated Caliber"
        sub.text = "SIMULATED DEVICE — no radio involved"
        statePill.text = "SIMULATED"
        rssiView.text = "RSSI n/a"
        adapter.submitSnapshot(snap)
        DiagnosticLog.info("GATT", "(demo) simulated GATT snapshot loaded")
    }

    private fun demoRead(uuid: String) {
        val c = FakeCaliberDevice.snapshot().characteristic(uuid) ?: return
        adapter.updateValue(uuid, c.lastReadHex, c.lastReadText)
    }

    private fun demoNotify(uuid: String, enable: Boolean) {
        if (enable) {
            val S = "0000-1000-8000-00805f9b34fb"
            val scripted = FakeCaliberDevice.scriptedNotifications()[
                uuid.lowercase().replace("0000-0000-1000-8000-00805f9b34fb", S)
            ]
            scripted?.let { bytes ->
                DiagnosticLog.rx(
                    "NOTIFY", "(demo) WATCH → PHONE: $uuid → " +
                        NoiseColorFitCaliber2881Driver.interpretNotification(uuid, bytes)
                )
                adapter.updateValue(uuid, bytes.toHex(), null)
            }
        }
        adapter.setNotified(uuid, enable)
    }

    // ---------------------------------------------------------------- real link

    @SuppressLint("MissingPermission")
    private fun startReal() {
        val addr = address
        if (addr == null) {
            title.text = "No device selected"
            return
        }
        if (!BlePermissions.hasConnectPermission(this)) {
            title.text = "BLUETOOTH_CONNECT permission missing"
            return
        }
        // Reuse an existing live link to the same device, else create a new one.
        val existing = WatchLink.connection
        if (existing != null && WatchLink.online && DeviceRegistry.lastConnectedAddress == addr) {
            connection = existing
            existing.listener = explorerListener
            title.text = DeviceRegistry.lastSnapshot?.deviceName
                ?: DeviceRegistry.get(addr)?.name ?: "Watch"
            sub.text = addr
            statePill.text = "CONNECTED"
            adapter.submitSnapshot(DeviceRegistry.lastSnapshot)
            return
        }
        val device = runCatching {
            BlePermissions.adapter(this)?.getRemoteDevice(addr)
        }.getOrNull()
        if (device == null) {
            title.text = "Could not resolve device $addr"
            return
        }
        val conn = BleConnection(this, NoiseColorFitCaliber2881Driver)
        conn.listener = explorerListener
        connection = conn
        createdLink = true
        WatchLink.attach(conn, DeviceRegistry.get(addr)?.name)
        title.text = DeviceRegistry.get(addr)?.name ?: "Watch"
        sub.text = addr
        statePill.text = "CONNECTING"
        conn.connect(device)
    }

    private val explorerListener = object : BleConnection.Listener {
        override fun onStateChanged(state: ConnectionState, note: String) {
            runOnUiThread {
                statePill.text = state.name
            }
        }

        override fun onSnapshot(snapshot: GattSnapshot) {
            runOnUiThread { adapter.submitSnapshot(snapshot) }
        }

        override fun onCharacteristicRead(charUuid: String, bytes: ByteArray) {
            runOnUiThread {
                val interpreted = NoiseColorFitCaliber2881Driver.interpretRead(charUuid, bytes)
                adapter.updateValue(charUuid, bytes.toHex(), interpreted?.values?.joinToString())
            }
        }

        override fun onNotification(charUuid: String, bytes: ByteArray) {
            runOnUiThread { adapter.updateValue(charUuid, bytes.toHex(), null) }
        }

        override fun onSubscriptionChanged(charUuid: String, enabled: Boolean) {
            runOnUiThread { adapter.setNotified(charUuid, enabled) }
        }

        override fun onRssi(rssi: Int) {
            runOnUiThread { rssiView.text = "RSSI $rssi dBm" }
        }
    }

    // ---------------------------------------------------------------- actions

    private fun onReadClicked(uuid: String) {
        if (demo) demoRead(uuid) else connection?.readCharacteristic(uuid)
            ?: toast("Not connected")
    }

    private fun onNotifyToggled(uuid: String, enable: Boolean) {
        if (demo) demoNotify(uuid, enable)
        else connection?.setNotifications(uuid, enable) ?: toast("Not connected")
    }

    private fun toastDescribe(uuid: String) {
        val label = GattExplorer.standardCharacteristicName(uuid)
            ?: GattExplorer.standardServiceName(uuid) ?: "vendor/unknown UUID"
        toast("$uuid\n$label")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        if (!isFinishing || demo) {
            // keep the link when navigating (e.g. going to Diagnostics and back)
        }
        if (isChangingConfigurations.not() && isFinishing && createdLink) {
            // Leaving explorer deliberately → end link.
            WatchLink.detach()
        }
        super.onDestroy()
    }
}

// =====================================================================
// Flattened GATT tree adapter
// =====================================================================

class GattTreeAdapter(
    private val onRead: (String) -> Unit,
    private val onToggleNotify: (String, Boolean) -> Unit,
    private val onRowInfo: (String) -> Unit,
) : RecyclerView.Adapter<GattTreeAdapter.Row>() {

    sealed class RowData {
        data class Service(val model: GattServiceModel) : RowData()
        data class Characteristic(val svcUuid: String, val model: GattCharacteristicModel) : RowData()
    }

    private val rows = ArrayList<RowData>()
    private val values = HashMap<String, Pair<String?, String?>>()   // uuid -> (hex, text)
    private val notified = HashSet<String>()

    fun submitSnapshot(snapshot: GattSnapshot?) {
        rows.clear()
        values.clear()
        if (snapshot != null) {
            for (s in snapshot.services) {
                rows += RowData.Service(s)
                for (c in s.characteristics) {
                    rows += RowData.Characteristic(s.uuid, c)
                    if (c.lastReadHex != null || c.lastReadText != null) {
                        values[c.uuid] = c.lastReadHex to c.lastReadText
                    }
                    if (c.notificationsEnabled) notified += c.uuid
                }
            }
        }
        notifyDataSetChanged()
    }

    fun updateValue(uuid: String, hex: String?, text: String?) {
        values[uuid.lowercase()] = hex to text
        notifyRowsFor(uuid)
    }

    fun setNotified(uuid: String, on: Boolean) {
        if (on) notified += uuid.lowercase() else notified -= uuid.lowercase()
        notifyRowsFor(uuid)
    }

    private fun notifyRowsFor(uuid: String) {
        rows.forEachIndexed { i, r ->
            if (r is RowData.Characteristic && r.model.uuid.equals(uuid, ignoreCase = true)) {
                notifyItemChanged(i)
            }
        }
    }

    class Row(view: View) : RecyclerView.ViewHolder(view) {
        val serviceBlock: View = view.findViewById(R.id.svcBlock)
        val svcUuid: TextView = view.findViewById(R.id.svcUuid)
        val svcLabel: TextView = view.findViewById(R.id.svcLabel)
        val charBlock: View = view.findViewById(R.id.charBlock)
        val charUuid: TextView = view.findViewById(R.id.charUuid)
        val charLabel: TextView = view.findViewById(R.id.charLabel)
        val charProps: TextView = view.findViewById(R.id.charProps)
        val charValue: TextView = view.findViewById(R.id.charValue)
        val readBtn: MaterialButton = view.findViewById(R.id.charRead)
        val notifyBtn: MaterialButton = view.findViewById(R.id.charNotify)
        val writeNote: TextView = view.findViewById(R.id.charWriteNote)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Row {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gatt_row, parent, false)
        return Row(v)
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(h: Row, position: Int) {
        when (val row = rows[position]) {
            is RowData.Service -> {
                h.serviceBlock.visibility = View.VISIBLE
                h.charBlock.visibility = View.GONE
                h.svcUuid.text = "SERVICE  ${row.model.uuid}"
                val label = GattExplorer.standardServiceName(row.model.uuid)
                h.svcLabel.text = label ?: "vendor-specific / unidentified"
                h.serviceBlock.setOnClickListener { onRowInfo(row.model.uuid) }
            }
            is RowData.Characteristic -> {
                h.serviceBlock.visibility = View.GONE
                h.charBlock.visibility = View.VISIBLE
                val c = row.model
                h.charUuid.text = c.uuid
                h.charLabel.text =
                    GattExplorer.standardCharacteristicName(c.uuid) ?: "unidentified characteristic"
                h.charProps.text = c.properties.joinToString("  ")

                val v = values[c.uuid.lowercase()] ?: (c.lastReadHex to c.lastReadText)
                h.charValue.visibility =
                    if (v.first != null || v.second != null) View.VISIBLE else View.GONE
                h.charValue.text = buildString {
                    v.first?.let { append("hex: ").append(it) }
                    v.second?.let { if (isNotEmpty()) append('\n'); append("value: ").append(it) }
                    if (c.uuid.lowercase() in notified) {
                        if (isNotEmpty()) append('\n')
                        append("● listening for notifications")
                    }
                }

                h.readBtn.visibility = if (c.readable) View.VISIBLE else View.GONE
                h.readBtn.setOnClickListener { onRead(c.uuid) }

                h.notifyBtn.visibility = if (c.notifiable) View.VISIBLE else View.GONE
                val on = c.uuid.lowercase() in notified || c.notificationsEnabled
                h.notifyBtn.text = if (on) "NOTIFY OFF" else "NOTIFY ON"
                h.notifyBtn.setOnClickListener { onToggleNotify(c.uuid, !on) }

                h.writeNote.visibility = if (c.writableAny) View.VISIBLE else View.GONE
                h.writeNote.text = "⚠ writable — writes are disabled in diagnostic mode"
                h.charBlock.setOnClickListener { onRowInfo(c.uuid) }
            }
        }
    }
}
