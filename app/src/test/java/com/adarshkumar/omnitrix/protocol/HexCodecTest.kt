package com.adarshkumar.omnitrix.protocol

import com.adarshkumar.omnitrix.protocol.HexCodec.toHex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HexCodecTest {

    @Test
    fun `bytes render as spaced uppercase hex`() {
        assertEquals("FE EA 20 05", byteArrayOf(-2, -22, 32, 5).toHex())
    }

    @Test
    fun `empty array renders empty`() {
        assertEquals("", byteArrayOf().toHex())
    }

    @Test
    fun `parse accepts colon separated and plain hex`() {
        assertArrayEquals(byteArrayOf(0x0A, 0x0B), HexCodec.parse("0A:0B"))
        assertArrayEquals(byteArrayOf(0x0A, 0x0B), HexCodec.parse("0a0b"))
    }

    @Test
    fun `parse rejects odd length and non hex`() {
        assertNull(HexCodec.parse("ABC"))
        assertNull(HexCodec.parse("ZZ00"))
        assertNull(HexCodec.parse(""))
    }

    @Test
    fun `looksLikeHex sanity`() {
        assertTrue(HexCodec.looksLikeHex("0A0B"))
        assertTrue(HexCodec.looksLikeHex("DE:AD:BE:EF"))
        assertFalse(HexCodec.looksLikeHex("no"))
        assertFalse(HexCodec.looksLikeHex("ABC"))
    }
}
