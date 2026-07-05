package com.syncbridge.core.sync.engine

import com.syncbridge.core.common.model.ConflictRule
import com.syncbridge.core.common.model.DeleteRule
import com.syncbridge.core.common.model.FileTreeSnapshot
import com.syncbridge.core.common.model.SyncMode
import com.syncbridge.core.common.model.SyncOperationType
import kotlin.math.roundToInt

/**
 * The heart of the sync engine (see spec section 19 / 6). Pure function of three snapshots — never
 * touches the filesystem, network, or database — so it is fully unit-testable and reused by both the
 * dry-run/preview flow and the real execution flow (dry-run simply doesn't execute the resulting plan).
 *
 * Two-way sync deliberately does *not* compare local-vs-remote timestamps directly. A file missing
 * from one side is ambiguous by itself: it could be new-on-the-other-side, or deleted-on-this-side.
 * [priorState] (the last successful sync's per-file record) disambiguates the two: if the row shows
 * the file was known on the side where it is now missing, that's a deletion to propagate (subject to
 * [DeleteRule]); if the row has no record of it, it's a fresh file to push to the other side.
 */
object SyncPlanner {

    private const val MASS_DELETE_ABSOLUTE_THRESHOLD = 20
    private const val MASS_DELETE_PERCENT_THRESHOLD = 0.20

    fun plan(
        mode: SyncMode,
        conflictRule: ConflictRule,
        deleteRule: DeleteRule,
        local: FileTreeSnapshot,
        remote: FileTreeSnapshot,
        priorState: Map<String, SyncStateEntry>,
    ): SyncPlan {
        val operations = when (mode) {
            SyncMode.ONE_WAY_UPLOAD -> planOneWay(
                source = local, sourceIsLocal = true, destination = remote,
                priorState = priorState, deleteRule = deleteRule, forceMirror = false,
            )
            SyncMode.ONE_WAY_DOWNLOAD -> planOneWay(
                source = remote, sourceIsLocal = false, destination = local,
                priorState = priorState, deleteRule = deleteRule, forceMirror = false,
            )
            SyncMode.MIRROR_LOCAL_TO_REMOTE -> planOneWay(
                source = local, sourceIsLocal = true, destination = remote,
                priorState = priorState, deleteRule = DeleteRule.MIRROR_DELETE, forceMirror = true,
            )
            SyncMode.MIRROR_REMOTE_TO_LOCAL -> planOneWay(
                source = remote, sourceIsLocal = false, destination = local,
                priorState = priorState, deleteRule = DeleteRule.MIRROR_DELETE, forceMirror = true,
            )
            SyncMode.TWO_WAY -> planTwoWay(local, remote, priorState, conflictRule, deleteRule)
        }

        val warnings = massDeletionWarnings(operations, priorState)
        val flagged = if (warnings.isNotEmpty()) {
            operations.map { op ->
                if (op.type == SyncOperationType.DELETE_LOCAL || op.type == SyncOperationType.DELETE_REMOTE) {
                    op.copy(requiresConfirmation = true)
                } else {
                    op
                }
            }
        } else {
            operations
        }
        return SyncPlan(flagged, warnings)
    }

    // ---- One-way (and mirror, which is one-way + forced delete propagation) --------------------

