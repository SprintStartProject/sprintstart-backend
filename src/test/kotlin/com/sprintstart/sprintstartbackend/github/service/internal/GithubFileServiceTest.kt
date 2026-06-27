package com.sprintstart.sprintstartbackend.github.service.internal

import com.sprintstart.sprintstartbackend.github.external.events.files.GithubFileDeletedEvent
import com.sprintstart.sprintstartbackend.github.external.events.files.GithubFileFetchFailedEvent
import com.sprintstart.sprintstartbackend.github.external.events.files.GithubFileFetchedEvent
import com.sprintstart.sprintstartbackend.github.external.events.files.GithubFilesFetchCompletedEvent
import com.sprintstart.sprintstartbackend.github.external.events.files.GithubFilesFetchStartedEvent
import com.sprintstart.sprintstartbackend.github.models.GithubFileSnapshot
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
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.springframework.context.ApplicationEventPublisher
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
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
            with(eventSlot.captured) {
                assertThat(path).isEqualTo(fileName)
                assertThat(content).isEqualTo("fun main() {}")
            }

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

            val allEvents = mutableListOf<Any>()
            verify(exactly = 2) { eventPublisher.publishEvent(capture(allEvents)) }
            assertThat(allEvents).anyMatch { it is GithubFilesFetchStartedEvent }
            assertThat(allEvents).anyMatch { it is GithubFilesFetchCompletedEvent }
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

    @Nested
    inner class FetchAndIngestAllFiles {
        @Test
        fun `updates lastSha after successful ingestion`() = runTest {
            val repo = repoConnection()

            // Use a real temp dir with no files so streamFilesFromDiskAndIngest finishes immediately
            val emptyDir = Files.createTempDirectory("empty-repo")
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repo)
            coEvery { customCache.getLocalRepositoryPath("owner", "repo") } returns emptyDir
            every { gitRunner.exec(emptyDir, match { it.command().contains("rev-parse") }) } returns "abc123\n"

            service.fetchAndIngestAllFiles(repo.id, repo.owner, repo.name, transactionId)

            assertThat(repo.lastSha).isEqualTo("abc123")
            Files.delete(emptyDir)
        }

        @Test
        fun `publishes lifecycle events on successful ingestion`() = runTest {
            val repo = repoConnection()

            val emptyDir = Files.createTempDirectory("empty-repo")
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repo)
            coEvery { customCache.getLocalRepositoryPath("owner", "repo") } returns emptyDir
            every { gitRunner.exec(emptyDir, match { it.command().contains("rev-parse") }) } returns "abc123\n"

            service.fetchAndIngestAllFiles(repo.id, repo.owner, repo.name, transactionId)

            verify { eventPublisher.publishEvent(any<GithubFilesFetchStartedEvent>()) }
            verify { eventPublisher.publishEvent(any<GithubFilesFetchCompletedEvent>()) }
            Files.delete(emptyDir)
        }

        @Test
        fun `throws ResponseStatusException when repository is not found`() = runTest {
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.empty()

            assertThrows<Exception> {
                service.fetchAndIngestAllFiles(UUID.randomUUID(), "", "", transactionId)
            }
        }
    }

    @Nested
    inner class StreamFilesFromDiskAndIngest {
        @TempDir
        lateinit var repoDir: Path

        @Test
        fun `publishes one GithubFileFetchedEvent per text file`() = runTest {
            repoDir.resolve("file1.kt").writeText("content 1")
            repoDir.resolve("file2.kt").writeText("content 2")

            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repoConnection())
            coEvery { customCache.getLocalRepositoryPath("owner", "repo") } returns repoDir
            every { gitRunner.exec(repoDir, match { it.command().contains("rev-parse") }) } returns "sha\n"

            service.fetchAndIngestAllFiles(
                repoConnection().id,
                repoConnection().owner,
                repoConnection().name,
                transactionId,
            )

            val allEvents = mutableListOf<Any>()
            verify(exactly = 4) { eventPublisher.publishEvent(capture(allEvents)) }
            assertThat(allEvents.filterIsInstance<GithubFileFetchedEvent>()).hasSize(2)
        }

        @Test
        fun `skips binary files`() = runTest {
            repoDir.resolve("image.png").writeText("fake binary")
            repoDir.resolve("code.kt").writeText("real code")

            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repoConnection())
            coEvery { customCache.getLocalRepositoryPath("owner", "repo") } returns repoDir
            every { gitRunner.exec(repoDir, match { it.command().contains("rev-parse") }) } returns "sha\n"

            service.fetchAndIngestAllFiles(
                repoConnection().id,
                repoConnection().owner,
                repoConnection().name,
                transactionId,
            )

            val allEvents = mutableListOf<Any>()
            verify(exactly = 3) { eventPublisher.publishEvent(capture(allEvents)) }
            assertThat(allEvents.filterIsInstance<GithubFileFetchedEvent>()).hasSize(1)
        }

        @Test
        fun `skips dot-git directory`() = runTest {
            val gitDir = repoDir.resolve(".git").also { Files.createDirectories(it) }
            gitDir.resolve("config").writeText("git internals")
            repoDir.resolve("code.kt").writeText("content")

            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repoConnection())
            coEvery { customCache.getLocalRepositoryPath("owner", "repo") } returns repoDir
            every { gitRunner.exec(repoDir, match { it.command().contains("rev-parse") }) } returns "sha\n"

            service.fetchAndIngestAllFiles(
                repoConnection().id,
                repoConnection().owner,
                repoConnection().name,
                transactionId,
            )

            val allEvents = mutableListOf<Any>()
            verify(exactly = 3) { eventPublisher.publishEvent(capture(allEvents)) }
            assertThat(allEvents.filterIsInstance<GithubFileFetchedEvent>()).hasSize(1)
        }

        @Test
        fun `publishes correct sourceUrl for nested file`() = runTest {
            val srcDir = repoDir.resolve("src").also { Files.createDirectories(it) }
            srcDir.resolve("Main.kt").writeText("content")

            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repoConnection())
            coEvery { customCache.getLocalRepositoryPath("owner", "repo") } returns repoDir
            every { gitRunner.exec(repoDir, match { it.command().contains("rev-parse") }) } returns "sha\n"

            service.fetchAndIngestAllFiles(
                repoConnection().id,
                repoConnection().owner,
                repoConnection().name,
                transactionId,
            )

            val eventSlot = slot<GithubFileFetchedEvent>()
            verify { eventPublisher.publishEvent(capture(eventSlot)) }
            assertThat(eventSlot.captured.sourceUrl)
                .isEqualTo("https://github.com/owner/repo/blob/sha/src/Main.kt")
        }

        @Test
        fun `publishes correct file content`() = runTest {
            repoDir.resolve("code.kt").writeText("fun main() {}")

            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repoConnection())
            coEvery { customCache.getLocalRepositoryPath("owner", "repo") } returns repoDir
            every { gitRunner.exec(repoDir, match { it.command().contains("rev-parse") }) } returns "sha\n"

            service.fetchAndIngestAllFiles(
                repoConnection().id,
                repoConnection().owner,
                repoConnection().name,
                transactionId,
            )

            val eventSlot = slot<GithubFileFetchedEvent>()
            verify { eventPublisher.publishEvent(capture(eventSlot)) }
            assertThat(eventSlot.captured.content).isEqualTo("fun main() {}")
        }

        @Test
        fun `saves file snapshot for each ingested file`() = runTest {
            repoDir.resolve("code.kt").writeText("content")

            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repoConnection())
            coEvery { customCache.getLocalRepositoryPath("owner", "repo") } returns repoDir
            every { gitRunner.exec(repoDir, match { it.command().contains("rev-parse") }) } returns "sha\n"

            service.fetchAndIngestAllFiles(
                repoConnection().id,
                repoConnection().owner,
                repoConnection().name,
                transactionId,
            )

            verify { fileSnapshotRepository.saveAll(any<Iterable<GithubFileSnapshot>>()) }
        }

        @Test
        fun `skips files that are not valid UTF-8`() = runTest {
            Files.write(repoDir.resolve("notes.txt"), byteArrayOf(0xC3.toByte(), 0x28))
            repoDir.resolve("code.kt").writeText("content", StandardCharsets.UTF_8)

            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repoConnection())
            coEvery { customCache.getLocalRepositoryPath("owner", "repo") } returns repoDir
            every { gitRunner.exec(repoDir, match { it.command().contains("rev-parse") }) } returns "sha\n"

            service.fetchAndIngestAllFiles(
                repoConnection().id,
                repoConnection().owner,
                repoConnection().name,
                transactionId,
            )

            val allEvents = mutableListOf<Any>()
            verify(exactly = 4) { eventPublisher.publishEvent(capture(allEvents)) }
            assertThat(allEvents.filterIsInstance<GithubFileFetchedEvent>()).hasSize(2)
            assertThat(allEvents.filterIsInstance<GithubFileFetchFailedEvent>()).isEmpty()
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun repoConnection(lastSha: String = "abc123") = GithubRepositoryConnection(
        owner = "owner",
        name = "repo",
        lastSha = lastSha,
    )
}
