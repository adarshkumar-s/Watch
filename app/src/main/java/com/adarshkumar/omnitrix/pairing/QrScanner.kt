package com.adarshkumar.omnitrix.pairing

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

/**
 * CameraX analyzer that emits the RAW QR payload string exactly as decoded, once per
 * distinct value (debounced so a held phone doesn't fire repeatedly). The payload is
 * handed to the UI untouched — parsing/action is the UI's explicit decision.
 */
class QrScanner(
    private val onPayload: (String) -> Unit,
    private val onError: ((Exception) -> Unit)? = null,
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient()

    @Volatile private var deliveredValue: String? = null
    @Volatile var enabled: Boolean = true

    fun reset() {
        deliveredValue = null
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val media = imageProxy.image
        if (media == null || !enabled) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                val value = barcodes.firstOrNull()?.rawValue
                if (!value.isNullOrEmpty() && value != deliveredValue) {
                    deliveredValue = value
                    onPayload(value)
                }
            }
            .addOnFailureListener { e -> onError?.invoke(e) }
            .addOnCompleteListener { imageProxy.close() }
    }
}