    private fun planOneWay(
        source: FileTreeSnapshot,
        sourceIsLocal: Boolean,
        destination: FileTreeSnapshot,
        priorState: Map<String, SyncStateEntry>,
        deleteRule: DeleteRule,
        forceMirror: Boolean,
    ): List<SyncOperation> {
        val paths = source.entriesByRelativePath.keys + destination.entriesByRelativePath.keys + priorState.keys
        val ops = mutableListOf<SyncOperation>()
        val pushType = if (sourceIsLocal) SyncOperationType.UPLOAD else SyncOperationType.DOWNLOAD
        val deleteAtDestinationType = if (sourceIsLocal) SyncOperationType.DELETE_REMOTE else SyncOperationType.DELETE_LOCAL

        for (path in paths) {
            val srcEntry = source.entriesByRelativePath[path]
            val destEntry = destination.entriesByRelativePath[path]
            val prior = priorState[path]
            val priorKnewSource = if (sourceIsLocal) prior?.knownLocal == true else prior?.knownRemote == true

            if (srcEntry != null) {
                if (destEntry == null) {
                    ops += SyncOperation(path, pushType, reason = "New or missing-at-destination file from source")
                } else {
                    val priorSourceEntry = priorSourceSnapshot(prior, sourceIsLocal)
                    val unchangedSinceLastSync = priorSourceEntry != null && !contentDiffers(srcEntry, priorSourceEntry)
                    val identicalToDestination = !contentDiffers(srcEntry, destEntry)
                    when {
                        unchangedSinceLastSync -> Unit // already in sync per last known state; skip
                        identicalToDestination -> Unit // coincidentally identical; nothing to transfer
                        else -> ops += SyncOperation(path, pushType, reason = "Source file changed since last sync")
                    }
                }
            } else {
                // Missing at source.
                if (priorKnewSource) {
                    // It existed at source before and is now gone: propagate deletion per delete rule.
                    if (destEntry != null) {
                        when (deleteRule) {
                            DeleteRule.NEVER_DELETE -> Unit
                            DeleteRule.MIRROR_DELETE -> ops += SyncOperation(
                                path, deleteAtDestinationType,
                                reason = if (forceMirror) "Mirror: source no longer has this file" else "Delete rule: mirror delete",
                            )
                            DeleteRule.RECYCLE_BIN -> ops += SyncOperation(
                                path, deleteAtDestinationType, reason = "Source deleted; moving destination copy to recycle bin",
                            )
                            DeleteRule.ASK_BEFORE_DELETE -> ops += SyncOperation(
                                path, deleteAtDestinationType, reason = "Source deleted", requiresConfirmation = true,
                            )
                        }
                    }
                } else if (forceMirror && destEntry != null) {
                    // Destination-only file with no source history: not part of the mirror's source of truth.
                    ops += SyncOperation(path, deleteAtDestinationType, reason = "Mirror: file not present at source")
                }
                // Non-mirror one-way sync: destination-only files with no source history are left alone.
            }
        }
        return ops
    }

    private fun priorSourceSnapshot(prior: SyncStateEntry?, sourceIsLocal: Boolean): FileTreeSnapshot.FileEntry? {
        if (prior == null) return null
        val size = if (sourceIsLocal) prior.localSize else prior.remoteSize
        val modified = if (sourceIsLocal) prior.localLastModified else prior.remoteLastModified
        if (size == null || modified == null) return null
        return FileTreeSnapshot.FileEntry(prior.relativePath, size, modified, prior.checksum)
    }

    // ---- Two-way ---------------------------------------------------------------------------------

    private fun planTwoWay(
        local: FileTreeSnapshot,
        remote: FileTreeSnapshot,
        priorState: Map<String, SyncStateEntry>,
        conflictRule: ConflictRule,
        deleteRule: DeleteRule,
    ): List<SyncOperation> {
        val paths = local.entriesByRelativePath.keys + remote.entriesByRelativePath.keys + priorState.keys
        val ops = mutableListOf<SyncOperation>()

        for (path in paths) {
            val localEntry = local.entriesByRelativePath[path]
            val remoteEntry = remote.entriesByRelativePath[path]
            val prior = priorState[path]
            val priorLocal = prior?.takeIf { it.knownLocal }
                ?.let { FileTreeSnapshot.FileEntry(path, it.localSize!!, it.localLastModified!!, it.checksum) }
            val priorRemote = prior?.takeIf { it.knownRemote }
                ?.let { FileTreeSnapshot.FileEntry(path, it.remoteSize!!, it.remoteLastModified!!, it.checksum) }

            when {
                localEntry != null && remoteEntry != null -> {
                    if (prior == null) {
                        if (contentDiffers(localEntry, remoteEntry)) {
                            ops += conflictOperation(path, localEntry, remoteEntry, ConflictReason.BOTH_CREATED_INDEPENDENTLY, conflictRule)
                        } // else: identical content, nothing to do; state will be recorded as in sync.
                        continue
                    }
                    val localChanged = priorLocal == null || contentDiffers(localEntry, priorLocal)
                    val remoteChanged = priorRemote == null || contentDiffers(remoteEntry, priorRemote)
                    when {
                        !localChanged && !remoteChanged -> Unit
                        localChanged && !remoteChanged -> ops += SyncOperation(path, SyncOperationType.UPLOAD, localEntry, remoteEntry, "Local change detected")
                        !localChanged && remoteChanged -> ops += SyncOperation(path, SyncOperationType.DOWNLOAD, localEntry, remoteEntry, "Remote change detected")
                        else -> {
                            if (contentDiffers(localEntry, remoteEntry)) {
                                ops += conflictOperation(path, localEntry, remoteEntry, ConflictReason.BOTH_MODIFIED, conflictRule)
                            }
                        }
                    }
                }

                localEntry != null && remoteEntry == null -> {
                    val remoteHadIt = prior?.knownRemote == true
                    if (remoteHadIt) {
                        applyDelete(ops, path, SyncOperationType.DELETE_LOCAL, deleteRule, "Deleted on remote")
                    } else {
                        ops += SyncOperation(path, SyncOperationType.UPLOAD, localEntry, null, "New local file")
                    }
                }

                localEntry == null && remoteEntry != null -> {
                    val localHadIt = prior?.knownLocal == true
                    if (localHadIt) {
                        applyDelete(ops, path, SyncOperationType.DELETE_REMOTE, deleteRule, "Deleted locally")
                    } else {
                        ops += SyncOperation(path, SyncOperationType.DOWNLOAD, null, remoteEntry, "New remote file")
                    }
                }

                else -> Unit // both null: stale prior-state row only, nothing to do (state will be pruned)
            }
        }
        return ops
    }

