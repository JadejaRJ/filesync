package com.syncbridge.app.sync

/** Snapshot the Live Sync screen and the foreground-service notification both render. */
data class LiveSyncState(
    val profileId: Long,
    val profileName: String,
    val currentOperationLabel: String,
    val totalOperations: Int,
    val filesCompleted: Int = 0,
    val currentFile: String? = null,
    val currentFileBytesDone: Long = 0,
    val currentFileBytesTotal: Long = 0,
    val errors: Int = 0,
    val bytesUploaded: Long = 0,
    val bytesDownloaded: Long = 0,
    val isPaused: Boolean = false,
    val awaitingMassDeletionConfirmation: Boolean = false,
) {
    val progressFraction: Float
        get() = if (totalOperations == 0) 1f else filesCompleted.toFloat() / totalOperations
}
