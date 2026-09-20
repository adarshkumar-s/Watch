package com.adarshkumar.omnitrix.ble

import java.util.concurrent.CopyOnWriteArrayList

/** A BLE device as observed by the scanner — raw advertisement facts only. */
data class DiscoveredDevice(
    val address: String,
    var name: String? = null,
    var rssi: Int = -127,
    var txPower: Int? = null,
    var manufacturerDataHex: Map<Int, String> = emptyMap(), // companyId -> hex
    var serviceUuids: List<String> = emptyList(),
    var serviceDataHex: Map<String, String> = emptyMap(),   // service uuid -> hex
    var rawAdvHex: String? = null,                          // raw AD payload (getBytes)
    var lastSeenMillis: Long = 0L,
    var connectable: Boolean = true,
)

/** In-memory session registry: scan results + last GATT snapshot + last device facts. */
object DeviceRegistry {

    private val lock = Any()
    private val devices = LinkedHashMap<String, DiscoveredDevice>()

    var lastSnapshot: GattSnapshot? = null
        private set
    var lastDeviceInfo: MutableMap<String, String> = LinkedHashMap()
    var lastBatteryPercent: Int? = null
    var lastConnectedAddress: String? = null
    var lastRssi: Int? = null

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun addListener(l: () -> Unit) = listeners.add(l)
    fun removeListener(l: () -> Unit) = listeners.remove(l)

    fun upsert(device: DiscoveredDevice) {
        synchronized(lock) {
            val existing = devices[device.address]
            if (existing == null) {
                devices[device.address] = device
            } else {
                existing.name = device.name ?: existing.name
                existing.rssi = device.rssi
                existing.txPower = device.txPower ?: existing.txPower
                existing.manufacturerDataHex = device.manufacturerDataHex
                existing.serviceUuids = device.serviceUuids
                existing.serviceDataHex = device.serviceDataHex
                existing.rawAdvHex = device.rawAdvHex ?: existing.rawAdvHex
                existing.lastSeenMillis = device.lastSeenMillis
                existing.connectable = device.connectable
            }
        }
        notifyChanged()
    }

    fun all(): List<DiscoveredDevice> = synchronized(lock) {
        devices.values.sortedByDescending { it.rssi }
    }

    fun get(address: String): DiscoveredDevice? = synchronized(lock) { devices[address] }

    fun clear() {
        synchronized(lock) { devices.clear() }
        notifyChanged()
    }

    fun setSnapshot(snapshot: GattSnapshot?) {
        lastSnapshot = snapshot
        notifyChanged()
    }

    fun putDeviceInfo(key: String, value: String) {
        lastDeviceInfo[key] = value
        notifyChanged()
    }

    fun resetConnectionFacts() {
        lastDeviceInfo = LinkedHashMap()
        lastBatteryPercent = null
        lastRssi = null
    }

    private fun notifyChanged() = listeners.forEach { runCatching { it.invoke() } }
}
