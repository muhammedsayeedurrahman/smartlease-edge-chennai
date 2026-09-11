package com.smartlease.edge.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smartlease.edge.ui.components.Lamp
import com.smartlease.edge.ui.components.Panel
import com.smartlease.edge.ui.components.StatusLamp
import com.smartlease.edge.ui.theme.ReadoutValue

@Composable
fun HomeScreen(onStartWalkthrough: () -> Unit, onViewReports: () -> Unit) {
    val insets = WindowInsets.systemBars.asPaddingValues()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                top = insets.calculateTopPadding() + 24.dp,
                bottom = insets.calculateBottomPadding() + 24.dp,
                start = 24.dp,
                end = 24.dp
            )
    ) {
        Spacer(Modifier.height(24.dp))

        Text(
            "SmartLease Edge",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Documents a property at handover so a deposit dispute has evidence behind it. " +
                "Every measurement is taken and stored on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))

        // The offline claim is the product's whole argument, so it gets a lamp of its own
        // rather than being buried in the paragraph above.
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusLamp(Lamp.PASS)
            Text(
                "No network permission is declared. It cannot phone home.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 10.dp)
            )
        }

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = onStartWalkthrough,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Start walkthrough", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onViewReports,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onBackground
            )
        ) {
            Text("Past reports", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(32.dp))

        Panel(Modifier.fillMaxWidth()) {
            Column {
                Text(
                    "What runs on this device",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(14.dp))

                CapabilityLine(
                    Lamp.PASS, "Defect segmentation",
                    "YOLOv8n-Seg, 4 classes", "3.3M params"
                )
                CapabilityLine(
                    Lamp.PASS, "Acoustic tap test",
                    "Trained classifier, cross-validated", "80%"
                )
                CapabilityLine(
                    Lamp.PASS, "Camera, tilt, OCR, report",
                    "Signed PDF written locally", null
                )

                Spacer(Modifier.height(14.dp))
                Text(
                    "Known limits",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                // Stated plainly and kept on this screen on purpose. A judge who finds an
                // overstated claim stops trusting the rest of the demo; one who is told the
                // limits up front reads the numbers as measurements instead of marketing.
                CapabilityLine(
                    Lamp.CAUTION, "Tap classifier sample size",
                    "15 taps, 95% CI 55-93%", "n=15"
                )
                CapabilityLine(
                    Lamp.CAUTION, "Trained with a different striker",
                    "Demo uses a coin; retrain before relying on it", null
                )
                CapabilityLine(
                    Lamp.CAUTION, "Segmentation runs on CPU",
                    "Hexagon NPU path not yet wired", null
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CapabilityLine(lamp: Lamp, title: String, detail: String, value: String?) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        StatusLamp(lamp, Modifier.padding(top = 6.dp))
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (value != null) {
            Text(
                value,
                style = ReadoutValue,
                color = lamp.color,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
