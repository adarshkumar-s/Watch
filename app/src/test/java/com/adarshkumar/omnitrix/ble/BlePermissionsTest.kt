package com.adarshkumar.omnitrix.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure permission-policy tests incl. denial scenarios (audit point 10).
 */
class BlePermissionsTest {

    private fun grantedNone(p: String) = false
    private fun grantedAll(p: String) = true

    @Test
    fun `android 12+ requires scan and connect`() {
        assertEquals(
            listOf("android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"),
            BlePermissions.required(31).toList()
        )
        assertEquals(
            listOf("android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"),
            BlePermissions.required(34).toList()
        )
    }

    @Test
    fun `android 11 and below requires fine location at runtime`() {
        assertEquals(
            listOf("android.permission.ACCESS_FINE_LOCATION"),
            BlePermissions.required(30).toList()
        )
        assertEquals(
            listOf("android.permission.ACCESS_FINE_LOCATION"),
            BlePermissions.required(26).toList()
        )
    }

    @Test
    fun `nothing missing when all granted`() {
        assertTrue(BlePermissions.missing(31, ::grantedAll).isEmpty())
        assertTrue(BlePermissions.missing(29, ::grantedAll).isEmpty())
    }

    @Test
    fun `full denial reports everything as missing`() {
        assertEquals(2, BlePermissions.missing(33, ::grantedNone).size)
        assertEquals(1, BlePermissions.missing(28, ::grantedNone).size)
    }

    @Test
    fun `partial denial reports only the denied permission`() {
        val granted = { p: String -> p == "android.permission.BLUETOOTH_SCAN" }
        assertEquals(
            listOf("android.permission.BLUETOOTH_CONNECT"),
            BlePermissions.missing(31, granted)
        )
    }
}
