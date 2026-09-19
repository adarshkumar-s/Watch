package com.adarshkumar.omnitrix.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.adarshkumar.omnitrix.R
import com.adarshkumar.omnitrix.diag.DiagnosticExporter
import com.adarshkumar.omnitrix.diag.DiagnosticLog
import com.google.android.material.button.MaterialButton

/**
 * Diagnostic log screen: the full direction-tagged connection/protocol log with
 * COPY / EXPORT / CLEAR, plus entry points to QR↔BLE correlation and the full
 * diagnostic text export (omnitrix-diagnostic.txt).
 */
class DiagnosticsActivity : ComponentActivity() {

    private lateinit var logView: TextView
    private lateinit var countView: TextView
    private val listener: () -> Unit = { runOnUiThread { render() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostics)

        logView = findViewById(R.id.logText)
        countView = findViewById(R.id.logCount)

        findViewById<MaterialButton>(R.id.logCopy).setOnClickListener {
            copy(DiagnosticLog.render())
            toast("Log copied")
        }
        findViewById<MaterialButton>(R.id.logExport).setOnClickListener {
            startActivity(Intent.createChooser(DiagnosticExporter.shareIntent(this),
                "Export ${DiagnosticExporter.FILE_NAME}"))
        }
        findViewById<MaterialButton>(R.id.logClear).setOnClickListener {
            DiagnosticLog.clear(); render()
        }
        findViewById<MaterialButton>(R.id.logCorrelate).setOnClickListener {
            startActivity(Intent(this, CompareActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.logBack).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        DiagnosticLog.addListener(listener)
        render()
    }

    override fun onPause() {
        DiagnosticLog.removeListener(listener)
        super.onPause()
    }

    private fun render() {
        countView.text = "${DiagnosticLog.size()} entries  •  newest last"
        logView.text = DiagnosticLog.render().ifEmpty { "(no events yet)" }
    }

    private fun copy(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("omnitrix-log", text))
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
