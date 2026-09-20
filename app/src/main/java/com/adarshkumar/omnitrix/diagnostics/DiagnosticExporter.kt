package com.adarshkumar.omnitrix.diagnostics

import android.content.Context
import android.content.Intent
import android.os.Build
import com.adarshkumar.omnitrix.App
import com.adarshkumar.omnitrix.ble.AdvertisementParser
import com.adarshkumar.omnitrix.ble.DeviceRegistry
import com.adarshkumar.omnitrix.pairing.PairingManager
import com.adarshkumar.omnitrix.pairing.QrStore
import com.adarshkumar.omnitrix.protocol.HexCodec
import com.adarshkumar.omnitrix.protocol.UnverifiedLegacyCatalog

/**
 * Builds `omnitrix-diagnostic.txt` — a complete local diagnostic snapshot.
 * Exported ONLY via the user's explicit Share action; nothing is auto-uploaded.
 */
object DiagnosticExporter {

    const val FILE_NAME = "omnitrix-diagnostic.txt"

    fun build(context: Context): String {
        val sb = StringBuilder()
        sb.appendLine("OMNITRIX DIAGNOSTIC EXPORT")
        sb.appendLine("==========================")
        sb.appendLine("App version: ${App.instance.appVersionName}")
        sb.appendLine("Android: API ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
        sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("Exported: ${java.util.Date()}")
        sb.appendLine("Pairing state: ${PairingManager.state}")
        sb.appendLine()

        sb.appendLine("WATCH")
        sb.appendLine("-----")
        val snap = DeviceRegistry.lastSnapshot
        sb.appendLine("Name: ${snap?.deviceName ?: "—"}")
        sb.appendLine("Address (where permitted): ${DeviceRegistry.lastConnectedAddress ?: "—"}")
        val info = DeviceRegistry.lastDeviceInfo
        sb.appendLine("Firmware: ${info["Firmware revision"] ?: info["Software revision"] ?: "—"}")
        sb.appendLine("Battery: ${info["Battery"] ?: "—"}")
        info.forEach { (k, v) -> sb.appendLine("$k: $v") }
        DeviceRegistry.lastRssi?.let { sb.appendLine("Last RSSI: $it dBm") }
        sb.appendLine()

        sb.appendLine("QR PAYLOADS (verbatim; stored locally only)")
        sb.appendLine("-------------------------------------------")
        val payloads = QrStore(context).all()
        if (payloads.isEmpty()) sb.appendLine("(none captured)")
        payloads.forEachIndexed { i, p ->
            sb.appendLine("#${i + 1} formats=${p.formats.joinToString(",")}")
            sb.appendLine(p.raw)
        }
        sb.appendLine()

        sb.appendLine("BLE ADVERTISEMENTS (scan records)")
        sb.appendLine("---------------------------------")
        val devices = DeviceRegistry.all()
        if (devices.isEmpty()) sb.appendLine("(no scan results this session)")
        for (d in devices) {
            sb.appendLine("${d.address}  rssi=${d.rssi}  name=${d.name ?: "—"}  connectable=${d.connectable}  txPower=${d.txPower ?: "—"}")
            d.serviceUuids.forEach { sb.appendLine("  adv service: $it") }
            d.manufacturerDataHex.forEach { (k, v) ->
                sb.appendLine("  mfr[0x${k.toString(16).padStart(4, '0')}]: $v")
            }
            d.serviceDataHex.forEach { (k, v) -> sb.appendLine("  svcData[$k]: $v") }
            d.rawAdvHex?.let { raw ->
                sb.appendLine("  raw advertisement bytes: $raw")
                HexCodec.parse(raw)?.let { parsed ->
                    AdvertisementParser.render(parsed).lines()
                        .forEach { line -> sb.appendLine("    $line") }
                }
            }
        }
        sb.appendLine()

        sb.appendLine("GATT DATABASE (read/discovery mode — no commands sent)")
        sb.appendLine("------------------------------------------------------")
        sb.appendLine(snap?.render() ?: "(not connected / not discovered)")
        sb.appendLine()

        sb.appendLine("LEGACY ASSUMPTIONS (UNVERIFIED — from the pre-audit code, never sent)")
        sb.appendLine("----------------------------------------------------------------------")
        for (lu in UnverifiedLegacyCatalog.uuidAssumptions) {
            val present = snap?.let {
                it.findService(lu.uuid) != null || it.characteristic(lu.uuid) != null
            }
            sb.appendLine(
                "  ${lu.uuid} (${lu.role}): " +
                    (present?.let { if (it) "PRESENT in discovered GATT" else "ABSENT — app uses discovered services" }
                        ?: "no GATT snapshot yet")
            )
        }
        UnverifiedLegacyCatalog.packetAssumptions.forEach {
            sb.appendLine("  ${it.name} [${it.bytesHex}] — ${it.note}")
        }
        sb.appendLine()

        sb.appendLine("CONNECTION / PROTOCOL LOG")
        sb.appendLine("-------------------------")
        sb.appendLine(DiagnosticLog.render().ifEmpty { "(empty)" })
        sb.appendLine()
        sb.appendLine("NOTE: evidence labels — CONFIRMED=captured on device, LIKELY=public research,")
        sb.appendLine("UNKNOWN=not verified. Contains device identifiers; share only with people you trust.")
        return sb.toString()
    }

    fun shareIntent(context: Context): Intent {
        val text = build(context)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, FILE_NAME)
            putExtra(Intent.EXTRA_TEXT, text)
        }
    }
}
