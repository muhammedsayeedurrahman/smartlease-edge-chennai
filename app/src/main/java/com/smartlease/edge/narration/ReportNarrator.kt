package com.smartlease.edge.narration

import com.smartlease.edge.data.InspectionEntity

/**
 * Produces the prose body of one report section.
 *
 * Two implementations exist and the difference is visible to the reader, not hidden:
 * [TemplateReportNarrator] assembles a sentence from the structured findings, and
 * [GemmaReportNarrator] asks an on-device Gemma model to write it. Which one ran is carried
 * on [Narration.source] and printed on the PDF, because "a model wrote this paragraph" and
 * "a format string wrote this paragraph" are different claims about a document someone signs,
 * and the reader is entitled to know which they are holding.
 *
 * The seam exists at all because the narrative is the one part of the report that is not a
 * direct rendering of measured data. Everything else on the page -- the digest, the areas,
 * the confidences, the rupee arithmetic -- is computed. The narrative is written, and a
 * written sentence can say something the findings do not support. Hence [NarrationAudit],
 * which every implementation's output passes through before it reaches a page.
 */
interface ReportNarrator {

    /**
     * @param findings the findings for one section, already filtered by finding type. Never
     * empty -- a section with no findings is not built at all.
     * @return prose for this section. Implementations must not throw: a narrator that cannot
     * produce text returns [Narration] from the template fallback rather than failing the
     * report, because a report that fails to render is worse than a plainly-worded one.
     */
    suspend fun narrate(sectionTitle: String, findings: List<InspectionEntity>): Narration

    /** Releases any native resources. Safe to call more than once. */
    fun close() {}
}

/**
 * A section body plus an honest account of where it came from.
 *
 * @param text the prose to print.
 * @param source which narrator produced [text] -- after any fallback, so a Gemma narrator
 * that failed and fell back reports [NarrationSource.TEMPLATE], never GEMMA.
 * @param note a short human-readable reason when [source] is not what was configured (model
 * file missing, model failed to load, output rejected by the audit). Printed in diagnostics,
 * not on the tenant-facing page, and null when nothing went wrong.
 */
data class Narration(
    val text: String,
    val source: NarrationSource,
    val note: String? = null
)

enum class NarrationSource {
    /** Rule-based string assembly. Deterministic; cannot invent a finding. */
    TEMPLATE,

    /** Written by an on-device Gemma model via MediaPipe tasks-genai, and audited. */
    GEMMA,

    /** Written by an on-device Gemma model via LiteRT GenAI (.litertlm), and audited. */
    LITERT_GEMMA
}
