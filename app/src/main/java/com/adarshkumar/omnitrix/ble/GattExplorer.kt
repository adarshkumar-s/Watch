package com.adarshkumar.omnitrix.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import java.util.Locale
import java.util.UUID

/**
 * Builds a [GattSnapshot] purely from a connected [BluetoothGatt]'s discovered services
 * and names well-known SIG UUIDs so the diagnostic screens stay human-readable.
 */
object GattExplorer {

    fun snapshot(gatt: BluetoothGatt, deviceName: String?, deviceAddress: String?): GattSnapshot {
        val services = runCatching { gatt.services }.getOrNull() ?: emptyList()
        return GattSnapshot(
            deviceAddress = deviceAddress,
            deviceName = deviceName,
            services = services.map(::toModel),
            takenAtEpochMillis = System.currentTimeMillis(),
        )
    }

    private fun toModel(service: BluetoothGattService): GattServiceModel {
        return GattServiceModel(
            uuid = service.uuid.toString().lowercase(Locale.US),
            type = if (service.type == BluetoothGattService.SERVICE_TYPE_PRIMARY) "PRIMARY" else "SECONDARY",
            characteristics = service.characteristics.map { c ->
                GattCharacteristicModel(
                    uuid = c.uuid.toString().lowercase(Locale.US),
                    properties = propertyNames(c.properties),
                    descriptors = c.descriptors.map { d ->
                        GattDescriptorModel(
                            uuid = d.uuid.toString().lowercase(Locale.US),
                            readable = (d.permissions and
                                android.bluetooth.BluetoothGattDescriptor.PERMISSION_READ) != 0,
                        )
                    },
                )
            },
        )
    }

    fun propertyNames(props: Int): List<String> {
        val out = ArrayList<String>()
        if (props and BluetoothGattCharacteristic.PROPERTY_BROADCAST != 0) out += "BROADCAST"
        if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) out += "READ"
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) out += "WRITE_NO_RESPONSE"
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) out += "WRITE"
        if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) out += "NOTIFY"
        if (props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) out += "INDICATE"
        if (props and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE != 0) out += "SIGNED_WRITE"
        if (props and BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS != 0) out += "EXTENDED_PROPS"
        return out
    }

    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** 16-bit alias of a SIG-based UUID, or null for vendor 128-bit UUIDs. */
    fun shortId(uuid: String): Int? {
        val m = Regex("(?i)^0000([0-9a-f]{4})-0000-1000-8000-00805f9b34fb$").find(uuid)
            ?: return null
        return m.groupValues[1].toInt(16)
    }

    fun standardServiceName(uuid: String): String? = when (shortId(uuid)) {
        0x1800 -> "Generic Access"
        0x1801 -> "Generic Attribute"
        0x1802 -> "Immediate Alert"
        0x1805 -> "Current Time"
        0x180A -> "Device Information"
        0x180D -> "Heart Rate"
        0x180F -> "Battery Service"
        0x1812 -> "Human Interface Device"
        0x1816 -> "Cycling Speed and Cadence"
        0x181A -> "Environmental Sensing"
        0x181C -> "User Data"
        0x1822 -> "Pulse Oximeter"
        0xFEEA -> "Vendor service (LIKELY: Moyoung-family data channel on Da Fit-class watches; UNKNOWN for Caliber 2881)"
        0xFEE7 -> "Vendor service (UNKNOWN)"
        else -> null
    }

    fun standardCharacteristicName(uuid: String): String? = when (shortId(uuid)) {
        0x2A00 -> "Device Name"
        0x2A01 -> "Appearance"
        0x2A04 -> "Peripheral Preferred Connection Parameters"
        0x2A05 -> "Service Changed"
        0x2A19 -> "Battery Level"
        0x2A23 -> "System ID"
        0x2A24 -> "Model Number"
        0x2A25 -> "Serial Number"
        0x2A26 -> "Firmware Revision"
        0x2A27 -> "Hardware Revision"
        0x2A28 -> "Software Revision"
        0x2A29 -> "Manufacturer Name"
        0x2A2A -> "IEEE 11073 Regulatory Cert"
        0x2A37 -> "Heart Rate Measurement"
        0x2A38 -> "Body Sensor Location"
        0x2A39 -> "Heart Rate Control Point"
        0x2A42 -> "Blood Pressure Measurement"
        0x2A5F -> "Pulse Oximetry Continuous Measurement"
        0x2901 -> "Characteristic User Description"
        0x2902 -> "Client Characteristic Configuration"
        0xFEE1 -> "Likely steps characteristic (Moyoung-family hypothesis, UNKNOWN for 2881)"
        0xFEE2 -> "Likely DATA_OUT / phone→watch (Moyoung-family hypothesis, UNKNOWN for 2881)"
        0xFEE3 -> "Likely DATA_IN / notify (Moyoung-family hypothesis, UNKNOWN for 2881)"
        0xFEE5 -> "Unknown vendor characteristic"
        0xFEE6 -> "Unknown vendor characteristic"
        0xFEE7 -> "Unknown vendor characteristic"
        0xFEE8 -> "Unknown vendor characteristic"
        else -> null
    }
}
