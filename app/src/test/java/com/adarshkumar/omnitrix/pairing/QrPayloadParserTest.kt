package com.adarshkumar.omnitrix.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPayloadParserTest {

    @Test
    fun `empty payload is classified EMPTY`() {
        val p = QrPayloadParser.parse("")
        assertEquals(listOf(QrPayload.Format.EMPTY), p.formats)
        assertTrue(p.macCandidates.isEmpty())
    }

    @Test
    fun `null payload is classified EMPTY`() {
        assertEquals(listOf(QrPayload.Format.EMPTY), QrPayloadParser.parse(null).formats)
    }

    @Test
    fun `mac address payload detected`() {
        val p = QrPayloadParser.parse("AA:BB:CC:DD:EE:FF")
        assertTrue(QrPayload.Format.MAC in p.formats)
        assertEquals(listOf("AA:BB:CC:DD:EE:FF"), p.macCandidates)
    }

    @Test
    fun `plain 12 digit hex mac detected and normalized`() {
        val p = QrPayloadParser.parse("aabbccddeeff")
        assertTrue(QrPayload.Format.MAC in p.formats)
        assertEquals(listOf("AA:BB:CC:DD:EE:FF"), p.macCandidates)
    }

    @Test
    fun `mac embedded in larger payload is a candidate not an assumption`() {
        val p = QrPayloadParser.parse("device=watch;mac=11:22:33:44:55:66;pin=0000")
        assertTrue("11:22:33:44:55:66" in p.macCandidates)
        assertTrue(QrPayload.Format.KEY_VALUE in p.formats)
    }

    @Test
    fun `url detected but never treated as mac`() {
        val p = QrPayloadParser.parse("https://noise.example.com/pair?t=xyz")
        assertTrue(QrPayload.Format.URL in p.formats)
        assertEquals("https://noise.example.com/pair?t=xyz", p.urlValue)
        assertFalse(QrPayload.Format.MAC in p.formats)
    }

    @Test
    fun `json detected and keys extracted without evaluation`() {
        val p = QrPayloadParser.parse("""{"mac":"AA:BB:CC:DD:EE:FF","token":"t1"}""")
        assertTrue(QrPayload.Format.JSON in p.formats)
        assertTrue("mac" in p.jsonKeys && "token" in p.jsonKeys)
        assertTrue("AA:BB:CC:DD:EE:FF" in p.macCandidates)
    }

    @Test
    fun `uuid detected`() {
        val p = QrPayloadParser.parse("0000feea-0000-1000-8000-00805f9b34fb")
        assertTrue(QrPayload.Format.UUID in p.formats)
        assertEquals("0000feea-0000-1000-8000-00805f9b34fb", p.uuidCandidates.first())
    }

    @Test
    fun `jwt detected`() {
        val p = QrPayloadParser.parse("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.4Adcj3UFYzPUVaVF43FmMab6RlaQD8")
        assertTrue(QrPayload.Format.JWT in p.formats)
    }

    @Test
    fun `base64 blob detected`() {
        val p = QrPayloadParser.parse("QUFBQUFBQUE=") // "AAAAAAAA"
        assertTrue(QrPayload.Format.BASE64 in p.formats)
    }

    @Test
    fun `malformed garbage falls back to text`() {
        val p = QrPayloadParser.parse("%#^'@! not-a-format")
        assertEquals(listOf(QrPayload.Format.TEXT), p.formats)
    }

    @Test
    fun `binary payload is classified UNKNOWN`() {
        val p = QrPayloadParser.parse("binary")
        assertEquals(listOf(QrPayload.Format.UNKNOWN), p.formats)
    }

    @Test
    fun `8 char odd value is not mac and not hex`() {
        // 7 hex chars: odd length → not HEX; not 12 → not plain-MAC
        val p = QrPayloadParser.parse("AABBCCD")
        assertFalse(QrPayload.Format.MAC in p.formats)
        assertFalse(QrPayload.Format.HEX in p.formats)
    }

    @Test
    fun `parser never throws on adversarial input`() {
        val nasty = arrayOf(
            """{"unclosed":""",
            "\\".repeat(500),
            "https://",
            "=".repeat(300),
            "AA:BB:CC:DD:EE",
            "\u0000\u0001binary",
        )
        nasty.forEach { QrPayloadParser.parse(it) } // must simply not throw
    }
}
