package com.adarshkumar.omnitrix.protocol

/**
 * Decode-side of the Moyoung-family framing hypothesis. See [PacketEncoder] for the big
 * safety warning — this NEVER decides that a packet "is" Moyoung; it only reports whether
 * the byte pattern is *consistent* with that hypothesis so diagnostics can label it.
 */
object PacketDecoder {

    sealed class Result {
        /** Decoded cleanly under the hypothesis. */
        data class Valid(val frame: ProtocolFrame) : Result()
        /** Structurally plausible prefix but truncated/malformed; [reason] explains why. */
        data class Malformed(val reason: String, val raw: ByteArray) : Result()
        /** Does not look like this hypothesis at all. */
        data class NotThisProtocol(val raw: ByteArray) : Result()
    }

    /**
     * Legacy call used by tests/tools — throws on anything that is not a valid frame.
     */
    fun decode(bytes: ByteArray): ProtocolFrame = when (val r = analyze(bytes)) {
        is Result.Valid -> r.frame
        is Result.Malformed -> throw PacketEncoder.FrameException("malformed frame: ${r.reason}")
        is Result.NotThisProtocol -> throw PacketEncoder.FrameException("not a Moyoung-style frame")
    }

    /** Non-throwing analysis for the diagnostic annotator. */
    fun analyze(bytes: ByteArray): Result {
        if (bytes.size < 2 || bytes[0] != 0xFE.toByte() || bytes[1] != 0xEA.toByte()) {
            return Result.NotThisProtocol(bytes)
        }
        if (bytes.size < 5) {
            return Result.Malformed("frame too short for header+cmd (${bytes.size} bytes)", bytes)
        }
        val b2 = bytes[2].toInt() and 0xFF
        val b3 = bytes[3].toInt() and 0xFF
        val declaredSize = when {
            b2 == 16 -> b3                       // legacy MTU-20 framing
            b2 in 32..255 -> (b2 - 32) * 256 + b3 // MTU variant
            else -> return Result.Malformed("unexpected size-high byte 0x%02X".format(b2), bytes)
        }
        if (declaredSize != bytes.size) {
            return Result.Malformed(
                "declared size $declaredSize != actual ${bytes.size}", bytes
            )
        }
        val cmd = bytes[4].toInt() and 0xFF
        val payload = if (bytes.size > 5) bytes.copyOfRange(5, bytes.size) else byteArrayOf()
        return Result.Valid(
            ProtocolFrame(
                magic = ProtocolFrame.MOYOUNG_MAGIC,
                declaredSize = declaredSize,
                command = cmd,
                payload = payload,
            )
        )
    }

    /** One-line human annotation for the diagnostic log; never fabricates meaning. */
    fun annotate(bytes: ByteArray): String {
        val hex = with(HexCodec) { bytes.toHex() }
        return when (val r = analyze(bytes)) {
            is Result.Valid -> "hypothesis Moyoung-frame cmd=0x%02X payload=[%s] (LIKELY-family, UNKNOWN on 2881)"
                .format(r.frame.command, r.frame.payloadHex())
            is Result.Malformed -> "Moyoung-like prefix but ${r.reason} (raw=$hex)"
            is Result.NotThisProtocol -> "no known framing hypothesis matches (raw=$hex)"
        }
    }
}
