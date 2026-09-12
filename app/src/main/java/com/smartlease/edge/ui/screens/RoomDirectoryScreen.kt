package com.smartlease.edge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartlease.edge.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDirectoryScreen(
    onStartDiagnostic: () -> Unit
) {
    val presets = listOf("Living Room", "Master Bedroom", "Kitchen", "Bathroom")
    
    data class RoomItem(
        val title: String, 
        val targets: String, 
        val status: String, 
        val deduction: String? = null
    )
    
    val rooms = remember { mutableStateListOf(
        RoomItem("Living Room", "North Wall, Floor Tiling, Split AC", "Pending"),
        RoomItem("Master Bedroom", "East Wall, Window Frame", "Complete", "₹1,500 damage")
    ) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Property Setup", fontWeight = FontWeight.Bold) },
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
        ) {
            // Property Header Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .border(1.dp, SlateEdge, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = SlatePanel),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Flat 3B, Anna Nagar", style = MaterialTheme.typography.titleMedium, color = ReadoutPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Deposit: ₹90,000", style = MaterialTheme.typography.bodyMedium, color = ReadoutPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { 0.5f },
                            modifier = Modifier.weight(1f).height(6.dp),
                            color = LampGreen,
                            trackColor = SlateEdge
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("1 of 2 Rooms Checked", style = MaterialTheme.typography.labelMedium, color = ReadoutDim)
                    }
                }
            }

            // Horizontal Preset Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(presets) { preset ->
                    AssistChip(
                        onClick = { 
                            rooms.add(RoomItem(preset, "Standard Checks", "Pending")) 
                        },
                        label = { Text("+ $preset", color = ReadoutPrimary) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = SlatePanel
                        ),
                        border = AssistChipDefaults.assistChipBorder(
                            borderColor = SlateEdge,
                            enabled = true
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Room Card List
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(rooms) { room ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SlateEdge, RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = SlatePanel),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(room.title, style = MaterialTheme.typography.titleMedium, color = ReadoutPrimary)
                                
                                val statusColor = if (room.status == "Complete") LampGreen else LampAmber
                                val statusText = if (room.deduction != null) "${room.status} (${room.deduction})" else room.status
                                
                                Surface(
                                    color = statusColor.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = borderStrokeForStatus(statusColor)
                                ) {
                                    Text(
                                        text = statusText,
                                        color = statusColor,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Targets: ${room.targets}", style = MaterialTheme.typography.bodySmall, color = ReadoutDim)
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Button(
                                onClick = onStartDiagnostic,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SlateGround,
                                    contentColor = LampAmber
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, LampAmber),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Start Room Diagnostic →", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun borderStrokeForStatus(color: Color): androidx.compose.foundation.BorderStroke {
    return androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
}
