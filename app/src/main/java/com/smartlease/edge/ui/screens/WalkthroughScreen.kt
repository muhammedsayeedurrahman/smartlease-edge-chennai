package com.smartlease.edge.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.camera.view.PreviewView
import com.smartlease.edge.acoustic.AcousticTapClassifier
import com.smartlease.edge.acoustic.TrainedTapClassifier
import com.smartlease.edge.camera.ArAlignmentTracker
import com.smartlease.edge.camera.CameraController
import com.smartlease.edge.data.AppDatabase
import com.smartlease.edge.data.FindingType
import com.smartlease.edge.data.InspectionEntity
import com.smartlease.edge.data.Severity
import com.smartlease.edge.ir.CommonAcIrProfiles
import com.smartlease.edge.ir.IrController
import com.smartlease.edge.ocr.OcrEngine
import com.smartlease.edge.report.InspectionReport
import com.smartlease.edge.report.ReportGenerator
import com.smartlease.edge.safety.SafetyGate
import com.smartlease.edge.ui.components.Lamp
import com.smartlease.edge.ui.components.ReadingRow
import com.smartlease.edge.ui.components.StatusLamp
import com.smartlease.edge.ui.theme.ReadoutValue
import com.smartlease.edge.ui.theme.ReadoutValueLarge
import com.smartlease.edge.vision.DefectSegmenter
import com.smartlease.edge.vision.DefectSegmenterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** A logged finding keeps its severity, so the list can show it rather than flatten to text. */
private data class LoggedFinding(
    val lamp: Lamp,
    val label: String,
    val value: String,
    val detail: String?
)

/**
 * The inspection screen. Runs every subsystem in one flow: tilt alignment, capture with OCR
 * and defect segmentation, acoustic tap test, IR appliance trigger, then a local report
 * carrying a SHA-256 over its findings.
 *
 * Nothing heavy runs on the main thread: the two models load in a LaunchedEffect, and every
 * inference, the tap recording and the PDF render are dispatched to Dispatchers.Default.
 */
