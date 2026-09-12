package com.smartlease.edge.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.smartlease.edge.deduction.DeductionEngine
import com.smartlease.edge.deduction.DeductionLine
import com.smartlease.edge.deduction.DeductionSummary
import com.smartlease.edge.report.FindingsDigest
import com.smartlease.edge.report.InspectionReport
import com.smartlease.edge.report.QrCode
import com.smartlease.edge.report.ReportGenerator
import com.smartlease.edge.ui.components.Lamp
import com.smartlease.edge.ui.components.Panel
import com.smartlease.edge.ui.components.StatusLamp
import com.smartlease.edge.ui.theme.ReadoutValue
import com.smartlease.edge.ui.theme.ReadoutValueLarge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The report screen. This is the artifact the whole product exists to produce -- the thing
 * two people look at when a deposit is disputed -- so it is laid out as a document rather
 * than as a feed: a verdict that states the outcome, then the evidence behind it.
 *
 * What "signed" means here is stated plainly at the bottom: the SHA-256 printed on the
 * document and shown below is an integrity digest over the canonical findings, not a
 * digital signature. It proves the record has not changed; it does not prove who made it.
 * A report that implies more provenance than it carries is worse than one that implies
 * none -- it is the exact claim that would collapse under scrutiny in a dispute.
 */
@Composable
fun ReportScreen(report: InspectionReport?, onBack: () -> Unit) {
    val insets = WindowInsets.systemBars.asPaddingValues()
    val context = LocalContext.current
    var shareError by remember { mutableStateOf<String?>(null) }

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
            // Stacked rather than side by side: the session id is a full 36-character UUID,
            // which at this monospace size needs nearly the full row width on its own --
            // sharing a row with "Recorded" would squeeze that field to a one-character-wide
            // column that wraps vertically instead of reading as a line of text.
            MetaField("Session", report.sessionId, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            MetaField("Recorded", stamp, modifier = Modifier.fillMaxWidth())
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

            // Shown on screen as well as in the PDF footer so both parties can read the same
            // digest off the same phone and check it against the document afterwards.
            item {
                // Above the digest panel, not below it: the balance sheet is what a landlord
                // and tenant are actually here to look at, and the digest is how they check
                // the record afterwards -- outcome before verification, same order as the
                // verdict panel at the top of this screen.
                report.deductions?.let { deductions ->
                    Spacer(Modifier.height(6.dp))
                    DepositBalanceSheet(deductions)
                }

                Spacer(Modifier.height(6.dp))
                Panel(Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            "SHA-256 of ${report.findingCount} finding(s)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(10.dp))

                        // Scannable by any phone's stock camera app: no install, no network.
                        // The hex below it is the same string, so it can be checked either way.
                        val qr = remember(report.findingsSha256) {
                            runCatching { QrCode.bitmap(report.findingsSha256, 512).asImageBitmap() }
                                .getOrNull()
                        }
                        if (qr != null) {
                            Image(
                                bitmap = qr,
                                contentDescription = "QR code of the findings digest",
                                modifier = Modifier.size(200.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Scan to read this digest on another phone.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                        }

                        Text(
                            FindingsDigest.grouped(report.findingsSha256),
                            style = ReadoutValue,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Change one character of one finding and this digest changes. It is " +
                                "not a signature — it does not identify who recorded them.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { shareError = shareReport(context, report) },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Share report (PDF)", style = MaterialTheme.typography.titleMedium)
                }

                shareError?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(14.dp))
                // Provenance, stated exactly. Claiming a cryptographic signature this code
                // does not produce would be the one line an opposing party could break.
                Text(
                    "Written to this phone's storage with the session id and timestamp above. " +
                        "No copy left the device unless it was shared above. This is a " +
                        "timestamped record carrying an integrity digest, not a " +
                        "cryptographically signed document.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

/**
 * The deposit balance sheet, mirrored from the PDF's own rendering of it: what the deposit
 * was, what each line cost and why, and the refund figure a landlord and tenant are actually
 * here to look at. Nothing here that is not also on the printed page -- this panel and
 * [ReportGenerator.renderToPdf]'s deduction section must always tell the same story.
 */
@Composable
private fun DepositBalanceSheet(deductions: DeductionSummary) {
    Panel(Modifier.fillMaxWidth()) {
        Column {
            Text(
                "Deposit balance sheet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(10.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Deposit held",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "₹%,d".format(deductions.depositRupees),
                    style = ReadoutValue,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (deductions.lines.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                deductions.lines.forEach { line -> DeductionLineRow(line) }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(10.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Total deductions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "₹%,d".format(deductions.totalDeductionRupees),
                    style = ReadoutValue,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(14.dp))

            // The demo's punchline: what actually comes back. The largest, boldest readout
            // on this screen, the same treatment the tilt number gets on the walkthrough
            // screen for the thing a person is there to look at.
            Text(
                "Refund due",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "₹%,d".format(deductions.refundRupees),
                style = ReadoutValueLarge,
                color = if (deductions.refundRupees < 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )

            if (deductions.clearedNotes.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Not priced — for human review",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                deductions.clearedNotes.forEach { note ->
                    Text(
                        "•  $note",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            // Non-negotiable per the design doc: state exactly what this arithmetic does and
            // does not cover, in the one place a reader is looking at the rupee figures.
            Text(
                "These repair rates are the team's own Chennai contractor estimates, not " +
                    "published tariffs. Nothing below " +
                    "${"%.0f".format(DeductionEngine.PRICING_CONFIDENCE_FLOOR * 100f)}% model " +
                    "confidence, and nothing from the colour heuristic, is ever priced. The " +
                    "deposit figure above was entered by the operator and is not covered by " +
                    "the SHA-256 digest below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DeductionLineRow(line: DeductionLine) {
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            line.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            "₹%,d".format(line.amountRupees),
            style = ReadoutValue,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
    Text(
        line.basis,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun MetaField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
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

/**
 * Hand the rendered PDF to whatever the user picks. Before this existed the report was
 * written to app-private storage with no provider and no intent, so the document the whole
 * product is about could not reach either party.
 *
 * The digest goes in the message body as well as inside the PDF, so a recipient can compare
 * the two without opening anything.
 *
 * @return null on success, or a message to show the user.
 */
private fun shareReport(context: Context, report: InspectionReport): String? {
    val file = ReportGenerator.reportFile(context, report.sessionId)
    if (!file.exists()) {
        return "Report PDF not found — generate the report first."
    }
    return try {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".reports", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_SUBJECT,
                "SmartLease Edge report — session ${report.sessionId.take(8)}"
            )
            putExtra(
                Intent.EXTRA_TEXT,
                "SHA-256 of ${report.findingCount} finding(s): ${report.findingsSha256}"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share report").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
        null
    } catch (e: Exception) {
        "Could not share: ${e.message ?: e.javaClass.simpleName}"
    }
}
