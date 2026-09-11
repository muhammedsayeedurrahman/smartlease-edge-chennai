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
import com.smartlease.edge.report.ReportGenerator
import com.smartlease.edge.safety.SafetyGate
import com.smartlease.edge.ui.components.Lamp
import com.smartlease.edge.ui.components.ReadingRow
import com.smartlease.edge.ui.components.StatusLamp
import com.smartlease.edge.ui.theme.ReadoutValue
import com.smartlease.edge.ui.theme.ReadoutValueLarge
import com.smartlease.edge.vision.DefectSegmenterFactory
import kotlinx.coroutines.launch
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
 * and defect segmentation, acoustic tap test, IR appliance trigger, then a signed local report.
 */
@Composable
fun WalkthroughScreen(onReportGenerated: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val sessionId = remember { UUID.randomUUID().toString().take(8) }
    val cameraController = remember { CameraController(context, lifecycleOwner) }
    val arTracker = remember { ArAlignmentTracker(context) }
    val irController = remember { IrController(context) }
    val visionSegmenter = remember { DefectSegmenterFactory.create(context) }
    // null when no trained model ships -- classify() then keeps the heuristic
    val trainedTapModel = remember { TrainedTapClassifier.create(context) }
    val db = remember { AppDatabase.get(context) }

    var alignmentState by remember { mutableStateOf<ArAlignmentTracker.AlignmentState?>(null) }
    var findings by remember { mutableStateOf(listOf<LoggedFinding>()) }
    var lastSafetyVerdict by remember { mutableStateOf<SafetyGate.Verdict?>(null) }
    var busy by remember { mutableStateOf(false) }

    val hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val hasMicPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    DisposableEffect(Unit) {
        arTracker.start { alignmentState = it }
        onDispose { arTracker.stop() }
    }

    fun logFinding(
        type: FindingType,
        label: String,
        value: String,
        detail: String? = null,
        lamp: Lamp = Lamp.PASS
    ) {
        val verdict = SafetyGate.evaluate(label)
        lastSafetyVerdict = verdict
        val severity = verdict.escalatedSeverity ?: Severity.INFO
        // A safety escalation always outranks the caller's own lamp.
        val effective = if (verdict.escalatedSeverity != null) Lamp.FLAG else lamp
        findings = findings + LoggedFinding(effective, label, value, detail ?: verdict.reason)

        scope.launch {
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
                sessionId,
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
                    update = { previewView -> scope.launch { cameraController.bindTo(previewView) } }
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

        Spacer(Modifier.height(14.dp))

        // --- Controls. Capture is the primary action and is weighted as such. --------------
        Column(Modifier.padding(horizontal = 16.dp)) {
            Button(
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        try {
                            val bitmap = cameraController.captureBitmap()
                            val text = OcrEngine.readText(bitmap)
                            if (text.isNotBlank()) {
                                logFinding(
                                    FindingType.OCR_TEXT_READ, "Text read", "OCR",
                                    text.take(90)
                                )
                            }
                            val defects = visionSegmenter.segmentDefects(
                                bitmap, frameWidthInches = 48f, frameHeightInches = 36f
                            )
                            // The label states which segmenter actually ran: a heuristic result
                            // must never read like a model detection in a tenant-facing report.
                            val mode = if (visionSegmenter.isTrainedModel) "YOLOv8n-Seg" else "heuristic"
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
                        } finally {
                            busy = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(
                    if (busy) "Analysing" else "Capture and analyse",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { arTracker.captureBaseline() },
                    modifier = Modifier.weight(1f).height(46.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onBackground
                    )
                ) { Text("Set baseline") }

                OutlinedButton(
                    enabled = !busy && hasMicPermission,
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                val result = AcousticTapClassifier.recordAndClassifyOneTap(
                                    trained = trainedTapModel
                                )
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
                    val profile = CommonAcIrProfiles.profiles.first()
                    // Placeholder burst, replaced by the on-site capture for the demo unit.
                    val placeholderPattern = intArrayOf(9000, 4500, 560, 560, 560, 1690)
                    when (val result = irController.transmit(profile.typicalCarrierHz, placeholderPattern)) {
                        is IrController.TransmitResult.Success -> logFinding(
                            FindingType.IR_APPLIANCE_CHECK, "AC responded", "IR sent",
                            profile.brand + " profile, placeholder pattern", Lamp.PASS
                        )
                        is IrController.TransmitResult.Failure -> logFinding(
                            FindingType.IR_APPLIANCE_CHECK, "AC check skipped", "no IR",
                            result.reason, Lamp.CAUTION
                        )
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
                        val report = ReportGenerator.buildReport(
                            sessionId, "Demo Property, Chennai", stored
                        )
                        ReportGenerator.renderToPdf(context, report)
                        onReportGenerated(sessionId)
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
