package com.smartlease.edge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.smartlease.edge.ui.AppViewModel
import com.smartlease.edge.ui.Property
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePropertyScreen(
    viewModel: AppViewModel,
    isFlat: Boolean,
    onBack: () -> Unit,
    onPropertyCreated: (String) -> Unit
) {
    var propertyName by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var tenantName by remember { mutableStateOf("") }
    var deposit by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isFlat) "New Flat" else "New Home", fontWeight = FontWeight.Bold) },
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
                .padding(24.dp)
        ) {
            Text(
                "Property Details",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(24.dp))

            GlassTextField(
                value = propertyName,
                onValueChange = { propertyName = it },
                label = "Property Name (e.g. Flat 3B)"
            )
            Spacer(modifier = Modifier.height(16.dp))

            GlassTextField(
                value = location,
                onValueChange = { location = it },
                label = "Location"
            )
            Spacer(modifier = Modifier.height(16.dp))

            GlassTextField(
                value = tenantName,
                onValueChange = { tenantName = it },
                label = "Tenant Name (Optional)"
            )
            Spacer(modifier = Modifier.height(16.dp))

            GlassTextField(
                value = deposit,
                onValueChange = { deposit = it },
                label = "Deposit Amount (Optional)",
                keyboardType = KeyboardType.Number
            )
            
            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = {
                    val newProperty = Property(
                        id = UUID.randomUUID().toString(),
                        name = propertyName.ifEmpty { "Unnamed Property" },
                        location = location.ifEmpty { "Unknown Location" },
                        tenantName = tenantName,
                        depositAmount = deposit,
                        isFlat = isFlat
                    )
                    viewModel.addProperty(newProperty)
                    onPropertyCreated(newProperty.id)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Save & Proceed →", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Color.Transparent,
            focusedTextColor = MaterialTheme.colorScheme.onBackground,
            unfocusedTextColor = MaterialTheme.colorScheme.onBackground
        ),
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
    )
}
