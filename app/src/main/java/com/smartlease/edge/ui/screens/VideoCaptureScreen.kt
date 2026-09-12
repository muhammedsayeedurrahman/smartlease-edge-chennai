package com.smartlease.edge.ui.screens

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.smartlease.edge.ui.AppViewModel
import kotlinx.coroutines.delay
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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

    // Video State
    var recordingState by remember { mutableStateOf(RecordingState.IDLE) }
    var recordingFinished by remember { mutableStateOf(false) }
    
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
        // CameraX Preview
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                setupCamera(ctx, lifecycleOwner, previewView)
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
            
            Box(
                modifier = Modifier
                    .background(com.smartlease.edge.ui.theme.GlassUnified, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Capturing $surfaceType",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            
            // Timer Display
            val formattedTime = String.format("%02d:%02d", secondsElapsed / 60, secondsElapsed % 60)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(com.smartlease.edge.ui.theme.GlassUnified, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
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
                        .background(if (recordingState == RecordingState.RECORDING) com.smartlease.edge.ui.theme.LampRed.copy(alpha = alpha) else Color.Gray, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = formattedTime, color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }

        // Camera UI Controls (Center/Bottom)
        if (!recordingFinished) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
                    .background(com.smartlease.edge.ui.theme.SurfaceUnified.copy(alpha = 0.7f), RoundedCornerShape(32.dp))
                    .border(1.dp, com.smartlease.edge.ui.theme.BorderUnified, RoundedCornerShape(32.dp))
                    .padding(horizontal = 24.dp, vertical = 16.dp),
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
                    Spacer(modifier = Modifier.size(48.dp)) // Maintain spacing
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
                                // Stop and process
                                recordingState = RecordingState.IDLE
                                recordingFinished = true
                                
                                val updatedRoom = when (surfaceType) {
                                    "Top" -> room.copy(topRecorded = true)
                                    "Bottom" -> room.copy(bottomRecorded = true)
                                    "Sides" -> room.copy(sidesRecorded = true)
                                    else -> room
                                }
                                viewModel.updateRoom(updatedRoom)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (recordingState != RecordingState.IDLE) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop", tint = com.smartlease.edge.ui.theme.LampRed, modifier = Modifier.size(36.dp))
                    }
                }
                
                Spacer(modifier = Modifier.size(48.dp)) // Maintain spacing for symmetry
            }
        } else {
            // Finished Recording Overlay
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(32.dp)
                    .background(com.smartlease.edge.ui.theme.SurfaceUnified.copy(alpha = 0.95f), RoundedCornerShape(24.dp))
                    .border(1.dp, com.smartlease.edge.ui.theme.BorderUnified, RoundedCornerShape(24.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(com.smartlease.edge.ui.theme.LampGreen.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", color = com.smartlease.edge.ui.theme.LampGreen, style = MaterialTheme.typography.displaySmall)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Frames Captured", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "The backend ML model is processing the recorded frames.",
                    style = MaterialTheme.typography.bodySmall,
                    color = com.smartlease.edge.ui.theme.TextSecondaryUnified,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = onExit,
                    colors = ButtonDefaults.buttonColors(containerColor = com.smartlease.edge.ui.theme.IqooYellow, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Return to Room Config", fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedButton(
                    onClick = onAddAnotherRoom,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, com.smartlease.edge.ui.theme.BorderUnified),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
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

private fun setupCamera(context: Context, lifecycleOwner: androidx.lifecycle.LifecycleOwner, previewView: PreviewView) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
        } catch (exc: Exception) {
            // Handle exceptions silently for the mock setup
        }
    }, ContextCompat.getMainExecutor(context))
}
