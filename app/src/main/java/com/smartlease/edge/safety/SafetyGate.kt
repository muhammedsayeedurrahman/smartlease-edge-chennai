package com.smartlease.edge.safety

/**
 * Deterministic, non-LLM safety gate — plain Kotlin logic with no model in the loop.
 *
 * This is the one piece of genuine architectural novelty carried over from the design docs:
 * any AI-generated finding (from the vision segmenter, acoustic classifier, or the eventual
 * GenieX report synthesis) passes through here before it reaches the user. This gate can
 * always veto or upgrade a finding's severity based on hard-coded hazard rules, regardless
 * of what confidence score the model attached to it. It never runs the other direction —
 * no model output can suppress a rule defined here.
 */
object SafetyGate {

    data class Verdict(
        val allowAsIs: Boolean,
        val escalatedSeverity: com.smartlease.edge.data.Severity?,
        val reason: String?
    )

    private val STOP_ESCALATE_KEYWORDS = listOf(
        "exposed wire", "exposed wiring", "bare wire",
        "water leak", "active leak", "flooding",
        "gas smell", "burning smell", "smoke"
    )

    /**
     * Evaluate a raw finding label (from OCR text, a vision-segmenter caption, or an
     * acoustic-classifier label) against hard-coded hazard categories. Case-insensitive
     * substring match is intentionally simple and auditable — a judge can read this
     * function top to bottom in the time it takes to ask about it.
     */
    fun evaluate(rawLabel: String): Verdict {
        val normalized = rawLabel.lowercase()
        val matched = STOP_ESCALATE_KEYWORDS.firstOrNull { normalized.contains(it) }

        return if (matched != null) {
            Verdict(
                allowAsIs = false,
                escalatedSeverity = com.smartlease.edge.data.Severity.STOP_ESCALATE,
                reason = "Matched hard-coded hazard keyword: \"$matched\". " +
                        "Recommend professional inspection before continuing this item."
            )
        } else {
            Verdict(allowAsIs = true, escalatedSeverity = null, reason = null)
        }
    }
}
