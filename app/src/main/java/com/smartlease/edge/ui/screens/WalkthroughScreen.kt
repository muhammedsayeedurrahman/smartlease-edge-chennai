package com.smartlease.edge.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.smartlease.edge.vision.DefectSegmenterFactory
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The main demo screen. Walks through every subsystem from the design docs in one flow:
 * AR alignment status, capture plus OCR, capture plus vision segmenter, acoustic tap test,
 * IR transmit, safety-gated findings feed, then generate a signed local report.
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
    var findingsLog by remember { mutableStateOf(listOf<String>()) }
    var lastSafetyVerdict by remember { mutableStateOf<SafetyGate.Verdict?>(null) }
    var busy by remember { mutableStateOf(false) }

    val hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val hasMicPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    DisposableEffect(Unit) {
        arTracker.start { alignmentState = it }
        onDispose { arTracker.stop() }
    }

    fun logFinding(type: FindingType, label: String) {
        val verdict = SafetyGate.evaluate(label)
        lastSafetyVerdict = verdict
        val severity = verdict.escalatedSeverity ?: Severity.INFO
        val suffix = if (verdict.reason != null) " -> " + verdict.reason else ""
        findingsLog = findingsLog + ("[" + type.name + "] " + label + suffix)

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

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Walkthrough - session $sessionId", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))

        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx -> PreviewView(ctx) },
                modifier = Modifier.fillMaxWidth().height(240.dp),
                update = { previewView ->
                    scope.launch { cameraController.bindTo(previewView) }
                }
            )
        } else {
            Card(Modifier.fillMaxWidth().height(240.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("Camera permission not granted")
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        alignmentState?.let { s ->
            val statusText = if (s.deltaFromBaselineDeg == null)
                "Pitch " + s.pitchDeg.toInt() + ", Roll " + s.rollDeg.toInt() + " - no baseline captured yet"
            else
                "Delta from baseline: " + "%.1f".format(s.deltaFromBaselineDeg) + " deg - " + (if (s.isAligned) "ALIGNED" else "adjust angle")
            Text(statusText, fontSize = 12.sp)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { arTracker.captureBaseline() }) { Text("Set Baseline") }

            Button(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    try {
                        val bitmap = cameraController.captureBitmap()
                        val text = OcrEngine.readText(bitmap)
                        if (text.isNotBlank()) {
                            logFinding(FindingType.OCR_TEXT_READ, text.take(120))
                        }
                        val defects = visionSegmenter.segmentDefects(bitmap, frameWidthInches = 48f, frameHeightInches = 36f)
                        // Label reflects which segmenter actually ran: a heuristic result must
                        // never read like a model detection in the tenant-facing report.
                        val mode = if (visionSegmenter.isTrainedModel) "YOLOv8n-Seg" else "heuristic"
                        defects.forEach { d ->
                            val areaStr = "%.2f".format(d.areaSqFtEstimate)
                            val confStr = "%.0f".format(d.confidence * 100f)
                            logFinding(
                                FindingType.VISUAL_DEFECT,
                                d.label + ", ~" + areaStr + " sq ft (" + mode + ", " + confStr + "% confidence)"
                            )
                        }
                        if (defects.isEmpty() && text.isBlank()) {
                            findingsLog = findingsLog + "Capture: nothing flagged"
                        }
                    } finally {
                        busy = false
                    }
                }
            }) { Text("Capture + Analyze") }
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy && hasMicPermission, onClick = {
                scope.launch {
                    busy = true
                    try {
                        val result = AcousticTapClassifier.recordAndClassifyOneTap(trained = trainedTapModel)
                        logFinding(
                            FindingType.ACOUSTIC_TAP,
                            result.verdict.toString() + " (" + result.confidenceNote + ")"
                        )
                    } finally {
                        busy = false
                    }
                }
            }) { Text(if (hasMicPermission) "Tap Test (1.2s)" else "Mic permission needed") }

            Button(enabled = !busy, onClick = {
                val profile = CommonAcIrProfiles.profiles.first()
                // Placeholder pattern. Day 12 of the plan replaces this with the real
                // on-site captured burst for the actual demo AC unit.
                val placeholderPattern = intArrayOf(9000, 4500, 560, 560, 560, 1690)
                val result = irController.transmit(profile.typicalCarrierHz, placeholderPattern)
                val label = when (result) {
                    is IrController.TransmitResult.Success -> "IR transmit OK (" + profile.brand + " profile, placeholder pattern, replace with Day 12 capture)"
                    is IrController.TransmitResult.Failure -> "IR transmit failed: " + result.reason
                }
                logFinding(FindingType.IR_APPLIANCE_CHECK, label)
            }) { Text(if (irController.hasIrBlaster) "Trigger AC (IR)" else "No IR blaster detected") }
        }

        lastSafetyVerdict?.let { v ->
            if (!v.allowAsIs) {
                Spacer(Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFFFDECEC))) {
                    Text("WARNING: " + v.reason, Modifier.padding(10.dp), fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Findings log", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            findingsLog.forEach { line -> Text("- " + line, fontSize = 11.sp) }
        }

        Button(
            enabled = findingsLog.isNotEmpty() && !busy,
            onClick = {
                scope.launch {
                    busy = true
                    try {
                        val findings = db.inspectionDao().findingsForSessionOnce(sessionId)
                        val report = ReportGenerator.buildReport(sessionId, "Demo Property, Chennai", findings)
                        ReportGenerator.renderToPdf(context, report)
                        onReportGenerated(sessionId)
                    } finally {
                        busy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Generate Signed Report") }
    }
}
