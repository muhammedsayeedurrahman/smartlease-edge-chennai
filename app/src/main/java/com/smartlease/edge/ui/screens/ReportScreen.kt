package com.smartlease.edge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.smartlease.edge.report.InspectionReport
import com.smartlease.edge.ui.components.Lamp
import com.smartlease.edge.ui.components.Panel
import com.smartlease.edge.ui.components.StatusLamp
import com.smartlease.edge.ui.theme.ReadoutValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The report screen. This is the artifact the whole product exists to produce -- the thing
 * two people look at when a deposit is disputed -- so it is laid out as a document rather
 * than as a feed: a verdict that states the outcome, then the evidence behind it.
 *
 * What "signed" means here is stated plainly at the bottom. ReportGenerator's own comment is
 * explicit that it bakes in a timestamp and session id rather than a cryptographic signature,
 * and a report that implies more provenance than it carries is worse than one that implies
 * none -- it is the exact claim that would collapse under scrutiny in a dispute.
 */
@Composable
fun ReportScreen(report: InspectionReport?, onBack: () -> Unit) {
    val insets = WindowInsets.systemBars.asPaddingValues()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = insets.calculateTopPadding(), bottom = insets.calculateBottomPadding())
    ) {
        TextButton(onClick = onBack, modifier = Modifier.padding(start = 6.dp)) {
            Text("Back", style = MaterialTheme.typography.labelMedium)
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                "Inspection report",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (report == null) {
            // Empty state as direction, not apology.
            Column(Modifier.padding(20.dp)) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Nothing recorded for this session yet. Run a walkthrough and the " +
                        "findings will be written up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        val attentionRequired = report.overallVerdict.startsWith("ATTENTION")
        val lamp = if (attentionRequired) Lamp.FLAG else Lamp.PASS
        val stamp = remember(report.generatedAtEpochMillis) {
            SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
                .format(Date(report.generatedAtEpochMillis))
        }

        Spacer(Modifier.height(6.dp))

        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                report.propertyLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(10.dp))

            // Identity of the document. Both values are monospace because they are what two
            // people read aloud to each other to confirm they are holding the same report.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetaField("Session", report.sessionId)
                MetaField("Recorded", stamp)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Verdict first. Someone opening this in an argument needs the outcome before
        // the evidence, and the lamp colour is backed by the words beside it.
        Box(Modifier.padding(horizontal = 20.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(lamp.color.copy(alpha = 0.12f))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    StatusLamp(lamp, Modifier.padding(top = 6.dp))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(
                            if (attentionRequired) "Attention required" else "No hazard flagged",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            report.overallVerdict.substringAfter("— ").ifBlank { report.overallVerdict },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Evidence",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                report.sections.size.toString(),
                style = ReadoutValue,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.outline
        )

        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(report.sections) { section ->
                Panel(Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            // Stored as SCREAMING_SNAKE enum names; read back as prose.
                            section.title.lowercase(Locale.getDefault())
                                .replaceFirstChar { it.titlecase(Locale.getDefault()) },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            section.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
                // Provenance, stated exactly. Claiming a cryptographic signature this code
                // does not produce would be the one line an opposing party could break.
                Text(
                    "Written to this phone's storage with the session id and timestamp above. " +
                        "No copy left the device. This is a timestamped record, not a " +
                        "cryptographically signed document.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun MetaField(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = ReadoutValue,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
