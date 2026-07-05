package com.syncbridge.core.common.model

/** One local-folder-to-remote-folder link the user has created and SyncBridge remembers. */
data class SyncProfile(
    val id: Long = 0,
    val name: String,
    val localUri: String,
    val remoteConnectionId: Long,
    val remotePath: String,
    val syncMode: SyncMode,
    val conflictRule: ConflictRule,
    val deleteRule: DeleteRule,
    val scheduleRule: ScheduleRule,
    val networkRule: NetworkRule,
    val isRealtimeEnabled: Boolean = false,
    val isEnabled: Boolean = true,
    val filterConfig: FileFilterConfig = FileFilterConfig(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val lastSyncAt: Long? = null,
    val lastSyncStatus: SyncRunStatus? = null,
    val totalFilesSynced: Long = 0,
    val totalUploadBytes: Long = 0,
    val totalDownloadBytes: Long = 0,
    val totalErrors: Long = 0,
)
