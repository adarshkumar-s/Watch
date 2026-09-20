package com.adarshkumar.omnitrix.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.adarshkumar.omnitrix.R
import com.adarshkumar.omnitrix.ble.BlePermissions
import com.adarshkumar.omnitrix.diagnostics.DiagnosticExporter
import com.adarshkumar.omnitrix.pairing.PairingManager
import com.adarshkumar.omnitrix.pairing.QrScanner
import com.adarshkumar.omnitrix.pairing.QrPayloadParser
import com.adarshkumar.omnitrix.pairing.QrPayload
import com.google.android.material.button.MaterialButton
import java.util.concurrent.Executors

/**
 * QR diagnostic scanner.
 *
 * INVARIANTS (Phase 3):
 * - Shows the COMPLETE raw payload, verbatim.
 * - Classifies the format descriptively (URL/JSON/MAC/UUID/hex/base64/…).
 * - NEVER auto-executes anything from the payload (no auto-connect, no URL opening).
 * - Actions are explicit: COPY, SAVE, EXPORT, RESCAN.
 */
class QrScannerActivity : ComponentActivity() {

    private lateinit var preview: PreviewView
    private lateinit var hint: TextView
    private lateinit var permissionBlock: View
    private lateinit var resultPanel: View
    private lateinit var rawValue: TextView
    private lateinit var formatsView: TextView
    private lateinit var candidatesView: TextView

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var lastParsed: QrPayload? = null
    private val pairing by lazy { PairingManager(this) }
    private var cameraStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scanner)

        preview = findViewById(R.id.qrPreview)
        hint = findViewById(R.id.qrHint)
        permissionBlock = findViewById(R.id.qrPermissionBlock)
        resultPanel = findViewById(R.id.qrResultPanel)
        rawValue = findViewById(R.id.qrRawValue)
        formatsView = findViewById(R.id.qrFormats)
        candidatesView = findViewById(R.id.qrCandidates)

        findViewById<MaterialButton>(R.id.qrClose).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.qrGrantCamera).setOnClickListener {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), BlePermissions.REQUEST_CAMERA
            )
        }
        findViewById<MaterialButton>(R.id.qrCopy).setOnClickListener {
            lastParsed?.let { copy(it.raw); toast("QR payload copied") }
        }
        findViewById<MaterialButton>(R.id.qrSave).setOnClickListener {
            lastParsed?.let {
                pairing.record(it)
                toast("Saved locally for diagnostics")
            }
        }
        findViewById<MaterialButton>(R.id.qrExport).setOnClickListener {
            startActivity(
                Intent.createChooser(
                    DiagnosticExporter.shareIntent(this), "Export diagnostic result"
                )
            )
        }
        findViewById<MaterialButton>(R.id.qrRescan).setOnClickListener {
            resultPanel.visibility = View.GONE
            hint.visibility = View.VISIBLE
            analyzer.reset()
            analyzer.enabled = true
        }

        handleCameraPermission()
    }

    private val analyzer = QrScanner(
        onPayload = { raw -> runOnUiThread { showResult(raw) } },
        onError = { runOnUiThread { hint.text = "Scanner error — try again" } },
    )

    private fun handleCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            permissionBlock.visibility = View.GONE
            startCamera()
        } else {
            permissionBlock.visibility = View.VISIBLE
            preview.visibility = View.GONE
            hint.visibility = View.GONE
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == BlePermissions.REQUEST_CAMERA) handleCameraPermission()
    }

    private fun startCamera() {
        if (cameraStarted) return
        cameraStarted = true
        preview.visibility = View.VISIBLE
        hint.visibility = View.VISIBLE
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            runCatching {
                val provider = future.get()
                val previewUseCase = Preview.Builder().build().also {
                    it.surfaceProvider = preview.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(cameraExecutor, analyzer)
                provider.unbindAll()
                provider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, previewUseCase, analysis
                )
            }.onFailure {
                hint.text = "Camera failed to start: ${it.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun showResult(raw: String) {
        analyzer.enabled = false
        val parsed = QrPayloadParser.parse(raw)
        lastParsed = parsed
        // persist every scan automatically for diagnostics (local-only)
        pairing.record(parsed)

        rawValue.text = parsed.raw
        formatsView.text = "Detected format: " +
            parsed.formats.joinToString("  •  ") { it.label }

        val lines = ArrayList<String>()
        if (parsed.macCandidates.isNotEmpty())
            lines += "MAC candidate(s): ${parsed.macCandidates.joinToString()}"
        if (parsed.uuidCandidates.isNotEmpty())
            lines += "UUID candidate(s): ${parsed.uuidCandidates.joinToString()}"
        parsed.urlValue?.let { lines += "URL (not opened): $it" }
        if (parsed.jsonKeys.isNotEmpty())
            lines += "JSON keys: ${parsed.jsonKeys.joinToString()}"
        if (lines.isEmpty()) lines += "No structured fields detected."
        lines += "\nNothing was executed or transmitted."
        candidatesView.text = lines.joinToString("\n")

        hint.visibility = View.GONE
        resultPanel.visibility = View.VISIBLE
    }

    private fun copy(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("qr", text))
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        analyzer.enabled = false
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}
