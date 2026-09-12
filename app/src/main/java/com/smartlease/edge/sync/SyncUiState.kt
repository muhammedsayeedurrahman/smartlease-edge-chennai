package com.smartlease.edge.sync

/**
 * What a report/countersign screen shows about the *sync* side of an already-completed local
 * action. This is deliberately a separate, smaller sealed type from [SyncResult] rather than
 * the same one reused in the UI layer: a screen also needs [Idle] (nothing attempted yet) and
 * [Disabled] (never attempted, by configuration) states that [SyncResult] has no business
 * knowing about -- [SyncResult] describes what a network call did, not what a screen should
 * say before or instead of making one.
 *
 * Every branch here maps to specific, honest wording -- see [toSyncUiState] -- because this
 * codebase treats a UI message that implies more than actually happened as a correctness bug,
 * not a copywriting nit. In particular:
 *  - [Disabled] must never be worded as "synced" -- nothing left the device.
 *  - [Failed] must say the local record is intact -- sync is strictly best-effort and never
 *    blocks or undoes the offline flow.
 *  - [TamperConflict] must read as an integrity warning, not a routine error -- it means the
 *    server already holds this report under a *different* digest.
 */
sealed interface SyncUiState {

    /** Nothing has been attempted yet -- initial state before a screen kicks off a sync. */
    data object Idle : SyncUiState

    /** Sync is turned off by configuration. No network call was made. */
    data object Disabled : SyncUiState

    /** A sync call is currently in flight. */
    data object InFlight : SyncUiState

    /** The server accepted the upload/countersign. */
    data class Uploaded(val message: String) : SyncUiState

    /**
     * The attempt failed for an ordinary reason (offline, timeout, malformed response, server
     * error, or local/server-side validation rejection). [message] always states that the
     * local record is unaffected -- this is purely a "the copy on the server isn't there yet"
     * signal.
     */
    data class Failed(val message: String) : SyncUiState

    /**
     * The backend's 409: it already holds a record for this reportId (or signer role) that
     * disagrees with what was just sent. Never rendered like an ordinary [Failed] -- this is
     * the one state that means someone (or something) altered the record, not that the network
     * had a bad day.
     */
    data class TamperConflict(val message: String) : SyncUiState
}

/**
 * Maps a completed [SyncResult] onto the UI-facing [SyncUiState], exhaustively -- there is no
 * `else` branch, so a future case added to [SyncResult] fails this file to compile instead of
 * silently falling into a generic error state. [successMessage] renders the type-specific
 * success payload ([com.smartlease.edge.sync.ReportUploadAck] or
 * [com.smartlease.edge.sync.CountersignAck]) into the one line of copy a screen shows for it.
 *
 * This function never inspects [config] -- the "sync disabled" case is decided by callers
 * *before* a [SyncResult] exists at all (see the sync coordinators in this package), so that a
 * disabled config never results in a network call in the first place.
 */
fun <T> SyncResult<T>.toSyncUiState(successMessage: (T) -> String): SyncUiState = when (this) {
    is SyncResult.Success -> SyncUiState.Uploaded(successMessage(value))

    is SyncResult.NetworkUnavailable -> SyncUiState.Failed(
        "Could not reach the sync server ($message). The report is saved on this phone and has not been lost."
    )

    is SyncResult.Timeout -> SyncUiState.Failed(
        "The sync server did not respond in time ($message). The report is saved on this phone and has not been lost."
    )

    is SyncResult.ValidationRejected -> SyncUiState.Failed(
        "The sync server rejected this data ($message). The report is saved on this phone and has not been lost."
    )

    is SyncResult.Unauthenticated -> SyncUiState.Failed(
        "This build has no valid backend credential, so nothing was uploaded ($message). " +
            "The report is saved on this phone and has not been lost. Rebuilding with " +
            "SMARTLEASE_API_KEY set is the fix -- retrying will not help."
    )

    is SyncResult.NotAuthorized -> SyncUiState.Failed(
        "The server would not accept this without the token it issued when this report was " +
            "first uploaded ($message). The report and this countersignature are saved on " +
            "this phone and have not been lost."
    )

    is SyncResult.ServerError -> SyncUiState.Failed(
        "Sync server error (HTTP $httpStatusCode: $message). The report is saved on this phone and has not been lost."
    )

    is SyncResult.MalformedResponse -> SyncUiState.Failed(
        "The sync server sent an unexpected response ($message). The report is saved on this phone and has not been lost."
    )

    is SyncResult.TamperConflict -> SyncUiState.TamperConflict(
        "Integrity warning: the server already holds a record for this report with a " +
            "DIFFERENT digest ($message). Do not treat this as confirmed until this is " +
            "investigated -- the report on this phone has not changed."
    )
}
