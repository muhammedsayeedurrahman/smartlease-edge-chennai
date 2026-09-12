package com.smartlease.edge.deduction

import org.json.JSONObject

/**
 * The structured payload behind an `InspectionEntity.detailJson`.
 *
 * Findings used to persist `"{}"`, which meant the database held a human-readable label and
 * nothing a costing engine could act on. Everything the deposit arithmetic depends on now
 * goes in here, so the balance sheet is derived from stored evidence rather than from
 * whatever happened to be on screen at the time.
 */
sealed interface FindingDetail {

    fun toJson(): String

    data class VisualDefect(
        val defectClass: String,
        val areaSqFt: Float,
        val confidence: Float,
        /** True only when the trained YOLOv8n-Seg model produced this, not the colour heuristic. */
        val fromTrainedModel: Boolean
    ) : FindingDetail {
        override fun toJson(): String = JSONObject()
            .put(KEY_KIND, KIND_VISUAL)
            .put("defectClass", defectClass)
            .put("areaSqFt", areaSqFt.toDouble())
            .put("confidence", confidence.toDouble())
            .put("fromTrainedModel", fromTrainedModel)
            .toString()
    }

    data class AcousticTap(
        val verdict: String,
        val confidence: Float,
        val fromTrainedModel: Boolean
    ) : FindingDetail {
        override fun toJson(): String = JSONObject()
            .put(KEY_KIND, KIND_TAP)
            .put("verdict", verdict)
            .put("confidence", confidence.toDouble())
            .put("fromTrainedModel", fromTrainedModel)
            .toString()
    }

    data class ApplianceCheck(
        val appliance: String,
        val functional: Boolean
    ) : FindingDetail {
        override fun toJson(): String = JSONObject()
            .put(KEY_KIND, KIND_APPLIANCE)
            .put("appliance", appliance)
            .put("functional", functional)
            .toString()
    }

    /** Anything with no cost consequence — OCR reads, alignment notes. */
    data class Note(val text: String) : FindingDetail {
        override fun toJson(): String = JSONObject()
            .put(KEY_KIND, KIND_NOTE)
            .put("text", text)
            .toString()
    }

    companion object {
        private const val KEY_KIND = "kind"
        private const val KIND_VISUAL = "visual_defect"
        private const val KIND_TAP = "acoustic_tap"
        private const val KIND_APPLIANCE = "appliance_check"
        private const val KIND_NOTE = "note"

        /**
         * Parses a persisted payload. Returns null for `"{}"`, malformed JSON, or an
         * unrecognised kind — a finding we cannot interpret must be skipped by the costing
         * engine, never guessed at.
         */
        fun fromJson(json: String): FindingDetail? {
            val obj = try {
                JSONObject(json)
            } catch (e: Exception) {
                return null
            }
            return when (obj.optString(KEY_KIND)) {
                KIND_VISUAL -> VisualDefect(
                    defectClass = obj.optString("defectClass"),
                    areaSqFt = obj.optDouble("areaSqFt", 0.0).toFloat(),
                    confidence = obj.optDouble("confidence", 0.0).toFloat(),
                    fromTrainedModel = obj.optBoolean("fromTrainedModel", false)
                )
                KIND_TAP -> AcousticTap(
                    verdict = obj.optString("verdict"),
                    confidence = obj.optDouble("confidence", 0.0).toFloat(),
                    fromTrainedModel = obj.optBoolean("fromTrainedModel", false)
                )
                KIND_APPLIANCE -> ApplianceCheck(
                    appliance = obj.optString("appliance"),
                    functional = obj.optBoolean("functional", false)
                )
                KIND_NOTE -> Note(obj.optString("text"))
                else -> null
            }
        }
    }
}
