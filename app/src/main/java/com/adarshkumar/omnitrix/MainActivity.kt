package com.adarshkumar.omnitrix

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
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
import java.util.UUID
import java.util.concurrent.Executors
import java.util.regex.Pattern

class MainActivity : ComponentActivity() {
    companion object {
        private val SERVICE_UUID = UUID.fromString("16186f00-0000-1000-8000-00807f9b34fb")
        private val NOTIFY_UUID = UUID.fromString("16186f01-0000-1000-8000-00807f9b34fb")
        private val WRITE_UUID = UUID.fromString("16186f02-0000-1000-8000-00807f9b34fb")
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val PING = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x01, 0x00)
        private val ACK_OK = byteArrayOf(0x00, 0x00, 0x01, 0x01, 0x00, 0x00)
        private val ACK_END = byteArrayOf(0x00, 0x00, 0x01, 0x00, 0x00, 0x00)
    }

    private lateinit var scanner: BluetoothLeScanner
    private lateinit var status: TextView
    private lateinit var devices: TextView
    private lateinit var preview: PreviewView
    private lateinit var overlay: TextView
    private lateinit var closeScanner: Button
    private lateinit var connectionPill: TextView
    private lateinit var scanButton: Button
    private val seen = linkedMapOf<String, BluetoothDevice>()
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private var scanningQr = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        devices = findViewById(R.id.devices)
        preview = findViewById(R.id.preview)
        overlay = findViewById(R.id.scanOverlay)
        closeScanner = findViewById(R.id.closeScanner)
        connectionPill = findViewById(R.id.connectionPill)
        scanButton = findViewById(R.id.scanButton)
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
        devices.text = "Looking for a nearby ColorFit Caliber…"
        status.text = "Scanning nearby devices"
        connectionPill.text = "SEARCHING"
        scanner.startScan(callback)
        window.decorView.postDelayed({
            scanner.stopScan(callback)
            if (seen.isEmpty()) {
                status.text = "No Caliber found — keep the watch nearby and try again"
                connectionPill.text = "NOT FOUND"
                devices.text = "Tip: keep Bluetooth on and the watch within a few metres."
            } else {
                status.text = "Watch found — use its QR to connect"
                connectionPill.text = "FOUND"
            }
        }, 10000)
    }

    private fun openQrScanner() {
        scanningQr = true
        preview.visibility = View.VISIBLE
        overlay.visibility = View.VISIBLE
        closeScanner.visibility = View.VISIBLE
        findViewById<Button>(R.id.qrButton).visibility = View.GONE
        scanButton.visibility = View.GONE
        status.text = "Point the camera at the watch QR"
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
        scanButton.visibility = View.VISIBLE
    }

    private fun onQr(value: String) {
        if (!scanningQr) return
        closeQrScanner()
        findViewById<TextView>(R.id.qrValue).apply {
            text = "PAIRING DATA  •  ${value.take(58)}${if (value.length > 58) "…" else ""}"
            visibility = View.VISIBLE
        }
        status.text = "QR received — connecting securely over Bluetooth…"
        connectionPill.text = "CONNECTING"
        val mac = extractMac(value)
        if (mac != null) {
            runCatching { connectToDevice(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(mac)) }
                .onFailure {
                    status.text = "QR read, but the Bluetooth address could not be used"
                    connectionPill.text = "ERROR"
                }
        } else {
            status.text = "QR read — no Bluetooth address found; scanning instead…"
            startScan()
        }
    }

    private fun extractMac(value: String): String? {
        val p = Pattern.compile("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")
        return p.matcher(value).takeIf { it.find() }?.group()
    }

    private fun connectToDevice(device: BluetoothDevice) {
        gatt?.close()
        writeChar = null
        notifyChar = null
        gatt = if (Build.VERSION.SDK_INT >= 26) {
            device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(this, false, gattCallback)
        }
        status.text = "Connecting to ${runCatching { device.name }.getOrNull() ?: "ColorFit Caliber"}…"
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, statusCode: Int, newState: Int) {
            runOnUiThread {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    connectionPill.text = "CONNECTED"
                    status.text = "Connected — preparing the watch link…"
                    g.discoverServices()
                } else {
                    connectionPill.text = "DISCONNECTED"
                    status.text = "Watch disconnected (status $statusCode)"
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, statusCode: Int) {
            if (statusCode != BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread {
                    connectionPill.text = "ERROR"
                    status.text = "Service discovery failed ($statusCode)"
                }
                return
            }
            val service = g.getService(SERVICE_UUID)
            writeChar = service?.getCharacteristic(WRITE_UUID)
            notifyChar = service?.getCharacteristic(NOTIFY_UUID)
            runOnUiThread {
                devices.text = if (service == null) {
                    "ColorFit service was not found. The connected device may not be a Caliber 2881."
                } else {
                    "ColorFit Caliber 2881\n${service.characteristics.size} BLE characteristics discovered"
                }
            }
            if (service == null || writeChar == null || notifyChar == null) {
                runOnUiThread {
                    connectionPill.text = "UNSUPPORTED"
                    status.text = "Connected, but the expected Caliber protocol was not found"
                }
                return
            }
            enableNotifications(g)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, statusCode: Int) {
            if (descriptor.uuid == CCCD_UUID) {
                runOnUiThread {
                    if (statusCode == BluetoothGatt.GATT_SUCCESS) {
                        connectionPill.text = "READY"
                        status.text = "Connected — watch link ready"
                        devices.text = "ColorFit Caliber 2881\nBLE protocol channel ready\n\n16186F01  •  notifications / ACK\n16186F02  •  command channel"
                        sendProtocolPing()
                    } else {
                        connectionPill.text = "PARTIAL"
                        status.text = "Connected, but notifications could not be enabled ($statusCode)"
                    }
                }
            }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == NOTIFY_UUID) {
                val hex = characteristic.value.joinToString(" ") { "%02X".format(it) }
                runOnUiThread {
                    status.text = "Watch responded — BLE link active"
                    devices.append("\n\nRX  $hex")
                }
            }
        }
    }

    private fun enableNotifications(g: BluetoothGatt) {
        val characteristic = notifyChar ?: return
        g.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CCCD_UUID)
        if (descriptor == null) {
            runOnUiThread {
                connectionPill.text = "READY"
                status.text = "Connected — notification channel ready"
                sendProtocolPing()
            }
            return
        }
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        g.writeDescriptor(descriptor)
    }

    private fun sendProtocolPing() {
        val characteristic = writeChar ?: return
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        characteristic.value = PING
        gatt?.writeCharacteristic(characteristic)
    }

    private fun sendAck(data: ByteArray) {
        val characteristic = notifyChar ?: return
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        characteristic.value = data
        gatt?.writeCharacteristic(characteristic)
    }

    @Suppress("unused")
    private fun sendFrame(packet: ByteArray) {
        val characteristic = writeChar ?: return
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        characteristic.value = PING
        gatt?.writeCharacteristic(characteristic)
        window.decorView.postDelayed({
            characteristic.value = packet
            gatt?.writeCharacteristic(characteristic)
            window.decorView.postDelayed({
                sendAck(ACK_OK)
                window.decorView.postDelayed({ sendAck(ACK_END) }, 30)
            }, 80)
        }, 150)
    }

    private val callback = object : ScanCallback() {
        override fun onScanResult(type: Int, result: ScanResult) {
            val d = result.device
            val name = result.scanRecord?.deviceName ?: runCatching { d.name }.getOrNull() ?: "Unknown BLE device"
            if (name.contains("Caliber", true) || name.contains("Noise", true) || name.contains("2881", true)) {
                seen[d.address] = d
                devices.text = seen.values.joinToString("\n\n") { device ->
                    val n = runCatching { device.name }.getOrNull() ?: "Noise / Caliber"
                    "⌚  $n\n    ${device.address}"
                }
            }
        }
        override fun onScanFailed(errorCode: Int) {
            status.text = "BLE scan failed ($errorCode)"
            connectionPill.text = "ERROR"
        }
    }

    override fun onDestroy() {
        scanningQr = false
        gatt?.close()
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}
