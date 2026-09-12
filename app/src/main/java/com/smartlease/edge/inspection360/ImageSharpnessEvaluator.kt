package com.smartlease.edge.inspection360

import java.nio.ByteBuffer

object ImageSharpnessEvaluator {
    // 3x3 Laplacian Kernel: [0, 1, 0], [1, -4, 1], [0, 1, 0]
    fun computeLaplacianVariance(yBuffer: ByteBuffer, width: Int, height: Int): Double {
        val bytes = ByteArray(yBuffer.remaining())
        yBuffer.get(bytes)
        val laplacian = DoubleArray((width - 2) * (height - 2))
        var sum = 0.0

        var idx = 0
        for (y in 1 until height - 1) {
            val rowOffset = y * width
            for (x in 1 until width - 1) {
                val center = bytes[rowOffset + x].toInt() and 0xFF
                val up = bytes[rowOffset - width + x].toInt() and 0xFF
                val down = bytes[rowOffset + width + x].toInt() and 0xFF
                val left = bytes[rowOffset + x - 1].toInt() and 0xFF
                val right = bytes[rowOffset + x + 1].toInt() and 0xFF

                val value = (up + down + left + right - (4 * center)).toDouble()
                laplacian[idx++] = value
                sum += value
            }
        }

        if (laplacian.isEmpty()) return 0.0

        val mean = sum / laplacian.size
        var varianceSum = 0.0
        for (v in laplacian) {
            val diff = v - mean
            varianceSum += diff * diff
        }
        return varianceSum / laplacian.size
    }
}
