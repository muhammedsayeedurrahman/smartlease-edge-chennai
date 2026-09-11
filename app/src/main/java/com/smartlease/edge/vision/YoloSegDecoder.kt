package com.smartlease.edge.vision

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Turns raw YOLOv8n-Seg tensors into defect detections with real surface areas.
 *
 * Deliberately free of Android types so it can be unit-tested on the JVM — the geometry and
 * the area arithmetic are the parts worth testing, and they do not need a device.
 *
 * Inputs match the export contract in ml/YOLOV8/convert_yolov8_seg_qnn_pte.py:
 *   preds  [1, 40, 8400]      4 box + 4 class scores + 32 mask coefficients
 *   protos [1, 32, 160, 160]  32 prototype mask channels
 *
 * Masks matter more than boxes here: a diagonal crack fills a small fraction of its bounding
 * box, so charging a tenant for the box area would overstate the damage several times over.
 * Area is therefore measured from mask coverage, not from the box.
 */
object YoloSegDecoder {

    data class BoxF(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
        val area: Float get() = max(0f, width) * max(0f, height)
    }

    data class Detection(
        val box: BoxF,
        val classIndex: Int,
        val score: Float,
        /** Fraction of the visible frame covered by this defect's mask, in [0, 1]. */
        val coverageFraction: Float
    )

    /**
     * @param preds  flat [40 * 8400], channel-major: value(c, a) at c * ANCHORS + a
     * @param protos flat [32 * 160 * 160], value(k, y, x) at k * 160 * 160 + y * 160 + x
     */
    fun decode(
        preds: FloatArray,
        protos: FloatArray,
        letterbox: Letterbox,
        scoreThreshold: Float = YoloSegConfig.SCORE_THRESHOLD,
        iouThreshold: Float = YoloSegConfig.NMS_IOU_THRESHOLD,
        maxDetections: Int = YoloSegConfig.MAX_DETECTIONS
    ): List<Detection> {
        val expectedPreds = YoloSegConfig.PRED_CHANNELS * YoloSegConfig.ANCHORS
        require(preds.size == expectedPreds) {
            "preds must be $expectedPreds floats (40x8400), got ${preds.size}"
        }
        val expectedProtos = YoloSegConfig.MASK_COEFFS * YoloSegConfig.PROTO_SIZE * YoloSegConfig.PROTO_SIZE
        require(protos.size == expectedProtos) {
            "protos must be $expectedProtos floats (32x160x160), got ${protos.size}"
        }

        val candidates = collectCandidates(preds, scoreThreshold, letterbox)
        val kept = nonMaxSuppression(candidates, iouThreshold, maxDetections)
        val contentCells = letterbox.contentAreaInProtoCells()

        return kept.map { c ->
            Detection(
                box = c.box,
                classIndex = c.classIndex,
                score = c.score,
                coverageFraction = maskCoverage(protos, c, contentCells)
            )
        }
    }

    /** A surviving anchor, with its box already mapped back to source-image coordinates. */
    private data class Candidate(
        val box: BoxF,
        val inputBox: BoxF,
        val classIndex: Int,
        val score: Float,
        val coeffs: FloatArray
    )

    private fun collectCandidates(
        preds: FloatArray,
        scoreThreshold: Float,
        letterbox: Letterbox
    ): List<Candidate> {
        val anchors = YoloSegConfig.ANCHORS
        val out = mutableListOf<Candidate>()

        for (a in 0 until anchors) {
            var bestClass = -1
            var bestScore = scoreThreshold
            for (c in 0 until YoloSegConfig.NUM_CLASSES) {
                val s = preds[(YoloSegConfig.BOX_CHANNELS + c) * anchors + a]
                if (s > bestScore) {
                    bestScore = s
                    bestClass = c
                }
            }
            if (bestClass < 0) continue

            // Box arrives as centre-x, centre-y, width, height in 640-input pixels.
            val cx = preds[0 * anchors + a]
            val cy = preds[1 * anchors + a]
            val w = preds[2 * anchors + a]
            val h = preds[3 * anchors + a]
            val inputBox = BoxF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)

            val coeffs = FloatArray(YoloSegConfig.MASK_COEFFS) { k ->
                preds[(YoloSegConfig.BOX_CHANNELS + YoloSegConfig.NUM_CLASSES + k) * anchors + a]
            }

            out += Candidate(
                box = BoxF(
                    letterbox.sourceX(inputBox.left), letterbox.sourceY(inputBox.top),
                    letterbox.sourceX(inputBox.right), letterbox.sourceY(inputBox.bottom)
                ),
                inputBox = inputBox,
                classIndex = bestClass,
                score = bestScore,
                coeffs = coeffs
            )
        }
        return out
    }

    /** Greedy NMS, applied per class so a crack never suppresses an overlapping stain. */
    private fun nonMaxSuppression(
        candidates: List<Candidate>,
        iouThreshold: Float,
        maxDetections: Int
    ): List<Candidate> {
        val sorted = candidates.sortedByDescending { it.score }
        val kept = mutableListOf<Candidate>()
        for (c in sorted) {
            if (kept.size >= maxDetections) break
            val overlaps = kept.any { k ->
                k.classIndex == c.classIndex && iou(k.box, c.box) > iouThreshold
            }
            if (!overlaps) kept += c
        }
        return kept
    }

    internal fun iou(a: BoxF, b: BoxF): Float {
        val interW = min(a.right, b.right) - max(a.left, b.left)
        val interH = min(a.bottom, b.bottom) - max(a.top, b.top)
        if (interW <= 0f || interH <= 0f) return 0f
        val inter = interW * interH
        val union = a.area + b.area - inter
        return if (union <= 0f) 0f else inter / union
    }

    /**
     * Assemble this detection's mask (a coefficient-weighted sum of the 32 prototypes),
     * crop it to the detection box, and return covered cells as a fraction of the visible
     * frame. Cropping is what stops one object's prototype energy leaking across the wall.
     */
    private fun maskCoverage(protos: FloatArray, c: Candidate, contentCells: Float): Float {
        if (contentCells <= 0f) return 0f
        val size = YoloSegConfig.PROTO_SIZE
        val stride = YoloSegConfig.PROTO_STRIDE

        // Detection box in prototype-grid coordinates, clamped to the grid.
        val x0 = (c.inputBox.left / stride).roundToInt().coerceIn(0, size - 1)
        val y0 = (c.inputBox.top / stride).roundToInt().coerceIn(0, size - 1)
        val x1 = (c.inputBox.right / stride).roundToInt().coerceIn(x0 + 1, size)
        val y1 = (c.inputBox.bottom / stride).roundToInt().coerceIn(y0 + 1, size)

        val plane = size * size
        var covered = 0
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                var sum = 0f
                for (k in 0 until YoloSegConfig.MASK_COEFFS) {
                    sum += c.coeffs[k] * protos[k * plane + y * size + x]
                }
                if (sigmoid(sum) >= YoloSegConfig.MASK_THRESHOLD) covered++
            }
        }
        return (covered / contentCells).coerceIn(0f, 1f)
    }

    private fun sigmoid(x: Float): Float = 1f / (1f + exp(-x))
}
