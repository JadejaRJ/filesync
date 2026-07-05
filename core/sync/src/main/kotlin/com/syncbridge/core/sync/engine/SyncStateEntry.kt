package com.syncbridge.core.sync.engine

/**
 * One row of the persisted `sync_state` table: what the engine knew about a given relative path
 * the last time it successfully synced a profile. This is the "third point of reference" that lets
 * two-way sync tell "file was deleted on one side" apart from "file is new on the other side" instead
 * of naively diffing local vs remote snapshots.
 *
 * A null [localSize]/[localLastModified] means the local side did not have this file at last sync
 * (either it never existed locally, or a prior deletion was reconciled and the row cleared). Same for
 * the remote fields.
 */
data class SyncStateEntry(
    val relativePath: String,
    val localSize: Long? = null,
    val localLastModified: Long? = null,
    val remoteSize: Long? = null,
    val remoteLastModified: Long? = null,
    val checksum: String? = null,
) {
    val knownLocal: Boolean get() = localSize != null
    val knownRemote: Boolean get() = remoteSize != null
}
