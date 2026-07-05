package com.syncbridge.core.sync.engine

import com.google.common.truth.Truth.assertThat
import com.syncbridge.core.common.model.ConflictRule
import com.syncbridge.core.common.model.FileTreeSnapshot
import org.junit.Test

class ConflictResolverTest {

    private val local = FileTreeSnapshot.FileEntry("docs/notes.txt", size = 10, lastModifiedEpochMillis = 5_000, checksum = "AAA")
    private val remote = FileTreeSnapshot.FileEntry("docs/notes.txt", size = 20, lastModifiedEpochMillis = 9_000, checksum = "BBB")
    private val info = ConflictInfo(local, remote, ConflictReason.BOTH_MODIFIED)

    @Test
    fun `ask every time requires user input`() {
        assertThat(ConflictResolver.resolve(info, ConflictRule.ASK_EVERY_TIME, "2026-07-05"))
            .isEqualTo(ConflictResolution.RequiresUserInput)
    }

    @Test
    fun `keep newest picks the later modified time`() {
        assertThat(ConflictResolver.resolve(info, ConflictRule.KEEP_NEWEST, "2026-07-05"))
            .isEqualTo(ConflictResolution.KeepRemote)
    }

    @Test
    fun `keep newest picks local when local is later`() {
        val newerLocal = ConflictInfo(local.copy(lastModifiedEpochMillis = 99_999), remote, ConflictReason.BOTH_MODIFIED)
        assertThat(ConflictResolver.resolve(newerLocal, ConflictRule.KEEP_NEWEST, "2026-07-05"))
            .isEqualTo(ConflictResolution.KeepLocal)
    }

    @Test
    fun `keep both renames with local and remote suffixes preserving extension and directory`() {
        val resolution = ConflictResolver.resolve(info, ConflictRule.KEEP_BOTH_RENAME, "2026-07-05") as ConflictResolution.KeepBoth
        assertThat(resolution.localRenamedPath).isEqualTo("docs/notes.conflict-local-2026-07-05.txt")
        assertThat(resolution.remoteRenamedPath).isEqualTo("docs/notes.conflict-remote-2026-07-05.txt")
    }

    @Test
    fun `compare checksum skips when checksums are equal`() {
        val identical = ConflictInfo(local, remote.copy(checksum = "AAA"), ConflictReason.BOTH_MODIFIED)
        assertThat(ConflictResolver.resolve(identical, ConflictRule.COMPARE_CHECKSUM, "2026-07-05"))
            .isEqualTo(ConflictResolution.Skip)
    }

    @Test
    fun `compare checksum requires user input when checksums differ`() {
        assertThat(ConflictResolver.resolve(info, ConflictRule.COMPARE_CHECKSUM, "2026-07-05"))
            .isEqualTo(ConflictResolution.RequiresUserInput)
    }

    @Test
    fun `skip rule always skips`() {
        assertThat(ConflictResolver.resolve(info, ConflictRule.SKIP, "2026-07-05")).isEqualTo(ConflictResolution.Skip)
    }

    @Test
    fun `keep local and keep remote map directly`() {
        assertThat(ConflictResolver.resolve(info, ConflictRule.KEEP_LOCAL, "2026-07-05")).isEqualTo(ConflictResolution.KeepLocal)
        assertThat(ConflictResolver.resolve(info, ConflictRule.KEEP_REMOTE, "2026-07-05")).isEqualTo(ConflictResolution.KeepRemote)
    }

    @Test
    fun `rename handles a file with no extension`() {
        val noExt = ConflictInfo(
            local.copy(relativePath = "README"),
            remote.copy(relativePath = "README"),
            ConflictReason.BOTH_MODIFIED,
        )
        val (localName, remoteName) = ConflictResolver.renamedConflictPaths(noExt, "2026-07-05")
        assertThat(localName).isEqualTo("README.conflict-local-2026-07-05")
        assertThat(remoteName).isEqualTo("README.conflict-remote-2026-07-05")
    }
}