    private fun applyDelete(
        ops: MutableList<SyncOperation>,
        path: String,
        type: SyncOperationType,
        deleteRule: DeleteRule,
        reason: String,
    ) {
        when (deleteRule) {
            DeleteRule.NEVER_DELETE -> Unit
            DeleteRule.MIRROR_DELETE -> ops += SyncOperation(path, type, reason = reason)
            DeleteRule.RECYCLE_BIN -> ops += SyncOperation(path, type, reason = "$reason (recycle bin)")
            DeleteRule.ASK_BEFORE_DELETE -> ops += SyncOperation(path, type, reason = reason, requiresConfirmation = true)
        }
    }

    private fun conflictOperation(
        path: String,
        localEntry: FileTreeSnapshot.FileEntry,
        remoteEntry: FileTreeSnapshot.FileEntry,
        reason: ConflictReason,
        conflictRule: ConflictRule,
    ): SyncOperation {
        val requiresConfirmation = conflictRule == ConflictRule.ASK_EVERY_TIME
        return SyncOperation(
            relativePath = path,
            type = SyncOperationType.CONFLICT,
            localEntry = localEntry,
            remoteEntry = remoteEntry,
            reason = "File changed on both sides",
            requiresConfirmation = requiresConfirmation,
            conflictInfo = ConflictInfo(localEntry, remoteEntry, reason),
        )
    }

    /** Two entries are "the same" if size, modified time, and (when both present) checksum all agree. */
    private fun contentDiffers(a: FileTreeSnapshot.FileEntry, b: FileTreeSnapshot.FileEntry): Boolean {
        if (a.checksum != null && b.checksum != null) return a.checksum != b.checksum
        return a.size != b.size || a.lastModifiedEpochMillis != b.lastModifiedEpochMillis
    }

    private fun massDeletionWarnings(
        operations: List<SyncOperation>,
        priorState: Map<String, SyncStateEntry>,
    ): List<PlanWarning> {
        val deleteCount = operations.count {
            it.type == SyncOperationType.DELETE_LOCAL || it.type == SyncOperationType.DELETE_REMOTE
        }
        if (deleteCount == 0) return emptyList()
        val totalTracked = maxOf(priorState.size, 1)
        val percent = (deleteCount.toDouble() / totalTracked * 100).roundToInt()
        val exceedsAbsolute = deleteCount > MASS_DELETE_ABSOLUTE_THRESHOLD
        val exceedsPercent = deleteCount.toDouble() / totalTracked > MASS_DELETE_PERCENT_THRESHOLD
        return if (exceedsAbsolute || exceedsPercent) {
            listOf(PlanWarning.MassDeletion(deleteCount, totalTracked, percent))
        } else {
            emptyList()
        }
    }
}
