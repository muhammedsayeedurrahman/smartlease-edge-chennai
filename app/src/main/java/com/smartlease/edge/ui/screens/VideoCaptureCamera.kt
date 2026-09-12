package com.smartlease.edge.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.smartlease.edge.inspection360.ImageSharpnessEvaluator
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService

/**
 * CameraX plumbing for [VideoCaptureScreen]: preview binding, frame sampling and the
 * YUV/JPEG fallback for turning an [ImageProxy] into a [Bitmap]. Split out of the
 * composable file so that file stays focused on UI and under the project's line budget.
 */
private const val TAG = "VideoCaptureCamera"

/**
 * Frame retention budget: the UI only ever shows a small thumbnail (48dp filmstrip chips,
 * 54dp selector chips, a 200dp review preview) so nothing is gained by keeping a frame at
 * full camera resolution once it has been analyzed. Bounding the longer edge here keeps
 * each retained bitmap well under half a megabyte instead of ~1.2MB (640x480 ARGB_8888).
 */
private const val THUMBNAIL_MAX_DIMENSION_PX = 320

/** Minimum interval between sampled frames while recording, in milliseconds. */
private const val FRAME_SAMPLE_INTERVAL_MS = 1200L

fun setupCameraWithAnalysis(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    analysisExecutor: ExecutorService,
    onProviderReady: (ProcessCameraProvider) -> Unit,
    onError: (String) -> Unit,
    isRecording: () -> Boolean,
    onFrameSampled: (Bitmap, Double) -> Unit
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    var lastSampleTime = 0L

    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
            try {
                val now = System.currentTimeMillis()
                if (isRecording() && (now - lastSampleTime >= FRAME_SAMPLE_INTERVAL_MS)) {
                    lastSampleTime = now

                    // Compute Laplacian variance for sharpness
                    val yBuffer = imageProxy.planes[0].buffer
                    val sharpness = ImageSharpnessEvaluator.computeLaplacianVariance(
                        yBuffer.duplicate(), imageProxy.width, imageProxy.height
                    )

                    // Convert frame to bitmap
                    val bitmap = imageProxyToBitmap(imageProxy)
                    if (bitmap != null) {
                        onFrameSampled(bitmap, sharpness)
                    }
                }
            } catch (e: Exception) {
                // A dropped frame must not take the recording session down, but it must not
                // vanish silently either -- this is exactly the kind of transient error that
                // used to leave no diagnostic trail.
                Log.e(TAG, "Frame analysis failed for one sample; continuing recording", e)
            } finally {
                imageProxy.close()
            }
        }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
            onProviderReady(cameraProvider)
        } catch (exc: Exception) {
            // A silent black PreviewView with no diagnostic trail is exactly what this used
            // to leave behind (e.g. on a SecurityException from a revoked permission).
            Log.e(TAG, "Failed to bind camera to lifecycle", exc)
            onError(exc.message ?: "Unable to start the camera")
        }
    }, ContextCompat.getMainExecutor(context))
}

/**
 * Scales a captured frame down to [THUMBNAIL_MAX_DIMENSION_PX] on its longer edge. Returns
 * a fresh bitmap; the caller drops its reference to the full-resolution source rather than
 * recycling it, since Compose may still be mid-draw on it for the current frame.
 */
fun downscaleForThumbnail(source: Bitmap): Bitmap {
    val longerEdge = maxOf(source.width, source.height)
    if (longerEdge <= THUMBNAIL_MAX_DIMENSION_PX) return source
    val scale = THUMBNAIL_MAX_DIMENSION_PX.toFloat() / longerEdge
    val targetWidth = (source.width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (source.height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    return try {
        // First try built-in CameraX converter
        image.toBitmap()
    } catch (e: Exception) {
        try {
            // YUV fallback
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 90, out)
            val bytes = out.toByteArray()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (ex: Exception) {
            null
        }
    }
}
