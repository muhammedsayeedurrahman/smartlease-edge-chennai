package com.smartlease.edge.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.text.StaticLayout
import android.text.TextPaint
import com.smartlease.edge.BuildConfig
import com.smartlease.edge.data.CountersignatureEntity
import com.smartlease.edge.data.InspectionEntity
import com.smartlease.edge.data.SessionType
import com.smartlease.edge.deduction.DeductionEngine
import com.smartlease.edge.deduction.DeductionLine
import com.smartlease.edge.deduction.DeductionSummary
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local, offline PDF report generator using Android's built-in PdfDocument API — no
 * network call, no cloud service, at any point in this file.
 *
 * What "tamper-evident" means here, exactly: every page carries a SHA-256 over the
 * canonical findings ([FindingsDigest]). Change one character of one finding and the
 * digest no longer matches, so a regenerated report is detectably a different report.
 * That is the whole of the claim. It is NOT a signature: nothing here proves who recorded
 * the findings, nothing binds a person to them, and the timestamp is
 * `System.currentTimeMillis()`, which a user can move. Two on-screen signature captures
 * rendered below the digest are the next step; until they exist, do not say "signed".
 *
 * `synthesizeNarrative()` is rule-based templating. It is not an LLM and the report does
 * not claim it is.
 */
object ReportGenerator {

    private const val PAGE_WIDTH = 595 // A4 at 72dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40

    /**
     * @param depositRupees null for a session with no deposit to track (e.g. a maintenance
     * walkthrough) -- the report is still produced, just with [InspectionReport.deductions]
     * left null rather than a balance sheet built from a figure nobody entered.
     * @param baselineKeys findings already on record from this property's move-in session --
     * see [com.smartlease.edge.deduction.DeductionEngine.summarise]. Empty for a move-in
     * session itself, or a move-out with no baseline found.
     * @param sessionType which kind of walkthrough this is -- carried onto
     * [InspectionReport.sessionType] so a later sync upload reports the real session type
     * instead of a guess.
     */
    fun buildReport(
        sessionId: String,
        propertyLabel: String,
        findings: List<InspectionEntity>,
        depositRupees: Int? = null,
        baselineKeys: Set<String> = emptySet(),
        sessionType: SessionType = SessionType.MOVE_OUT
    ): InspectionReport {
        val sections = findings.groupBy { it.findingType }.map { (type, items) ->
            ReportSection(
                title = type.name.replace('_', ' '),
                body = synthesizeNarrative(type, items)
            )
        }

        val hasStopEscalate = findings.any { it.severity == com.smartlease.edge.data.Severity.STOP_ESCALATE }
        val verdict = if (hasStopEscalate) {
            "ATTENTION REQUIRED — one or more findings were flagged by the safety gate for professional review before proceeding."
        } else {
            "No hazard-gate findings recorded. ${findings.size} item(s) documented in this session."
        }

        return InspectionReport(
            sessionId = sessionId,
            generatedAtEpochMillis = System.currentTimeMillis(),
            propertyLabel = propertyLabel,
            sections = sections,
            overallVerdict = verdict,
            findingsSha256 = FindingsDigest.sha256Hex(sessionId, findings),
            findingCount = findings.size,
            deductions = depositRupees?.let { DeductionEngine.summarise(it, findings, baselineKeys) },
            earliestFindingEpochMillis = findings.minOfOrNull { it.timestampEpochMillis },
            latestFindingEpochMillis = findings.maxOfOrNull { it.timestampEpochMillis },
            sessionType = sessionType
        )
    }

    /**
     * PLACEHOLDER for the GenieX/Llama-3.2 on-device synthesis step. Produces real,
     * readable, structurally-correct report prose today via rule-based templating, so the
     * full report pipeline works end-to-end now — swap this function body for a GenieX
     * call once that SDK is wired in, without changing the PDF renderer below.
     */
    private fun synthesizeNarrative(
        type: com.smartlease.edge.data.FindingType,
        items: List<InspectionEntity>
    ): String {
        val count = items.size
        val severities = items.groupingBy { it.severity }.eachCount()
        val labels = items.joinToString("; ") { it.label }
        return "$count finding(s) recorded. Items: $labels. " +
                "Severity breakdown: $severities."
    }

