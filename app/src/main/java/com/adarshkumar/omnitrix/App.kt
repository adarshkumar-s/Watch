package com.adarshkumar.omnitrix

import android.app.Application
import android.content.pm.PackageManager
import com.adarshkumar.omnitrix.diagnostics.DiagnosticLog

class App : Application() {

    val appVersionName: String
        get() = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
        } catch (_: PackageManager.NameNotFoundException) {
            "unknown"
        }

    override fun onCreate() {
        super.onCreate()
        instance = this
        DiagnosticLog.info("APP", "OMNITRIX $appVersionName starting (diagnostic mode)")
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
