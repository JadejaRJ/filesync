package com.syncbridge.core.sync.engine

import com.google.common.truth.Truth.assertThat
import com.syncbridge.core.common.model.ConflictRule
import com.syncbridge.core.common.model.DeleteRule
import com.syncbridge.core.common.model.FileTreeSnapshot
import com.syncbridge.core.common.model.SyncMode
import com.syncbridge.core.common.model.SyncOperationType
import org.junit.Test

class SyncPlannerTest {

    private fun entry(path: String, size: Long = 100, modified: Long = 1_000) =
        FileTreeSnapshot.FileEntry(path, size, modified)

    private fun snapshot(vararg entries: FileTreeSnapshot.FileEntry) =
        FileTreeSnapshot(entries.associateBy { it.relativePath })

    // ---- One-way upload ----

    @Test
    fun `one-way upload pushes new local file`() {
        val plan = SyncPlanner.plan(
            mode = SyncMode.ONE_WAY_UPLOAD,
            conflictRule = ConflictRule.KEEP_NEWEST,
            deleteRule = DeleteRule.NEVER_DELETE,
            local = snapshot(entry("a.jpg")),
            remote = FileTreeSnapshot.EMPTY,
            priorState = emptyMap(),
        )
        assertThat(plan.operations).hasSize(1)
        assertThat(plan.operations.single().type).isEqualTo(SyncOperationType.UPLOAD)
    }

    @Test
    fun `one-way upload ignores remote-only file when delete rule is never`() {
        val plan = SyncPlanner.plan(
            mode = SyncMode.ONE_WAY_UPLOAD,
            conflictRule = ConflictRule.KEEP_NEWEST,
            deleteRule = DeleteRule.NEVER_DELETE,
            local = FileTreeSnapshot.EMPTY,
            remote = snapshot(entry("remote-only.txt")),
            priorState = emptyMap(),
        )
        assertThat(plan.operations).isEmpty()
    }

    @Test
    fun `one-way upload does not re-upload unchanged file`() {
        val prior = mapOf(
            "a.jpg" to SyncStateEntry("a.jpg", localSize = 100, localLastModified = 1_000, remoteSize = 100, remoteLastModified = 1_000),
        )
        val plan = SyncPlanner.plan(
            mode = SyncMode.ONE_WAY_UPLOAD,
            conflictRule = ConflictRule.KEEP_NEWEST,
            deleteRule = DeleteRule.NEVER_DELETE,
            local = snapshot(entry("a.jpg")),
            remote = snapshot(entry("a.jpg")),
            priorState = prior,
        )
        assertThat(plan.operations).isEmpty()
    }

    @Test
    fun `one-way upload re-uploads locally modified file`() {
        val prior = mapOf(
            "a.jpg" to SyncStateEntry("a.jpg", localSize = 100, localLastModified = 1_000, remoteSize = 100, remoteLastModified = 1_000),
        )
        val plan = SyncPlanner.plan(
            mode = SyncMode.ONE_WAY_UPLOAD,
            conflictRule = ConflictRule.KEEP_NEWEST,
            deleteRule = DeleteRule.NEVER_DELETE,
            local = snapshot(entry("a.jpg", size = 200, modified = 2_000)),
            remote = snapshot(entry("a.jpg")),
            priorState = prior,
        )
        assertThat(plan.operations).hasSize(1)
        assertThat(plan.operations.single().type).isEqualTo(SyncOperationType.UPLOAD)
    }

    @Test
    fun `one-way upload deletes remote file removed locally when mirror delete enabled`() {
        val prior = mapOf(
            "a.jpg" to SyncStateEntry("a.jpg", localSize = 100, localLastModified = 1_000, remoteSize = 100, remoteLastModified = 1_000),
        )
        val plan = SyncPlanner.plan(
            mode = SyncMode.ONE_WAY_UPLOAD,
            conflictRule = ConflictRule.KEEP_NEWEST,
            deleteRule = DeleteRule.MIRROR_DELETE,
            local = FileTreeSnapshot.EMPTY,
            remote = snapshot(entry("a.jpg")),
            priorState = prior,
        )
        assertThat(plan.operations).hasSize(1)
        assertThat(plan.operations.single().type).isEqualTo(SyncOperationType.DELETE_REMOTE)
    }

