package com.smartlease.edge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.smartlease.edge.sync.SyncUiState

/**
 * Renders the *sync* half of a report/countersign action, honestly and separately from the
 * local-only guarantees shown elsewhere on these screens. Every [SyncUiState] branch is
 * matched explicitly -- no `else` -- so a new state added to that sealed type fails this file
 * to compile instead of silently rendering nothing.
 *
 * Wording choices, deliberately:
 *  - [SyncUiState.Disabled] says "local-only", never "synced" -- nothing left the device.
 *  - [SyncUiState.Failed] always repeats that the local record is intact, because sync is
 *    strictly best-effort here and must never read as if the report itself is at risk.
 *  - [SyncUiState.TamperConflict] gets the loudest treatment on this screen (error-coloured
 *    background, not just error-coloured text) because it is not an ordinary failure -- it
 *    means the server already holds this report under a *different* digest.
 */
@Composable
fun SyncStatusPanel(state: SyncUiState, modifier: Modifier = Modifier) {
    when (state) {
        is SyncUiState.Idle -> Unit // nothing to say before the first attempt

        is SyncUiState.Disabled -> SyncLine(
            modifier,
            "Local-only -- sync is turned off. This report has not been sent anywhere.",
            MaterialTheme.colorScheme.onSurfaceVariant
        )

        is SyncUiState.InFlight -> Row(
            modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                "Syncing to server...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        is SyncUiState.Uploaded -> SyncLine(
            modifier,
            state.message,
            MaterialTheme.colorScheme.onSurfaceVariant
        )

        is SyncUiState.Failed -> SyncLine(
            modifier,
            state.message,
            MaterialTheme.colorScheme.error
        )

        is SyncUiState.TamperConflict -> Column(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f))
                .padding(14.dp)
        ) {
            Text(
                "Integrity warning",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(6.dp))
            Text(
                state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun SyncLine(modifier: Modifier, message: String, color: Color) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = color)
    }
}
