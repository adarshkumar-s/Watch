package com.adarshkumar.omnitrix

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.adarshkumar.omnitrix.ble.DeviceRegistry
import com.adarshkumar.omnitrix.devices.NoiseColorFitCaliber2881Driver
import com.adarshkumar.omnitrix.ui.BleScanActivity
import com.adarshkumar.omnitrix.ui.DiagnosticsActivity
import com.adarshkumar.omnitrix.ui.GattExplorerActivity
import com.adarshkumar.omnitrix.ui.QrScannerActivity
import com.adarshkumar.omnitrix.ui.WatchLink
import com.google.android.material.button.MaterialButton

/**
 * OMNITRIX dashboard.
 *
 * Diagnostic milestone: the only functional flows are
 *   SCAN WATCH QR / SCAN BLUETOOTH / CONNECTED WATCH / DIAGNOSTICS + SCAN (quick action).
 * Every firmware-dependent action reports exactly what the driver reports:
 * "Not yet supported on this firmware." — never a fake success.
 */
class MainActivity : ComponentActivity() {

    private lateinit var watchStatus: TextView
    private lateinit var watchDevice: TextView
    private lateinit var watchFirmware: TextView
    private lateinit var watchBattery: TextView
    private lateinit var connectionPill: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        watchStatus = findViewById(R.id.watchStatus)
        watchDevice = findViewById(R.id.watchDevice)
        watchFirmware = findViewById(R.id.watchFirmware)
        watchBattery = findViewById(R.id.watchBattery)
        connectionPill = findViewById(R.id.connectionPill)

        findViewById<MaterialButton>(R.id.btnQr).setOnClickListener {
            startActivity(Intent(this, QrScannerActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnBleScan).setOnClickListener {
            startActivity(Intent(this, BleScanActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnConnectedWatch).setOnClickListener {
            if (WatchLink.online) {
                startActivity(Intent(this, GattExplorerActivity::class.java))
            } else {
                toast("No watch connected yet — use SCAN BLUETOOTH first.")
            }
        }
        findViewById<MaterialButton>(R.id.btnDiagnostics).setOnClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }

        // Omnitrix action wheel. Only SCAN is backed by verified capability today.
        findViewById<MaterialButton>(R.id.actScan).setOnClickListener {
            startActivity(Intent(this, BleScanActivity::class.java))
        }
        unsupportedOnClick(R.id.actData, "DATA")
        unsupportedOnClick(R.id.actEnergy, "ENERGY")
        unsupportedOnClick(R.id.actAlert, "ALERT")
        unsupportedOnClick(R.id.actFind, "FIND")
        unsupportedOnClick(R.id.actSettings, "SETTINGS")
    }

    private fun unsupportedOnClick(viewId: Int, label: String) {
        findViewById<MaterialButton>(viewId).setOnClickListener {
            toast("$label: Not yet supported on this firmware (${NoiseColorFitCaliber2881Driver.FIRMWARE}).")
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    @SuppressLint("SetTextI18n")
    private fun refreshStatus() {
        val online = WatchLink.online
        watchStatus.text = if (online) "● ONLINE" else "● OFFLINE"
        watchStatus.setTextColor(
            getColor(if (online) R.color.omni_green else R.color.omni_text_muted)
        )
        connectionPill.text = if (online) "ONLINE" else "DIAGNOSTIC MODE"

        val info = DeviceRegistry.lastDeviceInfo
        watchDevice.text = "Device  •  " + (
            info["Device name (GATT)"]
                ?: DeviceRegistry.lastSnapshot?.deviceName
                ?: WatchLink.deviceLabel
                ?: "—"
            )
        watchFirmware.text = "Firmware  •  " + (
            info["Firmware revision"] ?: info["Software revision"] ?: "— (unread)"
            )
        watchBattery.text = "Battery  •  " + (info["Battery"] ?: "— (unread)")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
