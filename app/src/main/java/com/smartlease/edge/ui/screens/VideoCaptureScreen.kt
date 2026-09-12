package com.smartlease.edge.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.smartlease.edge.inspection360.ImageSharpnessEvaluator
import com.smartlease.edge.ui.AppViewModel
import com.smartlease.edge.ui.CapturedFrame
import com.smartlease.edge.vision.DefectSegmenterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

@Composable
fun VideoCaptureScreen(
    viewModel: AppViewModel,
    roomId: String,
    surfaceType: String,
    onExit: () -> Unit,
    onAddAnotherRoom: () -> Unit
) {
    val room = viewModel.rooms.find { it.id == roomId } ?: return
    
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    // Backend ML vision segmenter
    val defectSegmenter = remember { DefectSegmenterFactory.create(context) }

    // Video & Frame States
    var recordingState by remember { mutableStateOf(RecordingState.IDLE) }
    var recordingFinished by remember { mutableStateOf(false) }
    val capturedFrames = remember { mutableStateListOf<CapturedFrame>() }
    var selectedFrameIndex by remember { mutableStateOf(0) }
    
    // Timer State
    var secondsElapsed by remember { mutableStateOf(0) }
    
    LaunchedEffect(recordingState) {
        while (recordingState == RecordingState.RECORDING) {
            delay(1000)
            secondsElapsed++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // CameraX Live Preview with ImageAnalysis Frame Extraction
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                setupCameraWithAnalysis(
                    context = ctx,
                    lifecycleOwner = lifecycleOwner,
                    previewView = previewView,
                    isRecording = { recordingState == RecordingState.RECORDING },
                    onFrameSampled = { bitmap, sharpness ->
                        coroutineScope.launch(Dispatchers.Default) {
                            val defects = defectSegmenter.segmentDefects(
                                bitmap = bitmap,
                                frameWidthInches = 120f,
                                frameHeightInches = 160f
                            )
                            val newFrame = CapturedFrame(
                                surfaceType = surfaceType,
                                frameIndex = capturedFrames.size + 1,
                                bitmap = bitmap,
                                defects = defects,
                                sharpness = sharpness,
                                isTrainedModel = defectSegmenter.isTrainedModel
                            )
                            withContext(Dispatchers.Main) {
                                capturedFrames.add(newFrame)
                            }
                        }
                    }
                )
                previewView
            }
        )

        // Top HUD
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onExit,
                modifier = Modifier.background(com.smartlease.edge.ui.theme.GlassUnified, CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Exit", tint = Color.White)
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .background(com.smartlease.edge.ui.theme.GlassUnified, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "$surfaceType Surface",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                // Backend Model Badge
                Box(
                    modifier = Modifier
                        .background(
                            if (defectSegmenter.isTrainedModel) com.smartlease.edge.ui.theme.LampGreen.copy(alpha = 0.25f)
                            else com.smartlease.edge.ui.theme.LampAmber.copy(alpha = 0.25f),
                            RoundedCornerShape(8.dp)
                        )
                        .border(
                            1.dp,
                            if (defectSegmenter.isTrainedModel) com.smartlease.edge.ui.theme.LampGreen.copy(alpha = 0.6f)
                            else com.smartlease.edge.ui.theme.LampAmber.copy(alpha = 0.6f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (defectSegmenter.isTrainedModel) "● YOLOv8-Seg Backend" else "● Heuristic Vision Backend",
                        color = if (defectSegmenter.isTrainedModel) com.smartlease.edge.ui.theme.LampGreen else com.smartlease.edge.ui.theme.LampAmber,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            
            // Timer & Frame Counter Display
            val formattedTime = String.format("%02d:%02d", secondsElapsed / 60, secondsElapsed % 60)
            Column(horizontalAlignment = Alignment.End) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(com.smartlease.edge.ui.theme.GlassUnified, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    // Pulsing red dot when recording
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulseAlpha"
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (recordingState == RecordingState.RECORDING) com.smartlease.edge.ui.theme.LampRed.copy(alpha = alpha) 
                                else Color.Gray, 
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = formattedTime, color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
                
                if (capturedFrames.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${capturedFrames.size} frames",
                        color = com.smartlease.edge.ui.theme.IqooYellow,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Viewfinder Guide Overlay
        if (!recordingFinished) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 40.dp, vertical = 120.dp)
                    .border(
                        1.dp,
                        if (recordingState == RecordingState.RECORDING) com.smartlease.edge.ui.theme.IqooYellow.copy(alpha = 0.5f)
                        else Color.White.copy(alpha = 0.25f),
                        RoundedCornerShape(16.dp)
                    )
            )
        }

        // Camera UI Controls & Live Filmstrip (Bottom)
        if (!recordingFinished) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Live Converted Frames Filmstrip
                if (capturedFrames.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .background(com.smartlease.edge.ui.theme.SurfaceUnified.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                            .border(1.dp, com.smartlease.edge.ui.theme.BorderUnified, RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LIVE FRAMES",
                            style = MaterialTheme.typography.labelSmall,
                            color = com.smartlease.edge.ui.theme.IqooYellow,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            itemsIndexed(capturedFrames) { index, frame ->
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            1.dp,
                                            if (frame.defects.isNotEmpty()) com.smartlease.edge.ui.theme.LampAmber
                                            else com.smartlease.edge.ui.theme.LampGreen,
                                            RoundedCornerShape(8.dp)
                                        )
                                ) {
                                    Image(
                                        bitmap = frame.bitmap.asImageBitmap(),
                                        contentDescription = "Frame ${index + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(3.dp)
                                            .size(8.dp)
                                            .background(
                                                if (frame.defects.isNotEmpty()) com.smartlease.edge.ui.theme.LampAmber
                                                else com.smartlease.edge.ui.theme.LampGreen,
                                                CircleShape
                                            )
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Controls Bar
                Row(
                    modifier = Modifier
                        .background(com.smartlease.edge.ui.theme.SurfaceUnified.copy(alpha = 0.85f), RoundedCornerShape(32.dp))
                        .border(1.dp, com.smartlease.edge.ui.theme.BorderUnified, RoundedCornerShape(32.dp))
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pause/Resume Button
                    if (recordingState != RecordingState.IDLE) {
                        IconButton(
                            onClick = { 
                                recordingState = if (recordingState == RecordingState.RECORDING) RecordingState.PAUSED else RecordingState.RECORDING 
                            },
                            modifier = Modifier.size(48.dp).background(com.smartlease.edge.ui.theme.GlassUnified, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (recordingState == RecordingState.RECORDING) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Pause/Resume",
                                tint = Color.White
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }

                    // Main Record/Stop Button
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .border(4.dp, Color.White, CircleShape)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(if (recordingState == RecordingState.IDLE) com.smartlease.edge.ui.theme.LampRed else com.smartlease.edge.ui.theme.GlassUnified)
                            .clickable {
                                if (recordingState == RecordingState.IDLE) {
                                    recordingState = RecordingState.RECORDING
                                } else {
                                    // Stop recording and finalize frames
                                    recordingState = RecordingState.IDLE
                                    recordingFinished = true
                                    
                                    // Update room status
                                    val updatedRoom = when (surfaceType) {
                                        "Top" -> room.copy(topRecorded = true)
                                        "Bottom" -> room.copy(bottomRecorded = true)
                                        "Sides" -> room.copy(sidesRecorded = true)
                                        else -> room
                                    }
                                    viewModel.updateRoom(updatedRoom)
                                    viewModel.addFramesToRoom(roomId, capturedFrames.toList())
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (recordingState != RecordingState.IDLE) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = com.smartlease.edge.ui.theme.LampRed, modifier = Modifier.size(36.dp))
                        }
                    }
                    
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }
        } else {
            // Finished Recording Review & Converted Frames Gallery Overlay
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .padding(16.dp)
                    .background(com.smartlease.edge.ui.theme.SurfaceUnified.copy(alpha = 0.96f), RoundedCornerShape(24.dp))
                    .border(1.dp, com.smartlease.edge.ui.theme.BorderUnified, RoundedCornerShape(24.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "$surfaceType Inspection Complete",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${capturedFrames.size} frames converted & analyzed",
                            style = MaterialTheme.typography.bodySmall,
                            color = com.smartlease.edge.ui.theme.TextSecondaryUnified
                        )
                    }
                    val totalDefects = capturedFrames.sumOf { it.defects.size }
                    Box(
                        modifier = Modifier
                            .background(
                                if (totalDefects > 0) com.smartlease.edge.ui.theme.LampAmber.copy(alpha = 0.2f)
                                else com.smartlease.edge.ui.theme.LampGreen.copy(alpha = 0.2f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (totalDefects > 0) "$totalDefects Defect(s) Found" else "✓ Clean Surface",
                            color = if (totalDefects > 0) com.smartlease.edge.ui.theme.LampAmber else com.smartlease.edge.ui.theme.LampGreen,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (capturedFrames.isNotEmpty()) {
                    val safeIndex = selectedFrameIndex.coerceIn(0, capturedFrames.size - 1)
                    val activeFrame = capturedFrames[safeIndex]

                    // Active Frame Full Preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, com.smartlease.edge.ui.theme.BorderUnified, RoundedCornerShape(16.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = activeFrame.bitmap.asImageBitmap(),
                            contentDescription = "Selected Frame",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Frame tag
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Frame #${safeIndex + 1} of ${capturedFrames.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Filmstrip Frame Selector
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(capturedFrames) { idx, frame ->
                            val isSelected = idx == safeIndex
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) com.smartlease.edge.ui.theme.IqooYellow else com.smartlease.edge.ui.theme.BorderUnified,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedFrameIndex = idx }
                            ) {
                                Image(
                                    bitmap = frame.bitmap.asImageBitmap(),
                                    contentDescription = "Thumb $idx",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Defect & Quality Details for Selected Frame
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(com.smartlease.edge.ui.theme.CarbonBlack, RoundedCornerShape(12.dp))
                            .border(1.dp, com.smartlease.edge.ui.theme.BorderUnified, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (activeFrame.defects.isEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Clean", tint = com.smartlease.edge.ui.theme.LampGreen, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "No defects detected on this frame",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = com.smartlease.edge.ui.theme.LampGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                "Surface is uniform, solid, and meets quality baseline.",
                                style = MaterialTheme.typography.bodySmall,
                                color = com.smartlease.edge.ui.theme.TextSecondaryUnified,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        } else {
                            activeFrame.defects.forEachIndexed { dIdx, defect ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = "Defect", tint = com.smartlease.edge.ui.theme.LampAmber, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = defect.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Class: ${defect.defectClass} • Est: ${String.format("%.2f", defect.areaSqFtEstimate)} sq ft • Conf: ${String.format("%.0f%%", defect.confidence * 100)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = com.smartlease.edge.ui.theme.TextSecondaryUnified
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Button(
                    onClick = onExit,
                    colors = ButtonDefaults.buttonColors(containerColor = com.smartlease.edge.ui.theme.IqooYellow, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Return to Room Config", fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedButton(
                    onClick = onAddAnotherRoom,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, com.smartlease.edge.ui.theme.BorderUnified),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Add Another Room", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

enum class RecordingState {
    IDLE, RECORDING, PAUSED
}

private fun setupCameraWithAnalysis(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    isRecording: () -> Boolean,
    onFrameSampled: (Bitmap, Double) -> Unit
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    val analysisExecutor = Executors.newSingleThreadExecutor()
    var lastSampleTime = 0L

    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
            try {
                val now = System.currentTimeMillis()
                if (isRecording() && (now - lastSampleTime >= 1200L)) {
                    lastSampleTime = now
                    
                    // Compute Laplacian variance for sharpness
                    val yBuffer = imageProxy.planes[0].buffer
                    val sharpness = ImageSharpnessEvaluator.computeLaplacianVariance(
                        yBuffer.duplicate(), imageProxy.width, imageProxy.height
                    )

                    // Convert frame to bitmap
                    val bitmap = imageProxyToBitmap(imageProxy)
                    if (bitmap != null) {
                        onFrameSampled(bitmap, sharpness)
                    }
                }
            } catch (e: Exception) {
                // Ignore transient frame errors
            } finally {
                imageProxy.close()
            }
        }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
        } catch (exc: Exception) {
            // Ignore setup errors
        }
    }, ContextCompat.getMainExecutor(context))
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    return try {
        // First try built-in CameraX converter
        image.toBitmap()
    } catch (e: Exception) {
        try {
            // YUV fallback
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 90, out)
            val bytes = out.toByteArray()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (ex: Exception) {
            null
        }
    }
}
