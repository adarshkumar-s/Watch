package com.adarshkumar.omnitrix.analysis

import com.adarshkumar.omnitrix.pairing.QrPayloadParser
import com.adarshkumar.omnitrix.analysis.QrBleCorrelation.BleObservation
import com.adarshkumar.omnitrix.analysis.QrBleCorrelation.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrBleCorrelationTest {

    private fun observation(
        address: String? = "AA:BB:CC:DD:EE:FF",
        name: String? = "ColorFit Caliber",
        mfr: List<String> = listOf("6d6f796f756e672d7632"),
        advServices: List<String> = listOf("0000feea-0000-1000-8000-00805f9b34fb"),
        gattServices: List<String> = emptyList(),
    ) = BleObservation(
        address = address, name = name, manufacturerDataHexList = mfr,
        advertisedServiceUuids = advServices, gattServiceUuids = gattServices,
    )

    @Test
    fun `qr mac matching ble address is a MATCH`() {
        val qr = QrPayloadParser.parse("AA:BB:CC:DD:EE:FF")
        val findings = QrBleCorrelation.correlate(qr, observation())
        val f = findings.first { it.label.contains("MAC") }
        assertEquals(Verdict.MATCH, f.verdict)
    }

    @Test
    fun `qr mac not matching ble address is NO_MATCH, not a guess`() {
        val qr = QrPayloadParser.parse("11:22:33:44:55:66")
        val findings = QrBleCorrelation.correlate(qr, observation())
        val f = findings.first { it.label.contains("MAC") }
        assertEquals(Verdict.NO_MATCH, f.verdict)
        assertTrue(f.detail.contains("serial") || f.detail.contains("identifier"))
    }

    @Test
    fun `missing qr degrades gracefully`() {
        val findings = QrBleCorrelation.correlate(null, observation())
        assertTrue(findings.any { it.verdict == Verdict.NO_DATA })
    }

    @Test
    fun `qr uuid present in gatt services is a MATCH`() {
        val qr = QrPayloadParser.parse("0000feea-0000-1000-8000-00805f9b34fb")
        val findings = QrBleCorrelation.correlate(
            qr, observation(gattServices = listOf("0000feea-0000-1000-8000-00805f9b34fb"))
        )
        val f = findings.first { it.label.contains("UUID") }
        assertEquals(Verdict.MATCH, f.verdict)
    }

    @Test
    fun `caliber name yields device hint finding`() {
        val qr = QrPayloadParser.parse("some-random-payload")
        val findings = QrBleCorrelation.correlate(qr, observation())
        assertTrue(findings.any { it.label.contains("name") && it.verdict == Verdict.MATCH })
    }

    @Test
    fun `empty universe yields NO_DATA findings only`() {
        val findings = QrBleCorrelation.correlate(
            null,
            observation(address = null, name = null, mfr = emptyList(), advServices = emptyList())
        )
        assertTrue(findings.all { it.verdict == Verdict.NO_DATA })
    }
}
