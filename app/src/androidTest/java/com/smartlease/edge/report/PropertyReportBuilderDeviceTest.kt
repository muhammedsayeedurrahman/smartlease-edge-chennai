package com.smartlease.edge.report

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartlease.edge.data.FindingType
import com.smartlease.edge.data.SessionType
import com.smartlease.edge.data.Severity
import com.smartlease.edge.deduction.FindingDetail
import com.smartlease.edge.ui.CapturedFrame
import com.smartlease.edge.ui.Property
import com.smartlease.edge.ui.Room
import com.smartlease.edge.vision.DefectSegmenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the bridge from the room-by-room capture flow's in-memory state to the
 * [com.smartlease.edge.data.InspectionEntity] rows the report pipeline consumes.
 *
 * Instrumented rather than a JVM unit test for a real reason, not convenience:
 * [CapturedFrame] holds an `android.graphics.Bitmap` and [FindingDetail.toJson] goes through
 * `org.json.JSONObject`, both of which are stubbed to throw in the local unit-test JVM. A
 * test that mocked its way around those would be testing the mocks.
 */
@RunWith(AndroidJUnit4::class)
class PropertyReportBuilderDeviceTest {

    private fun bitmap(): Bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)

    private fun defect(
        defectClass: String = "crack",
        label: String = "crack in wall surface",
        areaSqFt: Float = 1.5f,
        confidence: Float = 0.82f
    ) = DefectSegmenter.Defect(
        boundingBox = RectF(0f, 0f, 10f, 10f),
        label = label,
        defectClass = defectClass,
        areaSqFtEstimate = areaSqFt,
        confidence = confidence
    )

    private fun property(deposit: String = "20000") = Property(
        id = PROPERTY_ID,
        name = "Flat 12B",
        location = "Chennai",
        tenantName = "Tenant",
        depositAmount = deposit,
        isFlat = true
    )

    private fun room(
        id: String = "room-1",
        type: String = "Hall",
        frames: List<CapturedFrame> = emptyList(),
        propertyId: String = PROPERTY_ID
    ) = Room(id = id, propertyId = propertyId, type = type, frames = frames)

    private fun frame(
        surfaceType: String = "Sides",
        defects: List<DefectSegmenter.Defect> = emptyList(),
        trained: Boolean = true,
        timestampMs: Long = 1_757_000_000_000L
    ) = CapturedFrame(
        surfaceType = surfaceType,
        frameIndex = 0,
        timestampMs = timestampMs,
        bitmap = bitmap(),
        defects = defects,
        isTrainedModel = trained
    )

    @Test
    fun oneFindingPerDetection_carryingItsOwnClassAndArea() {
        val findings = PropertyReportBuilder.findingsFor(
            property = property(),
            rooms = listOf(
                room(frames = listOf(frame(defects = listOf(defect(), defect("damp_stain", "damp patch", 2.25f)))))
            ),
            sessionId = SESSION_ID,
            sessionType = SessionType.MOVE_OUT
        )

        assertEquals(2, findings.size)
        assertTrue(findings.all { it.findingType == FindingType.VISUAL_DEFECT })

        val first = FindingDetail.fromJson(findings[0].detailJson) as FindingDetail.VisualDefect
        assertEquals("crack", first.defectClass)
        assertEquals(1.5f, first.areaSqFt, 0.0001f)
        assertEquals(0.82f, first.confidence, 0.0001f)

        val second = FindingDetail.fromJson(findings[1].detailJson) as FindingDetail.VisualDefect
        assertEquals("damp_stain", second.defectClass)
        assertEquals(2.25f, second.areaSqFt, 0.0001f)
    }

    @Test
    fun labelNamesTheRoomAndSurface_whileDefectClassStaysStructured() {
        val findings = PropertyReportBuilder.findingsFor(
            property = property(),
            rooms = listOf(room(type = "Kitchen", frames = listOf(frame("Top", listOf(defect()))))),
            sessionId = SESSION_ID,
            sessionType = SessionType.MOVE_OUT
        )

        // The prose says where it is; the rate card is never asked to parse it back.
        assertEquals("Kitchen -- Top: crack in wall surface", findings.single().label)
        val detail = FindingDetail.fromJson(findings.single().detailJson) as FindingDetail.VisualDefect
        assertEquals("crack", detail.defectClass)
    }

    @Test
    fun heuristicDetectionsAreNotLaunderedIntoModelClaims() {
        val findings = PropertyReportBuilder.findingsFor(
            property = property(),
            rooms = listOf(room(frames = listOf(frame(defects = listOf(defect()), trained = false)))),
            sessionId = SESSION_ID,
            sessionType = SessionType.MOVE_OUT
        )

        val detail = FindingDetail.fromJson(findings.single().detailJson) as FindingDetail.VisualDefect
        // If this ever flips to true, a colour heuristic is being reported as a trained model.
        assertEquals(false, detail.fromTrainedModel)
    }

    @Test
    fun visualDefectsAreNotable_notInfo() {
        val findings = PropertyReportBuilder.findingsFor(
            property = property(),
            rooms = listOf(room(frames = listOf(frame(defects = listOf(defect()))))),
            sessionId = SESSION_ID,
            sessionType = SessionType.MOVE_OUT
        )

        assertEquals(Severity.NOTABLE, findings.single().severity)
    }

    @Test
    fun roomsBelongingToAnotherPropertyAreExcluded() {
        val findings = PropertyReportBuilder.findingsFor(
            property = property(),
            rooms = listOf(
                room(frames = listOf(frame(defects = listOf(defect())))),
                room(id = "room-2", propertyId = "some-other-property", frames = listOf(frame(defects = listOf(defect()))))
            ),
            sessionId = SESSION_ID,
            sessionType = SessionType.MOVE_OUT
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun framesWithNoDetectionsProduceNoFindings() {
        val findings = PropertyReportBuilder.findingsFor(
            property = property(),
            rooms = listOf(room(frames = listOf(frame(), frame("Top")))),
            sessionId = SESSION_ID,
            sessionType = SessionType.MOVE_OUT
        )

        // A clean frame is not a finding. Emitting a "nothing found" row would put an item on
        // a deposit sheet that no detection supports.
        assertTrue(findings.isEmpty())
    }

    @Test
    fun sessionTypeAndPropertyLabelAreCarriedOntoEveryRow() {
        val findings = PropertyReportBuilder.findingsFor(
            property = property(),
            rooms = listOf(room(frames = listOf(frame(defects = listOf(defect()))))),
            sessionId = SESSION_ID,
            sessionType = SessionType.MOVE_IN
        )

        assertEquals(SessionType.MOVE_IN, findings.single().sessionType)
        assertEquals("Flat 12B", findings.single().propertyLabel)
        assertEquals(SESSION_ID, findings.single().sessionId)
    }

    @Test
    fun eachFindingKeepsItsOwnFrameTimestamp() {
        val findings = PropertyReportBuilder.findingsFor(
            property = property(),
            rooms = listOf(
                room(
                    frames = listOf(
                        frame(defects = listOf(defect()), timestampMs = 1_757_000_000_000L),
                        frame("Top", listOf(defect()), timestampMs = 1_757_000_090_000L)
                    )
                )
            ),
            sessionId = SESSION_ID,
            sessionType = SessionType.MOVE_OUT
        )

        // The Section 63 certificate prints a capture window derived from these.
        assertEquals(1_757_000_000_000L, findings.first().timestampEpochMillis)
        assertEquals(1_757_000_090_000L, findings.last().timestampEpochMillis)
    }

    // -- deposit parsing --------------------------------------------------------------------

    @Test
    fun depositIsReadFromPlainDigits() {
        assertEquals(20_000, PropertyReportBuilder.depositRupeesOrNull(property("20000")))
    }

    @Test
    fun depositSurvivesSeparatorsAndCurrencyText() {
        // A legible figure must not be thrown away just because someone typed it the way
        // people actually type rupee amounts.
        assertEquals(20_000, PropertyReportBuilder.depositRupeesOrNull(property("20,000")))
        assertEquals(20_000, PropertyReportBuilder.depositRupeesOrNull(property("Rs 20,000")))
    }

    @Test
    fun noDepositStaysNullRatherThanBecomingZero() {
        // Zero would render as "the entire deposit is forfeit" on a document someone signs.
        assertNull(PropertyReportBuilder.depositRupeesOrNull(property("")))
        assertNull(PropertyReportBuilder.depositRupeesOrNull(property("not a number")))
    }

    @Test
    fun anImplausiblyLargeDepositDoesNotCrash() {
        // Overflows Int; must come back as "no usable figure", not an exception mid-report.
        assertNull(PropertyReportBuilder.depositRupeesOrNull(property("999999999999")))
    }

    private companion object {
        const val PROPERTY_ID = "property-1"
        const val SESSION_ID = "11111111-1111-1111-1111-111111111111"
    }
}