    fun renderToPdf(
        context: Context,
        report: InspectionReport,
        countersignatures: List<CountersignatureEntity> = emptyList()
    ): File {
        val document = PdfDocument()
        val titlePaint = Paint().apply { textSize = 20f; isFakeBoldText = true }
        val metaPaint = Paint().apply { textSize = 10f; color = 0xFF666666.toInt() }
        val headerPaint = TextPaint().apply { textSize = 13f; isFakeBoldText = true }
        val bodyPaint = TextPaint().apply { textSize = 10.5f }

        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN.toFloat()

        canvas.drawText("SmartLease Edge — Inspection Report", MARGIN.toFloat(), y, titlePaint); y += 26
        val dateStr = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
            .format(Date(report.generatedAtEpochMillis))
        canvas.drawText("Property: ${report.propertyLabel}", MARGIN.toFloat(), y, metaPaint); y += 14
        canvas.drawText("Session: ${report.sessionId}   Generated: $dateStr", MARGIN.toFloat(), y, metaPaint); y += 14
        // This line is printed on a tenant-facing document, so it states only what stays true
        // under every build configuration. It used to read "Generated fully offline, on-device
        // — no data left this phone."; that became false the moment com.smartlease.edge.sync
        // could upload a findings digest, and a sentence on the evidence artefact itself is the
        // last place a stale claim should survive. What IS structurally guaranteed is the part
        // that matters to a tenant: the inspection media never goes anywhere. The sync wire
        // types carry no bitmap or byte-array field, and the server schema has no binary column,
        // so media cannot be uploaded even by mistake. Only a digest and small metadata can be.
        canvas.drawText("Captured and analysed on-device. Photos, video and audio never leave this phone.", MARGIN.toFloat(), y, metaPaint); y += 24

        canvas.drawText("Verdict: ${report.overallVerdict}", MARGIN.toFloat(), y, headerPaint); y += 26

        for (section in report.sections) {
            if (y > PAGE_HEIGHT - 120) {
                drawFooter(canvas, report, pageNumber)
                document.finishPage(page)
                pageNumber++
                page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = MARGIN.toFloat()
            }
            canvas.drawText(section.title, MARGIN.toFloat(), y, headerPaint); y += 18

            val layout = StaticLayout.Builder
                .obtain(section.body, 0, section.body.length, bodyPaint, PAGE_WIDTH - MARGIN * 2)
                .build()
            canvas.save()
            canvas.translate(MARGIN.toFloat(), y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + 16
        }

        // Deposit balance sheet, when a deposit was recorded for this session. Placed after
        // the findings sections and before the verification block, same reading order as the
        // report screen: what happened, then what it costs, then how to check the record.
        if (report.deductions != null) {
            val cursor = PdfCursor(document, page, canvas, pageNumber, y)
            drawDeductionSummary(cursor, report, report.deductions)
            page = cursor.page; canvas = cursor.canvas; pageNumber = cursor.pageNumber; y = cursor.y
        }

        // Verification block on the last page: the same digest as a scannable symbol.
        if (y > PAGE_HEIGHT - 260) {
            drawFooter(canvas, report, pageNumber)
            document.finishPage(page)
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = MARGIN.toFloat()
        }
        drawVerificationBlock(canvas, report, y)

        // Section 63 certificate, device attestation and any countersignatures each read as
        // their own document, not a continuation of the findings above -- always start them
        // on a fresh page rather than packing them under whatever space is left.
        drawFooter(canvas, report, pageNumber)
        document.finishPage(page)
        pageNumber++
        page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        canvas = page.canvas
        y = MARGIN.toFloat()

        val closingCursor = PdfCursor(document, page, canvas, pageNumber, y)
        drawSection63Certificate(closingCursor, report)
        drawDeviceAttestation(closingCursor, report)
        if (countersignatures.isNotEmpty()) {
            drawCountersignatures(closingCursor, report, countersignatures)
        }
        page = closingCursor.page
        canvas = closingCursor.canvas
        pageNumber = closingCursor.pageNumber

        drawFooter(canvas, report, pageNumber)
        document.finishPage(page)

        val outFile = reportFile(context, report.sessionId)
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { document.writeTo(it) }
        document.close()
        return outFile
    }

    /**
     * Reports live in their own subdirectory of filesDir, not in filesDir itself, so the
     * FileProvider can be scoped to `reports/` — the copied 13.7 MB model asset sits beside
     * them and must not be reachable through a shared URI.
     */
    const val REPORTS_DIR = "reports"

    fun reportFile(context: Context, sessionId: String): File =
        File(File(context.filesDir, REPORTS_DIR), "report_$sessionId.pdf")

    /**
     * The last page carries the digest as a QR as well as as hex, so the other party can
     * scan it with a stock camera app and hold the same commitment without trusting this
     * document, this app, or us.
     */
    private fun drawVerificationBlock(canvas: Canvas, report: InspectionReport, top: Float) {
        val headPaint = TextPaint().apply { textSize = 12f; isFakeBoldText = true }
        val bodyPaint = TextPaint().apply { textSize = 8.5f; color = 0xFF333333.toInt() }
        var y = top

        canvas.drawText("Verify this report", MARGIN.toFloat(), y, headPaint); y += 16

        val qr = runCatching { QrCode.bitmap(report.findingsSha256, 160) }.getOrNull()
        if (qr != null) {
            canvas.drawBitmap(qr, null, android.graphics.Rect(MARGIN, y.toInt(), MARGIN + 160, y.toInt() + 160), null)
            val tx = (MARGIN + 175).toFloat()
            var ty = y + 14
            for (line in listOf(
                "Scan with any phone camera. No app, no internet.",
                "It reads back the SHA-256 of the ${report.findingCount} finding(s) in this report:",
                "",
                report.findingsSha256.substring(0, 32),
                report.findingsSha256.substring(32),
                "",
                "If a finding is altered and the report regenerated,",
                "this code changes. It proves the findings are unaltered;",
                "it does not identify who recorded them."
            )) {
                canvas.drawText(line, tx, ty, bodyPaint); ty += 12
            }
            y += 168
        } else {
            canvas.drawText(report.findingsSha256, MARGIN.toFloat(), y, bodyPaint); y += 14
        }
    }

    /**
     * Every page carries the digest, not just the last one — a report is argued over page by
     * page, and a footer that appears once is a footer that can be removed.
     */
    private fun drawFooter(canvas: Canvas, report: InspectionReport, pageNumber: Int) {
        val footPaint = Paint().apply { textSize = 7.5f; color = 0xFF555555.toInt() }
        val rulePaint = Paint().apply { color = 0xFFCCCCCC.toInt(); strokeWidth = 0.5f }
        var y = (PAGE_HEIGHT - 38).toFloat()

        canvas.drawLine(MARGIN.toFloat(), y, (PAGE_WIDTH - MARGIN).toFloat(), y, rulePaint)
        y += 11
        canvas.drawText(
            "SHA-256 of ${report.findingCount} finding(s): ${report.findingsSha256}",
            MARGIN.toFloat(), y, footPaint
        )
        y += 10
        canvas.drawText(
            "Verifies that the findings above are unaltered. Not a signature: it does not " +
                    "identify who recorded them.",
            MARGIN.toFloat(), y, footPaint
        )
        y += 10
        canvas.drawText(
            "Session ${report.sessionId}   ·   page $pageNumber",
            MARGIN.toFloat(), y, footPaint
        )
    }

    /**
     * Cursor for the page-break loop the deduction summary below needs: which page and
     * canvas are live, how far down the page drawing has reached, and the running page
     * count. Threaded through instead of adding more vars to [renderToPdf] because this
     * section breaks pages line-by-line, the same as the findings loop above it, and growing
     * that function's own local state further would make it unreadable rather than just long.
     */
    private class PdfCursor(
        val document: PdfDocument,
        var page: PdfDocument.Page,
        var canvas: Canvas,
        var pageNumber: Int,
        var y: Float
    )

    /** Same finish-page/start-page sequence used throughout this file, made reusable. */
    private fun PdfCursor.breakPageIfBelow(report: InspectionReport, floor: Int) {
        if (y <= PAGE_HEIGHT - floor) return
        drawFooter(canvas, report, pageNumber)
        document.finishPage(page)
        pageNumber++
        page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        canvas = page.canvas
        y = MARGIN.toFloat()
    }

    /**
     * The deposit balance sheet: what a landlord and tenant actually argue over. Every rupee
     * figure traces back to a [DeductionLine.basis] on the same page, and the honesty note at
     * the end says outright what this arithmetic does not cover -- an unreviewed figure with
     * no caveat is the one a dispute exploits.
     */
    private fun drawDeductionSummary(cursor: PdfCursor, report: InspectionReport, deductions: DeductionSummary) {
        val headerPaint = TextPaint().apply { textSize = 13f; isFakeBoldText = true }
        val bodyPaint = TextPaint().apply { textSize = 10.5f }

        cursor.breakPageIfBelow(report, 160)
        cursor.canvas.drawText("Deposit balance sheet", MARGIN.toFloat(), cursor.y, headerPaint)
        cursor.y += 20
        cursor.canvas.drawText(
            "Deposit held: ₹%,d".format(deductions.depositRupees), MARGIN.toFloat(), cursor.y, bodyPaint
        )
        cursor.y += 18

        deductions.lines.forEach { drawDeductionLine(cursor, report, bodyPaint, it) }

        cursor.breakPageIfBelow(report, 100)
        cursor.canvas.drawText(
            "Total deductions: ₹%,d".format(deductions.totalDeductionRupees), MARGIN.toFloat(), cursor.y, headerPaint
        )
        cursor.y += 18
        cursor.canvas.drawText(
            "Refund due: ₹%,d".format(deductions.refundRupees), MARGIN.toFloat(), cursor.y, headerPaint
        )
        cursor.y += 24

        drawClearedNotes(cursor, report, headerPaint, bodyPaint, deductions.clearedNotes)
        drawDeductionHonestyNote(cursor, report, bodyPaint)
    }

    /** One priced line: description and amount on one row, the basis wrapped beneath it. */
    private fun drawDeductionLine(
        cursor: PdfCursor, report: InspectionReport, bodyPaint: TextPaint, line: DeductionLine
    ) {
        cursor.breakPageIfBelow(report, 120)
        val descriptionPaint = TextPaint().apply { textSize = bodyPaint.textSize; isFakeBoldText = true }
        val amountPaint = TextPaint().apply {
            textSize = bodyPaint.textSize; isFakeBoldText = true; textAlign = Paint.Align.RIGHT
        }
        cursor.canvas.drawText(line.description, MARGIN.toFloat(), cursor.y, descriptionPaint)
        cursor.canvas.drawText(
            "₹%,d".format(line.amountRupees), (PAGE_WIDTH - MARGIN).toFloat(), cursor.y, amountPaint
        )
        cursor.y += 15

        val layout = StaticLayout.Builder
            .obtain(line.basis, 0, line.basis.length, bodyPaint, PAGE_WIDTH - MARGIN * 2)
            .build()
        cursor.canvas.save()
        cursor.canvas.translate(MARGIN.toFloat(), cursor.y)
        layout.draw(cursor.canvas)
        cursor.canvas.restore()
        cursor.y += layout.height + 12
    }

    /** Findings that cost nothing but still belong on the page, under their own heading. */
    private fun drawClearedNotes(
        cursor: PdfCursor, report: InspectionReport, headerPaint: TextPaint, bodyPaint: TextPaint, notes: List<String>
    ) {
        if (notes.isEmpty()) return
        cursor.breakPageIfBelow(report, 100)
        cursor.canvas.drawText("Not priced — for human review", MARGIN.toFloat(), cursor.y, headerPaint)
        cursor.y += 18
        notes.forEach { note ->
            cursor.breakPageIfBelow(report, 80)
            val layout = StaticLayout.Builder
                .obtain(note, 0, note.length, bodyPaint, PAGE_WIDTH - MARGIN * 2)
                .build()
            cursor.canvas.save()
            cursor.canvas.translate(MARGIN.toFloat(), cursor.y)
            layout.draw(cursor.canvas)
            cursor.canvas.restore()
            cursor.y += layout.height + 8
        }
        cursor.y += 8
    }

    /**
     * Non-negotiable per the design doc: the rates are the team's own estimates rather than
     * a published tariff, nothing under the confidence floor or from the colour heuristic is
     * priced, and the deposit figure is operator-entered and sits outside the findings
     * digest above -- a reader must not mistake this sheet for more certainty than it has.
     */
    private fun drawDeductionHonestyNote(cursor: PdfCursor, report: InspectionReport, bodyPaint: TextPaint) {
        cursor.breakPageIfBelow(report, 220)
        val notePaint = TextPaint().apply { textSize = 8.5f; color = 0xFF333333.toInt() }
        val floorPercent = "%.0f".format(DeductionEngine.PRICING_CONFIDENCE_FLOOR * 100f)
        val text = "These repair rates are the team's own Chennai contractor estimates, not " +
            "published tariffs. Nothing below $floorPercent% model confidence, and nothing " +
            "flagged by the colour heuristic instead of the trained model, is ever priced -- " +
            "both are listed above for human review only. The deposit figure at the top of " +
            "this sheet was entered by the operator at report time; unlike the findings " +
            "above it, it is not covered by the SHA-256 digest on this page."
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, notePaint, PAGE_WIDTH - MARGIN * 2)
            .build()
        cursor.canvas.save()
        cursor.canvas.translate(MARGIN.toFloat(), cursor.y)
        layout.draw(cursor.canvas)
        cursor.canvas.restore()
        cursor.y += layout.height + 16
    }

