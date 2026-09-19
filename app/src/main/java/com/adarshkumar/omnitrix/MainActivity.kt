package com.adarshkumar.omnitrix

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.regex.Pattern

class MainActivity : ComponentActivity() {
    private lateinit var scanner: BluetoothLeScanner
    private lateinit var status: TextView
    private lateinit var devices: TextView
    private lateinit var preview: PreviewView
    private lateinit var overlay: TextView
    private lateinit var closeScanner: Button
    private val seen = linkedMapOf<String, BluetoothDevice>()
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var gatt: BluetoothGatt? = null
    private var scanningQr = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        devices = findViewById(R.id.devices)
        preview = findViewById(R.id.preview)
        overlay = findViewById(R.id.scanOverlay)
        closeScanner = findViewById(R.id.closeScanner)
        val manager = getSystemService(BluetoothManager::class.java)
        scanner = manager.adapter.bluetoothLeScanner

        findViewById<Button>(R.id.scanButton).setOnClickListener { startScan() }
        findViewById<Button>(R.id.qrButton).setOnClickListener { openQrScanner() }
        closeScanner.setOnClickListener { closeQrScanner() }

        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        }
        permissions += Manifest.permission.CAMERA
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 10)
    }

    private fun startScan() {
        closeQrScanner()
        seen.clear()
        devices.text = ""
        status.text = "Scanning for ColorFit Caliber…"
        scanner.startScan(callback)
        window.decorView.postDelayed({
            scanner.stopScan(callback)
            status.text = if (seen.isEmpty()) "No matching BLE device found" else "Scan complete — select a detected device by scanning its QR"
        }, 10000)
    }

    private fun openQrScanner() {
        scanningQr = true
        preview.visibility = View.VISIBLE
        overlay.visibility = View.VISIBLE
        closeScanner.visibility = View.VISIBLE
        findViewById<Button>(R.id.qrButton).visibility = View.GONE
        status.text = "Scanning watch QR…"
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val previewUseCase = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            val detector = BarcodeScanning.getClient()
            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                val media = imageProxy.image
                if (media == null || !scanningQr) { imageProxy.close(); return@setAnalyzer }
                val image = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
                detector.process(image).addOnSuccessListener { codes ->
                    val value = codes.firstOrNull()?.rawValue ?: return@addOnSuccessListener
                    runOnUiThread { onQr(value) }
                }.addOnCompleteListener { imageProxy.close() }
            }
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, previewUseCase, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun closeQrScanner() {
        scanningQr = false
        preview.visibility = View.GONE
        overlay.visibility = View.GONE
        closeScanner.visibility = View.GONE
        findViewById<Button>(R.id.qrButton).visibility = View.VISIBLE
    }

    private fun onQr(value: String) {
        if (!scanningQr) return
        closeQrScanner()
        findViewById<TextView>(R.id.qrValue).apply {
            text = "PAIRING QR: $value"
            visibility = View.VISIBLE
        }
        status.text = "QR received — looking for the watch over Bluetooth…"
        val mac = extractMac(value)
        if (mac != null) {
            runCatching { connectToDevice(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(mac)) }
                .onFailure { status.text = "QR read, but its device address could not be used: ${it.message}" }
        } else {
            status.text = "QR read. No Bluetooth address was present; starting a short Caliber scan…"
            startScan()
        }
    }

    private fun extractMac(value: String): String? {
        val p = Pattern.compile("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")
        return p.matcher(value).takeIf { it.find() }?.group()
    }

    private fun connectToDevice(device: BluetoothDevice) {
        gatt?.close()
        gatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        status.text = "Connecting to ${runCatching { device.name }.getOrNull() ?: "ColorFit Caliber"}…"
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, statusCode: Int, newState: Int) {
            runOnUiThread {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    status.text = "Connected — discovering watch services…"
                    g.discoverServices()
                } else {
                    status.text = "Watch disconnected (status $statusCode)"
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, statusCode: Int) {
            runOnUiThread {
                if (statusCode != BluetoothGatt.GATT_SUCCESS) {
                    status.text = "Connected, but service discovery failed ($statusCode)"
                    return@runOnUiThread
                }
                val lines = g.services.flatMap { service ->
                    listOf("SERVICE ${service.uuid}") + service.characteristics.map { c -> "  ${c.uuid}  [${c.properties.toString(16)}]" }
                }
                devices.text = lines.joinToString("\n")
                status.text = "Connected — GATT diagnostics ready"
            }
        }
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

    override fun onDestroy() {
        scanningQr = false
        gatt?.close()
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}
