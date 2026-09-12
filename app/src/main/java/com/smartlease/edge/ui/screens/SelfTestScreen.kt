package com.smartlease.edge.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.smartlease.edge.diagnostics.SelfTest
import com.smartlease.edge.ir.IrController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Device & Model Check — everything about this handset, on this handset.
 *
 * Reachable in release, deliberately: the judging phone is a loaner and there may be no
 * cable, no laptop and no adb. If a number about that device is not on a screen here, it is
 * not knowable at the venue in time to react to it.
 */
@Composable
fun SelfTestScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val irController = remember { IrController(context) }

    var sections by remember { mutableStateOf<List<SelfTest.Section>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var irCandidateStatus by remember { mutableStateOf<String?>(null) }

    fun run() {
        scope.launch {
            running = true; status = null
            try {
                // Loads models and times ten inferences — never on the main thread.
                sections = withContext(Dispatchers.Default) { SelfTest.run(context) }
            } catch (e: Exception) {
                status = "Self-test failed: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                running = false
            }
        }
    }

    LaunchedEffect(Unit) { run() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("< Back") }
        Text("Device & Model Check", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            "Screenshot this. Every value is read from this handset at runtime — nothing here " +
                    "is a committed constant.",
            fontSize = 11.sp
        )

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !running, onClick = { run() }) {
                Text(if (running) "Running…" else "Re-run")
            }
            OutlinedButton(enabled = !running && sections.isNotEmpty(), onClick = {
                copyToClipboard(context, SelfTest.asText(sections))
                status = "Copied to clipboard"
            }) { Text("Copy") }
            OutlinedButton(enabled = !running && sections.isNotEmpty(), onClick = {
                status = shareText(context, SelfTest.asText(sections)) ?: "Shared"
            }) { Text("Share .txt") }
        }

        status?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, fontSize = 11.sp)
        }

        if (running && sections.isEmpty()) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Loading models and timing inference…", fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "IR LAB — candidate O General/Fujitsu_AC frame (2026-09-12 lead, UNVERIFIED)",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
        Text(
            "Not the demo pattern. Ported from IRremoteESP8266's real protocol data, but " +
                    "not confirmed to work on this AC brand/model — see FujitsuAcCandidate.kt " +
                    "and docs/EXECUTION_PLAN.md. Only use this to actually test against a unit.",
            fontSize = 10.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = irController.hasIrBlaster, onClick = {
                irCandidateStatus = when (val r = irController.transmitFujitsuCandidate(turnOn = true)) {
                    is IrController.TransmitResult.Success -> "Sent candidate ON frame — did the AC react?"
                    is IrController.TransmitResult.Failure -> "Failed: ${r.reason}"
                }
            }) { Text("Send candidate ON") }
            OutlinedButton(enabled = irController.hasIrBlaster, onClick = {
                irCandidateStatus = when (val r = irController.transmitFujitsuCandidate(turnOn = false)) {
                    is IrController.TransmitResult.Success -> "Sent candidate OFF frame — did the AC react?"
                    is IrController.TransmitResult.Failure -> "Failed: ${r.reason}"
                }
            }) { Text("Send candidate OFF") }
        }
        if (!irController.hasIrBlaster) {
            Text("No IR blaster detected on this device.", fontSize = 10.sp)
        }
        irCandidateStatus?.let { Text(it, fontSize = 11.sp) }

        Spacer(Modifier.height(10.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            sections.forEach { section ->
                Text(
                    section.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                )
                section.rows.forEach { (k, v) ->
                    if (v.isEmpty()) {
                        Text(k, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text(
                            "$k: $v",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("SmartLease self-test", text))
}

/** @return an error message, or null on success. */
private fun shareText(context: Context, text: String): String? = try {
    val dir = File(context.filesDir, "exports").apply { mkdirs() }
    val f = File(dir, "selftest-${System.currentTimeMillis()}.txt")
    f.writeText(text)
    val uri = FileProvider.getUriForFile(context, context.packageName + ".reports", f)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "SmartLease Edge device check")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share device check").apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    })
    null
} catch (e: Exception) {
    "Could not share: ${e.message ?: e.javaClass.simpleName}"
}
