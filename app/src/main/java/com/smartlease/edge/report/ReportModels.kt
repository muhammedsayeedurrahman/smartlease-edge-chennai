package com.smartlease.edge.report

import com.smartlease.edge.data.SessionType
import com.smartlease.edge.deduction.DeductionSummary
import com.smartlease.edge.narration.NarrationSource

/**
 * @param narrationSource who wrote [body]. Carried onto the section rather than inferred at
 * render time, because the PDF states it to the reader and a report must not be able to
 * describe its own prose as model-written after falling back to a template.
 */
data class ReportSection(
    val title: String,
    val body: String,
    val narrationSource: NarrationSource = NarrationSource.TEMPLATE
)

data class InspectionReport(
    val sessionId: String,
    val generatedAtEpochMillis: Long,
    val propertyLabel: String,
    val sections: List<ReportSection>,
    val overallVerdict: String,
    /** Lowercase hex SHA-256 over the canonical findings — see [FindingsDigest]. */
    val findingsSha256: String,
    val findingCount: Int,
    /** Null when the session recorded no deposit, e.g. a maintenance walkthrough. */
    val deductions: DeductionSummary? = null,
    /** Null only when [findingCount] is 0. Feeds the Section 63 certificate's capture window. */
    val earliestFindingEpochMillis: Long? = null,
    val latestFindingEpochMillis: Long? = null,
    /**
     * Which kind of walkthrough produced this report. Defaulted to [SessionType.MOVE_OUT]
     * only for source compatibility with existing call sites that predate this field --
     * [com.smartlease.edge.ui.screens.WalkthroughScreen] always passes the real value it
     * already has in scope. Consumed by [com.smartlease.edge.sync] so an uploaded report is
     * tagged with the same session type the walkthrough was actually run as, never guessed
     * after the fact.
     */
    val sessionType: SessionType = SessionType.MOVE_OUT
)
