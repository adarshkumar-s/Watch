package com.adarshkumar.omnitrix.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.adarshkumar.omnitrix.R

/**
 * Legacy QR activity retained for source compatibility.
 * The launcher now uses MainActivity's integrated scanner, so this screen
 * deliberately contains no dependencies on the old diagnostic packages.
 */
class QrScannerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scanner)
    }
}
