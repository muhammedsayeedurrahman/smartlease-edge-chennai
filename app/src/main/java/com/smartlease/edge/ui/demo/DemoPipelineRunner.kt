package com.smartlease.edge.ui.demo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.smartlease.edge.R
import com.smartlease.edge.data.FindingType
import com.smartlease.edge.data.InspectionEntity
import com.smartlease.edge.data.SessionType
import com.smartlease.edge.data.Severity
import com.smartlease.edge.deduction.DeductionSummary
import com.smartlease.edge.deduction.FindingDetail
import com.smartlease.edge.narration.ReportNarratorFactory
import com.smartlease.edge.report.InspectionReport
import com.smartlease.edge.report.ReportGenerator
import com.smartlease.edge.safety.SafetyGate
import com.smartlease.edge.ui.CapturedFrame
import com.smartlease.edge.ui.Room
import com.smartlease.edge.vision.DefectSegmenter
import com.smartlease.edge.vision.DefectSegmenterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Runs the real capture -> vision -> deduction -> narration -> report pipeline against three
 * bundled move-in/move-out room pairs, so [JudgeDemoScreen] can walk a judge through the actual
 * app subsystems executing across a whole property rather than a scripted mock of them.
 *
 * Every number the screen shows after [run] completes -- the per-room defect lists, the rupee
 * figures, which narrator wrote the summary, the PDF's own SHA-256 -- comes out of the same
 * [DefectSegmenterFactory], [com.smartlease.edge.deduction.DeductionEngine] and
 * [ReportNarratorFactory] a live inspection uses. Only the six input photographs are fixed;
 * everything downstream of them is computed fresh, on this device, every time this runs.
 *
 * Each move-out photograph is the exact same photograph as its move-in pair with one small
 * defect composited on -- see docs/JUDGE_DEMO_GUIDE.md -- because that is the realistic case a
 * move-out inspection exists to catch, not two different rooms standing in for "before/after".
 */
object DemoPipelineRunner {

    private const val DEMO_PROPERTY_LABEL = "Harbour View · 2BHK"

    /** The demo deposit findings are priced against -- not a real property's figure. */
    const val DEMO_DEPOSIT_RUPEES = 20_000

    /** @param frameWidthInches / frameHeightInches match [com.smartlease.edge.diagnostics.SelfTest]'s own stand-in for a wall-sized frame. */
    private const val DEMO_FRAME_WIDTH_INCHES = 48f
    private const val DEMO_FRAME_HEIGHT_INCHES = 36f

    private data class RoomSpec(
        val roomType: String,
        val surfaceType: String,
        val moveInRes: Int,
        val moveOutRes: Int
    )

    private val DEMO_ROOMS = listOf(
        RoomSpec("Living Room", "Wall", R.drawable.demo_move_in_wall, R.drawable.demo_move_out_cracked_wall),
        RoomSpec("Kitchen", "Backsplash", R.drawable.demo_move_in_kitchen, R.drawable.demo_move_out_kitchen),
        RoomSpec("Bedroom", "Wall", R.drawable.demo_move_in_bedroom, R.drawable.demo_move_out_bedroom)
    )

    /** One room's move-in/move-out photographs and what the segmenter found on the move-out one. */
    data class RoomResult(
        val roomType: String,
        val surfaceType: String,
        val moveInBitmap: Bitmap,
        val moveOutBitmap: Bitmap,
        val defects: List<DefectSegmenter.Defect>,
        val isTrainedModel: Boolean
    )

    data class Result(
        val rooms: List<RoomResult>,
        val findings: List<InspectionEntity>,
        val deduction: DeductionSummary?,
        val narratorStatus: String,
        val report: InspectionReport,
        val pdfFile: File
    ) {
        /** The section [ReportGenerator.buildReport] wrote for [findings] -- title, body and which narrator wrote it. */
        val findingsSection get() = report.sections.firstOrNull { it.title == FindingType.VISUAL_DEFECT.name.replace('_', ' ') }

        /** Same segmenter instance ran every room this session, so this flag is uniform across [rooms]. */
        val usingTrainedModel get() = rooms.firstOrNull()?.isTrainedModel ?: false
    }

