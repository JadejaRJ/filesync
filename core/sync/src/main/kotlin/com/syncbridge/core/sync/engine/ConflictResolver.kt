package com.syncbridge.core.sync.engine

import com.syncbridge.core.common.model.ConflictRule

/** What the executor should actually do about a [ConflictInfo], derived from the profile's [ConflictRule]. */
sealed class ConflictResolution {
    /** Overwrite the remote copy with the local one. */
    data object KeepLocal : ConflictResolution()

    /** Overwrite the local copy with the remote one. */
    data object KeepRemote : ConflictResolution()

    /** Upload/download both under renamed paths so no data is lost. */
    data class KeepBoth(val localRenamedPath: String, val remoteRenamedPath: String) : ConflictResolution()

    /** Leave both sides untouched; the conflict is recorded but not acted on. */
    data object Skip : ConflictResolution()

    /** The engine cannot decide automatically; surface this file in the Conflicts UI and pause it. */
    data object RequiresUserInput : ConflictResolution()
}

/**
 * Turns a detected conflict (see [SyncPlanner]) into a concrete action per the profile's [ConflictRule].
 * Pure and stateless: the caller supplies [dateStamp] (rather than reading the system clock here) so
 * resolution is deterministic and unit-testable.
 */
object ConflictResolver {

    fun resolve(conflictInfo: ConflictInfo, rule: ConflictRule, dateStamp: String): ConflictResolution {
        return when (rule) {
            ConflictRule.ASK_EVERY_TIME -> ConflictResolution.RequiresUserInput
            ConflictRule.SKIP -> ConflictResolution.Skip
            ConflictRule.KEEP_LOCAL -> ConflictResolution.KeepLocal
            ConflictRule.KEEP_REMOTE -> ConflictResolution.KeepRemote
            ConflictRule.KEEP_NEWEST -> {
                if (conflictInfo.local.lastModifiedEpochMillis >= conflictInfo.remote.lastModifiedEpochMillis) {
                    ConflictResolution.KeepLocal
                } else {
                    ConflictResolution.KeepRemote
                }
            }
            ConflictRule.KEEP_BOTH_RENAME -> {
                val (localName, remoteName) = renamedConflictPaths(conflictInfo, dateStamp)
                ConflictResolution.KeepBoth(localName, remoteName)
            }
            ConflictRule.COMPARE_CHECKSUM -> {
                val localSum = conflictInfo.local.checksum
                val remoteSum = conflictInfo.remote.checksum
                if (localSum != null && remoteSum != null && localSum == remoteSum) {
                    ConflictResolution.Skip // content is actually identical; nothing to do
                } else {
                    ConflictResolution.RequiresUserInput // genuinely different content; needs a human
                }
            }
        }
    }

    /**
     * Builds `name.conflict-local-<date>.ext` / `name.conflict-remote-<date>.ext` alongside the
     * original path, preserving any directory prefix, per the naming scheme in spec section 8.
     */
    fun renamedConflictPaths(conflictInfo: ConflictInfo, dateStamp: String): Pair<String, String> {
        val path = conflictInfoPath(conflictInfo)
        val dir = path.substringBeforeLast('/', missingDelimiterValue = "")
        val fileName = path.substringAfterLast('/')
        val base = fileName.substringBeforeLast('.', missingDelimiterValue = fileName)
        val ext = fileName.substringAfterLast('.', missingDelimiterValue = "")
        val suffix = if (ext.isNotEmpty() && ext != fileName) ".$ext" else ""

        fun build(tag: String) = "$base.conflict-$tag-$dateStamp$suffix"

        val localName = build("local")
        val remoteName = build("remote")
        return if (dir.isEmpty()) {
            localName to remoteName
        } else {
            "$dir/$localName" to "$dir/$remoteName"
        }
    }

    private fun conflictInfoPath(conflictInfo: ConflictInfo): String =
        conflictInfo.local.relativePath.ifEmpty { conflictInfo.remote.relativePath }
}
