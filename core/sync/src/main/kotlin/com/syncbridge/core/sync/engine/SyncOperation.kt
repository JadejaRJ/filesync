package com.syncbridge.core.sync.engine

import com.syncbridge.core.common.model.FileTreeSnapshot
import com.syncbridge.core.common.model.SyncOperationType

/**
 * One planned action the transfer engine must execute. [relativePath] is where the data is read from
 * (the existing file on the source side). [destinationPathOverride] is only set when the destination
 * path differs from the source path — currently just the "keep both" conflict resolution, which
 * copies a file to a *renamed* path on the other side while leaving the original untouched on both
 * sides. For every other operation type, the destination path equals [relativePath].
 */
data class SyncOperation(
    val relativePath: String,
    val type: SyncOperationType,
    val localEntry: FileTreeSnapshot.FileEntry? = null,
    val remoteEntry: FileTreeSnapshot.FileEntry? = null,
    val reason: String,
    val requiresConfirmation: Boolean = false,
    val conflictInfo: ConflictInfo? = null,
    val destinationPathOverride: String? = null,
) {
    val destinationPath: String get() = destinationPathOverride ?: relativePath
}

/** Extra context attached to a [SyncOperation] of type [SyncOperationType.CONFLICT]. */
data class ConflictInfo(
    val local: FileTreeSnapshot.FileEntry,
    val remote: FileTreeSnapshot.FileEntry,
    val reason: ConflictReason,
)

enum class ConflictReason {
    /** Both sides changed the file since the last recorded sync. */
    BOTH_MODIFIED,

    /** The file appeared independently on both sides with no prior sync history and differing content. */
    BOTH_CREATED_INDEPENDENTLY,
}

/** The full result of running [SyncPlanner]: what to do, plus any safety warnings that block execution. */
data class SyncPlan(
    val operations: List<SyncOperation>,
    val warnings: List<PlanWarning> = emptyList(),
) {
    val requiresUserConfirmation: Boolean
        get() = warnings.isNotEmpty() || operations.any { it.requiresConfirmation }
}

sealed class PlanWarning {
    data class MassDeletion(
        val deleteCount: Int,
        val totalTracked: Int,
        val percent: Int,
    ) : PlanWarning()
}