    // ---- Mirror ----

    @Test
    fun `mirror local to remote deletes extraneous remote-only file with no prior history`() {
        val plan = SyncPlanner.plan(
            mode = SyncMode.MIRROR_LOCAL_TO_REMOTE,
            conflictRule = ConflictRule.KEEP_NEWEST,
            deleteRule = DeleteRule.NEVER_DELETE, // ignored: mirror forces delete propagation
            local = FileTreeSnapshot.EMPTY,
            remote = snapshot(entry("extra.txt")),
            priorState = emptyMap(),
        )
        assertThat(plan.operations).hasSize(1)
        assertThat(plan.operations.single().type).isEqualTo(SyncOperationType.DELETE_REMOTE)
    }

    // ---- Two-way ----

    @Test
    fun `two-way uploads new local file`() {
        val plan = SyncPlanner.plan(
            mode = SyncMode.TWO_WAY,
            conflictRule = ConflictRule.KEEP_NEWEST,
            deleteRule = DeleteRule.NEVER_DELETE,
            local = snapshot(entry("new.txt")),
            remote = FileTreeSnapshot.EMPTY,
            priorState = emptyMap(),
        )
        assertThat(plan.operations.single().type).isEqualTo(SyncOperationType.UPLOAD)
    }

    @Test
    fun `two-way downloads new remote file`() {
        val plan = SyncPlanner.plan(
            mode = SyncMode.TWO_WAY,
            conflictRule = ConflictRule.KEEP_NEWEST,
            deleteRule = DeleteRule.NEVER_DELETE,
            local = FileTreeSnapshot.EMPTY,
            remote = snapshot(entry("new.txt")),
            priorState = emptyMap(),
        )
        assertThat(plan.operations.single().type).isEqualTo(SyncOperationType.DOWNLOAD)
    }

    @Test
    fun `two-way treats file missing locally with known prior local state as a local deletion to propagate`() {
        val prior = mapOf(
            "gone.txt" to SyncStateEntry("gone.txt", localSize = 10, localLastModified = 1, remoteSize = 10, remoteLastModified = 1),
        )
        val plan = SyncPlanner.plan(
            mode = SyncMode.TWO_WAY,
            conflictRule = ConflictRule.KEEP_NEWEST,
            deleteRule = DeleteRule.MIRROR_DELETE,
            local = FileTreeSnapshot.EMPTY,
            remote = snapshot(entry("gone.txt", size = 10, modified = 1)),
            priorState = prior,
        )
        assertThat(plan.operations.single().type).isEqualTo(SyncOperationType.DELETE_REMOTE)
    }

    @Test
    fun `two-way does not resurrect a file deleted on both sides`() {
        val prior = mapOf(
            "gone.txt" to SyncStateEntry("gone.txt", localSize = 10, localLastModified = 1, remoteSize = 10, remoteLastModified = 1),
        )
        val plan = SyncPlanner.plan(
            mode = SyncMode.TWO_WAY,
            conflictRule = ConflictRule.KEEP_NEWEST,
            deleteRule = DeleteRule.MIRROR_DELETE,
            local = FileTreeSnapshot.EMPTY,
            remote = FileTreeSnapshot.EMPTY,
            priorState = prior,
        )
        assertThat(plan.operations).isEmpty()
    }

    @Test
    fun `two-way detects conflict when both sides changed since last sync`() {
        val prior = mapOf(
            "doc.txt" to SyncStateEntry("doc.txt", localSize = 10, localLastModified = 1, remoteSize = 10, remoteLastModified = 1),
        )
        val plan = SyncPlanner.plan(
            mode = SyncMode.TWO_WAY,
            conflictRule = ConflictRule.ASK_EVERY_TIME,
            deleteRule = DeleteRule.NEVER_DELETE,
            local = snapshot(entry("doc.txt", size = 20, modified = 5)),
            remote = snapshot(entry("doc.txt", size = 30, modified = 5)),
            priorState = prior,
        )
        val op = plan.operations.single()
        assertThat(op.type).isEqualTo(SyncOperationType.CONFLICT)
        assertThat(op.requiresConfirmation).isTrue()
        assertThat(op.conflictInfo?.reason).isEqualTo(ConflictReason.BOTH_MODIFIED)
    }

