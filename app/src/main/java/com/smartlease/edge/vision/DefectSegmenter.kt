package com.smartlease.edge.vision

import android.graphics.Bitmap
import android.graphics.RectF

/**
 * Vision defect segmenter — interface-first, HONESTLY STUBBED.
 *
 * Per the design doc's own risk assessment, this is the highest-risk subsystem: exporting a
 * fine-tuned YOLOv8-Seg model to ExecuTorch .pte format and getting it through the Hexagon
 * NPU delegate is real ML-engineering work that has not happened yet (12-day plan, Days 3-8).
 * Rather than fake a working model here, this file defines the exact contract the real
 * implementation will satisfy, plus a heuristic fallback that is honestly labeled as such —
 * matching the design doc's own fallback recommendation: "basic color/contrast-based
 * highlighting of dark or discolored regions... no custom-trained model required."
 *
 * Swap HeuristicDefectSegmenter for a real TfliteDefectSegmenter (ExecuTorch/.pte-backed)
 * once Days 6-8 of the plan produce a hardware-tested model, without changing any caller —
 * that's the entire point of coding to this interface now.
 */
interface DefectSegmenter {
    /**
     * @param label tenant-facing description, e.g. "crack in wall surface". Display only.
     * @param defectClass canonical class key, e.g. "crack". This — never [label] — is what
     *   the costing engine looks up, because the rate card is written in the model's own
     *   vocabulary. Passing the description here prices nothing and fails silently, which
     *   is exactly what happened before RepairTariffCoverageTest existed.
     */
    data class Defect(
        val boundingBox: RectF,
        val label: String,
        val defectClass: String,
        val areaSqFtEstimate: Float,
        val confidence: Float
    )

    /** @param frameWidthInches / frameHeightInches — real-world size of the photographed area, for sq-ft conversion. */
    fun segmentDefects(bitmap: Bitmap, frameWidthInches: Float, frameHeightInches: Float): List<Defect>

    val isTrainedModel: Boolean
}

/**
 * Placeholder implementation used until the real trained/exported model exists.
 * Flags large contiguous dark/discolored regions via basic pixel sampling — genuinely
 * runs, genuinely on-device, genuinely NOT the vision-segmenter novelty claim the pitch
 * makes. `isTrainedModel = false` exists specifically so the UI layer can show an honest
 * "heuristic mode" badge instead of silently overclaiming.
 */
class HeuristicDefectSegmenter : DefectSegmenter {

    override val isTrainedModel = false

    override fun segmentDefects(bitmap: Bitmap, frameWidthInches: Float, frameHeightInches: Float): List<DefectSegmenter.Defect> {
        val gridCols = 12
        val gridRows = 16
        val cellW = bitmap.width / gridCols
        val cellH = bitmap.height / gridRows
        val flaggedCells = mutableListOf<Pair<Int, Int>>()

        for (row in 0 until gridRows) {
            for (col in 0 until gridCols) {
                val cx = (col * cellW + cellW / 2).coerceIn(0, bitmap.width - 1)
                val cy = (row * cellH + cellH / 2).coerceIn(0, bitmap.height - 1)
                val pixel = bitmap.getPixel(cx, cy)
                if (looksLikeStainOrDamage(pixel)) flaggedCells += col to row
            }
        }

        if (flaggedCells.isEmpty()) return emptyList()

        val minCol = flaggedCells.minOf { it.first }
        val maxCol = flaggedCells.maxOf { it.first }
        val minRow = flaggedCells.minOf { it.second }
        val maxRow = flaggedCells.maxOf { it.second }

        val box = RectF(
            (minCol * cellW).toFloat(), (minRow * cellH).toFloat(),
            ((maxCol + 1) * cellW).toFloat(), ((maxRow + 1) * cellH).toFloat()
        )

        val fractionOfFrame = flaggedCells.size.toFloat() / (gridCols * gridRows)
        val areaSqFt = fractionOfFrame * (frameWidthInches / 12f) * (frameHeightInches / 12f)

        return listOf(
            DefectSegmenter.Defect(
                boundingBox = box,
                label = "possible discoloration/damage (heuristic, unverified)",
                // Deliberately not a model class name: a heuristic hit must never match the
                // rate card, and `fromTrainedModel = false` already blocks pricing upstream.
                defectClass = HEURISTIC_CLASS,
                areaSqFtEstimate = areaSqFt,
                confidence = 0.4f // deliberately capped low — this is a placeholder, not a trained detector
            )
        )
    }

    private fun looksLikeStainOrDamage(pixel: Int): Boolean {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        val brightness = (r + g + b) / 3
        // Dark or brownish/yellowish patches read as candidate water-stain/mold regions.
        // This is a crude color heuristic, not a model — see class doc.
        val isDark = brightness < 90
        val isBrownish = r > g && g > b && (r - b) > 20
        return isDark || isBrownish
    }

    private companion object {
        /** Not a model class, and deliberately absent from the rate card. */
        const val HEURISTIC_CLASS = "possible discoloration/damage"
    }
}