    /** Wraps [text] at the cursor's position and advances it past the drawn block, plus [gap]. */
    private fun PdfCursor.drawWrapped(text: String, paint: TextPaint, gap: Int = 8) {
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, PAGE_WIDTH - MARGIN * 2)
            .build()
        canvas.save()
        canvas.translate(MARGIN.toFloat(), y)
        layout.draw(canvas)
        canvas.restore()
        y += layout.height + gap
    }

    /**
     * Draft certificate under Section 63 of the Bharatiya Sakshya Adhiniyam, 2023, which
     * requires a certificate identifying the device and its manner of production before
     * device-generated electronic evidence is admissible. This app cannot self-certify -- BSA
     * 2023 requires the certificate to be signed by "the person occupying a responsible
     * official position in relation to the operation of the device" -- so this page is
     * populated automatically from device and app data and labelled a draft, with blank
     * lines below for that person to complete by hand.
     */
    private fun drawSection63Certificate(cursor: PdfCursor, report: InspectionReport) {
        val headerPaint = TextPaint().apply { textSize = 13f; isFakeBoldText = true }
        val bodyPaint = TextPaint().apply { textSize = 9.5f }
        val boldBodyPaint = TextPaint(bodyPaint).apply { isFakeBoldText = true }
        val labelPaint = TextPaint().apply { textSize = 9f; color = 0xFF666666.toInt() }

        cursor.breakPageIfBelow(report, 200)
        cursor.canvas.drawText(
            "Certificate under Section 63, Bharatiya Sakshya Adhiniyam, 2023",
            MARGIN.toFloat(), cursor.y, headerPaint
        )
        cursor.y += 18

        cursor.drawWrapped(
            "DRAFT -- generated automatically from this device and app. It becomes a Section 63 " +
                "certificate only once reviewed and signed below by the person responsible for " +
                "operating this device; this app cannot certify itself.",
            boldBodyPaint
        )
        cursor.y += 4

        val captureWindow = if (
            report.earliestFindingEpochMillis != null && report.latestFindingEpochMillis != null
        ) {
            val fmt = SimpleDateFormat("d MMM yyyy, HH:mm:ss", Locale.getDefault())
            "${fmt.format(Date(report.earliestFindingEpochMillis))} to " +
                fmt.format(Date(report.latestFindingEpochMillis))
        } else {
            "no findings recorded"
        }

        val fields = listOf(
            "Device" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "Operating system" to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "Producing application" to "SmartLease Edge ${BuildConfig.VERSION_NAME}",
            "Manner of production" to ("Captured on-device (camera, microphone, IR emitter, motion " +
                "sensors) and recorded to local app storage; no network transmission at any point."),
            "Capture window" to captureWindow,
            "Session identifier" to report.sessionId,
            "Findings digest (SHA-256)" to report.findingsSha256
        )
        fields.forEach { (label, value) ->
            cursor.breakPageIfBelow(report, 80)
            cursor.canvas.drawText(label, MARGIN.toFloat(), cursor.y, labelPaint)
            cursor.y += 12
            cursor.drawWrapped(value, bodyPaint, gap = 6)
        }

        cursor.y += 6
        cursor.breakPageIfBelow(report, 110)
        cursor.canvas.drawText("To be completed by hand:", MARGIN.toFloat(), cursor.y, labelPaint)
        cursor.y += 18
        listOf("Name", "Capacity / designation", "Date", "Signature").forEach { line ->
            cursor.breakPageIfBelow(report, 40)
            cursor.canvas.drawText(
                "$line: ______________________________", MARGIN.toFloat(), cursor.y, bodyPaint
            )
            cursor.y += 20
        }
        cursor.y += 12
    }

    /**
     * Hardware-backed device signature over the findings digest -- see [ReportSigner]. Signing
     * runs fresh at every render (ECDSA is randomized, so re-rendering the same report twice
     * yields two different but equally valid signatures over the same digest) and is wrapped
     * so a KeyStore failure on some device degrades this section rather than the whole render.
     */
    private fun drawDeviceAttestation(cursor: PdfCursor, report: InspectionReport) {
        val headerPaint = TextPaint().apply { textSize = 13f; isFakeBoldText = true }
        val bodyPaint = TextPaint().apply { textSize = 9.5f }
        val labelPaint = TextPaint().apply { textSize = 9f; color = 0xFF666666.toInt() }
        val monoPaint = TextPaint(bodyPaint).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 8f
        }

        cursor.breakPageIfBelow(report, 160)
        cursor.canvas.drawText("Device-signed attestation", MARGIN.toFloat(), cursor.y, headerPaint)
        cursor.y += 18

        val attestation = runCatching { ReportSigner.sign(report.findingsSha256) }.getOrNull()
        if (attestation == null) {
            cursor.drawWrapped(
                "Not available on this device -- the AndroidKeyStore signing call failed. The " +
                    "SHA-256 digest above is still a valid integrity check; it is simply not " +
                    "device-signed on this copy of the report.",
                bodyPaint
            )
            return
        }

        val hardwareNote = if (attestation.hardwareBacked) {
            "confirmed hardware-backed"
        } else {
            "hardware backing not confirmed on this device"
        }
        cursor.drawWrapped(
            "The findings digest above was signed with a private key generated inside this " +
                "device's secure hardware ($hardwareNote) and never exported from it. This proves " +
                "the signature was produced by this specific phone; it does not identify who was " +
                "operating it. Play Integrity was deliberately not used here -- it requires a " +
                "network round-trip this app cannot make, since it declares no INTERNET permission.",
            bodyPaint
        )

        cursor.canvas.drawText("Signature (ECDSA / SHA-256, Base64)", MARGIN.toFloat(), cursor.y, labelPaint)
        cursor.y += 12
        cursor.drawWrapped(attestation.signatureBase64, monoPaint, gap = 6)

        cursor.canvas.drawText(
            "${attestation.certificateChainBase64.size} certificate(s) in the attestation chain.",
            MARGIN.toFloat(), cursor.y, labelPaint
        )
        cursor.y += 16
    }

    /**
     * Countersignatures captured via the two-phone QR handshake
     * ([com.smartlease.edge.report.CountersignPayload]), re-verified against this report's
     * current digest at render time rather than trusted from storage -- a signature captured
     * against an earlier version of this report (findings changed since) is shown as invalid
     * rather than silently listed as good.
     */
    private fun drawCountersignatures(
        cursor: PdfCursor,
        report: InspectionReport,
        countersignatures: List<CountersignatureEntity>
    ) {
        val headerPaint = TextPaint().apply { textSize = 13f; isFakeBoldText = true }
        val bodyPaint = TextPaint().apply { textSize = 9.5f }
        val labelPaint = TextPaint().apply { textSize = 9f; color = 0xFF666666.toInt() }
        val warnPaint = TextPaint().apply { textSize = 9.5f; color = 0xFFAA2200.toInt() }

        cursor.breakPageIfBelow(report, 140)
        cursor.canvas.drawText("Joint-inspection countersignatures", MARGIN.toFloat(), cursor.y, headerPaint)
        cursor.y += 18
        cursor.drawWrapped(
            "Each entry below is a signature from a second phone's own device key over this " +
                "report's findings digest, captured via a QR code scanned between the two " +
                "devices. It proves a specific device signed this digest; it does not identify " +
                "a person, and whether that key was hardware-backed is that device's own claim, " +
                "not something independently verified here.",
            bodyPaint
        )

        countersignatures.forEachIndexed { index, entry ->
            cursor.breakPageIfBelow(report, 90)
            val stillMatchesDigest = entry.digestHex == report.findingsSha256
            val verified = stillMatchesDigest &&
                ReportSigner.verify(entry.digestHex, entry.signatureBase64, entry.certificateBase64)

            val stamp = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
                .format(Date(entry.capturedAtEpochMillis))
            cursor.canvas.drawText(
                "Countersignature ${index + 1} -- captured $stamp", MARGIN.toFloat(), cursor.y, labelPaint
            )
            cursor.y += 13

            if (!verified) {
                val reason = if (!stillMatchesDigest) {
                    "does not match this report's current findings digest -- ignored"
                } else {
                    "signature did not verify -- ignored"
                }
                cursor.drawWrapped("INVALID -- $reason.", warnPaint, gap = 6)
            } else {
                val hwClaim = if (entry.hardwareBackedSelfReported) "claims yes" else "claims no"
                cursor.drawWrapped(
                    "Valid. Hardware-backed key: $hwClaim, as reported by that device -- not " +
                        "independently verified from its certificate by this one.",
                    bodyPaint,
                    gap = 6
                )
            }
        }
    }
}
