package com.smartlease.edge.narration

import com.smartlease.edge.data.InspectionEntity

/**
 * The narrator that has always been here: a sentence assembled from the findings themselves.
 *
 * Kept as a first-class implementation rather than deleted once Gemma arrived, for two
 * reasons. It is the fallback whenever the model is absent or its output fails the audit, so
 * the report pipeline has no configuration in which it cannot produce a page. And it is the
 * honest baseline -- it cannot state anything the findings do not contain, because every word
 * of it is either a constant or a value copied out of a finding.
 */
object TemplateReportNarrator : ReportNarrator {

    override suspend fun narrate(sectionTitle: String, findings: List<InspectionEntity>): Narration =
        Narration(text = compose(findings), source = NarrationSource.TEMPLATE)

    /**
     * Exposed so [GemmaReportNarrator] can fall back to exactly this text rather than to an
     * apology, and so the fallback is provably the same prose the template path produces.
     */
    fun compose(findings: List<InspectionEntity>): String {
        val count = findings.size
        val severities = findings.groupingBy { it.severity }.eachCount()
        val labels = findings.joinToString("; ") { it.label }
        return "$count finding(s) recorded. Items: $labels. Severity breakdown: $severities."
    }
}
