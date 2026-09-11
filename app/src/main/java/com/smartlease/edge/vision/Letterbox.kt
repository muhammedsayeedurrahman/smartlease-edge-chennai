package com.smartlease.edge.vision

import kotlin.math.roundToInt

/**
 * Aspect-preserving resize geometry for the 640x640 model input.
 *
 * A plain scale-to-square would distort walls and corrupt the sq-ft estimate, which is the
 * number the report actually argues about. So the frame is scaled by a single factor and
 * centred, leaving grey padding on two sides. Everything here is pure arithmetic and
 * immutable — the padding must be subtracted back out when mapping detections to the
 * original image, and forgetting to do that is the classic source of boxes that sit
 * slightly off the defect.
 */
data class Letterbox(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val scale: Float,
    val padX: Int,
    val padY: Int,
    val contentWidth: Int,
    val contentHeight: Int
) {
    /** Map an x coordinate from model input space back to source-image space. */
    fun sourceX(inputX: Float): Float = ((inputX - padX) / scale).coerceIn(0f, sourceWidth.toFloat())

    /** Map a y coordinate from model input space back to source-image space. */
    fun sourceY(inputY: Float): Float = ((inputY - padY) / scale).coerceIn(0f, sourceHeight.toFloat())

    /**
     * Area of real frame content, measured in prototype-mask cells. Mask pixel counts are
     * divided by this (not by the full 160x160 grid) so padding never inflates coverage.
     */
    fun contentAreaInProtoCells(): Float {
        val w = contentWidth.toFloat() / YoloSegConfig.PROTO_STRIDE
        val h = contentHeight.toFloat() / YoloSegConfig.PROTO_STRIDE
        return w * h
    }

    companion object {
        fun forSource(sourceWidth: Int, sourceHeight: Int, target: Int = YoloSegConfig.INPUT_SIZE): Letterbox {
            require(sourceWidth > 0 && sourceHeight > 0) {
                "Source dimensions must be positive, got ${sourceWidth}x$sourceHeight"
            }
            val scale = minOf(target.toFloat() / sourceWidth, target.toFloat() / sourceHeight)
            val contentWidth = (sourceWidth * scale).roundToInt().coerceAtMost(target)
            val contentHeight = (sourceHeight * scale).roundToInt().coerceAtMost(target)
            return Letterbox(
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                scale = scale,
                padX = (target - contentWidth) / 2,
                padY = (target - contentHeight) / 2,
                contentWidth = contentWidth,
                contentHeight = contentHeight
            )
        }
    }
}
