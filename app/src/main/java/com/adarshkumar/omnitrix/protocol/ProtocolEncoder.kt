package com.adarshkumar.omnitrix.protocol

/**
 * Moyoung-family framing codec — HYPOTHESIS ONLY.
 *
 * Encodes/decodes the frame layout documented publicly for Da Fit-class watches
 * (Gadgetbridge `MoyoungConstants`, AGPL):
 *
 * ```
 * Fe Ea | sizeHi+32 sizeLo | cmd | payload…         (MTU != 20 variant)
 * Fe Ea | 16 len | cmd | payload…                    (legacy MTU-20 variant)
 * size  = total packet length (magic + size bytes + cmd + payload); empty payload ⇒ 5
 * ```
 *
 * ⚠ SAFETY: this codec is used by unit tests and by the log ANNOTATOR that labels inbound
 * watch bytes. It is deliberately NOT wired to any BLE write path — `BleConnection` has no
 * characteristic-value write API in diagnostic mode. Do not transport-encode anything
 * against the Caliber 2881 until the framing is CONFIRMED.
 */
object ProtocolEncoder {

    class FrameException(message: String) : Exception(message)

    /**
     * Encodes [command]+[payload] using the Moyoung V2 hypothesis framing.
     * Pure function. Caller is responsible for never shipping the result to a watch.
     */
    fun encodeMoyoungHypothesis(command: Int, payload: ByteArray = byteArrayOf()): ByteArray {
        require(command in 0..255) { "command must fit in a byte, got $command" }
        val size = 5 + payload.size // 2 magic + 2 size + 1 cmd + payload
        require(size <= 0xFFFF) { "payload too large: ${payload.size}" }
        val out = ByteArray(size)
        out[0] = 0xFE.toByte()
        out[1] = 0xEA.toByte()
        out[2] = ((32 + (size shr 8)) and 0xFF).toByte()
        out[3] = (size and 0xFF).toByte()
        out[4] = command.toByte()
        payload.copyInto(out, destinationOffset = 5)
        return out
    }

    /** Interprets bytes as a Moyoung-hypothesis frame. Throws [FrameException] when invalid. */
    fun decodeMoyoungHypothesis(bytes: ByteArray): ProtocolFrame {
        return ProtocolDecoder.decode(bytes)
    }
}
