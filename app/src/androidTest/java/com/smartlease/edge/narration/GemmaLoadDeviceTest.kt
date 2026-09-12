package com.smartlease.edge.narration

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartlease.edge.data.FindingType
import com.smartlease.edge.data.InspectionEntity
import com.smartlease.edge.data.SessionType
import com.smartlease.edge.data.Severity
import com.smartlease.edge.deduction.FindingDetail
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Loads whatever model is actually on this device and reports what happened.
 *
 * This is a measurement harness, not a pass/fail test, and it is deliberately written not to
 * fail when no model is present -- the demo build ships that way on purpose. What it does is
 * answer the three questions that cannot be answered by reading code:
 *
 *  1. Does [ReportNarratorFactory.create] return a Gemma narrator, or fall back to templating?
 *  2. How long does loading the weights take, and how long is the first paragraph?
 *  3. Does [NarrationAudit] accept what the model wrote?
 *
 * Every answer is written to logcat under [TAG] so it can be read back with
 * `adb logcat -d | grep GemmaLoadTest`.
 */
@RunWith(AndroidJUnit4::class)
class GemmaLoadDeviceTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun finding(label: String, defectClass: String, area: Float, confidence: Float) =
        InspectionEntity(
            sessionId = "00000000-0000-4000-8000-0000000gemma4",
            timestampEpochMillis = 1_757_000_000_000L,
            findingType = FindingType.VISUAL_DEFECT,
            label = label,
            detailJson = FindingDetail.VisualDefect(
                defectClass = defectClass,
                areaSqFt = area,
                confidence = confidence,
                fromTrainedModel = true
            ).toJson(),
            severity = Severity.NOTABLE,
            sessionType = SessionType.MOVE_OUT,
            propertyLabel = "Flat 12B"
        )

    @Test
    fun loadsWhateverModelIsOnThisDeviceAndReportsIt() = runBlocking {
        log("=== BEGIN ===")

        when (val located = GemmaModelLocator.locate(context)) {
            is GemmaModelLocator.Location.Missing -> {
                log("LOCATE: no model file")
                located.searched.forEach { log("LOCATE: searched $it") }
            }
            is GemmaModelLocator.Location.Found -> {
                log("LOCATE: found ${located.file.name}")
                log("LOCATE: path ${located.file.absolutePath}")
                log("LOCATE: bytes ${located.file.length()}")
            }
        }

        log("DESCRIBE: ${ReportNarratorFactory.describe(context)}")

        // The number that matters. `create` is what pins the weights, so this is the wait a
        // user actually sits through after tapping "Generate report".
        val loadStart = System.currentTimeMillis()
        val selection = ReportNarratorFactory.create(context)
        val loadMs = System.currentTimeMillis() - loadStart

        log("LOAD: usingModel=${selection.usingModel}")
        log("LOAD: status=${selection.status}")
        log("LOAD: elapsedMs=$loadMs")

        if (!selection.usingModel) {
            // Not a failure. The app is designed to produce reports either way, and saying so
            // plainly here is the point of the harness.
            log("RESULT: FELL BACK TO TEMPLATE - the runtime did not accept this model")
            log("=== END ===")
            return@runBlocking
        }

        val findings = listOf(
            finding("Hall -- Top: crack in wall surface", "crack", 0.33f, 0.83f),
            finding("Hall -- Top: peeling paint", "peeling_paint", 1.23f, 0.69f),
            finding("Hall -- Sides: spalling (surface breaking away)", "spalling", 0.42f, 0.61f)
        )

        val genStart = System.currentTimeMillis()
        val narration = selection.narrator.narrate("VISUAL DEFECT", findings)
        val genMs = System.currentTimeMillis() - genStart

        log("GENERATE: elapsedMs=$genMs")
        log("GENERATE: source=${narration.source}")
        narration.note?.let { log("GENERATE: note=$it") }
        log("GENERATE: chars=${narration.text.length}")
        // Roughly four characters per token is close enough to turn a paragraph into a rate.
        if (genMs > 0) log("GENERATE: approxTokensPerSec=${(narration.text.length / 4.0) / (genMs / 1000.0)}")
        log("GENERATE: text=${narration.text}")

        log("AUDIT: ${NarrationAudit.check(narration.text, findings)}")
        log("RESULT: MODEL NARRATED")

        selection.narrator.close()
        log("=== END ===")
    }

    private fun log(line: String) = Log.i(TAG, line).let { println("$TAG: $line") }

    private companion object {
        const val TAG = "GemmaLoadTest"
    }
}
