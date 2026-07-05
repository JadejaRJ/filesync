package com.syncbridge.core.common.model

/**
 * Include/exclude rules applied per sync profile before a file is considered for transfer. Excludes
 * always win over includes. An empty [includeExtensions] means "no extension restriction" (match all).
 */
data class FileFilterConfig(
    val includeExtensions: Set<String> = emptySet(),
    val excludeExtensions: Set<String> = DEFAULT_EXCLUDED_EXTENSIONS,
    val excludeHiddenFiles: Boolean = true,
    val maxFileSizeBytes: Long? = null,
    val excludeFolderNames: Set<String> = DEFAULT_EXCLUDED_FOLDERS,
    val excludeWildcardPatterns: Set<String> = DEFAULT_EXCLUDED_PATTERNS,
) {
    companion object {
        val DEFAULT_EXCLUDED_EXTENSIONS = setOf("tmp", "log", "uploading", "downloading")
        val DEFAULT_EXCLUDED_FOLDERS = setOf(".git", "node_modules", "cache", "thumbnails", ".thumbnails")
        val DEFAULT_EXCLUDED_PATTERNS = setOf("*.conflict-*")
    }
}

/**
 * Evaluates a [FileFilterConfig] against a relative path (using '/' separators). Pure and stateless
 * so it can be unit tested and shared between the local scanner and the remote scanner.
 */
object FileFilterMatcher {

    /** Returns true if the file at [relativePath] (with [sizeBytes]) should be included in sync. */
    fun shouldInclude(config: FileFilterConfig, relativePath: String, sizeBytes: Long): Boolean {
        val segments = relativePath.split('/').filter { it.isNotEmpty() }
        val fileName = segments.lastOrNull() ?: return false

        if (config.excludeHiddenFiles && segments.any { it.startsWith(".") }) return false

        if (segments.dropLast(1).any { it in config.excludeFolderNames }) return false

        config.maxFileSizeBytes?.let { max ->
            if (sizeBytes > max) return false
        }

        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
        if (extension.isNotEmpty() && extension.lowercase() in config.excludeExtensions.map { it.lowercase() }) return false

        if (config.excludeWildcardPatterns.any { matchesWildcard(fileName, it) }) return false

        if (config.includeExtensions.isNotEmpty()) {
            return extension.isNotEmpty() && extension.lowercase() in config.includeExtensions.map { it.lowercase() }
        }

        return true
    }

    /** Simple shell-style glob matcher supporting '*' (any run of characters) and '?' (single character). */
    fun matchesWildcard(input: String, pattern: String): Boolean {
        val regex = buildString {
            append('^')
            for (c in pattern) {
                when (c) {
                    '*' -> append(".*")
                    '?' -> append(".")
                    else -> append(Regex.escape(c.toString()))
                }
            }
            append('$')
        }.toRegex(RegexOption.IGNORE_CASE)
        return regex.matches(input)
    }
}
