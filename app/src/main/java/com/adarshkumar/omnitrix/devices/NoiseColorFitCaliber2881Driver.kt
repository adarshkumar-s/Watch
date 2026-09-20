package com.adarshkumar.omnitrix.devices

import com.adarshkumar.omnitrix.ble.DiscoveredDevice
import com.adarshkumar.omnitrix.ble.GattExplorer
import com.adarshkumar.omnitrix.ble.GattSnapshot
import com.adarshkumar.omnitrix.protocol.EvidenceLevel
import com.adarshkumar.omnitrix.protocol.ProtocolDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Driver for the Noise ColorFit Caliber (device id 2881, firmware R204.5.8).
 *
 * DIAGNOSTIC-MODE CONTRACT:
 * - Reads and annotates ONLY. No command packets are produced — [buildCommand] always
 *   returns [DeviceDriver.CommandResult.Unsupported] with
 *   "Not yet supported on this firmware."
 * - Moyoung-family associations are [EvidenceLevel.LIKELY] at best, NEVER CONFIRMED
 *   until captured from this exact watch.
 */
object NoiseColorFitCaliber2881Driver : DeviceDriver {

    const val FIRMWARE = "R204.5.8"

    override val modelId = "noise-colorfit-caliber-2881"
    override val displayName = "Noise ColorFit Caliber (2881)"

    private const val UNSUPPORTED_REASON =
        "Not yet supported on this firmware ($FIRMWARE) — protocol UNKNOWN."

    /** SIG UUIDs we know by number. */
    private const val SUFFIX = "0000-1000-8000-00805f9b34fb"
    private const val CHAR_BATTERY_LEVEL = "00002a19-$SUFFIX"
    private const val CHAR_FIRMWARE = "00002a26-$SUFFIX"
    private const val CHAR_HARDWARE = "00002a27-$SUFFIX"
    private const val CHAR_SOFTWARE = "00002a28-$SUFFIX"
    private const val CHAR_MANUFACTURER = "00002a29-$SUFFIX"
    private const val CHAR_MODEL = "00002a24-$SUFFIX"
    private const val CHAR_SERIAL = "00002a25-$SUFFIX"
    private const val CHAR_DEVICE_NAME = "00002a00-$SUFFIX"
    private const val SERVICE_MOYOUNG_HYPOTHESIS = "0000feea-$SUFFIX"

    override fun matchByAdvertisement(device: DiscoveredDevice): DeviceDriver.Match? {
        val name = device.name?.lowercase(Locale.US) ?: return null
        return when {
            "2881" in name -> DeviceDriver.Match(
                EvidenceLevel.LIKELY, "name contains device id \"2881\""
            )
            "caliber" in name -> DeviceDriver.Match(
                EvidenceLevel.LIKELY, "name contains \"Caliber\""
            )
            "noise" in name -> DeviceDriver.Match(
                EvidenceLevel.LIKELY, "Noise-branded advertisement (model unconfirmed)"
            )
            else -> null
        }
    }

    override fun matchByGatt(snapshot: GattSnapshot): DeviceDriver.Match? {
        // Reading manufacturer "MOYOUNG"/"MOYOUNG-V2" here would be CONFIRMED for the
        // protocol family on THIS watch — surfaced via standard Device Information reads.
        val manufacturer = snapshot.characteristic(CHAR_MANUFACTURER)?.lastReadText
        if (manufacturer != null && manufacturer.startsWith("MOYOUNG", ignoreCase = true)) {
            return DeviceDriver.Match(
                EvidenceLevel.CONFIRMED,
                "manufacturer name \"$manufacturer\" confirms the Moyoung protocol family on this device"
            )
        }
        if (snapshot.findService(SERVICE_MOYOUNG_HYPOTHESIS) != null) {
            return DeviceDriver.Match(
                EvidenceLevel.LIKELY,
                "0xFEEA service present (Moyoung-family hypothesis, not yet protocol-confirmed)"
            )
        }
        val nameish = snapshot.characteristic(CHAR_DEVICE_NAME)?.lastReadText
            ?: snapshot.deviceName
        if (nameish != null && ("caliber" in nameish.lowercase(Locale.US) || "2881" in nameish)) {
            return DeviceDriver.Match(
                EvidenceLevel.CONFIRMED,
                "GATT device name \"$nameish\" matches Caliber 2881"
            )
        }
        return null
    }

    override fun interpretRead(characteristicUuid: String, value: ByteArray): Map<String, String>? {
        val uuid = characteristicUuid.lowercase(Locale.US)
        fun text(): String = String(value, StandardCharsets.UTF_8).trim(Char(0))
        return when (uuid) {
            CHAR_BATTERY_LEVEL -> {
                if (value.isEmpty()) null
                else mapOf("Battery" to "${value[0].toInt() and 0xFF} %")
            }
            CHAR_FIRMWARE -> mapOf("Firmware revision" to text())
            CHAR_HARDWARE -> mapOf("Hardware revision" to text())
            CHAR_SOFTWARE -> mapOf("Software revision" to text())
            CHAR_MANUFACTURER -> mapOf("Manufacturer" to text())
            CHAR_MODEL -> mapOf("Model number" to text())
            CHAR_SERIAL -> mapOf("Serial number" to text())
            CHAR_DEVICE_NAME -> mapOf("Device name (GATT)" to text())
            else -> null
        }
    }

    override fun interpretNotification(characteristicUuid: String, value: ByteArray): String {
        val charLabel = GattExplorer.standardCharacteristicName(
            characteristicUuid.lowercase(Locale.US)
        ) ?: "characteristic ${characteristicUuid.uppercase(Locale.US)}"
        // Decode attempt is an annotation only; the raw hex is what matters.
        return "$charLabel → ${ProtocolDecoder.annotate(value)}"
    }

    override fun buildCommand(
        capability: DeviceDriver.Capability,
        args: Map<String, String>,
    ): DeviceDriver.CommandResult {
        // Protocol is UNKNOWN for 2881/R204.5.8 → nothing is emitted, ever.
        return DeviceDriver.CommandResult.Unsupported(
            "${capability.label}: $UNSUPPORTED_REASON"
        )
    }
}
