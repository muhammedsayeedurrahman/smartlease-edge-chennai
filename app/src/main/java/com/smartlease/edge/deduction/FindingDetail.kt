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
        val functional: Boolean,
        /**
         * False when the IR command was never actually sent -- e.g. `transmit()` threw before
         * any signal left the device. `functional` alone cannot express this: it is a claim
         * about the appliance, and a tool failure is not evidence about the appliance at all.
         * Defaults to true because the success call site never had to think about this. A row
         * persisted before this field existed has no key for it, but is not ambiguous: the
         * only two writers back then were the success branch (`functional = true`) and the
         * failure branch (`functional = false`), so a legacy row's `irTransmitted` is recovered
         * from its own `functional` value rather than assumed true.
         */
        val irTransmitted: Boolean = true,
        /** Why `transmit()` failed, when [irTransmitted] is false. Null otherwise. */
        val failureReason: String? = null
    ) : FindingDetail {
        override fun toJson(): String = JSONObject()
            .put(KEY_KIND, KIND_APPLIANCE)
            .put("appliance", appliance)
            .put("functional", functional)
            .put("irTransmitted", irTransmitted)
            .put("failureReason", failureReason)
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
                KIND_APPLIANCE -> {
                    val functional = obj.optBoolean("functional", false)
                    ApplianceCheck(
                        appliance = obj.optString("appliance"),
                        functional = functional,
                        // A legacy row (key absent) only ever came from one of two writers, and
                        // functional = false only ever came from the failure branch -- so the
                        // row's own functional value is the correct stand-in for irTransmitted.
                        irTransmitted = obj.optBoolean("irTransmitted", functional),
                        // opt() returns null both when the key is absent (legacy rows) and when a
                        // successful transmit stored no reason -- both cases mean "no reason".
                        failureReason = obj.opt("failureReason") as? String
                    )
                }
                KIND_NOTE -> Note(obj.optString("text"))
                else -> null
            }
        }
    }
}
