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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
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
    
    var roomName by remember { mutableStateOf(room.type) }
    var sqFt by remember { mutableStateOf(room.sqFt) }
    var height by remember { mutableStateOf(room.height) }
    var width by remember { mutableStateOf(room.width) }
    var length by remember { mutableStateOf(room.length) }
    var damages by remember { mutableStateOf(room.damages) }

    // Dynamic square footage calculation
    LaunchedEffect(width, length, height) {
        val w = width.toDoubleOrNull() ?: 0.0
        val l = length.toDoubleOrNull() ?: 0.0
        val h = height.toDoubleOrNull()
        if (w > 0 && l > 0) {
            sqFt = if (h != null && h > 0) {
                String.format("%.2f", w * l * h)
            } else {
                String.format("%.2f", w * l)
            }
        } else {
            sqFt = ""
        }
    }

    // Save changes to ViewModel whenever they update
    LaunchedEffect(roomName, sqFt, height, width, length, damages) {
        viewModel.updateRoom(room.copy(type = roomName, sqFt = sqFt, height = height, width = width, length = length, damages = damages))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${room.type} Config", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
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

            CorporateTextField(value = roomName, onValueChange = { roomName = it }, label = "Area Name")
            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    CorporateTextField(value = width, onValueChange = { width = it }, label = "Width (ft)", keyboardType = KeyboardType.Number)
                }
                Box(modifier = Modifier.weight(1f)) {
                    CorporateTextField(value = length, onValueChange = { length = it }, label = "Length (ft)", keyboardType = KeyboardType.Number)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    CorporateTextField(value = height, onValueChange = { height = it }, label = "Height (ft)", keyboardType = KeyboardType.Number)
                }
                Box(modifier = Modifier.weight(1f)) {
                    CorporateTextField(
                        value = sqFt, 
                        onValueChange = {}, 
                        label = "Total Sq Ft", 
                        keyboardType = KeyboardType.Number,
                        readOnly = true
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            CorporateTextField(value = damages, onValueChange = { damages = it }, label = "Pre-existing Damages (Optional)")


            Spacer(modifier = Modifier.height(32.dp))
            
            Text("2D Blueprint Viewer", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(16.dp))
            
            // Blueprint Canvas (Pseudo-3D)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                val w = width.toFloatOrNull() ?: 10f
                val l = length.toFloatOrNull() ?: 15f
                val h = height.toFloatOrNull() ?: 0f
                
                Canvas(modifier = Modifier.fillMaxSize().padding(32.dp)) {
                    val maxW = size.width
                    val maxH = size.height
                    
                    if (h <= 0f) {
                        // 2D Draw
                        val ratio = w / l
                        val canvasRatio = maxW / maxH
                        val (rectW, rectH) = if (ratio > canvasRatio) maxW to (maxW / ratio) else (maxH * ratio) to maxH
                        val offsetX = (maxW - rectW) / 2
                        val offsetY = (maxH - rectH) / 2
                        drawRect(com.smartlease.edge.ui.theme.CorporateYellow.copy(alpha = 0.3f), Offset(offsetX, offsetY), Size(rectW, rectH))
                        drawRect(com.smartlease.edge.ui.theme.CorporateYellow, Offset(offsetX, offsetY), Size(rectW, rectH), style = Stroke(4.dp.toPx()))
                    } else {
                        // Pseudo-3D Isometric Draw
                        val scale = minOf(maxW / (w + l * 0.5f), maxH / (h + l * 0.5f)) * 0.8f
                        
                        val scaledW = w * scale
                        val scaledL = l * scale
                        val scaledH = h * scale
                        
                        // Iso offsets
                        val isoDx = scaledL * 0.5f
                        val isoDy = scaledL * 0.5f
                        
                        val cx = maxW / 2 - (scaledW + isoDx) / 2
                        val cy = maxH / 2 - (scaledH + isoDy) / 2 + scaledH
                        
                        // Front face
                        val p1 = Offset(cx, cy)
                        val p2 = Offset(cx + scaledW, cy)
                        val p3 = Offset(cx + scaledW, cy - scaledH)
                        val p4 = Offset(cx, cy - scaledH)
                        
                        // Back face
                        val p5 = Offset(cx + isoDx, cy - isoDy)
                        val p6 = Offset(cx + scaledW + isoDx, cy - isoDy)
                        val p7 = Offset(cx + scaledW + isoDx, cy - scaledH - isoDy)
                        val p8 = Offset(cx + isoDx, cy - scaledH - isoDy)
                        
                        // Draw filled faces
                        val path = androidx.compose.ui.graphics.Path()
                        
                        // Floor
                        path.moveTo(p1.x, p1.y); path.lineTo(p2.x, p2.y); path.lineTo(p6.x, p6.y); path.lineTo(p5.x, p5.y); path.close()
                        drawPath(path, com.smartlease.edge.ui.theme.CorporateYellow.copy(alpha = 0.4f))
                        
                        // Left Wall
                        path.reset(); path.moveTo(p1.x, p1.y); path.lineTo(p4.x, p4.y); path.lineTo(p8.x, p8.y); path.lineTo(p5.x, p5.y); path.close()
                        drawPath(path, com.smartlease.edge.ui.theme.CorporateYellow.copy(alpha = 0.2f))
                        
                        // Draw Wireframe Lines
                        val color = com.smartlease.edge.ui.theme.CorporateYellow
                        val stroke = Stroke(3.dp.toPx())
                        
                        // Front
                        drawLine(color, p1, p2, strokeWidth = stroke.width)
                        drawLine(color, p2, p3, strokeWidth = stroke.width)
                        drawLine(color, p3, p4, strokeWidth = stroke.width)
                        drawLine(color, p4, p1, strokeWidth = stroke.width)
                        
                        // Back
                        drawLine(color.copy(alpha = 0.3f), p5, p6, strokeWidth = stroke.width)
                        drawLine(color, p6, p7, strokeWidth = stroke.width)
                        drawLine(color, p7, p8, strokeWidth = stroke.width)
                        drawLine(color.copy(alpha = 0.3f), p8, p5, strokeWidth = stroke.width)
                        
                        // Connecting edges
                        drawLine(color.copy(alpha = 0.3f), p1, p5, strokeWidth = stroke.width)
                        drawLine(color, p2, p6, strokeWidth = stroke.width)
                        drawLine(color, p3, p7, strokeWidth = stroke.width)
                        drawLine(color, p4, p8, strokeWidth = stroke.width)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            Text("Record Surfaces", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(16.dp))

            val topFrames = room.frames.filter { it.surfaceType == "Top" }
            val bottomFrames = room.frames.filter { it.surfaceType == "Bottom" }
            val sidesFrames = room.frames.filter { it.surfaceType == "Sides" }

            SurfaceRecordCard(
                title = "Top Wall (Ceiling)",
                isRecorded = room.topRecorded,
                frameCount = topFrames.size,
                defectCount = topFrames.sumOf { it.defects.size },
                onClick = { onRecordSurface(room.id, "Top") }
            )
            Spacer(modifier = Modifier.height(12.dp))
            SurfaceRecordCard(
                title = "Bottom Wall (Floor)",
                isRecorded = room.bottomRecorded,
                frameCount = bottomFrames.size,
                defectCount = bottomFrames.sumOf { it.defects.size },
                onClick = { onRecordSurface(room.id, "Bottom") }
            )
            Spacer(modifier = Modifier.height(12.dp))
            SurfaceRecordCard(
                title = "Side Walls",
                isRecorded = room.sidesRecorded,
                frameCount = sidesFrames.size,
                defectCount = sidesFrames.sumOf { it.defects.size },
                onClick = { onRecordSurface(room.id, "Sides") }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SurfaceRecordCard(
    title: String, 
    isRecorded: Boolean, 
    frameCount: Int = 0,
    defectCount: Int = 0,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isRecorded) com.smartlease.edge.ui.theme.LampGreen.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                if (isRecorded) {
                    val statusText = if (frameCount > 0) {
                        "$frameCount frames converted • ${if (defectCount > 0) "$defectCount defect(s)" else "All clean"}"
                    } else {
                        "Recorded • Ready for AI Analysis"
                    }
                    Text(
                        statusText, 
                        style = MaterialTheme.typography.bodySmall, 
                        color = if (defectCount > 0) com.smartlease.edge.ui.theme.LampAmber else com.smartlease.edge.ui.theme.LampGreen
                    )
                } else {
                    Text("Tap to record video & convert frames", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(if (isRecorded) com.smartlease.edge.ui.theme.LampGreen else MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.CameraAlt,
                    contentDescription = "Record",
                    tint = if (isRecorded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
