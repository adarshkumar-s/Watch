package com.adarshkumar.omnitrix

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.os.*
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.camera.core.*
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
        private val PING = byteArrayOf(0, 0, 0, 0, 1, 0)
        private val ACK_OK = byteArrayOf(0, 0, 1, 1, 0, 0)
        private val ACK_END = byteArrayOf(0, 0, 1, 0, 0, 0)
    }

    private lateinit var scanner: BluetoothLeScanner
    private lateinit var status: TextView
    private lateinit var devices: TextView
    private lateinit var preview: PreviewView
    private lateinit var overlay: TextView
    private lateinit var closeScanner: Button
    private lateinit var connectionPill: TextView
    private lateinit var scanButton: Button
    private lateinit var findWatchButton: Button
    private lateinit var syncButton: Button
    private val seen = linkedMapOf<String, BluetoothDevice>()
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private var scanningQr = false
    private var connectedAddress: String? = null
    private var sessionReady = false
    private var protocolBusy = false

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
        findWatchButton = findViewById(R.id.findWatchButton)
        syncButton = findViewById(R.id.syncButton)
        scanner = getSystemService(BluetoothManager::class.java).adapter.bluetoothLeScanner
        findViewById<Button>(R.id.scanButton).setOnClickListener { startScan() }
        findViewById<Button>(R.id.qrButton).setOnClickListener { openQrScanner() }
        findWatchButton.setOnClickListener { findWatch() }
        syncButton.setOnClickListener { initializeSession() }
        closeScanner.setOnClickListener { closeQrScanner() }
        setControlsEnabled(false)
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) { permissions += Manifest.permission.BLUETOOTH_SCAN; permissions += Manifest.permission.BLUETOOTH_CONNECT }
        permissions += Manifest.permission.CAMERA
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 10)
    }

    private fun setControlsEnabled(enabled: Boolean) {
        findWatchButton.isEnabled = enabled; syncButton.isEnabled = enabled
        findWatchButton.alpha = if (enabled) 1f else .45f; syncButton.alpha = if (enabled) 1f else .45f
    }

    private fun startScan() {
        closeQrScanner(); seen.clear(); devices.text = "Looking for a nearby ColorFit Caliber…"; status.text = "Scanning nearby devices"; connectionPill.text = "SEARCHING"
        scanner.startScan(callback)
        window.decorView.postDelayed({ scanner.stopScan(callback); if (seen.isEmpty()) { status.text = "No Caliber found — keep the watch nearby and try again"; connectionPill.text = "NOT FOUND"; devices.text = "Tip: keep Bluetooth on and the watch within a few metres." } else { status.text = "Watch found — use its QR to connect"; connectionPill.text = "FOUND" } }, 10000)
    }

    private fun openQrScanner() {
        scanningQr = true; preview.visibility = View.VISIBLE; overlay.visibility = View.VISIBLE; closeScanner.visibility = View.VISIBLE
        findViewById<Button>(R.id.qrButton).visibility = View.GONE; scanButton.visibility = View.GONE; status.text = "Point the camera at the watch QR"
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get(); val p = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build(); val detector = BarcodeScanning.getClient()
            analysis.setAnalyzer(cameraExecutor) { proxy -> val media = proxy.image; if (media == null || !scanningQr) { proxy.close(); return@setAnalyzer }; detector.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)).addOnSuccessListener { codes -> codes.firstOrNull()?.rawValue?.let { runOnUiThread { onQr(it) } } }.addOnCompleteListener { proxy.close() } }
            provider.unbindAll(); provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, p, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun closeQrScanner() { scanningQr = false; preview.visibility = View.GONE; overlay.visibility = View.GONE; closeScanner.visibility = View.GONE; findViewById<Button>(R.id.qrButton).visibility = View.VISIBLE; scanButton.visibility = View.VISIBLE }

    private fun onQr(value: String) {
        if (!scanningQr) return; closeQrScanner(); findViewById<TextView>(R.id.qrValue).apply { text = "PAIRING DATA  •  ${value.take(58)}${if (value.length > 58) "…" else ""}"; visibility = View.VISIBLE }
        status.text = "QR received — connecting securely over Bluetooth…"; connectionPill.text = "CONNECTING"
        val mac = extractMac(value)
        if (mac != null) runCatching { connectToDevice(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(mac)) }.onFailure { status.text = "QR read, but the Bluetooth address could not be used"; connectionPill.text = "ERROR" } else { status.text = "QR read — no Bluetooth address found; scanning instead…"; startScan() }
    }
    private fun extractMac(value: String): String? { val p = Pattern.compile("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}"); return p.matcher(value).takeIf { it.find() }?.group() }

    private fun connectToDevice(device: BluetoothDevice) {
        gatt?.close(); writeChar = null; notifyChar = null; sessionReady = false; connectedAddress = device.address; setControlsEnabled(false)
        gatt = if (Build.VERSION.SDK_INT >= 26) device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE) else device.connectGatt(this, false, gattCallback)
        status.text = "Connecting to ${runCatching { device.name }.getOrNull() ?: "ColorFit Caliber"}…"
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, statusCode: Int, newState: Int) { runOnUiThread { if (newState == BluetoothGatt.STATE_CONNECTED) { connectionPill.text = "CONNECTED"; status.text = "Connected — preparing the watch link…"; g.discoverServices() } else { sessionReady = false; setControlsEnabled(false); connectionPill.text = "DISCONNECTED"; status.text = "Watch disconnected (status $statusCode)" } } }
        override fun onServicesDiscovered(g: BluetoothGatt, statusCode: Int) {
            if (statusCode != BluetoothGatt.GATT_SUCCESS) { runOnUiThread { connectionPill.text = "ERROR"; status.text = "Service discovery failed ($statusCode)" }; return }
            val service = g.getService(SERVICE_UUID); writeChar = service?.getCharacteristic(WRITE_UUID); notifyChar = service?.getCharacteristic(NOTIFY_UUID)
            runOnUiThread { devices.text = if (service == null) "ColorFit service was not found." else "ColorFit Caliber 2881\n${service.characteristics.size} BLE characteristics discovered" }
            if (service == null || writeChar == null || notifyChar == null) { runOnUiThread { connectionPill.text = "UNSUPPORTED"; status.text = "Expected Caliber protocol was not found" }; return }
            enableNotifications(g)
        }
        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, statusCode: Int) {
            if (descriptor.uuid == CCCD_UUID && statusCode == BluetoothGatt.GATT_SUCCESS) { runOnUiThread { connectionPill.text = "READY"; status.text = "Connected — initializing Caliber protocol…"; setControlsEnabled(true); devices.text = "ColorFit Caliber 2881\nBLE channel ready\n\n16186F01  •  notifications / ACK\n16186F02  •  command channel" }; initializeSession() }
        }
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) { if (c.uuid == NOTIFY_UUID) { val hex = c.value.joinToString(" ") { "%02X".format(it) }; runOnUiThread { devices.append("\nRX  $hex") } } }
    }

    private fun enableNotifications(g: BluetoothGatt) { val c = notifyChar ?: return; g.setCharacteristicNotification(c, true); val d = c.getDescriptor(CCCD_UUID); if (d == null) { initializeSession(); return }; d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; g.writeDescriptor(d) }
    private fun varint(value: Int): ByteArray { var n = value; val out = ArrayList<Byte>(); while (true) { val b = n and 127; n = n ushr 7; out.add((if (n != 0) b or 128 else b).toByte()); if (n == 0) return out.toByteArray() } }
    private fun frame(opcode: Int, payload: ByteArray = byteArrayOf()): ByteArray = byteArrayOf(1, 0, 8) + varint(opcode) + payload
    private fun pbVarint(field: Int, value: Int) = varint((field shl 3)) + varint(value)
    private fun pbBytes(field: Int, data: ByteArray) = varint((field shl 3) or 2) + varint(data.size) + data
    private fun pbString(field: Int, value: String) = pbBytes(field, value.toByteArray())

    private fun initializeSession() {
        if (protocolBusy) return; val id = connectedAddress?.replace(":", "")?.lowercase() ?: "omnitrix"; val inner = pbVarint(1, 0) + pbString(2, id) + pbVarint(3, 0); val registration = pbBytes(3, pbBytes(3, inner))
        val frames = listOf(frame(0, byteArrayOf(0x9A.toByte(), 6, 2, 0x20, 0)), frame(0x10), frame(0x11, byteArrayOf(0x1A, 4, 0x12, 2, 8, 1)), frame(0x12, registration), frame(0x13, pbBytes(3, pbString(6, id))), frame(0x21), frame(0x20), frame(0x22))
        runOnUiThread { connectionPill.text = "SYNCING"; status.text = "Initializing watch session…" }; sendFrames(frames, 0)
    }

    private fun sendFrames(frames: List<ByteArray>, index: Int) { if (index >= frames.size) { protocolBusy = false; sessionReady = true; runOnUiThread { connectionPill.text = "ONLINE"; status.text = "Watch connected — controls ready"; setControlsEnabled(true) }; return }; protocolBusy = true; sendFrame(frames[index]) { window.decorView.postDelayed({ sendFrames(frames, index + 1) }, 120) } }

    private fun sendFrame(packet: ByteArray, done: (() -> Unit)? = null) {
        val command = writeChar ?: run { done?.invoke(); return }; command.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE; command.value = PING; gatt?.writeCharacteristic(command)
        window.decorView.postDelayed({ command.value = packet; gatt?.writeCharacteristic(command); window.decorView.postDelayed({ val ack = notifyChar; if (ack != null) { ack.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE; ack.value = ACK_OK; gatt?.writeCharacteristic(ack); window.decorView.postDelayed({ ack.value = ACK_END; gatt?.writeCharacteristic(ack); window.decorView.postDelayed({ done?.invoke() }, 30) }, 30) } else done?.invoke() }, 80) }, 150)
    }

    private fun findWatch() { if (!sessionReady) { status.text = "Finish watch setup first"; return }; status.text = "Sending find-watch command…"; sendFrame(frame(0xA1)) { runOnUiThread { status.text = "Find-watch command sent — check your watch" } } }

    private val callback = object : ScanCallback() { override fun onScanResult(type: Int, result: ScanResult) { val d = result.device; val name = result.scanRecord?.deviceName ?: runCatching { d.name }.getOrNull() ?: "Unknown BLE device"; if (name.contains("Caliber", true) || name.contains("Noise", true) || name.contains("2881", true)) { seen[d.address] = d; devices.text = seen.values.joinToString("\n\n") { device -> "⌚  ${runCatching { device.name }.getOrNull() ?: "Noise / Caliber"}\n    ${device.address}" } } }; override fun onScanFailed(errorCode: Int) { status.text = "BLE scan failed ($errorCode)"; connectionPill.text = "ERROR" } }
    override fun onDestroy() { scanningQr = false; gatt?.close(); cameraExecutor.shutdown(); super.onDestroy() }
}
