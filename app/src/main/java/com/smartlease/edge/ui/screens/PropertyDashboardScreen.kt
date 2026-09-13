package com.smartlease.edge.ui.screens

import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    onRoomSelected: (String, String) -> Unit,
    onGenerateReport: (SessionType) -> Unit = {}
) {
    val property = viewModel.properties.find { it.id == propertyId }
    val rooms = viewModel.getRoomsForProperty(propertyId)
    val capturedDefectCount = rooms.sumOf { room -> room.moveInFrames.sumOf { it.defects.size } + room.moveOutFrames.sumOf { it.defects.size } }
    var askingSessionType by remember { mutableStateOf(false) }
    // Seeded from persisted frames, not just false: this screen is recreated every time
    // navigation returns to it (e.g. after recording a surface), so a plain `false` default
    // would silently drop the tenant back into "Complete Move-In & Start Move-Out" even
    // though move-out recording had already started -- indistinguishable from move-out
    // "not working" from the outside.
    var isMoveOutMode by remember(propertyId) {
        mutableStateOf(rooms.any { it.moveOutFrames.isNotEmpty() })
    }
    
    val sessionTypeString = if (isMoveOutMode) "MOVE_OUT" else "MOVE_IN"

    val roomCategories = listOf(
        "Hall" to Icons.Rounded.Weekend,
        "Room" to Icons.Rounded.Bed,
        "Kitchen" to Icons.Rounded.Countertops,
        "Bathroom" to Icons.Rounded.Bathtub,
        "Balcony" to Icons.Rounded.Deck,
        "Garden" to Icons.Rounded.Yard,
        "Outer" to Icons.Rounded.Fence,
        "Custom" to Icons.Rounded.AddBox
    )

    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var isDocumentUploaded by remember { mutableStateOf(false) }
    var isAnalyzingDocument by remember { mutableStateOf(false) }
    var showSummaryDialog by remember { mutableStateOf(false) }
    var agreement by remember { mutableStateOf<com.smartlease.edge.data.RentalAgreementEntity?>(null) }

    LaunchedEffect(propertyId) {
        val db = com.smartlease.edge.data.AppDatabase.get(context)
        val loaded = db.propertyDao().getRentalAgreementForProperty(propertyId)
        if (loaded != null) {
            agreement = loaded
            isDocumentUploaded = true
        }
    }

    val documentPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            // A picker URI grant is otherwise one-shot; persisting it is what lets the
            // eye icon still open the PDF (and lets re-analysis still read it) after this
            // screen is recreated by navigation or the app is restarted.
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some providers don't grant persistable access; the summary/view still
                // works for this session, so this is not fatal.
            }
            scope.launch {
                isAnalyzingDocument = true
                val summary = com.smartlease.edge.domain.llm.DocumentAnalyzer(context)
                    .extractDosAndDonts(uri.toString())
                val db = com.smartlease.edge.data.AppDatabase.get(context)
                val newAgreement = com.smartlease.edge.data.RentalAgreementEntity(
                    id = UUID.randomUUID().toString(),
                    propertyId = propertyId,
                    pdfFilePath = uri.toString(),
                    dosAndDontsSummary = summary.text,
                    dosAndDontsSource = summary.source.name
                )
                db.propertyDao().insertRentalAgreement(newAgreement)
                agreement = newAgreement
                isDocumentUploaded = true
                isAnalyzingDocument = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(property?.name ?: "Property", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {},
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
            
            if (!isMoveOutMode) {
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
                            onRoomSelected(newRoom.id, sessionTypeString)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isDocumentUploaded) {
                    Column {
                        Text("Add Document", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                        Text("Completed", color = com.smartlease.edge.ui.theme.LampGreen, style = MaterialTheme.typography.bodySmall)
                    }
                    Row {
                        IconButton(onClick = {
                            val path = agreement?.pdfFilePath
                            if (path != null) {
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                        setDataAndType(android.net.Uri.parse(path), "application/pdf")
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e: android.content.ActivityNotFoundException) {
                                    Toast.makeText(context, "No app found to open PDF", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) {
                            Icon(Icons.Rounded.Visibility, contentDescription = "View Document", tint = com.smartlease.edge.ui.theme.CorporateYellow)
                        }
                        IconButton(onClick = { showSummaryDialog = true }) {
                            Icon(Icons.Rounded.Description, contentDescription = "View Summary", tint = com.smartlease.edge.ui.theme.CorporateYellow)
                        }
                    }
                } else if (isAnalyzingDocument) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = com.smartlease.edge.ui.theme.CorporateYellow
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Analyzing document with on-device AI…", color = MaterialTheme.colorScheme.onBackground)
                    }
                } else {
                    Button(
                        onClick = { documentPickerLauncher.launch("application/pdf") },
                        colors = ButtonDefaults.buttonColors(containerColor = com.smartlease.edge.ui.theme.CarbonBlack, contentColor = Color.White),
                        border = BorderStroke(1.dp, com.smartlease.edge.ui.theme.CorporateYellow),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Add Document", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            if (showSummaryDialog) {
                val credit = when (agreement?.dosAndDontsSource) {
                    "GEMMA" -> "Written by the on-device Gemma model from this lease's text."
                    else -> "On-device AI summary unavailable for this document -- showing extracted lease text."
                }
                AlertDialog(
                    onDismissRequest = { showSummaryDialog = false },
                    title = { Text("Lease Dos & Don'ts") },
                    text = {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 400.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = agreement?.dosAndDontsSummary ?: "No summary available.",
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = credit,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSummaryDialog = false }) { Text("Close") }
                    }
                )
            }
            
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
                        RoomListItem(
                            room = room, 
                            sessionType = sessionTypeString,
                            onClick = { onRoomSelected(room.id, sessionTypeString) },
                            onDelete = { viewModel.deleteRoom(room.id) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                
                val isMoveInComplete = rooms.isNotEmpty() && rooms.all { it.topRecorded && it.bottomRecorded && it.sidesRecorded }
                val isMoveOutComplete = rooms.isNotEmpty() && rooms.all { it.topMoveOutRecorded && it.bottomMoveOutRecorded && it.sidesMoveOutRecorded }

                val buttonText = when {
                    isMoveOutMode && isMoveOutComplete -> "Generate Report"
                    isMoveOutMode -> "Recording Move-Out"
                    isMoveInComplete -> "Complete Move-In & Start Move-Out"
                    else -> "Complete Move-In First"
                }

                Button(
                    onClick = { 
                        if (isMoveOutMode && isMoveOutComplete) {
                            onGenerateReport(SessionType.MOVE_OUT)
                        } else if (isMoveInComplete && !isMoveOutMode) {
                            isMoveOutMode = true
                        }
                    },
                    enabled = isMoveInComplete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(buttonText)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    if (capturedDefectCount > 0) {
                        "Workflow State: ${if (isMoveInComplete) "Move-In Complete. Ready for Move-Out." else "Recording Move-In phase."}"
                    } else {
                        "Record an area first to establish the Move-In baseline."
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
fun RoomListItem(room: Room, sessionType: String, onClick: () -> Unit, onDelete: () -> Unit) {
    val isComplete = if (sessionType == "MOVE_OUT") {
        room.topMoveOutRecorded && room.bottomMoveOutRecorded && room.sidesMoveOutRecorded
    } else {
        room.topRecorded && room.bottomRecorded && room.sidesRecorded
    }
    
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
                    val frames = if (sessionType == "MOVE_OUT") room.moveOutFrames else room.moveInFrames
                    if (frames.isNotEmpty()) {
                        add("${frames.size} frames")
                        val totalDefects = frames.sumOf { it.defects.size }
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
            
            Spacer(modifier = Modifier.width(12.dp))
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
