package com.smartlease.edge.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
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
import com.smartlease.edge.data.SessionType
import com.smartlease.edge.data.Severity
import com.smartlease.edge.deduction.FindingDetail
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
import com.smartlease.edge.inspection360.HeadingTracker
import com.smartlease.edge.inspection360.Quadrant
import com.smartlease.edge.inspection360.RoomDefectRecord
import com.smartlease.edge.inspection360.RoomInspectionCoordinator
import com.smartlease.edge.inspection360.WallInspectionAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Keyframe photo captured along a 360-degree room panorama turn.
 */
data class Panorama360Frame(
    val quadrant: Quadrant,
    val thumbnail: Bitmap,
    val azimuth: Float,
    val defects: List<DefectSegmenter.Defect>,
    val timestampMs: Long = System.currentTimeMillis()
)

/** Pre-filled deposit figure -- the demo's own stated amount, not a claim about any real lease. */
private const val DEFAULT_DEPOSIT_RUPEES = 90_000

/**
 * One hardcoded property, matched between a move-in and its later move-out session. A real
 * multi-property build would need an actual property picker/id; this app documents one
 * demo lease, so the label doubles as that id.
 */
private const val PROPERTY_LABEL = "Demo Property, Chennai"

/** Long enough for any real Chennai deposit, short enough that a mis-tap cannot run off screen. */
private const val MAX_DEPOSIT_DIGITS = 8

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
fun WalkthroughScreen(sessionType: SessionType, onReportGenerated: (InspectionReport) -> Unit) {
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

    // 360 auto-capture: walks the 4 compass quadrants, gated on device steadiness (gyro) and
    // frame sharpness (Laplacian variance), firing one defect-segmenter pass per wall. Needs
    // the same DefectSegmenter as manual capture, so it can't exist until models finish loading.
    var isAutoCaptureMode by remember { mutableStateOf(false) }
    var isMovingTooFast by remember { mutableStateOf(false) }
    val captured360Frames = remember { mutableStateListOf<Panorama360Frame>() }
    var selected360Frame by remember { mutableStateOf<Panorama360Frame?>(null) }

    // Nothing else in the app has a source for the deposit -- DeductionEngine needs a
    // rupee figure to subtract findings from, and until now nothing ever supplied one.
    // Kept as the raw digit string the field shows, not an Int, so a mid-edit empty field
    // (backspacing to retype) doesn't have to round-trip through a placeholder value.
    var depositInput by remember { mutableStateOf(DEFAULT_DEPOSIT_RUPEES.toString()) }

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
        detail: FindingDetail,
        noteText: String? = null,
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
        findings = findings + LoggedFinding(effective, label, value, noteText ?: verdict.reason)

        try {
            db.inspectionDao().insert(
                InspectionEntity(
                    sessionId = sessionId,
                    timestampEpochMillis = System.currentTimeMillis(),
                    findingType = type,
                    label = label,
                    // Used to be a hardcoded "{}" for every finding, which meant
                    // DeductionEngine.summarise had nothing to price a single row against --
                    // every session produced an empty balance sheet regardless of what was
                    // actually found. This is the one line that makes the deposit arithmetic
                    // possible at all.
                    detailJson = detail.toJson(),
                    severity = severity,
                    sessionType = sessionType,
                    propertyLabel = PROPERTY_LABEL
                )
            )
        } catch (e: Exception) {
            // The finding is already on screen; losing the row must not kill the session.
            note("Not saved", e.message ?: e.javaClass.simpleName)
        }
    }

    // Built once the segmenter finishes loading, not before -- RoomInspectionCoordinator needs
    // a real DefectSegmenter, not the nullable one manual capture tolerates. logFinding is a
    // suspend fun, so the coordinator's plain-lambda callback hops into scope.launch to call it,
    // same pattern as "Set baseline" below.
    val roomCoordinator = remember(visionSegmenter) {
        val segmenter = visionSegmenter ?: return@remember null
        RoomInspectionCoordinator(
            defectSegmenter = segmenter,
            onWallFrameProcessed = { quad, thumb, defects, az ->
                captured360Frames.add(
                    Panorama360Frame(
                        quadrant = quad,
                        thumbnail = thumb,
                        azimuth = az,
                        defects = defects
                    )
                )
            },
            onInspectionFinished = { records ->
                isAutoCaptureMode = false
                scope.launch {
                    records.forEach { record ->
                        if (record.defectClass == RoomDefectRecord.CLEAR) {
                            findings = findings + LoggedFinding(
                                Lamp.PASS, record.wall, "clear", "Nothing flagged on this wall"
                            )
                        } else {
                            logFinding(
                                type = FindingType.VISUAL_DEFECT,
                                label = "${record.wall}: ${record.label}",
                                value = "%.2f sq ft".format(record.areaSqFt),
                                detail = FindingDetail.VisualDefect(
                                    defectClass = record.defectClass,
                                    areaSqFt = record.areaSqFt,
                                    confidence = record.confidence,
                                    fromTrainedModel = segmenter.isTrainedModel
                                ),
                                noteText = "360 auto-capture, %.0f%% confidence".format(record.confidence * 100f),
                                lamp = if (record.confidence >= 0.6f) Lamp.FLAG else Lamp.CAUTION
                            )
                        }
                    }
                }
            }
        )
    }

    val wallAnalyzer = remember(roomCoordinator) {
        val coordinator = roomCoordinator ?: return@remember null
        val tracker = HeadingTracker(context) { _, _ -> }
        WallInspectionAnalyzer(
            headingTracker = tracker,
            onWallCaptured = { quad, bmp, thumb, z, az ->
                coordinator.onWallKeyframeAcquired(quad, bmp, thumb, z, az)
            },
            onSpeedWarning = { movingTooFast -> isMovingTooFast = movingTooFast }
        )
    }

    DisposableEffect(wallAnalyzer) {
        onDispose { wallAnalyzer?.stop() }
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
                if (sessionType == SessionType.MOVE_IN) "Move-in inspection" else "Move-out inspection",
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
                                val analyzerToBind = if (isAutoCaptureMode) wallAnalyzer else null
                                cameraController.bindTo(previewView, analyzerToBind)
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
            if (isAutoCaptureMode) {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isMovingTooFast) Color.Red.copy(alpha = 0.8f) else Color(0xFF2E7D32).copy(alpha = 0.85f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        if (isMovingTooFast) "SLOW DOWN TO CAPTURE" else "AUTO-CAPTURING 360 -- turn slowly",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            } else {
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

        // --- 360 Live Panorama Filmstrip (Frame-by-Frame below viewfinder) ---
        if (isAutoCaptureMode || captured360Frames.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Explore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "360° PANORAMA FRAMES (${captured360Frames.size}/4)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        when {
                            captured360Frames.size >= 4 -> "COMPLETE"
                            isAutoCaptureMode -> "RECORDING 360..."
                            else -> "${captured360Frames.size} CAPTURED"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (captured360Frames.size >= 4) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (captured360Frames.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Turn slowly in a full circle. Photos will appear here frame-by-frame as each wall is captured.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(captured360Frames) { frame ->
                            PanoramaFrameCard(frame = frame, onClick = { selected360Frame = frame })
                        }
                    }
                }
            }
        }

        selected360Frame?.let { frame ->
            AlertDialog(
                onDismissRequest = { selected360Frame = null },
                confirmButton = {
                    TextButton(onClick = { selected360Frame = null }) {
                        Text("Close")
                    }
                },
                title = {
                    Text("${frame.quadrant.name} Wall (%.0f°)".format(frame.azimuth))
                },
                text = {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            bitmap = frame.thumbnail.asImageBitmap(),
                            contentDescription = frame.quadrant.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(10.dp))
                        if (frame.defects.isEmpty()) {
                            Text(
                                "No defects detected on this wall.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF2E7D32)
                            )
                        } else {
                            Text(
                                "Detected ${frame.defects.size} defect(s):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(4.dp))
                            frame.defects.forEach { defect ->
                                Text(
                                    "• ${defect.label} (%.2f sq ft, %.0f%% conf)".format(
                                        defect.areaSqFtEstimate, defect.confidence * 100f
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            )
        }

        Spacer(Modifier.height(14.dp))

        // --- Controls. Capture is the primary action and is weighted as such. --------------
        Column(Modifier.padding(horizontal = 16.dp)) {
            // Gated on the permission, not just on `busy`: without CAMERA the PreviewView
            // above is never composed, so bindTo() never runs and captureBitmap() throws
            // IllegalStateException into a coroutine with nothing to catch it.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !busy && !modelsLoading && hasCameraPermission && !isAutoCaptureMode,
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
                                    type = FindingType.OCR_TEXT_READ, label = "Text read", value = "OCR",
                                    detail = FindingDetail.Note(text.take(90)),
                                    noteText = text.take(90)
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
                                    detail = FindingDetail.VisualDefect(
                                        defectClass = d.defectClass,
                                        areaSqFt = d.areaSqFtEstimate,
                                        confidence = d.confidence,
                                        fromTrainedModel = segmenter.isTrainedModel
                                    ),
                                    noteText = "%s, %.0f%% confidence".format(mode, d.confidence * 100f),
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
                modifier = Modifier.weight(1f).height(52.dp)
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

            OutlinedButton(
                enabled = !busy && !modelsLoading && hasCameraPermission && wallAnalyzer != null,
                onClick = {
                    isAutoCaptureMode = !isAutoCaptureMode
                    if (isAutoCaptureMode) {
                        captured360Frames.clear()
                        wallAnalyzer?.reset()
                    }
                },
                modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isAutoCaptureMode) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onBackground
                )
            ) {
                Text(
                    if (isAutoCaptureMode) "Stop 360" else "Start 360",
                    style = MaterialTheme.typography.titleMedium
                )
            }
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
                                type = FindingType.AR_BASELINE_ALIGNMENT,
                                label = "Baseline pose recorded", value = "baseline",
                                detail = FindingDetail.Note(pose),
                                noteText = "$pose. Held in memory for this session only."
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
                                // result.hollowProbability is the trained model's raw P(hollow)
                                // when a trained classification produced this verdict, and null
                                // whenever the heuristic decided instead -- no model shipped, or
                                // TrainedTapClassifier declined to score a too-short/too-quiet
                                // clip even with a model loaded. "Confidence" here means the
                                // model's confidence in its OWN verdict, so a solid reading
                                // reports 1 - P(hollow), not P(hollow) itself. Zero only when
                                // there is genuinely no probability to report: INCONCLUSIVE, or
                                // the heuristic path.
                                val tapConfidence = when {
                                    result.verdict == AcousticTapClassifier.TapVerdict.INCONCLUSIVE -> 0f
                                    result.hollowProbability == null -> 0f
                                    result.verdict == AcousticTapClassifier.TapVerdict.LIKELY_SOLID ->
                                        1f - result.hollowProbability
                                    else -> result.hollowProbability
                                }
                                logFinding(
                                    type = FindingType.ACOUSTIC_TAP, label = "Tap test", value = reading,
                                    detail = FindingDetail.AcousticTap(
                                        verdict = reading,
                                        confidence = tapConfidence,
                                        // Whether the trained model actually produced THIS
                                        // verdict, not merely whether one was loaded -- a
                                        // loaded model can still fall back to the heuristic
                                        // per tap, and reporting the wrong source here is what
                                        // would make DeductionEngine's basis text lie about
                                        // where the number came from.
                                        fromTrainedModel = result.hollowProbability != null
                                    ),
                                    noteText = result.confidenceNote,
                                    lamp = lamp
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
                            // "functional = true" here means only what the honesty comment
                            // above says: the emitter fired. DeductionEngine's clearedNotes
                            // wording is the one place that gets to say what that does and
                            // does not confirm -- this call site adds no claim of its own.
                            is IrController.TransmitResult.Success -> {
                                // Toast confirms only what the hardware call itself reports --
                                // the emitter fired. It cannot and does not claim the appliance
                                // reacted; ConsumerIrManager has no receive path to check that.
                                // A person still has to watch the unit to know if it responded.
                                Toast.makeText(
                                    context,
                                    "IR sent (${profile.brand}, ${profile.typicalCarrierHz / 1000}kHz) — watch the unit, this doesn't confirm it reacted",
                                    Toast.LENGTH_LONG
                                ).show()
                                logFinding(
                                    type = FindingType.IR_APPLIANCE_CHECK, label = "IR command transmitted", value = "IR sent",
                                    detail = FindingDetail.ApplianceCheck(
                                        appliance = "${profile.brand} AC", functional = true
                                    ),
                                    noteText = "%s, %d kHz — pattern not verified against this unit".format(
                                        profile.brand, profile.typicalCarrierHz / 1000
                                    ),
                                    lamp = Lamp.PASS
                                )
                            }
                            is IrController.TransmitResult.Failure -> {
                                Toast.makeText(
                                    context,
                                    "IR transmit failed: ${result.reason}",
                                    Toast.LENGTH_LONG
                                ).show()
                                logFinding(
                                    type = FindingType.IR_APPLIANCE_CHECK, label = "IR transmit failed", value = "no IR",
                                    detail = FindingDetail.ApplianceCheck(
                                        appliance = "${profile.brand} AC",
                                        functional = false,
                                        // The command never left the device, so this is a tool
                                        // failure, not a reading on the appliance -- irTransmitted
                                        // and the reason both have to be persisted, or
                                        // DeductionEngine and the report have no way to tell this
                                        // apart from an appliance that was actually checked.
                                        irTransmitted = false,
                                        failureReason = result.reason
                                    ),
                                    noteText = result.reason,
                                    lamp = Lamp.CAUTION
                                )
                            }
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

        Column(Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = depositInput,
                onValueChange = { new ->
                    // Reject anything that is not plain digits, and cap the length so a
                    // mis-tap cannot produce a refund figure with more zeros than any real
                    // Chennai deposit has. An empty field is allowed mid-edit; it is treated
                    // as "no deposit entered" below, same as a session with none at all.
                    if (new.isEmpty() || (new.length <= MAX_DEPOSIT_DIGITS && new.all(Char::isDigit))) {
                        depositInput = new
                    }
                },
                label = { Text("Deposit held (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(8.dp))

        Button(
            enabled = findings.isNotEmpty() && !busy,
            onClick = {
                scope.launch {
                    busy = true
                    try {
                        val stored = db.inspectionDao().findingsForSessionOnce(sessionId)
                        // Null when the field was left empty -- ReportGenerator then leaves
                        // the report's deductions unset rather than pricing against a figure
                        // nobody entered.
                        val depositRupees = depositInput.toIntOrNull()
                        // A move-out session prices only what's new since move-in: look up the
                        // most recent move-in session for this same property, if one exists on
                        // this device, and pass its finding keys through so DeductionEngine
                        // treats an already-there defect as "present at move-in", never billed.
                        val baselineKeys = if (sessionType == SessionType.MOVE_OUT) {
                            val baselineSessionId = db.inspectionDao()
                                .mostRecentSessionId(PROPERTY_LABEL, SessionType.MOVE_IN)
                            baselineSessionId
                                ?.let { db.inspectionDao().findingsForSessionOnce(it) }
                                ?.map { "${it.findingType}:${it.label}" }
                                ?.toSet()
                                ?: emptySet()
                        } else {
                            emptySet()
                        }
                        // Digest, layout and file write, all off the UI thread. Built once
                        // here and handed upwards -- MainActivity used to re-query and
                        // rebuild it, producing a second report object for the same session.
                        val report = withContext(Dispatchers.Default) {
                            val r = ReportGenerator.buildReport(
                                sessionId, PROPERTY_LABEL, stored, depositRupees, baselineKeys,
                                sessionType = sessionType
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

@Composable
private fun PanoramaFrameCard(
    frame: Panorama360Frame,
    onClick: () -> Unit
) {
    val hasDefects = frame.defects.isNotEmpty()
    val borderColor = if (hasDefects) Color(0xFFE57373) else Color(0xFF81C784)

    Column(
        modifier = Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(6.dp))
        ) {
            Image(
                bitmap = frame.thumbnail.asImageBitmap(),
                contentDescription = frame.quadrant.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Quadrant badge overlay
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    frame.quadrant.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp
                )
            }
            // Defect indicator dot
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(8.dp)
                    .background(if (hasDefects) Color.Red else Color(0xFF2E7D32), CircleShape)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (hasDefects) "${frame.defects.size} fault(s)" else "Clear",
            style = MaterialTheme.typography.labelSmall,
            color = if (hasDefects) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Text(
            "%.0f°".format(frame.azimuth),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.sp
        )
    }
}
