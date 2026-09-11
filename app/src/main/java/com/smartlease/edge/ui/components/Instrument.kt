package com.smartlease.edge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.smartlease.edge.ui.theme.LampAmber
import com.smartlease.edge.ui.theme.LampGreen
import com.smartlease.edge.ui.theme.LampRed
import com.smartlease.edge.ui.theme.ReadoutValue

/** Severity of a reading, mapped to the three instrument lamp colours. */
enum class Lamp(val color: Color) {
    PASS(LampGreen),
    CAUTION(LampAmber),
    FLAG(LampRed)
}

/**
 * A status dot.
 *
 * Always rendered next to a label and a value, never alone: colour is a fast second channel
 * for someone scanning the list, not the only way to read it. A landlord and tenant look at
 * this screen together and one of them may not distinguish amber from green.
 */
@Composable
fun StatusLamp(lamp: Lamp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(lamp.color)
    )
}

/**
 * One measured reading: what was measured, the value, and how sure the device is.
 *
 * The value is monospace so a column of readings aligns on the decimal point and does not
 * shift as digits change. Confidence sits on the same row rather than in a footnote, because
 * a finding without its confidence is the thing that starts an argument later.
 */
@Composable
fun ReadingRow(
    lamp: Lamp,
    label: String,
    value: String,
    detail: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        StatusLamp(lamp, Modifier.padding(top = 6.dp))
        Column(Modifier.padding(start = 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(value, style = ReadoutValue, color = lamp.color)
            }
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * A bordered panel. Hairline border rather than a drop shadow -- shadows on a dark ground
 * read as smudge, and an outline is what a physical instrument bezel actually looks like.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) { content() }
}
