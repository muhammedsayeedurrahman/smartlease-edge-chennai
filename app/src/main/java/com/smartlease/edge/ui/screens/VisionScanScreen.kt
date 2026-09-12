package com.smartlease.edge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartlease.edge.ui.theme.*

@Composable
fun VisionScanScreen(
    onContinue: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // Camera viewport background
    ) {
        // Mocked Camera Viewport
        // In a real implementation this would be an AndroidView wrapping a PreviewView
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // AR Framing Grid Mockup
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                Divider(color = SlateEdge.copy(alpha = 0.5f), thickness = 1.dp)
                Divider(color = SlateEdge.copy(alpha = 0.5f), thickness = 1.dp)
            }
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Divider(
                    color = SlateEdge.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxHeight().width(1.dp)
                )
                Divider(
                    color = SlateEdge.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxHeight().width(1.dp)
                )
            }
            
            // Mock Live Detection Overlay
            Box(
                modifier = Modifier
                    .offset(x = (-30).dp, y = 50.dp)
                    .size(width = 120.dp, height = 80.dp)
                    .border(2.dp, LampAmber)
            ) {
                Surface(
                    color = LampAmber,
                    modifier = Modifier.align(Alignment.TopStart).offset(y = (-20).dp)
                ) {
                    Text(
                        text = "CRACK 0.87",
                        color = SlateGround,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Top Status HUD
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
                .background(SlatePanel.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                .border(1.dp, SlateEdge, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(LampGreen, RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "AR ALIGNED · Δ 0.8°",
                color = LampGreen,
                style = MaterialTheme.typography.labelMedium
            )
        }

        // Bottom Floating Metric Sheet
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth()
                .border(1.dp, SlateEdge, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = SlatePanel),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Living Room · North Wall",
                    style = MaterialTheme.typography.titleMedium,
                    color = ReadoutPrimary
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Crack · 0.42 sq ft", style = ReadoutValue, color = ReadoutPrimary)
                    
                    Surface(
                        color = LampAmber.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, LampAmber.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "MODERATE",
                            color = LampAmber,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Est. repair ₹1,200 - ₹1,800",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReadoutDim
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { /* Dismiss logic */ },
                        modifier = Modifier.weight(0.3f)
                    ) {
                        Text("Dismiss", color = ReadoutDim)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = onContinue,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LampAmber,
                            contentColor = SlateGround
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(0.7f).height(48.dp)
                    ) {
                        Text("Continue to Tap Test →", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
