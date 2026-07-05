package com.syncbridge.core.sync.engine

import com.google.common.truth.Truth.assertThat
import com.syncbridge.core.common.model.ConflictRule
import com.syncbridge.core.common.model.DeleteRule
import com.syncbridge.core.common.model.FileFilterConfig
import com.syncbridge.core.common.model.SyncMode
import com.syncbridge.core.common.model.SyncRunStatus
import com.syncbridge.core.sync.engine.fakes.FakeLocalFileSystem
import com.syncbridge.core.sync.engine.fakes.FakeRemoteFileClient
import com.syncbridge.core.sync.engine.fakes.FakeSyncStateRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SyncExecutorTest {

    @Test
    fun `uploads new local file and records sync state`() = runTest {
        val local = FakeLocalFileSystem(mapOf("photo.jpg" to byteArrayOf(1, 2, 3)))
        val remote = FakeRemoteFileClient()
        val state = FakeSyncStateRepository()
        val executor = SyncExecutor(local, remote, remoteRootPath = "/backup", stateRepository = state)

        val summary = executor.execute(
            profileId = 1,
            mode = SyncMode.ONE_WAY_UPLOAD,
            conflictRule = ConflictRule.KEEP_NEWEST,
            deleteRule = DeleteRule.NEVER_DELETE,
            filter = FileFilterConfig(),
        )

        assertThat(summary.status).isEqualTo(SyncRunStatus.SUCCESS)
        assertThat(summary.filesUploaded).isEqualTo(1)
        assertThat(remote.files).containsKey("backup/photo.jpg")
        assertThat(state.state).containsKey("photo.jpg")
    }

    @Test
    fun `retries a transient upload failure and eventually succeeds`() = runTest {
        val local = FakeLocalFileSystem(mapOf("a.txt" to "hello".toByteArray()))
        val remote = FakeRemoteFileClient().apply { failUploadsUntilAttempt = 2 }
        val state = FakeSyncStateRepository()
        val executor = SyncExecutor(
            local, remote, remoteRootPath = "/", stateRepository = state,
            retryPolicy = RetryPolicy(maxAttempts = 5, baseDelayMillis = 1, maxDelayMillis = 5),
        )

        val summary = executor.execute(
            profileId = 1,
            mode = SyncMode.ONE_WAY_UPLOAD,
            conflictRule = ConflictRule.KEEP_NEWEST,
            deleteRule = DeleteRule.NEVER_DELETE,
            filter = FileFilterConfig(),
        )

        assertThat(summary.status).isEqualTo(SyncRunStatus.SUCCESS)
        assertThat(summary.filesUploaded).isEqualTo(1)
        assertThat(summary.errorsCount).isEqualTo(0)
    }

    @Test
    fun `pauses run and reports PAUSED status when mass deletion guard trips, without deleting anything`() = runTest {
        val trackedFiles = (1..10).associate { "f$it.txt" to "x".toByteArray() }
        val local = FakeLocalFileSystem() // everything gone locally
        val remote = FakeRemoteFileClient(trackedFiles)
        val prior = trackedFiles.keys.associateWith { path ->
            SyncStateEntry(path, localSize = 1, localLastModified = 1, remoteSize = 1, remoteLastModified = 1)
        }
        val state = FakeSyncStateRepository(prior)
        val executor = SyncExecutor(local, remote, remoteRootPath = "/", stateRepository = state)

        val summary = executor.execute(
            profileId = 1,
            mode = SyncMode.TWO_WAY,
            conflictRule = ConflictRule.KEEP_NEWEST,
            deleteRule = DeleteRule.MIRROR_DELETE,
            filter = FileFilterConfig(),
        )

        assertThat(summary.status).isEqualTo(SyncRunStatus.PAUSED)
        assertThat(summary.warnings).hasSize(1)
        assertThat(remote.files).hasSize(10) // nothing was actually deleted
    }

    @Test
    fun `confirmed mass deletion proceeds with the deletions`() = runTest {
        val trackedFiles = (1..10).associate { "f$it.txt" to "x".toByteArray() }
        val local = FakeLocalFileSystem()
        val remote = FakeRemoteFileClient(trackedFiles)
        val prior = trackedFiles.keys.associateWith { path ->
            SyncStateEntry(path, localSize = 1, localLastModified = 1, remoteSize = 1, remoteLastModified = 1)
        }
        val state = FakeSyncStateRepository(prior)
        val executor = SyncExecutor(local, remote, remoteRootPath = "/", stateRepository = state)

        val summary = executor.execute(
            profileId = 1,
            mode = SyncMode.TWO_WAY,
            conflictRule = ConflictRule.KEEP_NEWEST,
            deleteRule = DeleteRule.MIRROR_DELETE,
            filter = FileFilterConfig(),
            confirmedMassDeletion = true,
        )

        assertThat(summary.status).isEqualTo(SyncRunStatus.SUCCESS)
        assertThat(summary.filesDeleted).isEqualTo(10)
        assertThat(remote.files).isEmpty()
    }

    @Test
    fun `dry run computes a plan but performs no transfers`() = runTest {
        val local = FakeLocalFileSystem(mapOf("new.txt" to "content".toByteArray()))
        val remote = FakeRemoteFileClient()
        val state = FakeSyncStateRepository()
        val executor = SyncExecutor(local, remote, remoteRootPath = "/", stateRepository = state)

        val summary = executor.execute(
            profileId = 1,
            mode = SyncMode.ONE_WAY_UPLOAD,
            conflictRule = ConflictRule.KEEP_NEWEST,
            deleteRule = DeleteRule.NEVER_DELETE,
            filter = FileFilterConfig(),
            dryRun = true,
        )

        assertThat(summary.status).isEqualTo(SyncRunStatus.SUCCESS)
        assertThat(remote.files).isEmpty()
        assertThat(state.state).isEmpty()
    }

    @Test
    fun `conflict with keep-both rule creates renamed copies on both sides`() = runTest {
        val local = FakeLocalFileSystem(mapOf("doc.txt" to "local-version".toByteArray()))
        val remote = FakeRemoteFileClient(mapOf("doc.txt" to "remote-version".toByteArray()))
        val prior = mapOf(
            "doc.txt" to SyncStateEntry("doc.txt", localSize = 5, localLastModified = 1, remoteSize = 5, remoteLastModified = 1),
        )
        val state = FakeSyncStateRepository(prior)
        val executor = SyncExecutor(local, remote, remoteRootPath = "/", stateRepository = state)

        executor.execute(
            profileId = 1,
            mode = SyncMode.TWO_WAY,
            conflictRule = ConflictRule.KEEP_BOTH_RENAME,
            deleteRule = DeleteRule.NEVER_DELETE,
            filter = FileFilterConfig(),
        )

        assertThat(remote.files.keys.any { it.startsWith("doc.conflict-local-") }).isTrue()
        assertThat(local.files.keys.any { it.startsWith("doc.conflict-remote-") }).isTrue()
        // Originals are left untouched on both sides.
        assertThat(local.files).containsKey("doc.txt")
        assertThat(remote.files).containsKey("doc.txt")
    }
}
