package com.adarshkumar.omnitrix.devices

import com.adarshkumar.omnitrix.ble.DiscoveredDevice
import com.adarshkumar.omnitrix.ble.GattSnapshot
import com.adarshkumar.omnitrix.protocol.EvidenceLevel

/**
 * A watch-model driver. Drivers are isolated so future ColorFit models can be added
 * without touching BLE plumbing or UI.
 *
 * A driver may IDENTIFY a device, DESCRIBE well-known standard reads and INTERPRET
 * bytes already received. While the Caliber protocol remains unverified, drivers
 * must NOT build command packets — see [CommandResult.Unsupported].
 */
interface DeviceDriver {

    val modelId: String            // e.g. "noise-colorfit-caliber-2881"
    val displayName: String        // e.g. "Noise ColorFit Caliber 2881"

    data class Match(
        val confidence: EvidenceLevel,  // CONFIRMED only after validated on-device
        val reason: String,
    )

    /** Heuristic identification from advertisements. */
    fun matchByAdvertisement(device: DiscoveredDevice): Match?

    /** Stronger identification once the GATT database is known. */
    fun matchByGatt(snapshot: GattSnapshot): Match?

    /** Interprets bytes READ from a characteristic (read path only). Returns label→value. */
    fun interpretRead(characteristicUuid: String, value: ByteArray): Map<String, String>?

    /** Interprets notified bytes — annotation only, no fabricated meaning. */
    fun interpretNotification(characteristicUuid: String, value: ByteArray): String

    /** Builds a command packet. In diagnostic mode this always returns [CommandResult.Unsupported]. */
    fun buildCommand(capability: Capability, args: Map<String, String> = emptyMap()): CommandResult

    enum class Capability(val label: String) {
        BATTERY("Battery"),
        DEVICE_INFO("Device information"),
        TIME_SYNC("Time synchronization"),
        NOTIFICATIONS("Notifications"),
        INCOMING_CALL("Incoming calls"),
        FIND_WATCH("Find watch"),
        VIBRATION("Vibration"),
        STEPS("Steps"),
        HEART_RATE("Heart rate"),
        SPO2("SpO2"),
        SLEEP("Sleep"),
        ACTIVITY("Activity"),
        WATCH_FACES("Watch faces"),
        MEDIA_CONTROL("Media controls"),
    }

    sealed class CommandResult {
        /** Only ever reachable for CONFIRMED protocol facts; none exist yet for 2881. */
        data class Packet(val bytes: ByteArray, val description: String) : CommandResult()
        data class Unsupported(val reason: String) : CommandResult()
    }
}
