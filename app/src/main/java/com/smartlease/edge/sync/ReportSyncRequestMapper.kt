package com.smartlease.edge.sync

import com.smartlease.edge.report.InspectionReport

/**
 * Translates the app's own report model into the wire request this package sends. Kept as a
 * pure, Android-free mapping so it is unit-testable on the JVM without standing up a
 * composable or an Android context.
 */

/** Local session-type vocabulary and the wire vocabulary share the same two names by design. */
fun com.smartlease.edge.data.SessionType.toSyncSessionType(): SessionType = when (this) {
    com.smartlease.edge.data.SessionType.MOVE_IN -> SessionType.MOVE_IN
    com.smartlease.edge.data.SessionType.MOVE_OUT -> SessionType.MOVE_OUT
}

/**
 * Builds the outgoing upload request for this report.
 *
 * [ReportUploadRequest.digestSha256] is [InspectionReport.findingsSha256] verbatim -- never
 * recomputed here -- because the whole point of syncing is that the server ends up holding
 * the exact same digest the PDF and QR code on this device already commit to. Recomputing it
 * from the findings a second time would defeat that: a subtle bug in a second code path could
 * then upload a digest that quietly disagrees with the one already shown to the user.
 *
 * [ReportUploadRequest.depositRupees] and [ReportUploadRequest.totalDeductionRupees] are 0
 * when [InspectionReport.deductions] is null (a session with no deposit to track, e.g. a
 * maintenance walkthrough) -- there is no rupee figure to report, not a hidden negative one.
 */
fun InspectionReport.toReportUploadRequest(
    propertyRef: String,
    appVersion: String
): ReportUploadRequest = ReportUploadRequest(
    reportId = sessionId,
    propertyRef = propertyRef,
    sessionType = sessionType.toSyncSessionType(),
    createdAtEpochMs = generatedAtEpochMillis,
    digestSha256 = findingsSha256,
    appVersion = appVersion,
    findingsCount = findingCount,
    depositRupees = deductions?.depositRupees ?: 0,
    totalDeductionRupees = deductions?.totalDeductionRupees ?: 0
)
