package com.adarshkumar.omnitrix.devices

import com.adarshkumar.omnitrix.protocol.EvidenceLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverTest {

    private val driver = NoiseColorFitCaliber2881Driver

    @Test
    fun `every capability is unsupported while protocol is unknown`() {
        DeviceDriver.Capability.entries.forEach { cap ->
            val result = driver.buildCommand(cap)
            assertTrue("capability $cap must be unsupported", result is DeviceDriver.CommandResult.Unsupported)
            assertTrue(
                (result as DeviceDriver.CommandResult.Unsupported).reason
                    .contains("Not yet supported")
            )
        }
    }

    @Test
    fun `battery read interpretation`() {
        val out = driver.interpretRead(
            "00002a19-0000-1000-8000-00805f9b34fb", byteArrayOf(90)
        )
        assertEquals("90 %", out?.get("Battery"))
    }

    @Test
    fun `firmware read interpretation`() {
        val out = driver.interpretRead(
            "00002a26-0000-1000-8000-00805f9b34fb", "R204.5.8".toByteArray()
        )
        assertEquals("R204.5.8", out?.get("Firmware revision"))
    }

    @Test
    fun `fake device is identified via manufacturer MOYOUNG-V2`() {
        val match = driver.matchByGatt(FakeCaliberDevice.snapshot())
        assertNotNull(match)
        // Manufacturer "MOYOUNG-V2" in SIMULATED data → treated as CONFIRMED family id
        assertEquals(EvidenceLevel.CONFIRMED, match!!.confidence)
    }

    @Test
    fun `fake advertisement matches as likely`() {
        val match = driver.matchByAdvertisement(FakeCaliberDevice.advertisement())
        assertNotNull(match)
        assertEquals(EvidenceLevel.LIKELY, match!!.confidence)
    }

    @Test
    fun `fake snapshot has the hypothesized feea service`() {
        assertNotNull(FakeCaliberDevice.snapshot().findService("0000feea-0000-1000-8000-00805f9b34fb"))
    }
}
