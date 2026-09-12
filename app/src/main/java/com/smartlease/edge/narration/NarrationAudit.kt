package com.smartlease.edge.narration

import com.smartlease.edge.data.InspectionEntity
import com.smartlease.edge.data.Severity
import com.smartlease.edge.safety.SafetyGate

/**
 * Checks a model-written section body against the findings it claims to describe, and rejects
 * it if it says something they do not support.
 *
 * This is the price of letting a language model write on this document. Everything else on the
 * page is computed from measurements; the narrative is generated, and generated text can
 * assert a hazard nobody detected, a count nobody recorded, or a rupee figure nobody priced.
 * On a deposit report that is not a quality problem, it is a fabricated claim about someone's
 * property and money.
 *
 * The audit is deliberately conservative and deterministic. It cannot tell good prose from
 * bad, and does not try; it only refuses text that contradicts the structured record. When it
 * refuses, the caller falls back to [TemplateReportNarrator], so a rejection costs the report
 * some readability and costs the reader nothing.
 *
 * It runs in the same direction as [SafetyGate]: it can only veto or downgrade a model's
 * output, never let the model's wording suppress something the findings carry.
 */
object NarrationAudit {

    /** Longer than this and the model has stopped summarising and started writing an essay. */
    private const val MAX_CHARS = 1200

    /** A section body this short has not described anything. */
    private const val MIN_CHARS = 20

    /**
     * Currency and bare-number patterns. Pricing belongs to the deduction engine, which prints
     * its own balance sheet from the rate card; a rupee figure appearing in prose would be the
     * model's own arithmetic sitting next to real arithmetic, indistinguishable to a reader.
     */
    private val MONEY = Regex(
        // Symbol-or-word before a figure ("₹2,400", "Rs. 2,400", "INR 2400"), or a figure
        // followed by the word ("2,400 rupees"). `\bRs\b\.?` rather than `\bRs\.?\b`: the
        // latter cannot match "Rs." at all, because there is no word boundary between the
        // full stop and the space that follows it -- which let the most natural way to write
        // a rupee amount in Indian English through the check.
        """(?:(?:₹|\bRs\b\.?|\bINR\b)\s*[\d,]+)|(?:[\d,]+\s*\brupees?\b)""",
        RegexOption.IGNORE_CASE
    )

    sealed interface Result {
        data object Accepted : Result
        data class Rejected(val reason: String) : Result
    }

    fun check(text: String, findings: List<InspectionEntity>): Result {
        val trimmed = text.trim()

        if (trimmed.length < MIN_CHARS) {
            return Result.Rejected("narrative too short (${trimmed.length} chars)")
        }
        if (trimmed.length > MAX_CHARS) {
            return Result.Rejected("narrative too long (${trimmed.length} chars)")
        }
        MONEY.find(trimmed)?.let {
            return Result.Rejected("narrative quotes a money figure (\"${it.value}\"); pricing is the deduction engine's")
        }

        // A hazard word in the prose that no finding carries is the model inventing an
        // emergency. The reverse -- a real STOP_ESCALATE finding the prose fails to mention --
        // is also a rejection: the verdict line above it would then contradict the paragraph.
        val hazardInProse = SafetyGate.evaluate(trimmed).escalatedSeverity == Severity.STOP_ESCALATE
        val hazardInFindings = findings.any { it.severity == Severity.STOP_ESCALATE }
        if (hazardInProse && !hazardInFindings) {
            return Result.Rejected("narrative describes a hazard no finding recorded")
        }
        if (hazardInFindings && !hazardInProse) {
            return Result.Rejected("narrative omits a hazard the findings recorded")
        }

        // A stated count must be the real one. Only counts written as digits are checked:
        // matching spelled-out numbers would need a word-number parser whose own mistakes
        // would start rejecting correct text.
        statedCountOrNull(trimmed)?.let { stated ->
            if (stated != findings.size) {
                return Result.Rejected("narrative says $stated finding(s); there are ${findings.size}")
            }
        }

        return Result.Accepted
    }

    /**
     * The number in a phrase like "3 findings" or "7 items", if the text states one. Null when
     * it does not -- an unstated count is not a wrong count, and demanding one would reject
     * perfectly accurate prose that happens to describe rather than tally.
     */
    private fun statedCountOrNull(text: String): Int? =
        Regex("""\b(\d{1,4})\s+(?:finding|item|defect|issue)s?\b""", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
}
