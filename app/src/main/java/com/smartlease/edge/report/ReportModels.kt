package com.smartlease.edge.report

import com.smartlease.edge.deduction.DeductionSummary

data class ReportSection(val title: String, val body: String)

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
    val deductions: DeductionSummary? = null
)
