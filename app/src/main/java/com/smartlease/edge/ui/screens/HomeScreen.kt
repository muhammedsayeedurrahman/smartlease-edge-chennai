package com.smartlease.edge.ui.screens

import kotlinx.coroutines.launch
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.House
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartlease.edge.ui.AppViewModel
import com.smartlease.edge.ui.Property

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    onCreateProperty: (isFlat: Boolean) -> Unit,
    onPropertySelected: (String) -> Unit,
    onOpenSelfTest: () -> Unit
) {
    val properties = viewModel.properties

    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { 
            scope.launch {
                com.smartlease.edge.data.drive.GoogleDriveBackupHelper(context).exportFullBackupToDrive(it)
                android.widget.Toast.makeText(context, "Export complete", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                com.smartlease.edge.data.drive.GoogleDriveBackupHelper(context).importBackupFromDrive(it)
                android.widget.Toast.makeText(context, "Import merged successfully", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SmartLease", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) },
                actions = {
                    // Diagnostics has to be reachable from the first screen, with no
                    // property and no session: the questions it answers ("did the model
                    // load?", "is narration rule-based on this phone?") are exactly the
                    // ones asked before anyone has recorded anything.
                    IconButton(onClick = onOpenSelfTest) {
                        Icon(Icons.Rounded.Info, contentDescription = "Self-test")
                    }
                    var driveMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { driveMenuExpanded = true }) {
                            Icon(Icons.Rounded.CloudUpload, contentDescription = "Google Drive Backup")
                        }
                        DropdownMenu(
                            expanded = driveMenuExpanded,
                            onDismissRequest = { driveMenuExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export to Drive") },
                                onClick = { 
                                    driveMenuExpanded = false
                                    exportLauncher.launch("SmartLease_Backup.zip")
                                },
                                leadingIcon = { Icon(Icons.Rounded.CloudUpload, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Import from Drive") },
                                onClick = { 
                                    driveMenuExpanded = false
                                    importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                                },
                                leadingIcon = { Icon(Icons.Rounded.CloudDownload, contentDescription = null) }
                            )
                        }
                    }
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { expanded = true },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add Property", tint = Color.Black)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Add Home") },
                                onClick = { expanded = false; onCreateProperty(false) },
                                leadingIcon = { Icon(Icons.Rounded.House, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Add Flat") },
                                onClick = { expanded = false; onCreateProperty(true) },
                                leadingIcon = { Icon(Icons.Rounded.Apartment, contentDescription = null) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Large App Icon 
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(28.dp))
                    .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Home,
                    contentDescription = "App Icon",
                    modifier = Modifier.size(50.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(48.dp))

            if (properties.isEmpty()) {
                Spacer(modifier = Modifier.weight(0.5f))
                Icon(
                    imageVector = Icons.Rounded.Home,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No properties added yet",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap the + button to add a new Home or Flat.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
            } else {
                Text(
                    "Your Properties",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Start),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(properties) { property ->
                        PropertyItemCard(
                            property = property, 
                            onClick = { onPropertySelected(property.id) },
                            onDelete = { viewModel.deleteProperty(property.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PropertyTypeCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1f, animationSpec = tween(150), label = "scale")

    ElevatedCard(
        modifier = modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(RoundedCornerShape(24.dp))
            .clickable { onClick() },
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun PropertyItemCard(property: Property, onClick: () -> Unit, onDelete: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (property.isFlat) Icons.Rounded.Apartment else Icons.Rounded.House,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(property.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(4.dp))
                Text(property.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
