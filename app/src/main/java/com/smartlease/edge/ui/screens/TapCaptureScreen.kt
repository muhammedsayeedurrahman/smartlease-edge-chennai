package com.smartlease.edge.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.smartlease.edge.acoustic.AcousticTapClassifier
import com.smartlease.edge.acoustic.TapCaptureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Debug-only training-data capture. Not reachable in a release build.
 *
 * Records one continuous window per SPOT through
 * [AcousticTapClassifier.recordPcm] — the same AudioRecord configuration inference uses —
 * and writes uncompressed WAV. See [TapCaptureStore] for why a spot, and not a tap, is the
 * unit of capture.
 */
private const val SPOT_SECONDS = 8

@Composable
fun TapCaptureScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var label by remember { mutableStateOf(TapCaptureStore.LABELS.first()) }
    var surface by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var lastPeakDb by remember { mutableStateOf<Float?>(null) }
    var lastFile by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var counts by remember { mutableStateOf(TapCaptureStore.LABELS.associateWith { 0 }) }

    fun refresh() {
        counts = TapCaptureStore.LABELS.associateWith { TapCaptureStore.count(context, it) }
    }
    LaunchedEffect(Unit) { refresh() }

    val hasMic = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        TextButton(onClick = onBack) { Text("< Back") }
        Text("Tap capture (debug)", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "Records ${SPOT_SECONDS}s per spot at ${AcousticTapClassifier.CAPTURE_SAMPLE_RATE} Hz, " +
                    "uncompressed, through the same AudioRecord path inference uses. " +
                    "Strike 4-6 times during the window. One file per spot.",
            fontSize = 12.sp
        )

        Spacer(Modifier.height(14.dp))
        Text("Label", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TapCaptureStore.LABELS.forEach { l ->
                FilterChip(
                    selected = label == l,
                    onClick = { label = l },
                    label = { Text(l) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = surface,
            onValueChange = { surface = it },
            label = { Text("Surface (e.g. tile_floor, hollow_door)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(14.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Spots recorded", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                TapCaptureStore.LABELS.forEach { l ->
                    Text("$l: ${counts[l] ?: 0}", fontSize = 13.sp)
                }
                Text(
                    "total spots: ${counts.values.sum()}  (each spot = one LOGO group)",
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Button(
            enabled = hasMic && !busy,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    busy = true; recording = true; status = null
                    try {
                        val pcm = withContext(Dispatchers.Default) {
                            AcousticTapClassifier.recordPcm(SPOT_SECONDS * 1000)
                        }
                        val peak = AcousticTapClassifier.peakDbfs(pcm)
                        lastPeakDb = peak
                        val f = withContext(Dispatchers.Default) {
                            TapCaptureStore.writeSpot(
                                context, label, surface, pcm,
                                AcousticTapClassifier.CAPTURE_SAMPLE_RATE
                            )
                        }
                        lastFile = f.name
                        refresh()
                        status = when {
                            peak > -1.5f -> "SAVED but CLIPPING (${"%.1f".format(peak)} dBFS) — move back and redo this spot"
                            peak < -30f -> "SAVED but WEAK (${"%.1f".format(peak)} dBFS) — move closer and redo this spot"
                            else -> "Saved. Peak ${"%.1f".format(peak)} dBFS — good"
                        }
                    } catch (e: Exception) {
                        status = "Capture failed: ${e.message ?: e.javaClass.simpleName}"
                    } finally {
                        recording = false; busy = false
                    }
                }
            }
        ) { Text(if (recording) "Recording… strike now" else if (hasMic) "Record spot (${SPOT_SECONDS}s)" else "Mic permission needed") }

        status?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 12.sp)
        }
        lastFile?.let {
            Text("last: $it", fontSize = 10.sp)
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !busy, onClick = {
                val d = TapCaptureStore.deleteLast(context, label)
                refresh()
                status = if (d != null) "Deleted ${d.name}" else "Nothing to delete for '$label'"
            }) { Text("Discard last") }

            Button(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    try {
                        val zip = withContext(Dispatchers.Default) { TapCaptureStore.exportZip(context) }
                        status = shareFile(context, zip, "application/zip", "SmartLease tap captures")
                            ?: "Shared ${zip.name}"
                    } catch (e: Exception) {
                        status = "Export failed: ${e.message ?: e.javaClass.simpleName}"
                    } finally { busy = false }
                }
            }) { Text("Export all (zip)") }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Target: 24+ distinct spots, balanced across labels, spread over as many " +
                    "different surfaces as possible. Fold count is set by spots, not taps — " +
                    "see RECORDING_PROTOCOL.md.",
            fontSize = 11.sp
        )
    }
}

/** @return an error message, or null on success. */
private fun shareFile(context: Context, file: java.io.File, mime: String, title: String): String? =
    try {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".reports", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, title).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
        null
    } catch (e: Exception) {
        "Could not share: ${e.message ?: e.javaClass.simpleName}"
    }
