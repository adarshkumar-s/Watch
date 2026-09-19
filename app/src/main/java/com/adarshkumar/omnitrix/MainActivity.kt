package com.adarshkumar.omnitrix

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat

class MainActivity : ComponentActivity() {
    private lateinit var scanner: BluetoothLeScanner
    private lateinit var status: TextView
    private lateinit var devices: TextView
    private val seen = linkedMapOf<String, BluetoothDevice>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        devices = findViewById(R.id.devices)
        val manager = getSystemService(BluetoothManager::class.java)
        val adapter = manager.adapter
        scanner = adapter.bluetoothLeScanner
        findViewById<Button>(R.id.scanButton).setOnClickListener { startScan() }
        if (Build.VERSION.SDK_INT >= 31) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), 10)
    }

    private fun startScan() {
        seen.clear(); devices.text = ""; status.text = "Scanning for ColorFit Caliber…"
        scanner.startScan(callback)
        window.decorView.postDelayed({ scanner.stopScan(callback); status.text = if (seen.isEmpty()) "No BLE devices found" else "Scan complete — tap a device in the next build to connect" }, 10000)
    }

    private val callback = object : ScanCallback() {
        override fun onScanResult(type: Int, result: ScanResult) {
            val d = result.device
            val name = result.scanRecord?.deviceName ?: runCatching { d.name }.getOrNull() ?: "Unknown BLE device"
            if (name.contains("Caliber", true) || name.contains("Noise", true) || name.contains("2881", true)) {
                seen[d.address] = d
                devices.text = seen.values.joinToString("\n\n") { device ->
                    val n = runCatching { device.name }.getOrNull() ?: "Noise / Caliber"
                    "⌚ $n\n${device.address}"
                }
            }
        }
        override fun onScanFailed(errorCode: Int) { status.text = "BLE scan failed ($errorCode)" }
    }
}
