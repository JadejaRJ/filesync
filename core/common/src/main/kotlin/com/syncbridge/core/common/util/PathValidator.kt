package com.syncbridge.core.common.util

import com.syncbridge.core.common.error.AppError

/**
 * Resolves a relative path against a root and rejects anything that would escape it. Used by both
 * protocol clients (remote paths) and the sync engine (local relative paths) to prevent path
 * traversal via "..", absolute-path injection, or null bytes sneaking in from a malicious/corrupted
 * remote listing.
 */
object PathValidator {

    /** Combines [root] and [relativePath] into a normalized path, or throws [AppError.PathTraversalRejected]. */
    fun resolveSafe(root: String, relativePath: String): String {
        if (relativePath.any { it.code == 0 }) {
            throw AppError.PathTraversalRejected(relativePath)
        }
        val normalizedRoot = normalize(root)
        val segments = relativePath.split('/', '\\').filter { it.isNotEmpty() }
        val resolvedSegments = mutableListOf<String>()
        for (segment in segments) {
            when (segment) {
                "." -> continue
                ".." -> {
                    if (resolvedSegments.isEmpty()) {
                        throw AppError.PathTraversalRejected(relativePath)
                    }
                    resolvedSegments.removeAt(resolvedSegments.lastIndex)
                }
                else -> resolvedSegments.add(segment)
            }
        }
        val joined = resolvedSegments.joinToString("/")
        return if (normalizedRoot == "/") "/$joined" else "$normalizedRoot/$joined"
    }

    /** True if [candidate] contains characters invalid on common destination filesystems (FAT32/NTFS/most FTP servers). */
    fun hasInvalidFilenameCharacters(candidate: String): Boolean {
        val invalid = charArrayOf('<', '>', ':', '"', '|', '?', '*')
        return candidate.any { it in invalid } || candidate.any { it.code in 1..31 }
    }

    private fun normalize(path: String): String {
        val trimmed = path.trim().ifEmpty { "/" }
        return if (trimmed.startsWith("/")) trimmed.trimEnd('/').ifEmpty { "/" } else "/$trimmed".trimEnd('/')
    }
}
