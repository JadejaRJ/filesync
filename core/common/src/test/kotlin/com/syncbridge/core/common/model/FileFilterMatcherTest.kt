package com.syncbridge.core.common.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FileFilterMatcherTest {

    private val defaultConfig = FileFilterConfig()

    @Test
    fun `excludes default temp extensions`() {
        assertThat(FileFilterMatcher.shouldInclude(defaultConfig, "notes/scratch.tmp", 10)).isFalse()
        assertThat(FileFilterMatcher.shouldInclude(defaultConfig, "app.log", 10)).isFalse()
    }

    @Test
    fun `excludes hidden files by default`() {
        assertThat(FileFilterMatcher.shouldInclude(defaultConfig, ".hidden/file.txt", 10)).isFalse()
        assertThat(FileFilterMatcher.shouldInclude(defaultConfig, "docs/.env", 10)).isFalse()
    }

    @Test
    fun `excludes files in excluded folder names`() {
        assertThat(FileFilterMatcher.shouldInclude(defaultConfig, "project/node_modules/pkg/index.js", 10)).isFalse()
        assertThat(FileFilterMatcher.shouldInclude(defaultConfig, "project/.git/HEAD", 10)).isFalse()
    }

    @Test
    fun `excludes files larger than max size`() {
        val config = defaultConfig.copy(maxFileSizeBytes = 100)
        assertThat(FileFilterMatcher.shouldInclude(config, "video.mp4", 200)).isFalse()
        assertThat(FileFilterMatcher.shouldInclude(config, "video.mp4", 50)).isTrue()
    }

    @Test
    fun `include extensions restricts to only those extensions`() {
        val config = defaultConfig.copy(includeExtensions = setOf("jpg", "png"))
        assertThat(FileFilterMatcher.shouldInclude(config, "photo.jpg", 10)).isTrue()
        assertThat(FileFilterMatcher.shouldInclude(config, "photo.PNG", 10)).isTrue()
        assertThat(FileFilterMatcher.shouldInclude(config, "document.pdf", 10)).isFalse()
    }

    @Test
    fun `excludes conflict copies via wildcard pattern`() {
        assertThat(
            FileFilterMatcher.shouldInclude(defaultConfig, "notes.conflict-local-2026-07-05.txt", 10),
        ).isFalse()
    }

    @Test
    fun `wildcard matcher handles star and question mark`() {
        assertThat(FileFilterMatcher.matchesWildcard("photo1.jpg", "photo?.jpg")).isTrue()
        assertThat(FileFilterMatcher.matchesWildcard("photo12.jpg", "photo?.jpg")).isFalse()
        assertThat(FileFilterMatcher.matchesWildcard("archive.tar.gz", "*.gz")).isTrue()
    }

    @Test
    fun `plain files pass through default config`() {
        assertThat(FileFilterMatcher.shouldInclude(defaultConfig, "DCIM/Camera/IMG_0001.jpg", 5_000_000)).isTrue()
    }
}
