package com.smartlease.edge.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import com.smartlease.edge.data.InspectionEntity
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
     */
    fun buildReport(
        sessionId: String,
        propertyLabel: String,
        findings: List<InspectionEntity>,
        depositRupees: Int? = null
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
            deductions = depositRupees?.let { DeductionEngine.summarise(it, findings) }
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

    fun renderToPdf(context: Context, report: InspectionReport): File {
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
        canvas.drawText("Generated fully offline, on-device — no data left this phone.", MARGIN.toFloat(), y, metaPaint); y += 24

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
}
