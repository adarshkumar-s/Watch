package com.adarshkumar.omnitrix.ble

import com.adarshkumar.omnitrix.devices.FakeCaliberDevice
import com.adarshkumar.omnitrix.protocol.HexCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvertisementParserTest {

    private fun bytes(hex: String): ByteArray = HexCodec.parse(hex)!!

    @Test
    fun `parses flags + complete name + manufacturer + uuid16 list`() {
        // 02 01 06 | 11 09 "ColorFit Caliber" | 0C FF 0A0A "moyoung-v2" | 03 03 EAFE
        val r = AdvertisementParser.parse(
            bytes("0201061109436F6C6F724669742043616C696265720CFF0A0A6D6F796F756E672D76320303EAFE")
        )
        assertTrue(r.ok)
        assertEquals(4, r.fields.size)
        assertEquals(AdvertisementParser.AD_FLAGS, r.fields[0].type)
        assertEquals(AdvertisementParser.AD_COMPLETE_NAME, r.fields[1].type)
        assertEquals("ColorFit Caliber", String(r.fields[1].data, Charsets.UTF_8))
        val mfr = r.fieldsOfType(AdvertisementParser.AD_MANUFACTURER).single()
        assertEquals(0x0A0A, (mfr.data[1].toInt() and 0xFF shl 8) or (mfr.data[0].toInt() and 0xFF))
        assertEquals(AdvertisementParser.AD_COMPLETE_UUID16, r.fields[3].type)
    }

    @Test
    fun `empty payload parses to zero fields`() {
        val r = AdvertisementParser.parse(byteArrayOf())
        assertTrue(r.ok)
        assertTrue(r.fields.isEmpty())
    }

    @Test
    fun `zero-length structure terminates parsing`() {
        val r = AdvertisementParser.parse(bytes("02010600" + "FF".repeat(10)))
        assertTrue(r.ok)
        assertEquals(1, r.fields.size)
    }

    @Test
    fun `truncated structure is reported not thrown`() {
        // claims len=0x10 but only 4 bytes follow
        val r = AdvertisementParser.parse(bytes("1009AABB"))
        assertFalse(r.ok)
        assertTrue(r.fields.isEmpty())
        assertEquals(0, r.truncatedAt)
    }

    @Test
    fun `garbage single byte is reported`() {
        val r = AdvertisementParser.parse(bytes("FF"))
        assertFalse(r.ok)
    }

    @Test
    fun `fake caliber advertisement parses cleanly`() {
        val raw = FakeCaliberDevice.advertisement().rawAdvHex
        val r = AdvertisementParser.parse(HexCodec.parse(raw!!)!!)
        assertTrue(r.ok)
        assertEquals("ColorFit Caliber", String(
            r.fieldsOfType(AdvertisementParser.AD_COMPLETE_NAME).single().data, Charsets.UTF_8))
        assertTrue(AdvertisementParser.render(HexCodec.parse(raw)!!).contains("moyoung-v2"))
    }

    @Test
    fun `render includes type names and truncation markers`() {
        val out = AdvertisementParser.render(bytes("0201061009AA"))
        assertTrue(out.contains("Flags"))
        assertTrue(out.contains("truncated"))
    }
}
