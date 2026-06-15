package com.sprintstart.sprintstartbackend.github.service.internal

import com.sprintstart.sprintstartbackend.github.external.events.GithubFileDeletedEvent
import com.sprintstart.sprintstartbackend.github.external.events.GithubFileFetchedEvent
import com.sprintstart.sprintstartbackend.github.models.ConnectionStatus
import com.sprintstart.sprintstartbackend.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.github.models.exceptions.RepositoryNotInitializedException
import com.sprintstart.sprintstartbackend.github.repository.GithubFileSnapshotRepository
import com.sprintstart.sprintstartbackend.github.repository.GithubRepositoryConnectionRepository
import com.sprintstart.sprintstartbackend.github.util.CustomOnDiskCache
import com.sprintstart.sprintstartbackend.github.util.GitOperationRunner
import com.sprintstart.sprintstartbackend.github.util.OnDiskOperations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.context.ApplicationEventPublisher
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.writeText

class GithubFileServiceTest {
    private val onDiskOperations = OnDiskOperations()
    private val repoConnectionRepository = mockk<GithubRepositoryConnectionRepository>()
    private val fileSnapshotRepository = mockk<GithubFileSnapshotRepository>(relaxed = true)
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val customCache = mockk<CustomOnDiskCache>()
    private val gitRunner = mockk<GitOperationRunner>()

    private lateinit var service: GithubFileService

    private val transactionId = UUID.randomUUID()
    private val repoPath = Path.of("/fake/repo")

    @BeforeEach
    fun setUp() {
        service = GithubFileService(
            onDiskOperations = onDiskOperations,
            repoConnectionRepository = repoConnectionRepository,
            fileSnapshotRepository = fileSnapshotRepository,
            eventPublisher = eventPublisher,
            customCache = customCache,
            gitRunner = gitRunner,
        )
        every { repoConnectionRepository.save(any()) } answers { firstArg() }
        every { fileSnapshotRepository.save(any()) } answers { firstArg() }
    }

    // ── fetchAndIngestFileUpdatesIncremental ──────────────────────────────────

