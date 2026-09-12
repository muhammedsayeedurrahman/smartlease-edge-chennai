package com.smartlease.edge.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.smartlease.edge.data.AppDatabase
import com.smartlease.edge.data.CountersignatureEntity
import com.smartlease.edge.camera.CameraController
import com.smartlease.edge.report.BarcodeReader
import com.smartlease.edge.report.CountersignPayload
import com.smartlease.edge.report.InspectionReport
import com.smartlease.edge.report.QrCode
import com.smartlease.edge.report.ReportGenerator
import com.smartlease.edge.report.ReportSigner
import kotlinx.coroutines.launch

/**
 * The two-phone joint-inspection countersign handshake. One screen plays both roles because
 * the wire payload ([CountersignPayload]) is self-describing:
 *
 *  - Opened with [report] non-null (from the report screen): shows this session's own QR
 *    (a signing request over its findings digest) by default, and can also scan a request
 *    from someone else, or a response answering its own request.
 *  - Opened with [report] null (from the home screen, on the *other* person's phone, which
 *    has no session of its own here): has nothing of its own to request, so it starts in
 *    scan mode -- point it at the first phone's QR to sign that digest and hand back a
 *    response QR.
 *
 * A response is only ever accepted when [report] is non-null and the scanned payload's
 * session and digest match this report's own -- a signature for a different report, or a
 * stale one from before this report's findings last changed, is rejected rather than stored.
 */
@Composable
fun CountersignScreen(report: InspectionReport?, onBack: () -> Unit) {
    val insets = WindowInsets.systemBars.asPaddingValues()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.get(context) }
    val cameraController = remember { CameraController(context, lifecycleOwner) }

    val hasCameraPermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    // Nothing to show when there's no report of our own -- start pointed at the camera.
    var scanning by remember(report) { mutableStateOf(report == null) }
    var busy by remember { mutableStateOf(false) }
    var outgoingPayload by remember(report) {
        mutableStateOf(report?.let { CountersignPayload.Request(it.sessionId, it.findingsSha256).toJson() })
    }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var recordedCount by remember { mutableStateOf(0) }

    fun setStatus(message: String, error: Boolean) {
        statusMessage = message
        isError = error
    }

    fun handleScan() {
        scope.launch {
            busy = true
            try {
                val bitmap = cameraController.captureBitmap()
                val raw = BarcodeReader.readFirst(bitmap)
                if (raw == null) {
                    setStatus("No QR code found -- hold steady, fill the frame, try again.", true)
                    return@launch
                }
                when (val parsed = CountersignPayload.parse(raw)) {
                    null -> setStatus("That QR isn't a SmartLease Edge countersign code.", true)

                    is CountersignPayload.Parsed.Req -> {
                        val attestation = runCatching { ReportSigner.sign(parsed.digestHex) }.getOrNull()
                        val certificateBase64 = attestation?.certificateChainBase64?.firstOrNull()
                        if (attestation == null || certificateBase64 == null) {
                            setStatus("Signing failed on this device -- no attestation key available.", true)
                        } else {
                            outgoingPayload = CountersignPayload.Response(
                                sessionId = parsed.sessionId,
                                digestHex = parsed.digestHex,
                                signatureBase64 = attestation.signatureBase64,
                                certificateBase64 = certificateBase64,
                                hardwareBacked = attestation.hardwareBacked
                            ).toJson()
                            scanning = false
                            setStatus("Signed. Show this QR to the first phone to complete it.", false)
                        }
                    }

                    is CountersignPayload.Parsed.Resp -> {
                        if (report == null) {
                            setStatus("This phone has no report of its own to countersign.", true)
                        } else if (parsed.sessionId != report.sessionId || parsed.digestHex != report.findingsSha256) {
                            setStatus("That signature is for a different report -- not recorded.", true)
                        } else if (!ReportSigner.verify(parsed.digestHex, parsed.signatureBase64, parsed.certificateBase64)) {
                            setStatus("Signature did not verify -- not recorded.", true)
                        } else {
                            db.inspectionDao().insertCountersignature(
                                CountersignatureEntity(
                                    sessionId = report.sessionId,
                                    digestHex = parsed.digestHex,
                                    signatureBase64 = parsed.signatureBase64,
                                    certificateBase64 = parsed.certificateBase64,
                                    hardwareBacked = parsed.hardwareBacked,
                                    capturedAtEpochMillis = System.currentTimeMillis()
                                )
                            )
                            val all = db.inspectionDao().countersignaturesForSession(report.sessionId)
                            recordedCount = all.size
                            ReportGenerator.renderToPdf(context, report, all)
                            scanning = false
                            setStatus("Countersignature recorded and added to the report PDF.", false)
                        }
                    }
                }
            } catch (e: Exception) {
                setStatus("Scan failed: ${e.message ?: e.javaClass.simpleName}", true)
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = insets.calculateTopPadding(), bottom = insets.calculateBottomPadding())
    ) {
        TextButton(onClick = onBack, modifier = Modifier.padding(start = 6.dp)) {
            Text("Back", style = MaterialTheme.typography.labelMedium)
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Countersign", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Text(
                "Two devices, two hardware-backed signatures over the same findings digest -- " +
                    "each proves a specific phone signed it, not who was holding it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(20.dp))

        Column(Modifier.padding(horizontal = 20.dp).weight(1f)) {
            if (!scanning) {
                val payload = outgoingPayload
                if (payload != null) {
                    val qr = remember(payload) {
                        runCatching { QrCode.bitmap(payload, 480).asImageBitmap() }.getOrNull()
                    }
                    Text(
                        "Show this to the other phone",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(12.dp))
                    if (qr != null) {
                        Image(qr, contentDescription = "Countersign QR code", modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                    }
                } else {
                    Text(
                        "Nothing to show yet -- scan the first phone's QR to begin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { scanning = true; statusMessage = null },
                    enabled = hasCameraPermission,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Scan a QR")
                }
                if (!hasCameraPermission) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Camera permission is needed to scan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                AndroidView(
                    factory = { PreviewView(it) },
                    modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                    update = { previewView -> scope.launch { cameraController.bindTo(previewView) } }
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { handleScan() },
                    enabled = !busy && hasCameraPermission,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Capture QR")
                    }
                }
                if (report != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { scanning = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel -- show my QR instead")
                    }
                }
            }

            statusMessage?.let {
                Spacer(Modifier.height(14.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }

            if (recordedCount > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "$recordedCount countersignature(s) on this report so far.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
