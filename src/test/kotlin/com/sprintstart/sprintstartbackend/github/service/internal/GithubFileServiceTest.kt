package com.sprintstart.sprintstartbackend.github.service.internal

import com.sprintstart.sprintstartbackend.github.external.events.files.GithubFileDeletedEvent
import com.sprintstart.sprintstartbackend.github.models.ConnectionStatus
import com.sprintstart.sprintstartbackend.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.github.models.exceptions.RepositoryNotInitializedException
import com.sprintstart.sprintstartbackend.github.repository.GithubFileSnapshotRepository
import com.sprintstart.sprintstartbackend.github.repository.GithubRepositoryConnectionRepository
import com.sprintstart.sprintstartbackend.github.util.CustomOnDiskCache
import com.sprintstart.sprintstartbackend.github.util.GitOperationRunner
import com.sprintstart.sprintstartbackend.github.util.OnDiskOperations
import com.sprintstart.sprintstartbackend.upload.external.UploadIngestionApi
import io.mockk.coEvery
import io.mockk.coVerify
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
    private val uploadIngestionApi = mockk<UploadIngestionApi>(relaxed = true)

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
            uploadIngestionApi = uploadIngestionApi,
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
        fun `ingests modified file through upload module api`() = runTest {
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

            coVerify {
                uploadIngestionApi.ingestGithubFile(
                    fileName,
                    "fun main() {}",
                    "https://github.com/owner/repo/blob/new-sha/$fileName",
                )
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

            coVerify(exactly = 0) { uploadIngestionApi.ingestGithubFile(any(), any(), any()) }
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

    @Nested
    inner class FetchAndIngestAllFiles {
        @Test
        fun `sets status to FAILED when exception is thrown`() = runTest {
            val repo = repoConnection()
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repo)
            coEvery {
                customCache.getLocalRepositoryPath("owner", "repo")
            } throws RuntimeException("disk error")

            try {
                service.fetchAndIngestAllFiles(repo.id, transactionId)
            } catch (@Suppress("SwallowedException") e: RuntimeException) {
                // expected
            }

            assertThat(repo.status).isEqualTo(ConnectionStatus.FAILED)
        }

        @Test
        fun `always saves repository in finally block even on exception`() = runTest {
            val repo = repoConnection()
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repoConnection())
            coEvery {
                customCache.getLocalRepositoryPath("owner", "repo")
            } throws RuntimeException("disk error")

            try {
                service.fetchAndIngestAllFiles(repo.id, transactionId)
            } catch (@Suppress("SwallowedException") e: RuntimeException) {
                // expected
            }

            verify { repoConnectionRepository.save(any()) }
        }

        @Test
        fun `updates lastSha after successful ingestion`() = runTest {
            val repo = repoConnection()

            // Use a real temp dir with no files so streamFilesFromDiskAndIngest finishes immediately
            val emptyDir = Files.createTempDirectory("empty-repo")
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repo)
            coEvery { customCache.getLocalRepositoryPath("owner", "repo") } returns emptyDir
            every { gitRunner.exec(emptyDir, match { it.command().contains("rev-parse") }) } returns "abc123\n"

            service.fetchAndIngestAllFiles(repo.id, transactionId)

            assertThat(repo.lastSha).isEqualTo("abc123")
            Files.delete(emptyDir)
        }

        @Test
        fun `does not fail when repository has no resolvable HEAD yet`() = runTest {
            val repo = repoConnection(lastSha = "")
            val emptyDir = Files.createTempDirectory("empty-repo-no-head")

            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repo)
            coEvery { customCache.getLocalRepositoryPath("owner", "repo") } returns emptyDir
            every {
                gitRunner.exec(emptyDir, match { it.command().contains("rev-parse") })
            } throws RuntimeException("git rev-parse HEAD failed (exit 128)")

            service.fetchAndIngestAllFiles(repo.id, transactionId)

            assertThat(repo.status).isNotEqualTo(ConnectionStatus.FAILED)
            assertThat(repo.lastSha).isBlank()
            Files.delete(emptyDir)
        }
    }

    @Nested
    inner class StreamFilesFromDiskAndIngest {
        @TempDir
        lateinit var repoDir: Path

        @Test
        fun `ingests one file per text file`() = runTest {
            repoDir.resolve("file1.kt").writeText("content 1")
            repoDir.resolve("file2.kt").writeText("content 2")

            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repoConnection())
            coEvery { customCache.getLocalRepositoryPath("owner", "repo") } returns repoDir
            every { gitRunner.exec(repoDir, match { it.command().contains("rev-parse") }) } returns "sha\n"

            service.fetchAndIngestAllFiles(repoConnection().id, transactionId)

            coVerify(exactly = 2) { uploadIngestionApi.ingestGithubFile(any(), any(), any()) }
        }

        @Test
        fun `skips binary files`() = runTest {
            repoDir.resolve("image.png").writeText("fake binary")
            repoDir.resolve("code.kt").writeText("real code")

            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repoConnection())
            coEvery { customCache.getLocalRepositoryPath("owner", "repo") } returns repoDir
            every { gitRunner.exec(repoDir, match { it.command().contains("rev-parse") }) } returns "sha\n"

            service.fetchAndIngestAllFiles(repoConnection().id, transactionId)

            coVerify(exactly = 1) { uploadIngestionApi.ingestGithubFile(any(), any(), any()) }
        }

        @Test
        fun `skips dot-git directory`() = runTest {
            val gitDir = repoDir.resolve(".git").also { Files.createDirectories(it) }
            gitDir.resolve("config").writeText("git internals")
            repoDir.resolve("code.kt").writeText("content")

            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repoConnection())
            coEvery { customCache.getLocalRepositoryPath("owner", "repo") } returns repoDir
            every { gitRunner.exec(repoDir, match { it.command().contains("rev-parse") }) } returns "sha\n"

            service.fetchAndIngestAllFiles(repoConnection().id, transactionId)

            coVerify(exactly = 1) {
                uploadIngestionApi.ingestGithubFile(
                    match { !it.contains(".git") },
                    any(),
                    any(),
                )
            }
        }

        @Test
        fun `publishes correct sourceUrl for nested file`() = runTest {
            val srcDir = repoDir.resolve("src").also { Files.createDirectories(it) }
            srcDir.resolve("Main.kt").writeText("content")

            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repoConnection())
            coEvery { customCache.getLocalRepositoryPath("owner", "repo") } returns repoDir
            every { gitRunner.exec(repoDir, match { it.command().contains("rev-parse") }) } returns "sha\n"

            service.fetchAndIngestAllFiles(repoConnection().id, transactionId)

            coVerify {
                uploadIngestionApi.ingestGithubFile(
                    "src/Main.kt",
                    "content",
                    "https://github.com/owner/repo/blob/sha/src/Main.kt",
                )
            }
        }

        @Test
        fun `publishes correct file content`() = runTest {
            repoDir.resolve("code.kt").writeText("fun main() {}")

            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repoConnection())
            coEvery { customCache.getLocalRepositoryPath("owner", "repo") } returns repoDir
            every { gitRunner.exec(repoDir, match { it.command().contains("rev-parse") }) } returns "sha\n"

            service.fetchAndIngestAllFiles(repoConnection().id, transactionId)

            coVerify {
                uploadIngestionApi.ingestGithubFile(
                    "code.kt",
                    "fun main() {}",
                    "https://github.com/owner/repo/blob/sha/code.kt",
                )
            }
        }

        @Test
        fun `saves file snapshot for each ingested file`() = runTest {
            repoDir.resolve("code.kt").writeText("content")

            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repoConnection())
            coEvery { customCache.getLocalRepositoryPath("owner", "repo") } returns repoDir
            every { gitRunner.exec(repoDir, match { it.command().contains("rev-parse") }) } returns "sha\n"

            service.fetchAndIngestAllFiles(repoConnection().id, transactionId)

            verify { fileSnapshotRepository.save(any()) }
        }

        @Test
        fun `skips files that are not valid UTF-8`() = runTest {
            Files.write(repoDir.resolve("notes.txt"), byteArrayOf(0xC3.toByte(), 0x28))
            repoDir.resolve("code.kt").writeText("content", StandardCharsets.UTF_8)

            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repoConnection())
            coEvery { customCache.getLocalRepositoryPath("owner", "repo") } returns repoDir
            every { gitRunner.exec(repoDir, match { it.command().contains("rev-parse") }) } returns "sha\n"

            service.fetchAndIngestAllFiles(repoConnection().id, transactionId)

            coVerify(exactly = 1) { uploadIngestionApi.ingestGithubFile(any(), any(), any()) }
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun repoConnection(lastSha: String = "abc123") = GithubRepositoryConnection(
        owner = "owner",
        name = "repo",
        lastSha = lastSha,
    )
}