    /**
     * Runs entirely on [Dispatchers.Default]: bitmap decode, three rounds of segmentation and
     * PDF rendering are all CPU/IO work with no UI-thread requirement, and running them on the
     * caller's dispatcher (Main, from [JudgeDemoScreen]'s `rememberCoroutineScope()`) used to
     * freeze the whole screen for the run's duration -- no spinner, a Button that visually
     * looked unresponsive, and, since the UI couldn't recompose to disable it, a second tap
     * fired a second overlapping run instead of being ignored.
     */
    /**
     * @param onProgress a one-line status a judge can read while this runs -- device thermal
     * throttling can stretch a run to a minute or more (each on-device inference pass slows down
     * as the SoC downclocks to cool off), and a screen with no feedback for that long reads as
     * hung even though it is still working. Called from [Dispatchers.Default]; Compose's
     * snapshot state is safe to write from a background thread.
     */
    suspend fun run(context: Context, onProgress: (String) -> Unit = {}): Result = withContext(Dispatchers.Default) {
        onProgress("Loading the on-device vision model…")
        val segmenter = DefectSegmenterFactory.create(context)
        val sessionId = UUID.randomUUID().toString()

        val roomResults = mutableListOf<RoomResult>()
        val allFindings = mutableListOf<InspectionEntity>()
        val rooms = mutableListOf<Room>()

        DEMO_ROOMS.forEachIndexed { index, spec ->
            onProgress("Analyzing ${spec.roomType} (${index + 1}/${DEMO_ROOMS.size})…")
            val moveInBitmap = decodeDemoBitmap(context, spec.moveInRes)
            val moveOutBitmap = decodeDemoBitmap(context, spec.moveOutRes)
            val defects = segmenter.segmentDefects(
                moveOutBitmap,
                frameWidthInches = DEMO_FRAME_WIDTH_INCHES,
                frameHeightInches = DEMO_FRAME_HEIGHT_INCHES
            )
            roomResults += RoomResult(
                roomType = spec.roomType,
                surfaceType = spec.surfaceType,
                moveInBitmap = moveInBitmap,
                moveOutBitmap = moveOutBitmap,
                defects = defects,
                isTrainedModel = segmenter.isTrainedModel
            )

            // Same shape as com.smartlease.edge.report.PropertyReportBuilder.findingsFor -- a
            // second, hand-rolled mapping here would be exactly the kind of drift this demo
            // exists to avoid.
            allFindings += defects.map { defect ->
                val label = "${spec.roomType} -- ${spec.surfaceType}: ${defect.label}"
                val verdict = SafetyGate.evaluate(label)
                InspectionEntity(
                    sessionId = sessionId,
                    timestampEpochMillis = System.currentTimeMillis(),
                    findingType = FindingType.VISUAL_DEFECT,
                    label = label,
                    detailJson = FindingDetail.VisualDefect(
                        defectClass = defect.defectClass,
                        areaSqFt = defect.areaSqFtEstimate,
                        confidence = defect.confidence,
                        fromTrainedModel = segmenter.isTrainedModel
                    ).toJson(),
                    severity = verdict.escalatedSeverity ?: Severity.NOTABLE,
                    sessionType = SessionType.MOVE_OUT,
                    propertyLabel = DEMO_PROPERTY_LABEL
                )
            }

            rooms += Room(
                id = "demo-room-$index",
                propertyId = "demo-property",
                type = spec.roomType,
                moveInFrames = listOf(
                    CapturedFrame(
                        surfaceType = spec.surfaceType,
                        frameIndex = 0,
                        bitmap = moveInBitmap,
                        isTrainedModel = segmenter.isTrainedModel
                    )
                ),
                moveOutFrames = listOf(
                    CapturedFrame(
                        surfaceType = spec.surfaceType,
                        frameIndex = 0,
                        bitmap = moveOutBitmap,
                        defects = defects,
                        isTrainedModel = segmenter.isTrainedModel
                    )
                )
            )
        }

        onProgress("Selecting narrator and pricing deductions…")
        // Selected once, used once, closed once -- exactly the lifecycle
        // MainActivity.onGenerateReport gives it for a real session (see that file's own
        // comment on why: a loaded Gemma model pins hundreds of megabytes for the duration).
        val selection = ReportNarratorFactory.create(context)
        val report = try {
            ReportGenerator.buildReport(
                sessionId = sessionId,
                propertyLabel = DEMO_PROPERTY_LABEL,
                findings = allFindings,
                depositRupees = DEMO_DEPOSIT_RUPEES,
                sessionType = SessionType.MOVE_OUT,
                narrator = selection.narrator
            )
        } finally {
            selection.narrator.close()
        }

        onProgress("Rendering the report PDF…")
        val pdfFile = ReportGenerator.renderToPdf(context, report, rooms = rooms)

        Result(
            rooms = roomResults,
            findings = allFindings,
            deduction = report.deductions,
            narratorStatus = selection.status,
            report = report,
            pdfFile = pdfFile
        )
    }

    private fun decodeDemoBitmap(context: Context, resId: Int): Bitmap =
        checkNotNull(BitmapFactory.decodeResource(context.resources, resId)) {
            "Demo drawable $resId failed to decode"
        }
}
