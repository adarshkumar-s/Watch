package com.adarshkumar.omnitrix.protocol

/**
 * Direction-tagged protocol logging contract. Keeps the PHONE→WATCH / WATCH→PHONE
 * channels visually distinct in every diagnostic surface.
 */
enum class Direction(val tag: String) {
    PHONE_TO_WATCH("PHONE → WATCH"),
    WATCH_TO_PHONE("WATCH → PHONE"),
    LOCAL("LOCAL"),
}
