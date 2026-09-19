package com.adarshkumar.omnitrix.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.adarshkumar.omnitrix.R
import com.adarshkumar.omnitrix.ble.BlePermissions
import com.adarshkumar.omnitrix.ble.BleScanner
import com.adarshkumar.omnitrix.ble.DeviceRegistry
import com.adarshkumar.omnitrix.ble.DiscoveredDevice
import com.adarshkumar.omnitrix.devices.FakeCaliberDevice
import com.adarshkumar.omnitrix.devices.NoiseColorFitCaliber2881Driver
import com.google.android.material.button.MaterialButton

/**
 * Diagnostic BLE scan: shows ALL nearby devices (no Caliber-only filter), with RSSI,
 * name and address, and a [CONNECT] action per device. Handles permission, Bluetooth-off
 * and timeout flows explicitly.
 */
class BleScanActivity : ComponentActivity() {

    private lateinit var status: TextView
    private lateinit var permissionBlock: View
    private lateinit var permissionHint: TextView
    private lateinit var list: RecyclerView
    private lateinit var adapter: ScanDeviceAdapter

    private val scanner: BleScanner by lazy { BleScanner(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ble_scan)

        status = findViewById(R.id.scanStatus)
        permissionBlock = findViewById(R.id.scanPermissionBlock)
        permissionHint = findViewById(R.id.scanPermissionHint)
        list = findViewById(R.id.scanList)

        adapter = ScanDeviceAdapter { device ->
            DeviceRegistry.upsert(device)
            startActivity(
                Intent(this, GattExplorerActivity::class.java)
                    .putExtra(GattExplorerActivity.EXTRA_ADDRESS, device.address)
            )
        }
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        findViewById<MaterialButton>(R.id.scanToggle).setOnClickListener { toggleScan() }
        findViewById<MaterialButton>(R.id.scanGrant).setOnClickListener { BlePermissions.request(this) }
        findViewById<MaterialButton>(R.id.scanEnableBt).setOnClickListener {
            runCatching {
                startActivityForResult(
                    BlePermissions.enableBluetoothIntent(), BlePermissions.REQUEST_ENABLE_BT
                )
            }.onFailure { toast("Could not open Bluetooth settings") }
        }
        findViewById<MaterialButton>(R.id.scanClear).setOnClickListener {
            DeviceRegistry.clear(); adapter.submit(DeviceRegistry.all())
        }
        findViewById<MaterialButton>(R.id.scanDemo).setOnClickListener {
            DeviceRegistry.upsert(FakeCaliberDevice.advertisement())
            toast("Simulated Caliber added to the list")
            adapter.submit(DeviceRegistry.all())
        }
        findViewById<MaterialButton>(R.id.scanBack).setOnClickListener { finish() }

        scanner.listener = object : BleScanner.Listener {
            override fun onDevicesChanged(devices: List<DiscoveredDevice>) {
                runOnUiThread {
                    adapter.submit(devices)
                    if (scanner.scanning) status.text =
                        "Scanning… ${devices.size} device(s) found"
                }
            }

            override fun onScanStateChanged(scanning: Boolean) {
                runOnUiThread { updateScanUi(scanning) }
            }

            override fun onScanError(message: String) {
                runOnUiThread { status.text = message; updateGate() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.submit(DeviceRegistry.all())
        updateGate()
    }

    private fun updateGate() {
        val missingBt = BlePermissions.missing(this)
        val btOff = !BlePermissions.isBluetoothEnabled(this)
        permissionBlock.visibility = if (missingBt.isNotEmpty() || btOff) View.VISIBLE else View.GONE
        val missingCamera = missingBt.contains(Manifest.permission.CAMERA)
        permissionHint.text = buildString {
            if (missingBt.isNotEmpty()) {
                append("Missing permission(s): ")
                append(missingBt.joinToString { it.substringAfterLast('.') })
            }
            if (btOff) {
                if (isNotEmpty()) append('\n')
                append("Bluetooth is turned off.")
            }
        }
        findViewById<MaterialButton>(R.id.scanGrant).visibility =
            if (missingBt.isNotEmpty()) View.VISIBLE else View.GONE
        findViewById<MaterialButton>(R.id.scanEnableBt).visibility =
            if (btOff && missingBt.isEmpty()) View.VISIBLE else View.GONE
        if (missingCamera) { /* camera handled on QR screen */ }

        if (missingBt.isEmpty() && !btOff && !scanner.scanning &&
            DeviceRegistry.all().isEmpty()
        ) {
            startScan() // auto-start on first open for diagnostic convenience
        }
    }

    private fun toggleScan() {
        if (scanner.scanning) scanner.stop() else startScan()
    }

    private fun startScan() {
        if (!BlePermissions.allGranted(this)) {
            BlePermissions.request(this); return
        }
        if (!BlePermissions.isBluetoothEnabled(this)) {
            toast("Turn Bluetooth on first"); return
        }
        status.text = "Scanning… all nearby BLE devices are shown (diagnostic mode)"
        scanner.start()
        updateScanUi(scanner.scanning)
    }

    private fun updateScanUi(scanning: Boolean) {
        findViewById<MaterialButton>(R.id.scanToggle).text =
            if (scanning) "STOP SCAN" else "SCAN"
        if (!scanning) {
            val count = DeviceRegistry.all().size
            status.text = if (count == 0)
                "Scan finished — nothing found. Keep the watch (and its screen) nearby."
            else
                "Scan finished — $count device(s). RSSI updates while scanning."
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == BlePermissions.REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) startScan()
            updateGate()
        }
    }

    @Deprecated("framework callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == BlePermissions.REQUEST_ENABLE_BT) updateGate()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        scanner.release()
        super.onDestroy()
    }
}

/** Simple RecyclerView adapter for scan rows. */
class ScanDeviceAdapter(
    private val onConnect: (DiscoveredDevice) -> Unit,
) : RecyclerView.Adapter<ScanDeviceAdapter.Row>() {

    private val items = ArrayList<DiscoveredDevice>()

    fun submit(devices: List<DiscoveredDevice>) {
        items.clear()
        items.addAll(devices)
        notifyDataSetChanged()
    }

    class Row(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.rowName)
        val address: TextView = view.findViewById(R.id.rowAddress)
        val rssi: TextView = view.findViewById(R.id.rowRssi)
        val hint: TextView = view.findViewById(R.id.rowHint)
        val connect: MaterialButton = view.findViewById(R.id.rowConnect)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Row {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scan_device, parent, false)
        return Row(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Row, position: Int) {
        val d = items[position]
        val ctx = holder.itemView.context
        holder.name.text = d.name ?: "(unnamed BLE device)"
        holder.address.text = d.address
        holder.rssi.text = "${d.rssi} dBm"
        holder.hint.text = NoiseColorFitCaliber2881Driver.matchByAdvertisement(d)?.let {
            "★ ${it.reason} [${it.confidence}]"
        } ?: BleScanner.deviceHint(d.name)
        holder.connect.isEnabled = d.connectable
        holder.connect.alpha = if (d.connectable) 1f else 0.4f
        holder.connect.setOnClickListener { onConnect(d) }
        holder.itemView.setOnClickListener {
            Toast.makeText(ctx, describe(d), Toast.LENGTH_LONG).show()
        }
    }

    private fun describe(d: DiscoveredDevice): String {
        val sb = StringBuilder()
        sb.append("Services: ")
        sb.append(if (d.serviceUuids.isEmpty()) "none advertised" else
            d.serviceUuids.joinToString { it.take(8) + "…" })
        if (d.manufacturerDataHex.isNotEmpty()) {
            sb.append("\nManufacturer data: ")
            sb.append(d.manufacturerDataHex.entries.joinToString {
                "0x${it.key.toString(16)}=${it.value}"
            })
        }
        return sb.toString()
    }
}
