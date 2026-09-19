package com.adarshkumar.omnitrix.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Codec tests for the Moyoung-family framing HYPOTHESIS.
 * Passing these proves only that our hypothesis implementation is internally
 * consistent — NOT that the Caliber 2881 speaks this protocol.
 */
class PacketCodecTest {

    @Test
    fun `encode produces documented header layout`() {
        val frame = PacketEncoder.encodeMoyoungHypothesis(
            0x31, byteArrayOf(0x68, 0xA3.toByte(), 0x10, 0x03, 0x08)
        )
        // From Gadgetbridge docs: fe ea 20 0a 31 68 a3 10 03 08
        val expected = byteArrayOf(
            0xFE.toByte(), 0xEA.toByte(), 0x20, 0x0A, 0x31,
            0x68, 0xA3.toByte(), 0x10, 0x03, 0x08,
        )
        assertArrayEquals(expected, frame)
    }

    @Test
    fun `encode empty payload declares size 5`() {
        val frame = PacketEncoder.encodeMoyoungHypothesis(0x01)
        assertEquals(5, frame.size)
        assertEquals(0x20.toByte(), frame[2]) // (32 + (5>>8)) = 32
        assertEquals(0x05.toByte(), frame[3])
    }

    @Test
    fun `round trip encode then decode`() {
        val payload = byteArrayOf(1, 2, 3, 4, 5, 6, 7)
        val encoded = PacketEncoder.encodeMoyoungHypothesis(0x42, payload)
        val decoded = PacketDecoder.decode(encoded)
        assertEquals(0x42, decoded.command)
        assertArrayEquals(payload, decoded.payload)
        assertEquals(encoded.size, decoded.declaredSize)
    }

    @Test
    fun `decode rejects wrong magic`() {
        val result = PacketDecoder.analyze(byteArrayOf(0x00, 0x00, 0x20, 0x05, 0x31))
        assertTrue(result is PacketDecoder.Result.NotThisProtocol)
    }

    @Test
    fun `decode rejects truncated frame`() {
        val result = PacketDecoder.analyze(byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x20))
        assertTrue(result is PacketDecoder.Result.Malformed)
    }

    @Test
    fun `decode rejects declared size mismatch`() {
        // header claims 0x0A total, but only 6 bytes present
        val bad = byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x20, 0x0A, 0x31, 0x00)
        val result = PacketDecoder.analyze(bad)
        assertTrue(result is PacketDecoder.Result.Malformed)
    }

    @Test
    fun `decode rejects garbage command channel bytes`() {
        val garbage = "hello world".toByteArray()
        val result = PacketDecoder.analyze(garbage)
        assertTrue(result is PacketDecoder.Result.NotThisProtocol)
    }

    @Test
    fun `legacy mtu20 framing is recognized`() {
        // fe ea 10 len cmd payload — legacy variant
        val legacy = byteArrayOf(0xFE.toByte(), 0xEA.toByte(), 0x10, 0x06, 0x31, 0x00)
        val result = PacketDecoder.analyze(legacy)
        assertTrue(result is PacketDecoder.Result.Valid)
        assertEquals(6, (result as PacketDecoder.Result.Valid).frame.declaredSize)
    }

    @Test
    fun `annotate never claims certainty`() {
        val frame = PacketEncoder.encodeMoyoungHypothesis(0x62, byteArrayOf(0))
        val note = PacketDecoder.annotate(frame)
        assertTrue(note.contains("UNKNOWN on 2881"))
    }
}
