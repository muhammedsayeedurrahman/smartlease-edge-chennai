package com.smartlease.edge.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.smartlease.edge.ui.AppViewModel
import com.smartlease.edge.ui.Room

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomConfigScreen(
    viewModel: AppViewModel,
    roomId: String,
    onBack: () -> Unit,
    onRecordSurface: (roomId: String, surfaceType: String) -> Unit
) {
    val room = viewModel.rooms.find { it.id == roomId } ?: return
    
    var sqFt by remember { mutableStateOf(room.sqFt) }
    var height by remember { mutableStateOf(room.height) }
    var width by remember { mutableStateOf(room.width) }
    var length by remember { mutableStateOf(room.length) }
    var damages by remember { mutableStateOf(room.damages) }

    // Save changes to ViewModel whenever they update
    LaunchedEffect(sqFt, height, width, length, damages) {
        viewModel.updateRoom(room.copy(sqFt = sqFt, height = height, width = width, length = length, damages = damages))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${room.type} Config", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Text("Dimensions", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    GlassTextField(value = width, onValueChange = { width = it }, label = "Width (ft)", keyboardType = KeyboardType.Number)
                }
                Box(modifier = Modifier.weight(1f)) {
                    GlassTextField(value = length, onValueChange = { length = it }, label = "Length (ft)", keyboardType = KeyboardType.Number)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    GlassTextField(value = height, onValueChange = { height = it }, label = "Height (ft)", keyboardType = KeyboardType.Number)
                }
                Box(modifier = Modifier.weight(1f)) {
                    GlassTextField(value = sqFt, onValueChange = { sqFt = it }, label = "Total Sq Ft", keyboardType = KeyboardType.Number)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            GlassTextField(value = damages, onValueChange = { damages = it }, label = "Pre-existing Damages (Optional)")

            Spacer(modifier = Modifier.height(32.dp))
            
            Text("2D Blueprint Viewer", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(16.dp))
            
            // Blueprint Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                val w = width.toFloatOrNull() ?: 10f
                val l = length.toFloatOrNull() ?: 15f
                
                Canvas(modifier = Modifier.fillMaxSize().padding(32.dp)) {
                    // Calculate proportional size
                    val maxCanvasWidth = size.width
                    val maxCanvasHeight = size.height
                    
                    val ratio = w / l
                    val canvasRatio = maxCanvasWidth / maxCanvasHeight
                    
                    val (rectW, rectH) = if (ratio > canvasRatio) {
                        maxCanvasWidth to (maxCanvasWidth / ratio)
                    } else {
                        (maxCanvasHeight * ratio) to maxCanvasHeight
                    }

                    val offsetX = (maxCanvasWidth - rectW) / 2
                    val offsetY = (maxCanvasHeight - rectH) / 2

                    drawRect(
                        color = com.smartlease.edge.ui.theme.IqooYellow.copy(alpha = 0.3f),
                        topLeft = Offset(offsetX, offsetY),
                        size = Size(rectW, rectH)
                    )
                    
                    drawRect(
                        color = com.smartlease.edge.ui.theme.IqooYellow,
                        topLeft = Offset(offsetX, offsetY),
                        size = Size(rectW, rectH),
                        style = Stroke(width = 4.dp.toPx())
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            Text("Record Surfaces", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(16.dp))

            SurfaceRecordCard(
                title = "Top Wall (Ceiling)",
                isRecorded = room.topRecorded,
                onClick = { onRecordSurface(room.id, "Top") }
            )
            Spacer(modifier = Modifier.height(12.dp))
            SurfaceRecordCard(
                title = "Bottom Wall (Floor)",
                isRecorded = room.bottomRecorded,
                onClick = { onRecordSurface(room.id, "Bottom") }
            )
            Spacer(modifier = Modifier.height(12.dp))
            SurfaceRecordCard(
                title = "Side Walls",
                isRecorded = room.sidesRecorded,
                onClick = { onRecordSurface(room.id, "Sides") }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SurfaceRecordCard(title: String, isRecorded: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isRecorded) com.smartlease.edge.ui.theme.LampGreen.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .border(
                1.dp, 
                if (isRecorded) com.smartlease.edge.ui.theme.LampGreen.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline, 
                RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                if (isRecorded) {
                    Text("Recorded • Ready for AI Analysis", style = MaterialTheme.typography.bodySmall, color = com.smartlease.edge.ui.theme.LampGreen)
                } else {
                    Text("Tap to record video", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(if (isRecorded) com.smartlease.edge.ui.theme.LampGreen else MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Record",
                    tint = if (isRecorded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
