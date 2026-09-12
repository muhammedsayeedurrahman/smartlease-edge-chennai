package com.smartlease.edge.deduction

/**
 * What a single defect costs to put right, and why.
 *
 * `basis` is carried so the PDF can show the arithmetic rather than a bare number: a
 * tenant who is losing money from a deposit is entitled to see how the figure was reached,
 * and a landlord who disputes it needs to know which line to argue with.
 */
data class DeductionLine(
    val description: String,
    val basis: String,
    val amountRupees: Int
)

/**
 * The deposit balance sheet for one inspection session.
 *
 * Deliberately not a running total mutated as findings arrive — [DeductionEngine] builds
 * the whole thing from the persisted findings in one pass, so the same session always
 * produces the same sheet and the report is reproducible from the database alone.
 */
data class DeductionSummary(
    val depositRupees: Int,
    val lines: List<DeductionLine>,
    val totalDeductionRupees: Int,
    val refundRupees: Int,
    /** Findings that cost nothing but belong in the report — a working AC, a solid tile. */
    val clearedNotes: List<String>
) {
    val hasDeductions: Boolean get() = lines.isNotEmpty()
}