@Composable
fun WalkthroughScreen(onReportGenerated: (InspectionReport) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Full UUID, not the first 8 hex characters. The session ID goes into the digest and
    // onto the report; 32 bits of client-generated identifier is not an identifier.
    val sessionId = remember { UUID.randomUUID().toString() }
    val cameraController = remember { CameraController(context, lifecycleOwner) }
    val arTracker = remember { ArAlignmentTracker(context) }
    val irController = remember { IrController(context) }
    val db = remember { AppDatabase.get(context) }

    // Loaded off the main thread by the LaunchedEffect below, not in remember { }.
    // DefectSegmenterFactory.create copies a 13.7 MB asset on first run and then parses a
    // TorchScript module; TrainedTapClassifier.create parses a 73 KB JSON carrying a
    // 10,280-float mel filterbank. Both used to happen during composition, which froze the
    // screen for seconds the first time anyone opened it.
    var visionSegmenter by remember { mutableStateOf<DefectSegmenter?>(null) }
    var trainedTapModel by remember { mutableStateOf<TrainedTapClassifier?>(null) }
    var modelsLoading by remember { mutableStateOf(true) }

    var alignmentState by remember { mutableStateOf<ArAlignmentTracker.AlignmentState?>(null) }
    var findings by remember { mutableStateOf(listOf<LoggedFinding>()) }
    var lastSafetyVerdict by remember { mutableStateOf<SafetyGate.Verdict?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val segmenter = withContext(Dispatchers.Default) { DefectSegmenterFactory.create(context) }
        // null when no trained model ships -- classify() then keeps the heuristic
        val tap = withContext(Dispatchers.Default) { TrainedTapClassifier.create(context) }
        visionSegmenter = segmenter
        trainedTapModel = tap
        modelsLoading = false
    }

    val hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val hasMicPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    DisposableEffect(Unit) {
        arTracker.start { alignmentState = it }
        onDispose { arTracker.stop() }
    }

    /** A failure the operator should see, on the same list as the findings. */
    fun note(label: String, detail: String) {
        findings = findings + LoggedFinding(Lamp.CAUTION, label, "error", detail)
    }

    // Suspends until the row is committed. It used to fire-and-forget into scope.launch
    // while "Save report" read the same table, so the last finding of a session could
    // be missing from the PDF -- and from the digest computed over it.
    suspend fun logFinding(
        type: FindingType,
        label: String,
        value: String,
        detail: String? = null,
        lamp: Lamp = Lamp.PASS
    ) {
        val verdict = SafetyGate.evaluate(label)
        lastSafetyVerdict = verdict
        // The gate only ever returns STOP_ESCALATE or nothing, so without this every visual
        // defect was filed INFO and Severity.NOTABLE was unreachable by any code path -- the
        // severity column in the report was decoration. A defect finding is NOTABLE; the
        // hazard gate still overrides it upwards and nothing overrides it downwards.
        val severity = verdict.escalatedSeverity
            ?: if (type == FindingType.VISUAL_DEFECT) Severity.NOTABLE else Severity.INFO
        // A safety escalation always outranks the caller's own lamp.
        val effective = if (verdict.escalatedSeverity != null) Lamp.FLAG else lamp
        findings = findings + LoggedFinding(effective, label, value, detail ?: verdict.reason)

        try {
            db.inspectionDao().insert(
                InspectionEntity(
                    sessionId = sessionId,
                    timestampEpochMillis = System.currentTimeMillis(),
                    findingType = type,
                    label = label,
                    detailJson = "{}",
                    severity = severity
                )
            )
        } catch (e: Exception) {
            // The finding is already on screen; losing the row must not kill the session.
            note("Not saved", e.message ?: e.javaClass.simpleName)
        }
    }

    val insets = WindowInsets.systemBars.asPaddingValues()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = insets.calculateTopPadding(), bottom = insets.calculateBottomPadding())
    ) {
        // Session rail. The id is monospace because it is an identifier printed on the report
        // and read back aloud when two people compare copies.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Inspection",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                sessionId.take(8),
                style = ReadoutValue,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // --- Viewfinder with the alignment readout laid over it ---------------------------
        Box(
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
        ) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            // COMPATIBLE backs the preview with a TextureView. The default
                            // PERFORMANCE mode uses a SurfaceView composited in its own window,
                            // which paints over everything below it in the Compose tree.
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }
                    },
                    // clipToBounds: PreviewView scales the frame to fill, and without a clip
                    // the TextureView paints well past its declared box.
                    modifier = Modifier.fillMaxSize().clipToBounds(),
                    update = { previewView ->
                        scope.launch {
                            try {
                                cameraController.bindTo(previewView)
                            } catch (e: Exception) {
                                // Another app holding the camera would otherwise crash the
                                // walkthrough the moment this screen composes.
                                note("Camera unavailable", e.message ?: e.javaClass.simpleName)
                            }
                        }
                    }
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Camera access is off. Enable it to capture evidence.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // The one bold element on this screen: how square the phone is to the wall. It is
            // what the person is actively adjusting, so it is the largest thing here, on a
            // scrim so it stays legible against both a white wall and a dark corner.
            alignmentState?.let { s ->
                val delta = s.deltaFromBaselineDeg
                val lamp = when {
                    delta == null -> Lamp.CAUTION
                    s.isAligned -> Lamp.PASS
                    else -> Lamp.CAUTION
                }
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (delta == null) "Tilt" else "Off baseline",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                        Text(
                            if (delta == null)
                                "%d / %d deg".format(s.pitchDeg.toInt(), s.rollDeg.toInt())
                            else
                                "%.1f deg".format(delta),
                            style = ReadoutValueLarge,
                            color = Color.White
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusLamp(lamp)
                        Text(
                            when {
                                delta == null -> "Set a baseline"
                                s.isAligned -> "Aligned"
                                else -> "Adjust angle"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }

        if (modelsLoading) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Text(
                    "Loading models",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // --- Controls. Capture is the primary action and is weighted as such. --------------
        Column(Modifier.padding(horizontal = 16.dp)) {
            // Gated on the permission, not just on `busy`: without CAMERA the PreviewView
            // above is never composed, so bindTo() never runs and captureBitmap() throws
            // IllegalStateException into a coroutine with nothing to catch it.
            Button(
                enabled = !busy && !modelsLoading && hasCameraPermission,
                onClick = {
                    scope.launch {
                        busy = true
                        try {
                            val segmenter = visionSegmenter ?: return@launch
                            val bitmap = cameraController.captureBitmap()
                            val text = try {
                                OcrEngine.readText(bitmap)
                            } catch (e: Exception) {
                                // OcrEngine resumes with the exception on ML Kit failure. A failed
                                // text read must not take the capture -- or the walkthrough -- down.
                                note("OCR unavailable", e.message ?: "unknown error")
                                ""
                            }
                            if (text.isNotBlank()) {
                                logFinding(
                                    FindingType.OCR_TEXT_READ, "Text read", "OCR",
                                    text.take(90)
                                )
                            }
                            // 640x640 forward pass plus an 8400-anchor decode. On the main thread
                            // this was hundreds of milliseconds of frozen UI per capture.
                            val defects = withContext(Dispatchers.Default) {
                                segmenter.segmentDefects(
                                    bitmap, frameWidthInches = 48f, frameHeightInches = 36f
                                )
                            }
                            // The label states which segmenter actually ran: a heuristic result
                            // must never read like a model detection in a tenant-facing report.
                            val mode = if (segmenter.isTrainedModel) "YOLOv8n-Seg" else "heuristic"
                            defects.forEach { d ->
                                logFinding(
                                    type = FindingType.VISUAL_DEFECT,
                                    label = d.label,
                                    value = "%.2f sq ft".format(d.areaSqFtEstimate),
                                    detail = "%s, %.0f%% confidence".format(mode, d.confidence * 100f),
                                    lamp = if (d.confidence >= 0.6f) Lamp.FLAG else Lamp.CAUTION
                                )
                            }
                            if (defects.isEmpty() && text.isBlank()) {
                                findings = findings + LoggedFinding(
                                    Lamp.PASS, "Capture", "clear", "Nothing flagged in this frame"
                                )
                            }
                        } catch (e: Exception) {
                            note("Capture failed", e.message ?: e.javaClass.simpleName)
                        } finally {
                            busy = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(
                    when {
                        !hasCameraPermission -> "Camera access needed"
                        modelsLoading -> "Loading models"
                        busy -> "Analysing"
                        else -> "Capture and analyse"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        arTracker.captureBaseline()
                        // Recorded so the report shows a baseline was set and when.
                        // FindingType.AR_BASELINE_ALIGNMENT was declared and never emitted.
                        scope.launch {
                            val s = alignmentState
                            val pose = if (s == null) "pose unavailable"
                            else "pitch %d, roll %d".format(s.pitchDeg.toInt(), s.rollDeg.toInt())
                            logFinding(
                                FindingType.AR_BASELINE_ALIGNMENT,
                                "Baseline pose recorded", "baseline",
                                "$pose. Held in memory for this session only."
                            )
                        }
                    },
                    modifier = Modifier.weight(1f).height(46.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onBackground
                    )
                ) { Text("Set baseline") }

                OutlinedButton(
                    enabled = !busy && !modelsLoading && hasMicPermission,
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                // 1.2 s of blocking AudioRecord reads plus a 4096-point FFT. This
                                // ran on the main thread and froze the UI for the whole recording.
                                val result = withContext(Dispatchers.Default) {
                                    AcousticTapClassifier.recordAndClassifyOneTap(
                                        trained = trainedTapModel
                                    )
                                }
                                val lamp = when (result.verdict) {
                                    AcousticTapClassifier.TapVerdict.LIKELY_HOLLOW -> Lamp.FLAG
                                    AcousticTapClassifier.TapVerdict.LIKELY_SOLID -> Lamp.PASS
                                    AcousticTapClassifier.TapVerdict.INCONCLUSIVE -> Lamp.CAUTION
                                }
                                val reading = when (result.verdict) {
                                    AcousticTapClassifier.TapVerdict.LIKELY_HOLLOW -> "hollow"
                                    AcousticTapClassifier.TapVerdict.LIKELY_SOLID -> "solid"
                                    AcousticTapClassifier.TapVerdict.INCONCLUSIVE -> "unclear"
                                }
                                logFinding(
                                    FindingType.ACOUSTIC_TAP, "Tap test", reading,
                                    result.confidenceNote, lamp
                                )
                            } catch (e: Exception) {
                                // AudioRecord construction throws if the mic is held elsewhere.
                                note("Tap test failed", e.message ?: e.javaClass.simpleName)
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(46.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onBackground
                    )
                ) { Text(if (hasMicPermission) "Tap test" else "Mic is off") }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                enabled = !busy && irController.hasIrBlaster,
                onClick = {
                    scope.launch {
                        val profile = CommonAcIrProfiles.profiles.first()
                        // NEC-family header timings. The demo unit's real burst has to be captured
                        // on-site with an external receiver -- ConsumerIrManager cannot receive IR
                        // (see IrController) -- so the label below never claims more than was done:
                        // the emitter fired, and nothing confirmed the appliance responded.
                        val necHeaderBurst = intArrayOf(9000, 4500, 560, 560, 560, 1690)
                        when (val result =
                            irController.transmit(profile.typicalCarrierHz, necHeaderBurst)) {
                            is IrController.TransmitResult.Success -> logFinding(
                                FindingType.IR_APPLIANCE_CHECK, "IR command transmitted", "IR sent",
                                "%s, %d kHz — pattern not verified against this unit".format(
                                    profile.brand, profile.typicalCarrierHz / 1000
                                ),
                                Lamp.PASS
                            )
                            is IrController.TransmitResult.Failure -> logFinding(
                                FindingType.IR_APPLIANCE_CHECK, "IR transmit failed", "no IR",
                                result.reason, Lamp.CAUTION
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onBackground
                )
            ) {
                Text(
                    if (irController.hasIrBlaster) "Trigger AC over IR"
                    else "This phone has no IR blaster"
                )
            }
        }

        lastSafetyVerdict?.let { v ->
            if (!v.allowAsIs) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusLamp(Lamp.FLAG)
                    Text(
                        v.reason ?: "Escalated for review",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Findings",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                findings.size.toString(),
                style = ReadoutValue,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.outline
        )

        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (findings.isEmpty()) {
                // An empty state is an instruction, not an apology.
                Text(
                    "Point the camera at a wall and capture. Findings collect here and go " +
                        "straight into the report.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
            findings.forEach { f ->
                ReadingRow(f.lamp, f.label, f.value, f.detail)
            }
        }

        Button(
            enabled = findings.isNotEmpty() && !busy,
            onClick = {
                scope.launch {
                    busy = true
                    try {
                        val stored = db.inspectionDao().findingsForSessionOnce(sessionId)
                        // Digest, layout and file write, all off the UI thread. Built once
                        // here and handed upwards -- MainActivity used to re-query and
                        // rebuild it, producing a second report object for the same session.
                        val report = withContext(Dispatchers.Default) {
                            val r = ReportGenerator.buildReport(
                                sessionId, "Demo Property, Chennai", stored
                            )
                            ReportGenerator.renderToPdf(context, r)
                            r
                        }
                        onReportGenerated(report)
                    } catch (e: Exception) {
                        note("Report generation failed", e.message ?: e.javaClass.simpleName)
                    } finally {
                        busy = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(52.dp)
        ) {
            Text(
                if (findings.isEmpty()) "Capture a finding first" else "Save report",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
