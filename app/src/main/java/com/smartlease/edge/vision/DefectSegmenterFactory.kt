package com.smartlease.edge.vision

import android.content.Context
import android.util.Log

/**
 * Chooses the best available defect segmenter, and is honest about which one it picked.
 *
 * The trained YOLOv8n-Seg model is preferred. If it cannot be loaded — asset missing, native
 * library unavailable on this device, corrupt export — we fall back to the colour heuristic
 * rather than failing the walkthrough. Callers read [DefectSegmenter.isTrainedModel] to label
 * findings correctly, so a fallback never gets reported as a model detection.
 */
object DefectSegmenterFactory {

    private const val TAG = "DefectSegmenterFactory"

    fun create(context: Context): DefectSegmenter {
        val trained = YoloSegDefectSegmenter.create(context)
        if (trained != null) {
            Log.i(TAG, "Using trained YOLOv8n-Seg segmenter")
            return trained
        }
        Log.w(TAG, "Trained model unavailable - falling back to heuristic segmenter")
        return HeuristicDefectSegmenter()
    }
}
