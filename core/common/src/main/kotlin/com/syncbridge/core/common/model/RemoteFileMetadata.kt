package com.syncbridge.core.common.model

/** Metadata for one entry (file or directory) as reported by a remote protocol client. */
data class RemoteFileMetadata(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModifiedEpochMillis: Long,
    val isSymlink: Boolean = false,
)

/** Metadata for one entry (file or directory) discovered while scanning a local SAF tree. */
data class LocalFileMetadata(
    val relativePath: String,
    val uriString: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModifiedEpochMillis: Long,
)

/**
 * A directory snapshot: every file discovered below the sync root, keyed by path relative to that root
 * using '/' separators. Directories are not included as entries; they are implied by file paths.
 */
data class FileTreeSnapshot(
    val entriesByRelativePath: Map<String, FileEntry>,
) {
    data class FileEntry(
        val relativePath: String,
        val size: Long,
        val lastModifiedEpochMillis: Long,
        val checksum: String? = null,
    )

    companion object {
        val EMPTY = FileTreeSnapshot(emptyMap())
    }
}