    @Nested
    inner class FetchAndIngestFileUpdatesIncremental {
        @Test
        fun `throws RepositoryNotInitializedException when lastSha is blank`() = runTest {
            val repo = repoConnection(lastSha = "")

            var thrown: Exception? = null
            try {
                service.fetchAndIngestFileUpdatesIncremental(repo, transactionId)
            } catch (e: RepositoryNotInitializedException) {
                thrown = e
            }

            assertThat(thrown)
                .isNotNull()
                .hasMessageContaining("owner/repo")
        }

        @Test
        fun `does nothing when repository is already up to date`() = runTest {
            val sha = "abc123"
            val repo = repoConnection(lastSha = sha)

            coEvery { customCache.getLocalRepositoryPath("owner", "repo") } returns repoPath
            every { gitRunner.exec(repoPath, match { it.command().contains("fetch") }) } returns ""
            every { gitRunner.exec(repoPath, match { it.command().contains("merge") }) } returns ""
            every { gitRunner.exec(repoPath, match { it.command().contains("rev-parse") }) } returns "$sha\n"

            service.fetchAndIngestFileUpdatesIncremental(repo, transactionId)

            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
        }

        @Test
        fun `publishes GithubFileFetchedEvent for modified file`() = runTest {
            val repo = repoConnection(lastSha = "old-sha")

            coEvery { customCache.getLocalRepositoryPath("owner", "repo") } returns repoPath
            every { gitRunner.exec(repoPath, match { it.command().contains("fetch") }) } returns ""
            every { gitRunner.exec(repoPath, match { it.command().contains("merge") }) } returns ""
            every { gitRunner.exec(repoPath, match { it.command().contains("rev-parse") }) } returns "new-sha\n"
            every { gitRunner.exec(repoPath, match { it.command().contains("diff") }) } returns "src/Main.kt\n"

            // Create a real temp file so fetchFileUpdate can read it
            val tempFile = Files.createTempFile("Main", ".kt")
            tempFile.writeText("fun main() {}")
            val localRepoPath = tempFile.parent
            val fileName = tempFile.fileName.toString()

            coEvery { customCache.getLocalRepositoryPath("owner", "repo") } returns localRepoPath
            every { gitRunner.exec(localRepoPath, match { it.command().contains("fetch") }) } returns ""
            every { gitRunner.exec(localRepoPath, match { it.command().contains("merge") }) } returns ""
            every { gitRunner.exec(localRepoPath, match { it.command().contains("rev-parse") }) } returns "new-sha\n"
            every { gitRunner.exec(localRepoPath, match { it.command().contains("diff") }) } returns "$fileName\n"

            service.fetchAndIngestFileUpdatesIncremental(repo, transactionId)

            val eventSlot = slot<GithubFileFetchedEvent>()
            verify { eventPublisher.publishEvent(capture(eventSlot)) }

            assertThat(eventSlot.captured.path).isEqualTo(fileName)
            assertThat(eventSlot.captured.content).isEqualTo("fun main() {}")
            assertThat(eventSlot.captured.transactionId).isEqualTo(transactionId)

            Files.delete(tempFile)
        }

        @Test
        fun `publishes GithubFileDeletedEvent for deleted file`() = runTest {
            val repo = repoConnection(lastSha = "old-sha")
            val nonExistentFile = "deleted/file.kt"

            coEvery { customCache.getLocalRepositoryPath("owner", "repo") } returns repoPath
            every { gitRunner.exec(repoPath, match { it.command().contains("fetch") }) } returns ""
            every { gitRunner.exec(repoPath, match { it.command().contains("merge") }) } returns ""
            every { gitRunner.exec(repoPath, match { it.command().contains("rev-parse") }) } returns "new-sha\n"
            every { gitRunner.exec(repoPath, match { it.command().contains("diff") }) } returns "$nonExistentFile\n"

            service.fetchAndIngestFileUpdatesIncremental(repo, transactionId)

            val eventSlot = slot<GithubFileDeletedEvent>()
            verify { eventPublisher.publishEvent(capture(eventSlot)) }

            assertThat(eventSlot.captured.path).isEqualTo(nonExistentFile)
            assertThat(eventSlot.captured.transactionId).isEqualTo(transactionId)
        }

        @Test
        fun `skips binary files in diff output`() = runTest {
            val repo = repoConnection(lastSha = "old-sha")

            coEvery { customCache.getLocalRepositoryPath("owner", "repo") } returns repoPath
            every { gitRunner.exec(repoPath, match { it.command().contains("fetch") }) } returns ""
            every { gitRunner.exec(repoPath, match { it.command().contains("merge") }) } returns ""
            every { gitRunner.exec(repoPath, match { it.command().contains("rev-parse") }) } returns "new-sha\n"
            every { gitRunner.exec(repoPath, match { it.command().contains("diff") }) } returns
                "image.png\narchive.zip\n"

            service.fetchAndIngestFileUpdatesIncremental(repo, transactionId)

            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
        }

        @Test
        fun `updates lastSha on repository after successful update`() = runTest {
            val repo = repoConnection(lastSha = "old-sha")

            coEvery { customCache.getLocalRepositoryPath("owner", "repo") } returns repoPath
            every { gitRunner.exec(repoPath, match { it.command().contains("fetch") }) } returns ""
            every { gitRunner.exec(repoPath, match { it.command().contains("merge") }) } returns ""
            every { gitRunner.exec(repoPath, match { it.command().contains("rev-parse") }) } returns "new-sha\n"
            every { gitRunner.exec(repoPath, match { it.command().contains("diff") }) } returns ""

            service.fetchAndIngestFileUpdatesIncremental(repo, transactionId)

            assertThat(repo.lastSha).isEqualTo("new-sha")
        }
    }

    // ── fetchAndIngestAllFiles ────────────────────────────────────────────────

    @Nested
    inner class FetchAndIngestAllFiles {
        @Test
        fun `sets status to FAILED when exception is thrown`() = runTest {
            val repo = repoConnection()
            coEvery {
                customCache.getLocalRepositoryPath("owner", "repo")
            } throws RuntimeException("disk error")

            try {
                service.fetchAndIngestAllFiles(repo, transactionId)
            } catch (e: RuntimeException) {
                // expected
            }

            assertThat(repo.status).isEqualTo(ConnectionStatus.FAILED)
        }

        @Test
        fun `always saves repository in finally block even on exception`() = runTest {
            val repo = repoConnection()
            coEvery {
                customCache.getLocalRepositoryPath("owner", "repo")
            } throws RuntimeException("disk error")

            try {
                service.fetchAndIngestAllFiles(repo, transactionId)
            } catch (e: RuntimeException) {
                // expected
            }

            verify { repoConnectionRepository.save(repo) }
        }

        @Test
        fun `updates lastSha after successful ingestion`() = runTest {
            val repo = repoConnection()

            // Use a real temp dir with no files so streamFilesFromDiskAndIngest finishes immediately
            val emptyDir = Files.createTempDirectory("empty-repo")
            coEvery { customCache.getLocalRepositoryPath("owner", "repo") } returns emptyDir
            every { gitRunner.exec(emptyDir, match { it.command().contains("rev-parse") }) } returns "abc123\n"

            service.fetchAndIngestAllFiles(repo, transactionId)

            assertThat(repo.lastSha).isEqualTo("abc123")
            Files.delete(emptyDir)
        }
    }

