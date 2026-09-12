package com.smartlease.edge.report

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.smartlease.edge.data.FindingType
import com.smartlease.edge.data.InspectionEntity
import com.smartlease.edge.data.SessionType
import com.smartlease.edge.data.Severity
import com.smartlease.edge.deduction.FindingDetail
import com.smartlease.edge.narration.NarrationSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders a real PDF through the real pipeline.
 *
 * This is the one path the rest of the suite does not reach: every other test checks a string
 * or a decision, and none of them draws. The first report rendered after the narration credit
 * was added printed the credit on top of the heading beneath it, because `Canvas.drawText`
 * positions a baseline rather than a top edge -- a mistake no assertion about *text* could
 * have caught.
 *
 * The file is left in the app's `reports/` directory under a fixed session id so it can be
 * pulled and looked at:
 *
 * ```
 * adb exec-out 'run-as com.smartlease.edge cat files/reports/report_<RENDER_SESSION_ID>.pdf' > out.pdf
 * ```
 */
@RunWith(AndroidJUnit4::class)
class ReportRenderDeviceTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun finding(
        label: String,
        defectClass: String,
        areaSqFt: Float,
        confidence: Float
    ) = InspectionEntity(
        sessionId = RENDER_SESSION_ID,
        timestampEpochMillis = 1_757_000_000_000L,
        findingType = FindingType.VISUAL_DEFECT,
        label = label,
        detailJson = FindingDetail.VisualDefect(
            defectClass = defectClass,
            areaSqFt = areaSqFt,
            confidence = confidence,
            fromTrainedModel = true
        ).toJson(),
        severity = Severity.NOTABLE,
        sessionType = SessionType.MOVE_OUT,
        propertyLabel = "Flat 12B"
    )

    @Test
    fun rendersAReportCarryingTheNarrationCredit() = runBlocking {
        // A spread of confidences on purpose: some above the pricing threshold and some below,
        // so the rendered page exercises both the balance sheet and the "not priced" list.
        val findings = listOf(
            finding("Hall -- Top: crack in wall surface", "crack", 0.33f, 0.83f),
            finding("Hall -- Top: peeling paint", "peeling_paint", 1.23f, 0.69f),
            finding("Hall -- Top: spalling (surface breaking away)", "spalling", 0.42f, 0.61f),
            finding("Hall -- Top: peeling paint", "peeling_paint", 3.02f, 0.75f),
            finding("Hall -- Sides: crack in wall surface", "crack", 0.43f, 0.55f)
        )

        val report = ReportGenerator.buildReport(
            sessionId = RENDER_SESSION_ID,
            propertyLabel = "Flat 12B",
            findings = findings,
            depositRupees = 20_000
        )

        // The default narrator is the template one, so this pins the fallback rendering --
        // the state the app is in on any device without model weights, which is the state the
        // demo build ships in.
        assertTrue(
            "expected the template narrator by default, got ${report.sections.map { it.narrationSource }}",
            report.sections.all { it.narrationSource == NarrationSource.TEMPLATE }
        )

        val pdf = ReportGenerator.renderToPdf(context, report)

        assertTrue("report was not written to ${pdf.absolutePath}", pdf.exists())
        // A PdfDocument that failed to lay anything out still produces a valid, tiny file, so
        // existence alone proves nothing. Three pages of text and a QR code is tens of KB.
        assertTrue("report is implausibly small at ${pdf.length()} bytes", pdf.length() > 20_000)
    }

    private companion object {
        /** Fixed so the rendered file has a predictable name to pull. */
        const val RENDER_SESSION_ID = "00000000-0000-4000-8000-00000000render"
    }
}
