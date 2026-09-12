package com.smartlease.edge.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartlease.edge.ui.theme.*
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcousticIrScreen(
    onGenerateSummary: () -> Unit
) {
    var isHollow by remember { mutableStateOf(true) }
    var irVerified by remember { mutableStateOf(false) }
    var selectedAppliance by remember { mutableStateOf("Living Room Split AC") }
    var applianceDropdownExpanded by remember { mutableStateOf(false) }
    
    val appliances = listOf("Living Room Split AC", "Geyser", "TV")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SlateGround,
                    titleContentColor = ReadoutPrimary
                )
            )
        },
        containerColor = SlateGround
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Acoustic Tap Card (Top Half)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, SlateEdge, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = SlatePanel),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Knuckle-tap tile near microphone.", color = ReadoutDim, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Circular Gauge
                    val gaugeColor = if (isHollow) LampAmber else LampGreen
                    val gaugeText = if (isHollow) "HOLLOW\n(Conf: 0.91)" else "SOLID\n(Conf: 0.98)"
                    val locationText = if (isHollow) "Kitchen Floor Tile #7" else "Bathroom Wall Tile"
                    
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .border(4.dp, gaugeColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = gaugeText,
                                style = ReadoutValue,
                                color = gaugeColor,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(locationText, style = MaterialTheme.typography.labelMedium, color = ReadoutPrimary)
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Waveform Transient Visualizer Mockup
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                    ) {
                        val path = Path()
                        val width = size.width
                        val height = size.height
                        path.moveTo(0f, height / 2)
                        
                        val points = 50
                        for (i in 0..points) {
                            val x = i * (width / points)
                            // Simulate a damping transient waveform
                            val damping = kotlin.math.exp(-i * 0.1f)
                            val amplitude = if (i < 5) 0f else damping * height / 2
                            val y = height / 2 + sin(i * 1.5f) * amplitude
                            path.lineTo(x, y)
                        }
                        
                        drawPath(
                            path = path,
                            color = gaugeColor.copy(alpha = 0.8f),
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    OutlinedButton(
                        onClick = { isHollow = !isHollow }, // Toggle for demo purposes
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ReadoutPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateEdge)
                    ) {
                        Text("Tap Another Surface")
                    }
                }
            }

            // IR Appliance Card (Bottom Half)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateEdge, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = SlatePanel),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("IR Appliance Verification", style = MaterialTheme.typography.titleMedium, color = ReadoutPrimary)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Target Selector Dropdown
                    ExposedDropdownMenuBox(
                        expanded = applianceDropdownExpanded,
                        onExpandedChange = { applianceDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedAppliance,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = applianceDropdownExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = SlateGround,
                                unfocusedContainerColor = SlateGround,
                                focusedTextColor = ReadoutPrimary,
                                unfocusedTextColor = ReadoutPrimary,
                                focusedIndicatorColor = LampAmber,
                                unfocusedIndicatorColor = SlateEdge
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = applianceDropdownExpanded,
                            onDismissRequest = { applianceDropdownExpanded = false },
                            modifier = Modifier.background(SlatePanel)
                        ) {
                            appliances.forEach { appliance ->
                                DropdownMenuItem(
                                    text = { Text(appliance, color = ReadoutPrimary) },
                                    onClick = {
                                        selectedAppliance = appliance
                                        applianceDropdownExpanded = false
                                        irVerified = false
                                    }
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = { irVerified = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SlateEdge, contentColor = ReadoutPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Fire Test Signal (38 kHz)")
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Verification Indicator
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(if (irVerified) LampGreen else SlateEdge, RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (irVerified) "Functional ✓ (IR Compressor Verified)" else "Waiting for confirmation...",
                            color = if (irVerified) LampGreen else ReadoutDim,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            // Bottom CTA
            Button(
                onClick = onGenerateSummary,
                colors = ButtonDefaults.buttonColors(containerColor = LampAmber, contentColor = SlateGround),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Generate Final Summary →", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