    // ── streamFilesFromDiskAndIngest — needs real filesystem ─────────────────

    @Nested
    inner class StreamFilesFromDiskAndIngest {
        @TempDir
        lateinit var repoDir: Path

        @Test
        fun `publishes one event per text file`() = runTest {
            repoDir.resolve("file1.kt").writeText("content 1")
            repoDir.resolve("file2.kt").writeText("content 2")

            coEvery { customCache.getLocalRepositoryPath("owner", "repo") } returns repoDir
            every { gitRunner.exec(repoDir, match { it.command().contains("rev-parse") }) } returns "sha\n"

            service.fetchAndIngestAllFiles(repoConnection(), transactionId)

            verify(exactly = 2) { eventPublisher.publishEvent(any<GithubFileFetchedEvent>()) }
        }

        @Test
        fun `skips binary files`() = runTest {
            repoDir.resolve("image.png").writeText("fake binary")
            repoDir.resolve("code.kt").writeText("real code")

            coEvery { customCache.getLocalRepositoryPath("owner", "repo") } returns repoDir
            every { gitRunner.exec(repoDir, match { it.command().contains("rev-parse") }) } returns "sha\n"

            service.fetchAndIngestAllFiles(repoConnection(), transactionId)

            verify(exactly = 1) { eventPublisher.publishEvent(any<GithubFileFetchedEvent>()) }
        }

        @Test
        fun `skips dot-git directory`() = runTest {
            val gitDir = repoDir.resolve(".git").also { Files.createDirectories(it) }
            gitDir.resolve("config").writeText("git internals")
            repoDir.resolve("code.kt").writeText("content")

            coEvery { customCache.getLocalRepositoryPath("owner", "repo") } returns repoDir
            every { gitRunner.exec(repoDir, match { it.command().contains("rev-parse") }) } returns "sha\n"

            service.fetchAndIngestAllFiles(repoConnection(), transactionId)

            val eventSlot = slot<GithubFileFetchedEvent>()
            verify(exactly = 1) { eventPublisher.publishEvent(capture(eventSlot)) }
            assertThat(eventSlot.captured.path).doesNotContain(".git")
        }

        @Test
        fun `publishes correct sourceUrl for nested file`() = runTest {
            val srcDir = repoDir.resolve("src").also { Files.createDirectories(it) }
            srcDir.resolve("Main.kt").writeText("content")

            coEvery { customCache.getLocalRepositoryPath("owner", "repo") } returns repoDir
            every { gitRunner.exec(repoDir, match { it.command().contains("rev-parse") }) } returns "sha\n"

            service.fetchAndIngestAllFiles(repoConnection(), transactionId)

            val eventSlot = slot<GithubFileFetchedEvent>()
            verify { eventPublisher.publishEvent(capture(eventSlot)) }

            assertThat(eventSlot.captured.sourceUrl)
                .isEqualTo("https://github.com/owner/repo/blob/main/src/Main.kt")
        }

        @Test
        fun `publishes correct file content`() = runTest {
            repoDir.resolve("code.kt").writeText("fun main() {}")

            coEvery { customCache.getLocalRepositoryPath("owner", "repo") } returns repoDir
            every { gitRunner.exec(repoDir, match { it.command().contains("rev-parse") }) } returns "sha\n"

            service.fetchAndIngestAllFiles(repoConnection(), transactionId)

            val eventSlot = slot<GithubFileFetchedEvent>()
            verify { eventPublisher.publishEvent(capture(eventSlot)) }

            assertThat(eventSlot.captured.content).isEqualTo("fun main() {}")
        }

        @Test
        fun `saves file snapshot for each ingested file`() = runTest {
            repoDir.resolve("code.kt").writeText("content")

            coEvery { customCache.getLocalRepositoryPath("owner", "repo") } returns repoDir
            every { gitRunner.exec(repoDir, match { it.command().contains("rev-parse") }) } returns "sha\n"

            service.fetchAndIngestAllFiles(repoConnection(), transactionId)

            verify { fileSnapshotRepository.save(any()) }
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun repoConnection(lastSha: String = "abc123") = GithubRepositoryConnection(
        owner = "owner",
        name = "repo",
        lastSha = lastSha,
    )
}
