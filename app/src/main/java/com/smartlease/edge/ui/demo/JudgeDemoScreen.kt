package com.smartlease.edge.ui.demo

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.smartlease.edge.R
import com.smartlease.edge.narration.NarrationSource
import com.smartlease.edge.report.ReportGenerator
import com.smartlease.edge.vision.describeFault
import kotlinx.coroutines.launch

/**
 * A judge-facing, end-to-end walkthrough of the real pipeline: it never scripts a fake result.
 * [DemoPipelineRunner.run] decodes the two bundled photographs and runs them through the same
 * [com.smartlease.edge.vision.DefectSegmenterFactory], deduction engine and
 * [com.smartlease.edge.narration.ReportNarratorFactory] a live inspection uses, then hands this
 * screen the real defects, the real rupee figures, whichever narrator actually ran on this
 * device, and a real PDF on disk with its own SHA-256 -- so a presenter can show the
 * architecture executing stage by stage instead of describing it.
 *
 * This never writes to Room and never touches a live property; [DemoPipelineRunner] only reads
 * two drawables and writes one demo PDF into this app's own files dir.
 */
private enum class DemoStage(val label: String) {
    EVIDENCE("1 · Evidence"),
    VISION("2 · On-device vision"),
    DEDUCTION("3 · Deduction pricing"),
    NARRATION("4 · Narration"),
    REPORT("5 · Report & tamper-evidence")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JudgeDemoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var stage by rememberSaveable { mutableStateOf(DemoStage.EVIDENCE) }
    var isRunning by remember { mutableStateOf(false) }
    var runError by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<DemoPipelineRunner.Result?>(null) }

    fun runPipeline() {
        // DemoPipelineRunner.run() now hops to Dispatchers.Default immediately, so this state
        // write reaches the Button's `enabled` before any heavy work starts -- but a guard here
        // costs nothing and means a stray extra tap can never queue a second overlapping run.
        if (isRunning) return
        isRunning = true
        runError = null
        scope.launch {
            try {
                result = DemoPipelineRunner.run(context)
                stage = DemoStage.VISION
            } catch (e: Exception) {
                runError = e.message ?: e.javaClass.simpleName
            } finally {
                isRunning = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Judge walkthrough", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IntroCard()

            if (stage != DemoStage.EVIDENCE) {
                StageProgress(stage)
            }

            when (stage) {
                DemoStage.EVIDENCE -> EvidenceStage(
                    isRunning = isRunning,
                    runError = runError,
                    onRun = ::runPipeline
                )
                DemoStage.VISION -> result?.let { VisionStage(it) }
                DemoStage.DEDUCTION -> result?.let { DeductionStage(it) }
                DemoStage.NARRATION -> result?.let { NarrationStage(it) }
                DemoStage.REPORT -> result?.let { ReportStage(it, context) }
            }

            if (stage != DemoStage.EVIDENCE) {
                StageNav(
                    stage = stage,
                    onPrev = { stage = DemoStage.entries[stage.ordinal - 1] },
                    onNext = if (stage != DemoStage.REPORT) {
                        { stage = DemoStage.entries[stage.ordinal + 1] }
                    } else null,
                    onRestart = {
                        stage = DemoStage.EVIDENCE
                        result = null
                        runError = null
                    }
                )
            }
        }
    }
}

