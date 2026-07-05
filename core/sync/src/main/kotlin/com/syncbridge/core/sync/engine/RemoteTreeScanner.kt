package com.syncbridge.core.sync.engine

import com.syncbridge.core.common.client.RemoteFileClient
import com.syncbridge.core.common.model.FileFilterConfig
import com.syncbridge.core.common.model.FileFilterMatcher
import com.syncbridge.core.common.model.FileTreeSnapshot
import com.syncbridge.core.common.model.RemoteFileMetadata

/** Recursively walks a [RemoteFileClient] tree rooted at [rootPath], applying [FileFilterConfig]. */
object RemoteTreeScanner {

    suspend fun scan(client: RemoteFileClient, rootPath: String, filter: FileFilterConfig): FileTreeSnapshot {
        val entries = mutableMapOf<String, FileTreeSnapshot.FileEntry>()
        scanDir(client, rootPath, "", filter, entries)
        return FileTreeSnapshot(entries)
    }

    private suspend fun scanDir(
        client: RemoteFileClient,
        absoluteDirPath: String,
        relativeDirPath: String,
        filter: FileFilterConfig,
        out: MutableMap<String, FileTreeSnapshot.FileEntry>,
    ) {
        val children: List<RemoteFileMetadata> = client.listFiles(absoluteDirPath)
        for (child in children) {
            if (child.isSymlink) {
                // Symlinks are not followed; surfaced as a per-file error by the executor if it ever tries to touch them.
                continue
            }
            val relativePath = if (relativeDirPath.isEmpty()) child.name else "$relativeDirPath/${child.name}"
            if (child.isDirectory) {
                val segments = relativePath.split('/')
                if (segments.any { it in filter.excludeFolderNames }) continue
                scanDir(client, child.path, relativePath, filter, out)
            } else {
                if (!FileFilterMatcher.shouldInclude(filter, relativePath, child.size)) continue
                out[relativePath] = FileTreeSnapshot.FileEntry(relativePath, child.size, child.lastModifiedEpochMillis)
            }
        }
    }
}
