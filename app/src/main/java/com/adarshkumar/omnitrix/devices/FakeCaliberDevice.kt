package com.adarshkumar.omnitrix.devices

import com.adarshkumar.omnitrix.ble.DiscoveredDevice
import com.adarshkumar.omnitrix.ble.GattCharacteristicModel
import com.adarshkumar.omnitrix.ble.GattDescriptorModel
import com.adarshkumar.omnitrix.ble.GattServiceModel
import com.adarshkumar.omnitrix.ble.GattSnapshot

/**
 * A simulated Caliber 2881, marked with the Moyoung-family hypothesis, used to
 * exercise the UI and unit tests without physical hardware. Clearly labeled FAKE.
 */
object FakeCaliberDevice {

    const val FAKE_ADDRESS = "FA:KE:C4:28:81:00"

    private const val S = "0000-1000-8000-00805f9b34fb"

    fun advertisement(): DiscoveredDevice = DiscoveredDevice(
        address = FAKE_ADDRESS,
        name = "ColorFit Caliber 2881 (SIMULATED)",
        rssi = -52,
        txPower = -4,
        manufacturerDataHex = mapOf(0x0A0A to "6d6f796f756e672d7632"), // "moyoung-v2"
        serviceUuids = listOf("0000feea-$S"),
        serviceDataHex = emptyMap(),
        // AD: Flags, Complete Name, mfr(0x0A0A)="moyoung-v2", Complete UUID16 list (0xFEEA)
        rawAdvHex = "020106" + "1109" + "436F6C6F724669742043616C69626572" +
            "0CFF0A0A6D6F796F756E672D7632" + "0303EAFE",
        lastSeenMillis = System.currentTimeMillis(),
        connectable = true,
    )

    fun snapshot(): GattSnapshot {
        val cccd = GattDescriptorModel("00002902-$S", readable = true)
        return GattSnapshot(
            deviceAddress = FAKE_ADDRESS,
            deviceName = "ColorFit Caliber 2881 (SIMULATED)",
            takenAtEpochMillis = System.currentTimeMillis(),
            services = listOf(
                GattServiceModel(
                    "00001800-$S", "PRIMARY",
                    listOf(
                        GattCharacteristicModel("00002a00-$S", listOf("READ"), emptyList(),
                            lastReadHex = "43 6F 6C 6F 72 46 69 74 20 43 61 6C 69 62 65 72",
                            lastReadText = "ColorFit Caliber"),
                        GattCharacteristicModel("00002a01-$S", listOf("READ"), emptyList(),
                            lastReadHex = "42 02", lastReadText = null),
                    )
                ),
                GattServiceModel(
                    "0000180a-$S", "PRIMARY",
                    listOf(
                        GattCharacteristicModel("00002a29-$S", listOf("READ"), emptyList(),
                            lastReadHex = "4D 4F 59 4F 55 4E 47 2D 56 32",
                            lastReadText = "MOYOUNG-V2"),
                        GattCharacteristicModel("00002a24-$S", listOf("READ"), emptyList(),
                            lastReadHex = "32 38 38 31", lastReadText = "2881"),
                        GattCharacteristicModel("00002a26-$S", listOf("READ"), emptyList(),
                            lastReadHex = "52 32 30 34 2E 35 2E 38",
                            lastReadText = "R204.5.8"),
                    )
                ),
                GattServiceModel(
                    "0000180f-$S", "PRIMARY",
                    listOf(
                        GattCharacteristicModel("00002a19-$S", listOf("READ", "NOTIFY"),
                            listOf(cccd), lastReadHex = "5A", lastReadText = "90 %")
                    )
                ),
                GattServiceModel(
                    "0000feea-$S", "PRIMARY",
                    listOf(
                        GattCharacteristicModel("0000fee2-$S",
                            listOf("WRITE", "WRITE_NO_RESPONSE"), emptyList()),
                        GattCharacteristicModel("0000fee3-$S",
                            listOf("NOTIFY", "INDICATE"), listOf(cccd.copy())),
                        GattCharacteristicModel("0000fee1-$S",
                            listOf("READ", "NOTIFY"), listOf(cccd.copy()))
                    )
                ),
            ),
        )
    }

    /** Scripted notify bytes for UI demos (Moyoung-hypothesis framed find-phone packet). */
    fun scriptedNotifications(): Map<String, ByteArray> = mapOf(
        "0000fee3-$S" to byteArrayOf(
            0xFE.toByte(), 0xEA.toByte(), 0x20, 0x06, 0x62, 0x00
        )
    )
}
