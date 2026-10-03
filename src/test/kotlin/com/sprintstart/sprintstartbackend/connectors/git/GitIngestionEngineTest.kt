package com.sprintstart.sprintstartbackend.connectors.git

import com.sprintstart.sprintstartbackend.connectors.git.utils.GitCommitSink
import com.sprintstart.sprintstartbackend.connectors.git.utils.GitFileChange
import com.sprintstart.sprintstartbackend.connectors.git.utils.GitFileSink
import com.sprintstart.sprintstartbackend.connectors.git.utils.GitIngestionEngine
import com.sprintstart.sprintstartbackend.shared.git.GitChangeSet
import com.sprintstart.sprintstartbackend.shared.git.GitCommit
import com.sprintstart.sprintstartbackend.shared.git.GitConfig
import com.sprintstart.sprintstartbackend.shared.git.GitFileRead
import com.sprintstart.sprintstartbackend.shared.git.GitIngestConfig
import com.sprintstart.sprintstartbackend.shared.git.GitLog
import com.sprintstart.sprintstartbackend.shared.git.GitRepositoryCache
import com.sprintstart.sprintstartbackend.shared.git.GitRepositoryCoordinates
import com.sprintstart.sprintstartbackend.shared.git.GitRevisionState
import com.sprintstart.sprintstartbackend.shared.git.GitWorkingTree
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class GitIngestionEngineTest {
    @TempDir
    lateinit var repositoryPath: Path

    private val repositoryCache = mockk<GitRepositoryCache>()
    private val revisionState = mockk<GitRevisionState>()
    private val workingTree = mockk<GitWorkingTree>()
    private val changeSet = mockk<GitChangeSet>()
    private val gitLog = mockk<GitLog>()
    private val sink = mockk<GitFileSink>(relaxed = true)

    private val coordinates = GitRepositoryCoordinates(
        host = "github.com",
        namespace = "owner",
        name = "repo",
        username = "x-access-token",
        secret = "token",
    )

    private fun engine(batchSize: Int = 32) = GitIngestionEngine(
        repositoryCache = repositoryCache,
        revisionState = revisionState,
        workingTree = workingTree,
        changeSet = changeSet,
        gitLog = gitLog,
        config = GitConfig(ingest = GitIngestConfig(batchSize = batchSize)),
    )

    @BeforeEach
    fun setUp() {
        coEvery { repositoryCache.getLocalRepositoryPath(coordinates) } returns repositoryPath
        coEvery { revisionState.knowsRevision(any(), any()) } returns true
    }

    /** A tracked file that exists on disk, so the engine sees it as present. */
    private fun existingFile(relativePath: String) {
        Files.writeString(repositoryPath.resolve(relativePath), "content")
    }

    // ── full ingest ───────────────────────────────────────────────────────────

    @Test
    fun `reports every tracked file and returns the ingested revision`() = runTest {
        existingFile("Main.kt")
        coEvery { revisionState.updateLocal(repositoryPath) } returns REVISION
        coEvery { workingTree.trackedFiles(repositoryPath) } returns listOf("Main.kt")
        coEvery { workingTree.readFile(repositoryPath, "Main.kt") } returns GitFileRead.Text("fun main() {}", "hash")

        val outcome = engine().ingestWorkingTree(coordinates, sink)

        assertThat(outcome.revision).isEqualTo(REVISION)
        assertThat(outcome.complete).isTrue()
        coVerify {
            sink.onBatch(
                match { batch ->
                    batch.single().let { it is GitFileChange.Modified && it.relativePath == "Main.kt" }
                },
            )
        }
    }

    @Test
    fun `reads a file through the shared hash rather than hashing content again`() = runTest {
        existingFile("Main.kt")
        coEvery { revisionState.updateLocal(any()) } returns REVISION
        coEvery { workingTree.trackedFiles(any()) } returns listOf("Main.kt")
        coEvery { workingTree.readFile(any(), "Main.kt") } returns GitFileRead.Text("text", "given-hash")

        engine().ingestWorkingTree(coordinates, sink)

        coVerify {
            sink.onBatch(
                match { (it.single() as GitFileChange.Modified).sha256 == "given-hash" },
            )
        }
    }

    @Test
    fun `skips binary files without reporting them as changes or failures`() = runTest {
        existingFile("logo.png")
        coEvery { revisionState.updateLocal(any()) } returns REVISION
        coEvery { workingTree.trackedFiles(any()) } returns listOf("logo.png")
        coEvery { workingTree.readFile(any(), "logo.png") } returns GitFileRead.Binary

        val outcome = engine().ingestWorkingTree(coordinates, sink)

        assertThat(outcome.complete).isTrue()
        coVerify(exactly = 0) { sink.onBatch(any()) }
        coVerify(exactly = 0) { sink.onFailure(any(), any()) }
    }

    @Test
    fun `reports a file it could not read and still completes the rest`() = runTest {
        existingFile("good.kt")
        existingFile("big.json")
        coEvery { revisionState.updateLocal(any()) } returns REVISION
        coEvery { workingTree.trackedFiles(any()) } returns listOf("good.kt", "big.json")
        coEvery { workingTree.readFile(any(), "good.kt") } returns GitFileRead.Text("x", "hash")
        coEvery { workingTree.readFile(any(), "big.json") } returns GitFileRead.TooLarge(900)

        val outcome = engine().ingestWorkingTree(coordinates, sink)

        assertThat(outcome.complete).isFalse()
        assertThat(outcome.failures.single().relativePath).isEqualTo("big.json")
        coVerify { sink.onFailure("big.json", any()) }
        coVerify(exactly = 1) { sink.onBatch(any()) }
    }

    @Test
    fun `hands changes over in batches of the configured size`() = runTest {
        val paths = (1..5).map { "File$it.kt" }
        paths.forEach(::existingFile)
        coEvery { revisionState.updateLocal(any()) } returns REVISION
        coEvery { workingTree.trackedFiles(any()) } returns paths
        coEvery { workingTree.readFile(any(), any()) } returns GitFileRead.Text("x", "hash")

        val sizes = mutableListOf<Int>()
        coEvery { sink.onBatch(any()) } answers { sizes += firstArg<List<GitFileChange>>().size }

        engine(batchSize = 2).ingestWorkingTree(coordinates, sink)

        assertThat(sizes).containsExactly(2, 2, 1)
    }

    // ── incremental ingest ────────────────────────────────────────────────────

    @Test
    fun `reads everything when the caller has no cursor yet`() = runTest {
        existingFile("Main.kt")
        coEvery { revisionState.updateLocal(any()) } returns REVISION
        coEvery { workingTree.trackedFiles(any()) } returns listOf("Main.kt")
        coEvery { workingTree.readFile(any(), any()) } returns GitFileRead.Text("x", "hash")

        engine().ingestFileChangesSince(coordinates, "", sink)

        coVerify { revisionState.updateLocal(repositoryPath) }
        coVerify { workingTree.trackedFiles(repositoryPath) }
    }

    /**
     * A full ingest must see the remote's revision even when the cached clone is stale: a retry
     * after a failed first ingest would otherwise re-read the revision the failed attempt left
     * behind.
     */
    @Test
    fun `fetches before a full ingest instead of reading the cached revision as-is`() = runTest {
        existingFile("Main.kt")
        coEvery { revisionState.updateLocal(repositoryPath) } returns NEW_REVISION
        coEvery { workingTree.trackedFiles(any()) } returns listOf("Main.kt")
        coEvery { workingTree.readFile(any(), any()) } returns GitFileRead.Text("x", "hash")

        val outcome = engine().ingestWorkingTree(coordinates, sink)

        assertThat(outcome.revision).isEqualTo(NEW_REVISION)
        coVerify(exactly = 0) { revisionState.currentRevision(any()) }
    }

    @Test
    fun `reads nothing when the remote did not move`() = runTest {
        coEvery { revisionState.updateLocal(repositoryPath) } returns REVISION

        val outcome = engine().ingestFileChangesSince(coordinates, REVISION, sink)

        assertThat(outcome.revision).isEqualTo(REVISION)
        assertThat(outcome.complete).isTrue()
        coVerify(exactly = 0) { changeSet.changedPaths(any(), any(), any()) }
        coVerify(exactly = 0) { workingTree.trackedFiles(any()) }
        coVerify(exactly = 0) { sink.onBatch(any()) }
    }

    @Test
    fun `diffs the stored cursor against the fetched revision`() = runTest {
        existingFile("Changed.kt")
        coEvery { revisionState.updateLocal(repositoryPath) } returns NEW_REVISION
        coEvery { changeSet.changedPaths(repositoryPath, OLD_REVISION, NEW_REVISION) } returns listOf("Changed.kt")
        coEvery { workingTree.readFile(repositoryPath, "Changed.kt") } returns GitFileRead.Text("y", "hash")

        val outcome = engine().ingestFileChangesSince(coordinates, OLD_REVISION, sink)

        assertThat(outcome.revision).isEqualTo(NEW_REVISION)
        assertThat(outcome.resyncedPaths).isNull()
        coVerify { sink.onBatch(match { it.single() is GitFileChange.Modified }) }
    }

    /**
     * A stored cursor can outlive the clone's knowledge of it: a re-clone only fetches tracked
     * heads. Diffing against it would fail on every run and pin the cursor, so the engine reads
     * everything instead and reports what it saw for deletion reconciliation.
     */
    @Test
    fun `falls back to a full ingest when the cursor revision is missing from the clone`() = runTest {
        existingFile("Main.kt")
        coEvery { revisionState.updateLocal(repositoryPath) } returns NEW_REVISION
        coEvery { revisionState.knowsRevision(repositoryPath, OLD_REVISION) } returns false
        coEvery { workingTree.trackedFiles(repositoryPath) } returns listOf("Main.kt")
        coEvery { workingTree.readFile(repositoryPath, "Main.kt") } returns GitFileRead.Text("x", "hash")

        val outcome = engine().ingestFileChangesSince(coordinates, OLD_REVISION, sink)

        assertThat(outcome.revision).isEqualTo(NEW_REVISION)
        assertThat(outcome.resyncedPaths).containsExactly("Main.kt")
        coVerify(exactly = 0) { changeSet.changedPaths(any(), any(), any()) }
        coVerify { sink.onBatch(match { it.single() is GitFileChange.Modified }) }
    }

    @Test
    fun `reports a changed path that is gone from disk as deleted rather than reading it`() = runTest {
        coEvery { revisionState.updateLocal(repositoryPath) } returns NEW_REVISION
        coEvery { changeSet.changedPaths(repositoryPath, OLD_REVISION, NEW_REVISION) } returns listOf("Gone.kt")

        engine().ingestFileChangesSince(coordinates, OLD_REVISION, sink)

        coVerify {
            sink.onBatch(match { (it.single() as GitFileChange.Deleted).relativePath == "Gone.kt" })
        }
        coVerify(exactly = 0) { workingTree.readFile(any(), any()) }
    }

    // ── commits ───────────────────────────────────────────────────────────────

    @Test
    fun `forwards every commit the history holds`() = runTest {
        val commitSink = mockk<GitCommitSink>(relaxed = true)
        coEvery { revisionState.updateLocal(repositoryPath) } returns REVISION
        coEvery { gitLog.commits(repositoryPath, null) } returns listOf(
            GitCommit("a", "Ada", Instant.parse("2026-01-01T00:00:00Z"), "First"),
            GitCommit("b", "Bob", Instant.parse("2026-01-02T00:00:00Z"), "Second"),
        )

        val outcome = engine().ingestCommitsSince(coordinates, null, commitSink)

        assertThat(outcome.revision).isEqualTo(REVISION)
        coVerify(exactly = 2) { commitSink.onCommit(any()) }
    }

    @Test
    fun `treats a blank commit cursor as no cursor so a first run reads everything`() = runTest {
        coEvery { revisionState.updateLocal(repositoryPath) } returns REVISION
        coEvery { gitLog.commits(repositoryPath, null) } returns emptyList()

        engine().ingestCommitsSince(coordinates, "", mockk<GitCommitSink>(relaxed = true))

        coVerify { gitLog.commits(repositoryPath, null) }
    }

    @Test
    fun `reads only the commits after a stored cursor`() = runTest {
        coEvery { revisionState.updateLocal(repositoryPath) } returns NEW_REVISION
        coEvery { gitLog.commits(repositoryPath, OLD_REVISION) } returns emptyList()

        engine().ingestCommitsSince(coordinates, OLD_REVISION, mockk<GitCommitSink>(relaxed = true))

        coVerify { gitLog.commits(repositoryPath, OLD_REVISION) }
    }

    @Test
    fun `reads the full history when the commit cursor is missing from the clone`() = runTest {
        coEvery { revisionState.updateLocal(repositoryPath) } returns NEW_REVISION
        coEvery { revisionState.knowsRevision(repositoryPath, OLD_REVISION) } returns false
        coEvery { gitLog.commits(repositoryPath, null) } returns emptyList()

        val outcome = engine().ingestCommitsSince(coordinates, OLD_REVISION, mockk<GitCommitSink>(relaxed = true))

        assertThat(outcome.revision).isEqualTo(NEW_REVISION)
        coVerify { gitLog.commits(repositoryPath, null) }
        coVerify(exactly = 0) { gitLog.commits(repositoryPath, OLD_REVISION) }
    }

    @Test
    fun `brings the clone up to date before reading commits`() = runTest {
        coEvery { revisionState.updateLocal(repositoryPath) } returns REVISION
        coEvery { gitLog.commits(any(), any()) } returns emptyList()

        engine().ingestCommitsSince(coordinates, null, mockk<GitCommitSink>(relaxed = true))

        coVerify { revisionState.updateLocal(repositoryPath) }
    }

    @Test
    fun `reports whether the clone still matches its remote`() = runTest {
        coEvery { revisionState.isUpToDate(repositoryPath) } returns false

        assertThat(engine().isUpToDate(coordinates)).isFalse()
    }

    private companion object {
        const val REVISION = "aaaa1111"
        const val OLD_REVISION = "bbbb2222"
        const val NEW_REVISION = "cccc3333"
    }
}
