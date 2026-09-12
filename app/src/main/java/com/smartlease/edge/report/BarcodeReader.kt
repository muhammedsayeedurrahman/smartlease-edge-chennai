package com.smartlease.edge.report

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device QR/barcode decoding via ML Kit's bundled scanner — same offline guarantee as
 * [com.smartlease.edge.ocr.OcrEngine]: the model ships in the APK, no Play Services stream,
 * no network. Mirrors OcrEngine's still-capture-then-decode shape rather than a live
 * frame-by-frame analyzer, since a single tap-to-capture is enough for a QR a person is
 * deliberately holding up to the lens.
 */
object BarcodeReader {

    private val scanner = BarcodeScanning.getClient()

    /** Raw text payload of the first barcode found in [bitmap], or null if none decoded. */
    suspend fun readFirst(bitmap: Bitmap): String? = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (cont.isActive) cont.resume(barcodes.firstOrNull()?.rawValue)
            }
            .addOnFailureListener { e ->
                if (cont.isActive) cont.resumeWithException(e)
            }
    }
}
