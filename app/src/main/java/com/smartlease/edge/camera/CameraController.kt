package com.smartlease.edge.camera

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thin CameraX wrapper: live preview + on-demand still capture as a Bitmap for the
 * OCR/vision/AR-alignment pipelines to consume. Uses the device's primary rear camera —
 * on the iQOO 15 that's the 50MP OIS main sensor, whose stabilization is what keeps the
 * baseline-alignment overlay usable handheld (see design doc's iQOO feature map).
 */
class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private var imageCapture: ImageCapture? = null

    suspend fun bindTo(previewView: PreviewView) {
        val provider = getCameraProvider()

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()

        provider.unbindAll()
        provider.bindToLifecycle(
            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture
        )
        imageCapture = capture
    }

    suspend fun captureBitmap(): Bitmap = suspendCancellableCoroutine { cont ->
        val capture = imageCapture
            ?: return@suspendCancellableCoroutine cont.resumeWithException(
                IllegalStateException("Camera not bound yet — call bindTo() first")
            )

        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                    val bitmap = image.toBitmap()
                    image.close()
                    if (cont.isActive) cont.resume(bitmap)
                }

                override fun onError(exception: ImageCaptureException) {
                    if (cont.isActive) cont.resumeWithException(exception)
                }
            }
        )
    }

    private suspend fun getCameraProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (cont.isActive) cont.resume(future.get())
            }, ContextCompat.getMainExecutor(context))
        }
}

// Note: ImageProxy.toBitmap() is a built-in CameraX member function (current camerax version) —
// no custom extension needed here; using it directly at the call site above.