    @Test
    fun `two-way flags independently created files with same name and different content as conflict`() {
        val plan = SyncPlanner.plan(
            mode = SyncMode.TWO_WAY,
            conflictRule = ConflictRule.KEEP_NEWEST,
            deleteRule = DeleteRule.NEVER_DELETE,
            local = snapshot(entry("both-new.txt", size = 10)),
            remote = snapshot(entry("both-new.txt", size = 999)),
            priorState = emptyMap(),
        )
        val op = plan.operations.single()
        assertThat(op.type).isEqualTo(SyncOperationType.CONFLICT)
        assertThat(op.conflictInfo?.reason).isEqualTo(ConflictReason.BOTH_CREATED_INDEPENDENTLY)
    }

    @Test
    fun `two-way skips when both sides created the same file with identical content`() {
        val plan = SyncPlanner.plan(
            mode = SyncMode.TWO_WAY,
            conflictRule = ConflictRule.KEEP_NEWEST,
            deleteRule = DeleteRule.NEVER_DELETE,
            local = snapshot(entry("same.txt", size = 10, modified = 1)),
            remote = snapshot(entry("same.txt", size = 10, modified = 1)),
            priorState = emptyMap(),
        )
        assertThat(plan.operations).isEmpty()
    }

    @Test
    fun `two-way skips unchanged file present on both sides`() {
        val prior = mapOf(
            "stable.txt" to SyncStateEntry("stable.txt", localSize = 10, localLastModified = 1, remoteSize = 10, remoteLastModified = 1),
        )
        val plan = SyncPlanner.plan(
            mode = SyncMode.TWO_WAY,
            conflictRule = ConflictRule.KEEP_NEWEST,
            deleteRule = DeleteRule.NEVER_DELETE,
            local = snapshot(entry("stable.txt", size = 10, modified = 1)),
            remote = snapshot(entry("stable.txt", size = 10, modified = 1)),
            priorState = prior,
        )
        assertThat(plan.operations).isEmpty()
    }

    // ---- Mass deletion guard ----

    @Test
    fun `mass deletion guard trips when deletions exceed the percent threshold`() {
        val prior = (1..10).associate { i ->
            "f$i.txt" to SyncStateEntry("f$i.txt", localSize = 1, localLastModified = 1, remoteSize = 1, remoteLastModified = 1)
        }
        // All 10 tracked files vanish locally (100% > 20% threshold) while remote still has them.
        val plan = SyncPlanner.plan(
            mode = SyncMode.TWO_WAY,
            conflictRule = ConflictRule.KEEP_NEWEST,
            deleteRule = DeleteRule.MIRROR_DELETE,
            local = FileTreeSnapshot.EMPTY,
            remote = snapshot(*prior.keys.map { entry(it, size = 1, modified = 1) }.toTypedArray()),
            priorState = prior,
        )
        assertThat(plan.warnings).hasSize(1)
        val warning = plan.warnings.single() as PlanWarning.MassDeletion
        assertThat(warning.deleteCount).isEqualTo(10)
        assertThat(plan.operations.all { it.requiresConfirmation }).isTrue()
    }

    @Test
    fun `mass deletion guard does not trip for a small number of deletions`() {
        val prior = (1..10).associate { i ->
            "f$i.txt" to SyncStateEntry("f$i.txt", localSize = 1, localLastModified = 1, remoteSize = 1, remoteLastModified = 1)
        }
        // Only f1.txt disappears locally (1 of 10 tracked = 10%), the rest are untouched on both sides.
        val plan = SyncPlanner.plan(
            mode = SyncMode.TWO_WAY,
            conflictRule = ConflictRule.KEEP_NEWEST,
            deleteRule = DeleteRule.MIRROR_DELETE,
            local = snapshot(*(2..10).map { "f$it.txt" }.map { entry(it, size = 1, modified = 1) }.toTypedArray()),
            remote = snapshot(*(1..10).map { "f$it.txt" }.map { entry(it, size = 1, modified = 1) }.toTypedArray()),
            priorState = prior,
        )
        assertThat(plan.warnings).isEmpty()
        assertThat(plan.operations).hasSize(1)
        assertThat(plan.operations.single().type).isEqualTo(SyncOperationType.DELETE_REMOTE)
    }
}
