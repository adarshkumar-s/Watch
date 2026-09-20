package com.adarshkumar.omnitrix.ble

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Runtime-permission policy, correct per API level:
 *
 * - API >= 31 (Android 12+): BLUETOOTH_SCAN (neverForLocation) + BLUETOOTH_CONNECT.
 * - API <= 30 (Android ≤ 11): legacy BLUETOOTH/ADMIN are install-time; scan results
 *   additionally need runtime ACCESS_FINE_LOCATION.
 * - CAMERA is handled separately by the QR screen.
 */
object BlePermissions {

    const val REQUEST_CODE = 41
    const val REQUEST_CAMERA = 42
    const val REQUEST_ENABLE_BT = 43

    fun required(sdk: Int = Build.VERSION.SDK_INT): Array<String> =
        if (sdk >= 31) arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) else arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
        )

    fun missing(context: Context): List<String> =
        required().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

    /** Pure form for unit tests: what remains ungranted at [sdk] given [isGranted]. */
    fun missing(sdk: Int, isGranted: (String) -> Boolean): List<String> =
        required(sdk).filterNot(isGranted)

    fun allGranted(context: Context): Boolean = missing(context).isEmpty()

    fun hasScanPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 31) granted(context, Manifest.permission.BLUETOOTH_SCAN)
        else granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasConnectPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 31) granted(context, Manifest.permission.BLUETOOTH_CONNECT)
        else true // legacy BLUETOOTH/BLUETOOTH_ADMIN are install-time on <= 30

    fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun request(activity: Activity) {
        val missing = missing(activity)
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, missing.toTypedArray(), REQUEST_CODE)
        }
    }

    fun adapter(context: Context): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun isBluetoothEnabled(context: Context): Boolean =
        adapter(context)?.isEnabled == true

    fun enableBluetoothIntent(): Intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
}
