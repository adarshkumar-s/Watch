package com.adarshkumar.omnitrix.diagnostics

/**
 * Canonical connection-log event categories (the audit-specified taxonomy).
 * Every logged BLE lifecycle event uses one of these as its category so the
 * chronology is machine-scannable: SCAN_STARTED … NOTIFICATION_ENABLED … ERROR.
 */
object LogEvent {
    const val SCAN_STARTED = "SCAN_STARTED"
    const val SCAN_STOPPED = "SCAN_STOPPED"
    const val DEVICE_FOUND = "DEVICE_FOUND"
    const val CONNECTING = "CONNECTING"
    const val CONNECTED = "CONNECTED"
    const val SERVICE_DISCOVERY = "SERVICE_DISCOVERY"
    const val SERVICE_FOUND = "SERVICE_FOUND"
    const val CHARACTERISTIC_FOUND = "CHARACTERISTIC_FOUND"
    const val NOTIFICATION_ENABLED = "NOTIFICATION_ENABLED"
    const val NOTIFICATION_DISABLED = "NOTIFICATION_DISABLED"
    const val READ_REQUEST = "READ_REQUEST"
    const val READ_RESPONSE = "READ_RESPONSE"
    const val RX_PACKET = "RX_PACKET"       // raw bytes only — meaning is NEVER fabricated
    const val DISCONNECTED = "DISCONNECTED"
    const val ERROR = "ERROR"

    // Informational (non-lifecycle) categories
    const val QR = "QR"
    const val DRIVER = "DRIVER"
    const val LEGACY = "LEGACY"             // UNVERIFIED legacy-assumption comparisons
    const val STATE = "STATE"
    const val APP = "APP"
}
