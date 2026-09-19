package com.adarshkumar.omnitrix.protocol

/**
 * How much we trust a protocol fact about the ColorFit Caliber 2881 / R204.5.8.
 *
 * CONFIRMED — captured from the actual watch by this diagnostic app.
 * LIKELY    — public research (e.g. Da Fit / Moyoung family) suggesting this, not validated here.
 * UNKNOWN   — never verified; the app must never emit UNKNOWN commands to the watch.
 */
enum class EvidenceLevel {
    CONFIRMED,
    LIKELY,
    UNKNOWN
}
