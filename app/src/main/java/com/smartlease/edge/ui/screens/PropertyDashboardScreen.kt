package com.smartlease.edge.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartlease.edge.data.SessionType
import com.smartlease.edge.ui.AppViewModel
import com.smartlease.edge.ui.Room
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertyDashboardScreen(
    viewModel: AppViewModel,
    propertyId: String,
    onBack: () -> Unit,
    onRoomSelected: (String) -> Unit,
    onGenerateReport: (SessionType) -> Unit = {}
) {
    val property = viewModel.properties.find { it.id == propertyId }
    val rooms = viewModel.getRoomsForProperty(propertyId)
    val capturedDefectCount = rooms.sumOf { room -> room.frames.sumOf { it.defects.size } }
    var askingSessionType by remember { mutableStateOf(false) }

    val roomCategories = listOf(
        "Hall" to Icons.Rounded.Weekend,
        "Room" to Icons.Rounded.Bed,
        "Kitchen" to Icons.Rounded.Countertops,
        "Bathroom" to Icons.Rounded.Bathtub,
        "Balcony" to Icons.Rounded.Deck,
        "Garden" to Icons.Rounded.Yard,
        "Outer" to Icons.Rounded.Fence
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(property?.name ?: "Property", fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Add New Area", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(16.dp))
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().height(180.dp)
            ) {
                items(roomCategories) { (title, icon) ->
                    RoomCategoryIcon(title = title, icon = icon) {
                        val newRoom = Room(
                            id = UUID.randomUUID().toString(),
                            propertyId = propertyId,
                            type = title
                        )
                        viewModel.addRoom(newRoom)
                        onRoomSelected(newRoom.id)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            Text("Existing Areas", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(16.dp))

            if (rooms.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No areas added yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(rooms) { room ->
                        RoomListItem(room = room, onClick = { onRoomSelected(room.id) })
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                // Without this the capture flow had no exit: areas could be recorded
                // indefinitely and nothing ever produced the report, PDF, digest, or
                // countersignature the whole product exists to produce.
                Button(
                    onClick = { askingSessionType = true },
                    enabled = capturedDefectCount > 0,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Generate report")
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    if (capturedDefectCount > 0) {
                        "$capturedDefectCount finding(s) across ${rooms.size} area(s) will be " +
                            "written up, priced against the deposit, and digest-stamped."
                    } else {
                        // Stating the actual precondition beats a disabled button with no
                        // explanation -- and it must not imply the areas were inspected and
                        // found clean, only that nothing has been detected yet.
                        "Record an area first. A report is only produced once the camera has " +
                            "flagged at least one defect -- nothing here is written up by hand."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (askingSessionType) {
        // Asked, never guessed: the two answers price differently. A move-out report charges
        // findings against the deposit; a move-in report records the same findings as the
        // baseline the tenant is NOT responsible for. Defaulting silently would put a rupee
        // figure on a document someone signs, derived from an assumption nobody made.
        AlertDialog(
            onDismissRequest = { askingSessionType = false },
            title = { Text("Which inspection is this?") },
            text = {
                Text(
                    "Move-in records the property's existing condition as the baseline. " +
                        "Move-out prices what changed since then against the deposit."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askingSessionType = false
                    onGenerateReport(SessionType.MOVE_OUT)
                }) { Text("Move-out") }
            },
            dismissButton = {
                TextButton(onClick = {
                    askingSessionType = false
                    onGenerateReport(SessionType.MOVE_IN)
                }) { Text("Move-in") }
            }
        )
    }
}

@Composable
fun RoomCategoryIcon(title: String, icon: ImageVector, onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.9f else 1f, animationSpec = tween(100), label = "scale")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
fun RoomListItem(room: Room, onClick: () -> Unit) {
    val isComplete = room.topRecorded && room.bottomRecorded && room.sidesRecorded
    
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(room.type, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                val details = buildList {
                    if (room.sqFt.isNotEmpty()) add("${room.sqFt} sq ft")
                    if (room.frames.isNotEmpty()) {
                        add("${room.frames.size} frames")
                        val totalDefects = room.frames.sumOf { it.defects.size }
                        if (totalDefects > 0) add("$totalDefects defects")
                    }
                }.joinToString(" • ")
                if (details.isNotEmpty()) {
                    Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            
            if (isComplete) {
                Box(
                    modifier = Modifier
                        .background(com.smartlease.edge.ui.theme.LampGreen.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Complete", color = com.smartlease.edge.ui.theme.LampGreen, style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("In Progress", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
