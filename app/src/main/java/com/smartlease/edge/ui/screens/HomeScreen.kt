package com.smartlease.edge.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(onStartWalkthrough: () -> Unit, onViewReports: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center
    ) {
        Text("SmartLease Edge", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Offline property verification — AR alignment, on-device diagnostics, " +
                    "IR-verified appliance function, signed local report. No cloud call " +
                    "at any stage.",
            fontSize = 14.sp
        )
        Spacer(Modifier.height(28.dp))

        Button(onClick = onStartWalkthrough, modifier = Modifier.fillMaxWidth()) {
            Text("Start Walkthrough")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onViewReports, modifier = Modifier.fillMaxWidth()) {
            Text("View Past Reports")
        }

        Spacer(Modifier.height(32.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Build status", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text("✅ Real: camera, AR alignment, OCR, IR transmit, acoustic decay analysis, safety gate, local PDF report", fontSize = 12.sp)
                Text("🔧 Placeholder: vision segmenter (heuristic mode until Days 6-8 model export), GenieX narrative synthesis (rule-based until wired in)", fontSize = 12.sp)
            }
        }
    }
}
