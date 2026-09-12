package com.smartlease.edge.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartlease.edge.ui.theme.*
import java.security.MessageDigest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSignatureScreen(
    onSignAndSave: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    val fakeHash = "9f3ac21e77b04d1ae8a0329c3629f121d5b3d68bc93e430c11109405d40026e6".substring(0, 24)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Final Summary", fontWeight = FontWeight.Bold) },
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
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Verdict Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, LampAmber, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = LampAmber.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "VERDICT · PARTIAL DEDUCTION",
                        style = MaterialTheme.typography.labelMedium,
                        color = LampAmber,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "₹2,400 of ₹90,000 deposit",
                        style = MaterialTheme.typography.titleLarge,
                        color = ReadoutPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Itemized Table
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateEdge, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = SlatePanel),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Itemized Deductions", style = MaterialTheme.typography.titleMedium, color = ReadoutPrimary)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    DeductionRow(item = "Crack, living room N wall (0.42 sq ft)", amount = "₹1,500")
                    Divider(color = SlateEdge, modifier = Modifier.padding(vertical = 8.dp))
                    DeductionRow(item = "Hollow tile, kitchen floor #7", amount = "₹900")
                    Divider(color = SlateEdge, modifier = Modifier.padding(vertical = 8.dp))
                    DeductionRow(item = "Split AC, living room (Functional IR)", amount = "₹0", isZero = true)
                }
            }

            // Dual Touch Signature Pads
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tenant Signature", style = MaterialTheme.typography.labelMedium, color = ReadoutPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    SignaturePad()
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Landlord Signature", style = MaterialTheme.typography.labelMedium, color = ReadoutPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    SignaturePad()
                }
            }

            // Tamper-Evident Footer
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SlateGround),
                colors = CardDefaults.cardColors(containerColor = SlateGround)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Auto-generated PDF Hash", style = MaterialTheme.typography.labelSmall, color = ReadoutDim)
                    Text("SHA-256: $fakeHash...", style = ReadoutValue.copy(fontSize = 12.sp), color = ReadoutDim)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Action Bar
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(
                    onClick = { /* Share Intent */ },
                    modifier = Modifier.weight(0.35f).height(52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ReadoutPrimary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateEdge)
                ) {
                    Text("Share PDF", fontWeight = FontWeight.SemiBold)
                }
                
                Button(
                    onClick = onSignAndSave,
                    modifier = Modifier.weight(0.65f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LampAmber, contentColor = SlateGround),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Both Sign & Save Document", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DeductionRow(item: String, amount: String, isZero: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isZero) LampGreen else ReadoutPrimary,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        Text(
            text = amount,
            style = ReadoutValue,
            color = if (isZero) LampGreen else ReadoutPrimary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SignaturePad() {
    var paths by remember { mutableStateOf(listOf<Path>()) }
    var currentPath by remember { mutableStateOf<Path?>(null) }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Color(0xFFE8EAED), RoundedCornerShape(8.dp))
                .border(1.dp, SlateEdge, RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentPath = Path().apply { moveTo(offset.x, offset.y) }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            currentPath?.lineTo(change.position.x, change.position.y)
                            // Force recomposition
                            currentPath = currentPath
                        },
                        onDragEnd = {
                            currentPath?.let { paths = paths + it }
                            currentPath = null
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                paths.forEach { path ->
                    drawPath(
                        path = path,
                        color = SlateGround,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
                currentPath?.let { path ->
                    drawPath(
                        path = path,
                        color = SlateGround,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
                
                if (paths.isEmpty() && currentPath == null) {
                    // Draw a placeholder line
                    drawLine(
                        color = SlateEdge.copy(alpha = 0.5f),
                        start = Offset(40f, size.height * 0.8f),
                        end = Offset(size.width - 40f, size.height * 0.8f),
                        strokeWidth = 2f
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        TextButton(
            onClick = { 
                paths = emptyList()
                currentPath = null
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Clear Pad", color = ReadoutDim, style = MaterialTheme.typography.labelSmall)
        }
    }
}
