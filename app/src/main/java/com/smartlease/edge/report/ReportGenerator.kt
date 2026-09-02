package com.smartlease.edge.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import com.smartlease.edge.data.InspectionEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local, offline PDF report generator using Android's built-in PdfDocument API — no
 * network call, no cloud service, at any point in this file. This is the "signed,
 * timestamped local report" from the architecture doc: "signed" here means a visible
 * timestamp + session ID baked into the document, not a cryptographic signature — the
 * design doc explicitly calls out dropping real crypto-signing as unnecessary complexity
 * for a demo, and that decision is carried through here.
 *
 * The natural-language synthesis step (turning structured findings into readable prose)
 * is the one piece meant to eventually call Qualcomm's GenieX/Llama-3.2 runtime on-device;
 * `synthesizeNarrative()` below is a rule-based placeholder for that step, clearly marked,
 * so the report pipeline is fully runnable end-to-end today while the real GenieX
 * integration is layered in without touching the PDF-rendering code at all.
 */
object ReportGenerator {

    private const val PAGE_WIDTH = 595 // A4 at 72dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40

    fun buildReport(sessionId: String, propertyLabel: String, findings: List<InspectionEntity>): InspectionReport {
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
            overallVerdict = verdict
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
            if (y > PAGE_HEIGHT - 100) {
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

        document.finishPage(page)

        val outFile = File(context.filesDir, "report_${report.sessionId}.pdf")
        FileOutputStream(outFile).use { document.writeTo(it) }
        document.close()
        return outFile
    }
}
