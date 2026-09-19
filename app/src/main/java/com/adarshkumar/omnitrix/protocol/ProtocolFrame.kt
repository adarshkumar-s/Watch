package com.adarshkumar.omnitrix.protocol

/**
 * A framed protocol message. The only framing hypothesis currently modeled is the
 * Moyoung-family one (see docs/PROTOCOL_RESEARCH.md), which is LIKELY for Da Fit-class
 * watches but UNKNOWN for the ColorFit Caliber 2881.
 *
 * This is a data holder + interpretation helpers only; nothing here can be transmitted.
 */
data class ProtocolFrame(
    val magic: Int,          // e.g. 0xFEEA for the Moyoung hypothesis
    val declaredSize: Int,   // size value carried in the frame
    val command: Int,
    val payload: ByteArray,
) {
    companion object {
        const val MOYOUNG_MAGIC = 0xFEEA
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProtocolFrame) return false
        return magic == other.magic && declaredSize == other.declaredSize &&
            command == other.command && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = magic
        result = 31 * result + declaredSize
        result = 31 * result + command
        result = 31 * result + payload.contentHashCode()
        return result
    }

    fun payloadHex(): String = with(HexCodec) { payload.toHex() }

    override fun toString(): String =
        "ProtocolFrame(magic=0x%04X size=%d cmd=0x%02X payload=%s)"
            .format(magic, declaredSize, command, payloadHex().ifEmpty { "∅" })
}
