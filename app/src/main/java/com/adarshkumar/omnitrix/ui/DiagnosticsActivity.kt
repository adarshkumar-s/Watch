package com.adarshkumar.omnitrix.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.adarshkumar.omnitrix.R
import com.adarshkumar.omnitrix.ble.AdvertisementParser
import com.adarshkumar.omnitrix.ble.DeviceRegistry
import com.adarshkumar.omnitrix.diagnostics.DiagnosticExporter
import com.adarshkumar.omnitrix.diagnostics.DiagnosticLog
import com.adarshkumar.omnitrix.protocol.HexCodec
import com.google.android.material.button.MaterialButton

/**
 * Diagnostic log screen: the full direction-tagged connection/protocol log with
 * COPY / EXPORT / CLEAR, plus entry points to QR↔BLE correlation and the full
 * diagnostic text export (omnitrix-diagnostic.txt).
 */
class DiagnosticsActivity : ComponentActivity() {

    private lateinit var logView: TextView
    private lateinit var countView: TextView
    private val listener: () -> Unit = { runOnUiThread { render() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostics)

        logView = findViewById(R.id.logText)
        countView = findViewById(R.id.logCount)
        findViewById<TextView>(R.id.devicePanel).text = renderDevicePanel()

        findViewById<MaterialButton>(R.id.logCopy).setOnClickListener {
            copy(DiagnosticLog.render())
            toast("Log copied")
        }
        findViewById<MaterialButton>(R.id.logExport).setOnClickListener {
            startActivity(Intent.createChooser(DiagnosticExporter.shareIntent(this),
                "Export ${DiagnosticExporter.FILE_NAME}"))
        }
        findViewById<MaterialButton>(R.id.logClear).setOnClickListener {
            DiagnosticLog.clear(); render()
        }
        findViewById<MaterialButton>(R.id.logCorrelate).setOnClickListener {
            startActivity(Intent(this, CompareActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.logBack).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        DiagnosticLog.addListener(listener)
        render()
    }

    override fun onPause() {
        DiagnosticLog.removeListener(listener)
        super.onPause()
    }

    private fun render() {
        countView.text = "${DiagnosticLog.size()} entries  •  newest last"
        logView.text = DiagnosticLog.render().ifEmpty { "(no events yet)" }
    }

    /** DEVICE section: advertisement facts + GATT summary (audit point 2). */
    private fun renderDevicePanel(): String {
        val sb = StringBuilder("DEVICE\n")
        val d = DeviceRegistry.lastConnectedAddress?.let { DeviceRegistry.get(it) }
            ?: DeviceRegistry.all().firstOrNull()
        if (d == null) {
            sb.append("  no device recorded yet — run SCAN BLUETOOTH\n")
        } else {
            sb.append("  name: ${d.name ?: "—"}\n")
            sb.append("  address: ${d.address}\n")
            sb.append("  rssi: ${d.rssi} dBm  txPower: ${d.txPower ?: "—"}\n")
            sb.append("  adv services: ${if (d.serviceUuids.isEmpty()) "—" else d.serviceUuids.joinToString()}\n")
            d.manufacturerDataHex.forEach { (k, v) ->
                sb.append("  mfr[0x${k.toString(16).padStart(4, '0')}]: $v\n")
            }
            d.serviceDataHex.forEach { (k, v) -> sb.append("  svcData[$k]: $v\n") }
            d.rawAdvHex?.let { raw ->
                HexCodec.parse(raw)?.let { sb.append(AdvertisementParser.render(it)).append('\n') }
            }
        }
        val snap = DeviceRegistry.lastSnapshot
        if (snap != null) {
            sb.append("GATT: ${snap.services.size} service(s) discovered on ${snap.deviceAddress ?: "—"}\n")
        }
        return sb.toString().trimEnd()
    }

    private fun copy(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("omnitrix-log", text))
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
