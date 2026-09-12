package com.smartlease.edge.deduction

import com.smartlease.edge.data.FindingType
import com.smartlease.edge.data.InspectionEntity

/**
 * Turns a session's stored findings into a deposit balance sheet.
 *
 * The rule that governs this whole file: **money is only ever charged against evidence the
 * system is confident in.** A colour-heuristic hit, or a trained detection below
 * [PRICING_CONFIDENCE_FLOOR], is reported as something a human should look at — it never
 * silently becomes a rupee figure deducted from someone's deposit. Our own validation puts
 * mask mAP50 at 0.27 across four classes (`ml/YOLOV8/val_metrics.json`), which is nowhere
 * near good enough to bill on unreviewed, and the report has to behave accordingly.
 *
 * Pure and stateless: same findings in, same sheet out, no device or database access.
 */
object DeductionEngine {

    /** Below this confidence a trained detection is flagged for review, not priced. */
    const val PRICING_CONFIDENCE_FLOOR = 0.60f

    fun summarise(depositRupees: Int, findings: List<InspectionEntity>): DeductionSummary {
        val details = findings.map { it to FindingDetail.fromJson(it.detailJson) }

        val lines = buildList {
            details.forEach { (entity, detail) ->
                when (detail) {
                    is FindingDetail.VisualDefect -> visualLine(detail)?.let { add(it) }
                    is FindingDetail.AcousticTap -> tapLine(detail)?.let { add(it) }
                    is FindingDetail.ApplianceCheck -> applianceLine(detail)?.let { add(it) }
                    else -> if (detail == null && entity.findingType == FindingType.VISUAL_DEFECT) {
                        // Legacy row written before structured details existed. Not priced.
                        Unit
                    }
                }
            }
        }

        val cleared = buildList {
            details.forEach { (_, detail) ->
                when (detail) {
                    is FindingDetail.VisualDefect ->
                        if (!isPriceable(detail)) add(reviewNote(detail))
                    is FindingDetail.AcousticTap ->
                        if (detail.verdict != HOLLOW) add(tapNote(detail))
                    is FindingDetail.ApplianceCheck -> when {
                        !detail.irTransmitted -> add(applianceNotAssessedNote(detail))
                        detail.functional -> add(
                            "${detail.appliance}: IR command transmitted — response not verified " +
                                "(Android cannot receive IR), no deduction."
                        )
                        else -> Unit
                    }
                    else -> Unit
                }
            }
        }

        val total = lines.sumOf { it.amountRupees }
        return DeductionSummary(
            depositRupees = depositRupees,
            lines = lines,
            totalDeductionRupees = total,
            refundRupees = depositRupees - total,
            clearedNotes = cleared
        )
    }

    private const val HOLLOW = "hollow"

    private fun isPriceable(d: FindingDetail.VisualDefect): Boolean =
        d.fromTrainedModel &&
            d.confidence >= PRICING_CONFIDENCE_FLOOR &&
            RepairTariff.rateFor(d.defectClass) != null

    private fun visualLine(d: FindingDetail.VisualDefect): DeductionLine? {
        if (!isPriceable(d)) return null
        val cost = RepairTariff.costOf(d.defectClass, d.areaSqFt) ?: return null
        val basis = RepairTariff.basisFor(d.defectClass, d.areaSqFt) ?: return null
        return DeductionLine(
            description = d.defectClass.replaceFirstChar { it.uppercase() },
            basis = "$basis · detected at %.0f%% confidence".format(d.confidence * 100f),
            amountRupees = cost
        )
    }

    private fun tapLine(d: FindingDetail.AcousticTap): DeductionLine? {
        if (d.verdict != HOLLOW) return null
        return DeductionLine(
            description = "Hollow tile",
            basis = "${tapConfidenceBasis(d)} · lift and re-bed one tile",
            amountRupees = RepairTariff.HOLLOW_TILE_RUPEES
        )
    }

    /**
     * A hollow verdict bills the same [RepairTariff.HOLLOW_TILE_RUPEES] regardless of source,
     * but what the sentence says about it must not overstate either direction: a heuristic
     * reading has no real probability behind it, so printing "0% confidence" would understate
     * it exactly as much as inventing a number would overstate a trained one. Say which
     * measurement produced the verdict instead of forcing both down the same
     * percentage-confidence sentence.
     */
    private fun tapConfidenceBasis(d: FindingDetail.AcousticTap): String = if (d.fromTrainedModel) {
        "Acoustic tap test returned hollow at %.0f%% confidence".format(d.confidence * 100f)
    } else {
        "Acoustic tap test returned hollow (decay/frequency heuristic -- no confidence score)"
    }

    /**
     * A tool failure is not evidence about the appliance. `irTransmitted = false` means the
     * IR command never left the device -- nothing was ever asked to respond, so there is
     * nothing here to bill. That case is routed to [applianceNotAssessedNote] instead; this
     * function only ever prices a check where the command actually went out.
     */
    private fun applianceLine(d: FindingDetail.ApplianceCheck): DeductionLine? {
        if (!d.irTransmitted) return null
        if (d.functional) return null
        return DeductionLine(
            description = "${d.appliance} not functional",
            basis = "Did not respond to IR command during inspection · service call",
            amountRupees = RepairTariff.APPLIANCE_NOT_FUNCTIONAL_RUPEES
        )
    }

    private fun applianceNotAssessedNote(d: FindingDetail.ApplianceCheck): String {
        val reasonSuffix = d.failureReason?.let { " ($it)" }.orEmpty()
        return "${d.appliance}: IR command could not be transmitted$reasonSuffix — " +
            "condition was not determined, no deduction."
    }

    private fun reviewNote(d: FindingDetail.VisualDefect): String = when {
        !d.fromTrainedModel ->
            "${d.defectClass}: flagged by the colour heuristic, not the trained model — listed for human review, not priced."
        RepairTariff.rateFor(d.defectClass) == null ->
            "${d.defectClass}: no agreed rate for this defect type — listed for human review, not priced."
        else ->
            "${d.defectClass}: detected at %.0f%%, below the %.0f%% pricing threshold — listed for human review, not priced."
                .format(d.confidence * 100f, PRICING_CONFIDENCE_FLOOR * 100f)
    }

    private fun tapNote(d: FindingDetail.AcousticTap): String = when (d.verdict) {
        "solid" -> "Tap test: solid — no void detected, no deduction."
        else -> "Tap test: inconclusive — re-test or inspect manually, not priced."
    }
}
