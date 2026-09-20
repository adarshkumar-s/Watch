package com.adarshkumar.omnitrix.ui

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.adarshkumar.omnitrix.R
import com.adarshkumar.omnitrix.analysis.QrBleCorrelation
import com.adarshkumar.omnitrix.ble.AdvertisementParser
import com.adarshkumar.omnitrix.ble.DeviceRegistry
import com.adarshkumar.omnitrix.pairing.QrPayloadParser
import com.adarshkumar.omnitrix.pairing.QrStore
import com.adarshkumar.omnitrix.protocol.HexCodec
import com.adarshkumar.omnitrix.protocol.UnverifiedLegacyCatalog
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase-7 correlation screen: places the QR payload next to BLE advertisements and
 * device information, then lists labeled findings describing how (or whether) Noise's
 * pairing process relates the two — strictly from observed data.
 */
class CompareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compare)

        val qrView: TextView = findViewById(R.id.cmpQr)
        val advView: TextView = findViewById(R.id.cmpAdv)
        val devView: TextView = findViewById(R.id.cmpDevice)
        val findingsView: TextView = findViewById(R.id.cmpFindings)

        findViewById<MaterialButton>(R.id.cmpBack).setOnClickListener { finish() }

        // ---- QR DATA ----
        val stored = QrStore(this).latest()
        val parsed = stored?.let { QrPayloadParser.parse(it.raw) }
        qrView.text = if (stored == null) {
            "No QR captured yet — use SCAN WATCH QR first."
        } else {
            buildString {
                appendLine("Scanned: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    .format(Date(stored.scannedAt)))
                appendLine("Formats: " + (parsed?.formats?.joinToString { it.label } ?: "—"))
                appendLine()
                append(stored.raw)
            }
        }

        // ---- BLE ADVERTISEMENT ----
        val devices = DeviceRegistry.all()
        val lastConnected = DeviceRegistry.lastConnectedAddress
        val chosen = devices.firstOrNull { it.address == lastConnected } ?: devices.firstOrNull()
        advView.text = when {
            devices.isEmpty() -> "No scan results yet — run SCAN BLUETOOTH."
            else -> devices.take(6).joinToString("\n\n") { d ->
                buildString {
                    appendLine("${d.name ?: "(unnamed)"}  [${d.address}]")
                    appendLine("rssi=${d.rssi} dBm  txPower=${d.txPower ?: "—"}  connectable=${d.connectable}")
                    if (d.serviceUuids.isNotEmpty())
                        appendLine("adv services: ${d.serviceUuids.joinToString()}")
                    d.manufacturerDataHex.forEach { (k, v) ->
                        appendLine("mfr 0x${k.toString(16).padStart(4, '0')}: $v")
                    }
                    d.serviceDataHex.forEach { (k, v) -> appendLine("svcData $k: $v") }
                    d.rawAdvHex?.let { raw ->
                        appendLine("raw adv bytes: $raw")
                        HexCodec.parse(raw)?.let { parsed ->
                            appendLine(AdvertisementParser.render(parsed))
                        }
                    }
                }.trim()
            }
        }

        // ---- DEVICE INFORMATION (GATT reads so far) ----
        val snap = DeviceRegistry.lastSnapshot
        val info = DeviceRegistry.lastDeviceInfo
        devView.text = buildString {
            appendLine("Connected: ${snap?.deviceName ?: "—"}  [${DeviceRegistry.lastConnectedAddress ?: "—"}]")
            if (info.isEmpty()) appendLine("No characteristics read yet — read Device Information / Battery in CONNECTED WATCH.")
            info.forEach { (k, v) -> appendLine("$k: $v") }
            snap?.let {
                appendLine()
                appendLine("GATT services: ${it.services.size}")
                it.services.forEach { s -> appendLine("  ${s.uuid}") }
                appendLine()
                appendLine("LEGACY UUIDS (UNVERIFIED, never sent):")
                for (lu in UnverifiedLegacyCatalog.uuidAssumptions) {
                    val present = it.findService(lu.uuid) != null ||
                        it.characteristic(lu.uuid) != null
                    appendLine("  ${lu.uuid} (${lu.role}): " +
                        if (present) "PRESENT — still UNVERIFIED as protocol"
                        else "ABSENT — relying on discovered services")
                }
            }
        }.trim()

        // ---- FINDINGS ----
        val observation = QrBleCorrelation.BleObservation(
            address = chosen?.address ?: DeviceRegistry.lastConnectedAddress,
            name = chosen?.name ?: snap?.deviceName,
            manufacturerDataHexList = chosen?.manufacturerDataHex?.values?.toList() ?: emptyList(),
            advertisedServiceUuids = chosen?.serviceUuids ?: emptyList(),
            gattServiceUuids = snap?.services?.map { it.uuid } ?: emptyList(),
        )
        val findings = QrBleCorrelation.correlate(parsed, observation)
        findingsView.text = findings.joinToString("\n\n") { f ->
            val mark = when (f.verdict) {
                QrBleCorrelation.Verdict.MATCH -> "✅ MATCH"
                QrBleCorrelation.Verdict.NO_MATCH -> "❌ NO MATCH"
                QrBleCorrelation.Verdict.NO_DATA -> "▫️ NO DATA"
            }
            "$mark — ${f.label}\n${f.detail}"
        }
    }
}
