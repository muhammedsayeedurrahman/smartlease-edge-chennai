package com.smartlease.edge.inspection360

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class WallInspectionAnalyzer(
    private val headingTracker: HeadingTracker,
    private val onWallCaptured: (Quadrant, Bitmap, Float) -> Unit,
    private val onSpeedWarning: (Boolean) -> Unit
) : ImageAnalysis.Analyzer {

    private val capturedQuadrants = mutableSetOf<Quadrant>()

    init {
        headingTracker.start()
    }

    fun reset() {
        capturedQuadrants.clear()
    }
    
    fun stop() {
        headingTracker.stop()
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val quadrant = headingTracker.currentQuadrant

        // 1. Skip if wall quadrant already captured
        if (capturedQuadrants.contains(quadrant)) {
            onSpeedWarning(false)
            imageProxy.close()
            return
        }

        // 2. Motion gate: ensure device is steady (< 0.25 rad/s ~ 15 deg/s)
        val isMovingTooFast = headingTracker.currentAngularVelocity > 0.25f
        onSpeedWarning(isMovingTooFast)
        
        if (isMovingTooFast) {
            imageProxy.close()
            return
        }

        // 3. Sharpness gate: check Laplacian variance on the Y plane
        val yBuffer = imageProxy.image?.planes?.get(0)?.buffer
        if (yBuffer != null) {
            // computeLaplacianVariance advances the buffer position, so we should duplicate it or handle carefully.
            // We'll pass a slice/duplicate so we don't consume the original buffer entirely.
            val variance = ImageSharpnessEvaluator.computeLaplacianVariance(
                yBuffer.duplicate(), imageProxy.width, imageProxy.height
            )
            
            // Variance threshold >= 100 indicates an edge-sharp, blur-free frame
            if (variance >= 100.0) {
                val bitmap = imageProxyToBitmap(imageProxy)
                if (bitmap != null) {
                    val estimatedZ = 2.0f // Replace with ARCore plane distance if active

                    capturedQuadrants.add(quadrant)
                    onWallCaptured(quadrant, bitmap, estimatedZ)
                }
            }
        }

        imageProxy.close()
    }
    
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        val yBuffer = image.planes[0].buffer // Y
        val uBuffer = image.planes[1].buffer // U
        val vBuffer = image.planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // U and V are swapped
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
}
