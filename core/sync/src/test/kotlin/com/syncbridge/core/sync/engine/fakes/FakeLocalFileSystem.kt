package com.syncbridge.core.sync.engine.fakes

import com.syncbridge.core.common.client.LocalFileSystem
import com.syncbridge.core.common.model.FileFilterConfig
import com.syncbridge.core.common.model.FileTreeSnapshot
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/** In-memory [LocalFileSystem] for exercising [com.syncbridge.core.sync.engine.SyncExecutor] without Android/SAF. */
class FakeLocalFileSystem(
    initialFiles: Map<String, ByteArray> = emptyMap(),
) : LocalFileSystem {
    val files = initialFiles.toMutableMap()
    val modifiedTimes = initialFiles.keys.associateWith { 1_000L }.toMutableMap()
    var permissionValid = true
    var freeSpace = Long.MAX_VALUE / 2

    override suspend fun hasValidPermission() = permissionValid

    override suspend fun scan(filter: FileFilterConfig): FileTreeSnapshot {
        val entries = files.entries.associate { (path, bytes) ->
            path to FileTreeSnapshot.FileEntry(path, bytes.size.toLong(), modifiedTimes[path] ?: 0)
        }
        return FileTreeSnapshot(entries)
    }

    override suspend fun openInputStream(relativePath: String): InputStream =
        ByteArrayInputStream(files[relativePath] ?: error("missing local file $relativePath"))

    override suspend fun openOutputStream(relativePath: String, sizeHintBytes: Long): OutputStream =
        object : ByteArrayOutputStream() {
            override fun close() {
                super.close()
                files[relativePath] = toByteArray()
                modifiedTimes[relativePath] = 2_000L
            }
        }

    override suspend fun delete(relativePath: String, moveToRecycleBin: Boolean) {
        files.remove(relativePath)
    }

    override suspend fun createDirectories(relativeDirPath: String) = Unit

    override suspend fun exists(relativePath: String) = files.containsKey(relativePath)

    override suspend fun sizeOf(relativePath: String): Long? = files[relativePath]?.size?.toLong()

    override suspend fun freeSpaceBytes(): Long = freeSpace
}
