package com.syncbridge.core.common.client

import com.syncbridge.core.common.model.FileFilterConfig
import com.syncbridge.core.common.model.FileTreeSnapshot
import java.io.InputStream
import java.io.OutputStream

/**
 * Abstraction over the local side of a sync profile's linked folder. The real implementation (in the
 * `:app` module) is backed by Android's Storage Access Framework (a persisted tree [android.net.Uri]);
 * it never assumes a raw filesystem path is available, since Android does not guarantee one for
 * SAF-selected folders. All paths here are relative to the linked root, using '/' separators.
 */
interface LocalFileSystem {

    /** True if the app still holds a valid, persisted permission grant for the linked folder. */
    suspend fun hasValidPermission(): Boolean

    /** Recursively scans the linked folder, applying [filter], and returns a snapshot for [SyncPlanner]. */
    suspend fun scan(filter: FileFilterConfig): FileTreeSnapshot

    suspend fun openInputStream(relativePath: String): InputStream

    /** Opens a stream for writing; implementations should write to a temp sibling and rename on close/verify. */
    suspend fun openOutputStream(relativePath: String, sizeHintBytes: Long): OutputStream

    suspend fun delete(relativePath: String, moveToRecycleBin: Boolean)

    suspend fun createDirectories(relativeDirPath: String)

    suspend fun exists(relativePath: String): Boolean

    suspend fun sizeOf(relativePath: String): Long?

    /** Bytes free at the underlying storage volume, used for the storage-full pre-check. */
    suspend fun freeSpaceBytes(): Long
}
