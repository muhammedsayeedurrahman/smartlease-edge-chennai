package com.smartlease.edge.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartlease.edge.report.InspectionReport

@Composable
fun ReportScreen(report: InspectionReport?, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        TextButton(onClick = onBack) { Text("< Back") }
        Text("Inspection Report", fontWeight = FontWeight.Bold, fontSize = 22.sp)

        if (report == null) {
            Spacer(Modifier.height(16.dp))
            Text("No report generated yet for this session.")
            return@Column
        }

        Spacer(Modifier.height(4.dp))
        Text(report.propertyLabel)
        Text("Session ${report.sessionId}", fontSize = 11.sp)
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Text(report.overallVerdict, Modifier.padding(12.dp))
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn {
            items(report.sections) { section ->
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(section.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(section.body, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