@Composable
private fun StageProgress(stage: DemoStage) {
    Column {
        LinearProgressIndicator(
            progress = { (stage.ordinal + 1f) / DemoStage.entries.size },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stage.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun StageNav(stage: DemoStage, onPrev: () -> Unit, onNext: (() -> Unit)?, onRestart: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onPrev, modifier = Modifier.weight(1f)) {
            Text(if (stage == DemoStage.VISION) "Back to evidence" else "Back")
        }
        if (onNext != null) {
            Button(onClick = onNext, modifier = Modifier.weight(1f)) {
                Text("Next")
            }
        } else {
            Button(onClick = onRestart, modifier = Modifier.weight(1f)) {
                Text("Restart walkthrough")
            }
        }
    }
}

@Composable
private fun IntroCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("Harbour View · 2BHK", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Move-in to move-out evidence workflow -- the same pipeline a live inspection runs, across three fixed rooms",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Offline evidence · transparent review · fingerprinted report",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

private data class DemoRoomEvidence(
    val roomType: String,
    val moveInRes: Int,
    val moveInTimestamp: String,
    val moveInDesc: String,
    val moveOutRes: Int,
    val moveOutTimestamp: String,
    val moveOutDesc: String
)

private val DEMO_EVIDENCE = listOf(
    DemoRoomEvidence(
        roomType = "Living Room · Wall",
        moveInRes = R.drawable.demo_move_in_wall,
        moveInTimestamp = "14 Aug 2026 · 10:12 IST",
        moveInDesc = "Recorded as clear. No visible surface break noted.",
        moveOutRes = R.drawable.demo_move_out_cracked_wall,
        moveOutTimestamp = "13 Sep 2026 · 16:42 IST",
        moveOutDesc = "Same wall, hairline crack now visible running down from the crown moulding."
    ),
    DemoRoomEvidence(
        roomType = "Kitchen · Backsplash",
        moveInRes = R.drawable.demo_move_in_kitchen,
        moveInTimestamp = "14 Aug 2026 · 10:20 IST",
        moveInDesc = "Tile backsplash recorded as clear, no chips or staining.",
        moveOutRes = R.drawable.demo_move_out_kitchen,
        moveOutTimestamp = "13 Sep 2026 · 16:50 IST",
        moveOutDesc = "Same backsplash, hairline crack and a faint stain now visible near the grout line."
    ),
    DemoRoomEvidence(
        roomType = "Bedroom · Wall",
        moveInRes = R.drawable.demo_move_in_bedroom,
        moveInTimestamp = "14 Aug 2026 · 10:28 IST",
        moveInDesc = "Bedroom wall recorded as clear at move-in.",
        moveOutRes = R.drawable.demo_move_out_bedroom,
        moveOutTimestamp = "13 Sep 2026 · 16:58 IST",
        moveOutDesc = "Same wall, scuff mark and hairline crack now visible above the nightstand."
    )
)

@Composable
private fun EvidenceStage(isRunning: Boolean, runError: String?, onRun: () -> Unit) {
    Text(
        "Evidence timeline",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )
    Text(
        "Three rooms, each recorded twice. The move-out photograph is the exact same shot as the move-in one with one small defect added -- the realistic case a move-out inspection exists to catch, not a different room standing in for \"before\".",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    DEMO_EVIDENCE.forEach { room ->
        Text(room.roomType, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            EvidenceCard(
                title = "Move-in",
                timestamp = room.moveInTimestamp,
                resource = room.moveInRes,
                description = room.moveInDesc,
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            EvidenceCard(
                title = "Move-out",
                timestamp = room.moveOutTimestamp,
                resource = room.moveOutRes,
                description = room.moveOutDesc,
                accent = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Rounded.PhotoCamera,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "These six photographs are a fixed reference pack. Everything after this point -- " +
                    "vision, pricing, narration, the PDF -- is computed fresh by this app, on this " +
                    "phone, when you tap the button below. Live inspections use the camera capture " +
                    "flow to produce the same two kinds of frame, for as many rooms as a property has.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }

    if (runError != null) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(
                "Pipeline failed: $runError",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }

    Button(
        onClick = onRun,
        enabled = !isRunning,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isRunning) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Running vision, deduction and narration…")
        } else {
            Icon(Icons.Rounded.Description, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Run the real pipeline on this evidence")
        }
    }
}

@Composable
private fun EvidenceCard(
    title: String,
    timestamp: String,
    resource: Int,
    description: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier, shape = RoundedCornerShape(18.dp)) {
        Column {
            Image(
                painter = painterResource(resource),
                contentDescription = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
                contentScale = ContentScale.Crop
            )
            Column(Modifier.padding(12.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(timestamp, style = MaterialTheme.typography.labelSmall, color = accent)
                Spacer(Modifier.height(6.dp))
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * What [com.smartlease.edge.vision.DefectSegmenterFactory] actually returned for each room's
 * move-out photograph -- the trained-model flag included, because whether a detection came from
 * the exported model or the heuristic fallback is exactly the distinction this app refuses to
 * blur anywhere else on the page.
 */
@Composable
private fun VisionStage(result: DemoPipelineRunner.Result) {
    StageHeader(
        "On-device vision",
        "Every move-out photograph across all ${result.rooms.size} rooms, run through " +
            (if (result.usingTrainedModel) "the trained defect segmenter" else "the heuristic fallback segmenter -- no exported model is bundled on this build") +
            "."
    )
    result.rooms.forEach { room ->
        Card(shape = RoundedCornerShape(18.dp)) {
            Column {
                Text(
                    "${room.roomType} · ${room.surfaceType}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 0.dp)
                )
                Image(
                    bitmap = room.moveOutBitmap.asImageBitmap(),
                    contentDescription = "${room.roomType} move-out photograph",
                    modifier = Modifier.fillMaxWidth().height(180.dp).padding(top = 8.dp),
                    contentScale = ContentScale.Crop
                )
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (room.defects.isEmpty()) {
                        Text(
                            "No defects detected on this frame by the current on-device segmenter.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        room.defects.forEach { defect ->
                            Text(
                                defect.describeFault(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeductionStage(result: DemoPipelineRunner.Result) {
    StageHeader(
        "Deduction pricing",
        "Every detected defect above, priced by the same rate-card engine a real move-out report uses -- against a demo deposit of ₹${DemoPipelineRunner.DEMO_DEPOSIT_RUPEES}."
    )
    val deduction = result.deduction
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (deduction == null || deduction.lines.isEmpty()) {
                Text(
                    "No priced deductions -- either no defect was detected, or the detection's confidence was below the pricing floor.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                deduction.lines.forEach { line ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(line.description, style = MaterialTheme.typography.bodyLarge)
                            Text(line.basis, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("₹${line.amountRupees}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider()
                CaseLine("Deposit", "₹${deduction.depositRupees}")
                CaseLine("Total deductions", "₹${deduction.totalDeductionRupees}")
                CaseLine("Refund due", "₹${deduction.refundRupees}")
            }
        }
    }
}

@Composable
private fun NarrationStage(result: DemoPipelineRunner.Result) {
    val section = result.findingsSection
    val source = section?.narrationSource ?: NarrationSource.TEMPLATE
    StageHeader(
        "Narration",
        "Which narrator actually ran on this device, and the paragraph it wrote from the findings above -- nothing is added that the findings don't already say."
    )
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CaseLine("Model selection", result.narratorStatus)
            HorizontalDivider()
            CaseLine(if (source == NarrationSource.GEMMA) "Written by Gemma" else "Written by template", section?.body ?: "(no findings section)")
            Text(
                ReportGenerator.narrationCredit(source),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReportStage(result: DemoPipelineRunner.Result, context: android.content.Context) {
    StageHeader(
        "Report & tamper-evidence",
        "A real PDF, written to this app's own storage just now, with the same SHA-256 fingerprint scheme every report on this app uses."
    )
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CaseLine("Property", result.report.propertyLabel)
            CaseLine("Session", result.report.sessionId.take(8) + "…")
            CaseLine("Verdict", result.report.overallVerdict)
            CaseLine("Findings", "${result.report.findingCount} recorded")
            HorizontalDivider()
            CaseLine("Report fingerprint (SHA-256)", result.report.findingsSha256)
            CaseLine("Privacy", "Photos, video and audio remain on the device")
        }
    }
    Button(
        onClick = { openDemoPdf(context, result.pdfFile) },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors()
    ) {
        Icon(Icons.Rounded.Description, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("View the generated PDF")
    }
    Text(
        "A live inspection ends with both parties countersigning on-screen; that step is skipped in this fixed demo.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun StageHeader(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CaseLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Same FileProvider authority and view-intent shape PropertyDashboardScreen uses to open a lease PDF. */
private fun openDemoPdf(context: android.content.Context, file: java.io.File) {
    try {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".reports", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app found to open PDF", Toast.LENGTH_SHORT).show()
    }
}
