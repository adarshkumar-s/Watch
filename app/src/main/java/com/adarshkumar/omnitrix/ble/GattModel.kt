package com.adarshkumar.omnitrix.ble

/**
 * Immutable-ish snapshot of a discovered GATT database, used by the explorer UI,
 * the driver and the diagnostic export. Built strictly from what the watch reports.
 */

data class GattDescriptorModel(
    val uuid: String,
    val readable: Boolean,
    var lastReadHex: String? = null,
)

data class GattCharacteristicModel(
    val uuid: String,
    val properties: List<String>,
    val descriptors: List<GattDescriptorModel>,
    var lastReadHex: String? = null,
    var lastReadText: String? = null,
    var notificationsEnabled: Boolean = false,
) {
    val readable: Boolean get() = "READ" in properties
    val notifiable: Boolean get() = "NOTIFY" in properties || "INDICATE" in properties
    val writableAny: Boolean get() = properties.any { it.startsWith("WRITE") }
}

data class GattServiceModel(
    val uuid: String,
    val type: String, // PRIMARY / SECONDARY
    val characteristics: List<GattCharacteristicModel>,
)

data class GattSnapshot(
    val deviceAddress: String?,
    val deviceName: String?,
    val services: List<GattServiceModel>,
    val takenAtEpochMillis: Long,
) {
    fun characteristic(uuid: String): GattCharacteristicModel? =
        services.asSequence().flatMap { it.characteristics.asSequence() }
            .firstOrNull { it.uuid.equals(uuid, ignoreCase = true) }

    fun findService(uuid: String): GattServiceModel? =
        services.firstOrNull { it.uuid.equals(uuid, ignoreCase = true) }

    /** Compact, human-readable dump for the diagnostic export. */
    fun render(): String {
        val sb = StringBuilder()
        for (s in services) {
            sb.append("SERVICE ${s.uuid} (${s.type})")
            GattExplorer.standardServiceName(s.uuid)?.let { sb.append("  <").append(it).append(">") }
            sb.append('\n')
            for (c in s.characteristics) {
                sb.append("  CHAR ${c.uuid}  [${c.properties.joinToString(\",\")}]")
                GattExplorer.standardCharacteristicName(c.uuid)?.let { sb.append("  <").append(it).append(">") }
                c.lastReadHex?.let { sb.append("  value=").append(it) }
                sb.append('\n')
                for (d in c.descriptors) {
                    sb.append("    DESC ${d.uuid}")
                    d.lastReadHex?.let { sb.append("  value=").append(it) }
                    sb.append('\n')
                }
            }
        }
        return sb.toString().trimEnd()
    }
}
