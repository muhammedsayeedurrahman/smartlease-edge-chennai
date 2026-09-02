package com.smartlease.edge.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device OCR via ML Kit's bundled Latin text recognizer — genuinely real, genuinely
 * offline (the bundled/on-device model, not the cloud API), no placeholder here. This is
 * the lowest-risk subsystem in the whole pipeline per the design doc's own risk table.
 */
object OcrEngine {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun readText(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                if (cont.isActive) cont.resume(visionText.text)
            }
            .addOnFailureListener { e ->
                if (cont.isActive) cont.resumeWithException(e)
            }
    }
}
