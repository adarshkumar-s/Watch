package com.adarshkumar.omnitrix.ble

import android.annotation.SuppressLint
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import com.adarshkumar.omnitrix.diagnostics.DiagnosticLog
import com.adarshkumar.omnitrix.diagnostics.LogEvent
import com.adarshkumar.omnitrix.pairing.PairingManager
import com.adarshkumar.omnitrix.pairing.PairingStateMachine
import com.adarshkumar.omnitrix.protocol.HexCodec.toHex
import java.util.Locale

/**
 * Diagnostic BLE scanner.
 *
 * - No name filtering: diagnostic mode surfaces ALL nearby devices and marks
 *   Caliber-likely ones, instead of hiding everything else.
 * - Deduplicates by address, keeping the strongest/latest RSSI record.
 * - Handles: missing permissions, Bluetooth-off, scan failures, timeout.
 */
class BleScanner(private val context: Context) {

    interface Listener {
        fun onDevicesChanged(devices: List<DiscoveredDevice>)
        fun onScanStateChanged(scanning: Boolean)
        fun onScanError(message: String)
    }

    var listener: Listener? = null

    private val handlerThread = HandlerThread("omnitrix-ble-scanner").apply { start() }
    private val handler = Handler(handlerThread.looper)
    private val timeoutRunnable = Runnable { stop(dueToTimeout = true) }

    @Volatile var scanning = false; private set

    var timeoutMillis: Long = 15_000

    private fun leScanner(): BluetoothLeScanner? =
        BlePermissions.adapter(context)?.bluetoothLeScanner

    @SuppressLint("MissingPermission") // guarded by explicit checks below
    fun start(): Boolean {
        if (scanning) return true
        if (!BlePermissions.hasScanPermission(context)) {
            listener?.onScanError("BLE scan permission missing")
            DiagnosticLog.info(LogEvent.ERROR, "scan refused: permission missing")
            return false
        }
        val adapter = BlePermissions.adapter(context)
        if (adapter == null || !adapter.isEnabled) {
            listener?.onScanError("Bluetooth is off")
            DiagnosticLog.info(LogEvent.ERROR, "scan refused: Bluetooth disabled")
            return false
        }
        val scanner = leScanner() ?: run {
            DiagnosticLog.info(LogEvent.ERROR, "BLE scanner unavailable")
            listener?.onScanError("BLE scanner unavailable")
            return false
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        return try {
            scanner.startScan(null, settings, callback)
            scanning = true
            handler.removeCallbacks(timeoutRunnable)
            handler.postDelayed(timeoutRunnable, timeoutMillis)
            DiagnosticLog.info(LogEvent.SCAN_STARTED, "BLE scan started (timeout ${timeoutMillis / 1000}s, no name filter)")
            listener?.onScanStateChanged(true)
            true
        } catch (se: SecurityException) {
            listener?.onScanError("BLE scan permission revoked")
            DiagnosticLog.info(LogEvent.ERROR, "startScan SecurityException: ${se.message}")
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun stop(dueToTimeout: Boolean = false) {
        if (!scanning) return
        scanning = false
        handler.removeCallbacks(timeoutRunnable)
        try {
            leScanner()?.stopScan(callback)
        } catch (se: SecurityException) {
            DiagnosticLog.info(LogEvent.ERROR, "stopScan SecurityException: ${se.message}")
        }
        DiagnosticLog.info(LogEvent.SCAN_STOPPED, if (dueToTimeout) "BLE scan finished (timeout)" else "BLE scan stopped")
        listener?.onScanStateChanged(false)
    }

    fun release() {
        stop()
        handlerThread.quitSafely()
    }

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            for (r in results) handleResult(r)
        }

        private fun handleResult(result: ScanResult) {
            val device = toModel(result) ?: return
            val isNew = DeviceRegistry.get(device.address) == null
            DeviceRegistry.upsert(device)
            if (isNew) {
                DiagnosticLog.info(
                    LogEvent.DEVICE_FOUND,
                    "DEVICE_FOUND ${device.address} name=${device.name ?: "—"} rssi=${device.rssi} dBm"
                )
                PairingManager.emit(PairingStateMachine.Event.DEVICE_FOUND)
            }
            listener?.onDevicesChanged(DeviceRegistry.all())
        }

        override fun onScanFailed(errorCode: Int) {
            val msg = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "scan already started"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "app registration failed"
                SCAN_FAILED_INTERNAL_ERROR -> "internal error"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "LE scanning unsupported"
                else -> "code $errorCode"
            }
            DiagnosticLog.info(LogEvent.ERROR, "BLE scan failed: $msg")
            listener?.onScanError("BLE scan failed: $msg")
        }
    }

    @SuppressLint("MissingPermission")
    private fun toModel(result: ScanResult): DiscoveredDevice? {
        if (!BlePermissions.hasConnectPermission(context)) return null
        val device = result.device ?: return null
        val record = result.scanRecord
        val mfr = HashMap<Int, String>()
        record?.manufacturerSpecificData?.let { msd ->
            for (i in 0 until msd.size()) {
                val company = msd.keyAt(i)
                val bytes = msd.valueAt(i) ?: continue
                mfr[company] = bytes.toHex("").lowercase(Locale.US)
            }
        }
        val serviceData = HashMap<String, String>()
        record?.serviceData?.let { sd ->
            for ((uuid, bytes) in sd) serviceData[uuid.toString()] = bytes.toHex("").lowercase(Locale.US)
        }
        return DiscoveredDevice(
            address = device.address ?: return null,
            name = record?.deviceName ?: runCatching { device.name }.getOrNull(),
            rssi = result.rssi,
            txPower = record?.txPowerLevel?.takeIf { it != Int.MIN_VALUE },
            manufacturerDataHex = mfr,
            serviceUuids = record?.serviceUuids?.map { it.toString() } ?: emptyList(),
            serviceDataHex = serviceData,
            rawAdvHex = record?.bytes?.toHex(""),
            lastSeenMillis = System.currentTimeMillis(),
            connectable = result.isConnectable,
        )
    }

    companion object {
        /** Marker used to label Caliber-likely entries in the scan list. */
        fun deviceHint(name: String?): String {
            val n = name?.lowercase(Locale.US) ?: return "BLE device"
            return when {
                "2881" in n -> "possible Caliber 2881"
                "caliber" in n -> "possible Noise Caliber"
                "noise" in n -> "Noise-branded device"
                else -> "BLE device"
            }
        }
    }
}
