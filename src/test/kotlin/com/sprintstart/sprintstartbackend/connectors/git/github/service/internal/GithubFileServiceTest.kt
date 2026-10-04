package com.sprintstart.sprintstartbackend.connectors.git.github.service.internal

import com.sprintstart.sprintstartbackend.connectors.ConnectionState
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.files.GithubFileDeletedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.files.GithubFileFetchFailedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.files.GithubFileFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.files.GithubFilesFetchCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.files.GithubFilesFetchFailedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.files.GithubFilesFetchStartedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.files.GithubFilesResyncedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.models.GithubFileSnapshot
import com.sprintstart.sprintstartbackend.connectors.git.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.git.github.models.GithubUser
import com.sprintstart.sprintstartbackend.connectors.git.github.models.GithubUserPat
import com.sprintstart.sprintstartbackend.connectors.git.github.models.exceptions.RepositoryNotInitializedException
import com.sprintstart.sprintstartbackend.connectors.git.github.repository.GithubFileSnapshotRepository
import com.sprintstart.sprintstartbackend.connectors.git.github.repository.GithubRepositoryConnectionRepository
import com.sprintstart.sprintstartbackend.connectors.git.github.util.GithubGitProvider
import com.sprintstart.sprintstartbackend.connectors.git.utils.GitFileChange
import com.sprintstart.sprintstartbackend.connectors.git.utils.GitFileSink
import com.sprintstart.sprintstartbackend.connectors.git.utils.GitIngestFailure
import com.sprintstart.sprintstartbackend.connectors.git.utils.GitIngestOutcome
import com.sprintstart.sprintstartbackend.connectors.git.utils.GitIngestionEngine
import com.sprintstart.sprintstartbackend.shared.git.GitRepositoryCoordinates
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional
import java.util.UUID
import kotlin.test.assertFailsWith

class GithubFileServiceTest {
    private val repoConnectionRepository = mockk<GithubRepositoryConnectionRepository>()
    private val fileSnapshotRepository = mockk<GithubFileSnapshotRepository>(relaxed = true)
    private val coordinatesFactory = mockk<GithubRepositoryCoordinatesFactory>()
    private val ingestionEngine = mockk<GitIngestionEngine>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    private val service = GithubFileService(
        repoConnectionRepository = repoConnectionRepository,
        fileSnapshotRepository = fileSnapshotRepository,
        coordinatesFactory = coordinatesFactory,
        provider = GithubGitProvider(),
        ingestionEngine = ingestionEngine,
        eventPublisher = eventPublisher,
    )

    private val transactionId = UUID.randomUUID()
    private val sink = slot<GitFileSink>()

    private val coordinates = GitRepositoryCoordinates(
        host = "github.com",
        namespace = "owner",
        name = "repo",
        username = "x-access-token",
        secret = "test-token",
    )

    private val connection = GithubRepositoryConnection(
        owner = "owner",
        name = "repo",
        user = GithubUser(id = GithubUserPat("auth-id", "token-name"), token = "test-token"),
    )

    private fun givenIngestSucceeds(
        revision: String = NEW_REVISION,
        failures: List<GitIngestFailure> = emptyList(),
        resyncedPaths: Set<String>? = null,
    ) {
        every { repoConnectionRepository.findById(connection.id) } returns Optional.of(connection)
        every { repoConnectionRepository.updateFileCursor(any(), any()) } just Runs
        every { coordinatesFactory.of(connection) } returns coordinates
        coEvery {
            ingestionEngine.ingestFileChangesSince(coordinates, any(), capture(sink))
        } returns GitIngestOutcome(revision, failures, resyncedPaths = resyncedPaths)
    }

    // ── full versus incremental ───────────────────────────────────────────────

    @Test
    fun `reads a repository in full when it has never been ingested`() = runTest {
        givenIngestSucceeds()

        service.fetchAndIngestAllFiles(connection.id, connection.owner, connection.name, transactionId)

        coVerify { ingestionEngine.ingestFileChangesSince(coordinates, "", any()) }
    }

    @Test
    fun `incremental update advances from the revision the last run stored`() = runTest {
        connection.lastSha = PREVIOUS_REVISION
        givenIngestSucceeds()

        service.fetchAndIngestFileUpdatesIncremental(connection, transactionId)

        coVerify { ingestionEngine.ingestFileChangesSince(coordinates, PREVIOUS_REVISION, any()) }
    }

    @Test
    fun `stores the revision the run reached`() = runTest {
        givenIngestSucceeds()

        service.fetchAndIngestAllFiles(connection.id, connection.owner, connection.name, transactionId)

        assertThat(connection.lastSha).isEqualTo(NEW_REVISION)
        verify { repoConnectionRepository.updateFileCursor(connection.id, NEW_REVISION) }
        verify(exactly = 0) { repoConnectionRepository.save(any()) }
    }

    @Test
    fun `incremental update without a cursor fails without calling the engine`() = runTest {
        assertFailsWith<RepositoryNotInitializedException> {
            service.fetchAndIngestFileUpdatesIncremental(connection, transactionId)
        }

        coVerify(exactly = 0) { ingestionEngine.ingestFileChangesSince(any(), any(), any()) }
        verify(exactly = 1) {
            eventPublisher.publishEvent(match<Any> { it is GithubFilesFetchFailedEvent })
        }
    }

    // ── terminal events ───────────────────────────────────────────────────────

    @Test
    fun `publishes exactly one started and one completed event on success`() = runTest {
        givenIngestSucceeds()

        service.fetchAndIngestAllFiles(connection.id, connection.owner, connection.name, transactionId)

        verify(exactly = 1) {
            eventPublisher.publishEvent(match<Any> { it is GithubFilesFetchStartedEvent })
        }
        verify(exactly = 1) {
            eventPublisher.publishEvent(match<Any> { it is GithubFilesFetchCompletedEvent })
        }
        verify(exactly = 0) {
            eventPublisher.publishEvent(match<Any> { it is GithubFilesFetchFailedEvent })
        }
    }

    @Test
    fun `completes and advances the cursor when only some files failed`() = runTest {
        givenIngestSucceeds(failures = listOf(GitIngestFailure("huge.json", "above the ingest size limit")))

        service.fetchAndIngestAllFiles(connection.id, connection.owner, connection.name, transactionId)

        assertThat(connection.lastSha).isEqualTo(NEW_REVISION)
        verify(exactly = 1) {
            eventPublisher.publishEvent(match<Any> { it is GithubFilesFetchCompletedEvent })
        }
    }

    @Test
    fun `fails without advancing the cursor when the ingest throws`() = runTest {
        every { repoConnectionRepository.findById(connection.id) } returns Optional.of(connection)
        every { coordinatesFactory.of(connection) } returns coordinates
        coEvery { ingestionEngine.ingestFileChangesSince(coordinates, any(), any()) } throws
            RuntimeException("git clone failed (exit 128)")

        assertFailsWith<RuntimeException> {
            service.fetchAndIngestAllFiles(connection.id, connection.owner, connection.name, transactionId)
        }

        assertThat(connection.lastSha).isEmpty()
        verify(exactly = 0) { repoConnectionRepository.updateFileCursor(any(), any()) }
        verify(exactly = 1) {
            eventPublisher.publishEvent(
                match<Any> { it is GithubFilesFetchFailedEvent && it.reason.contains("exit 128") },
            )
        }
    }

    @Test
    fun `reports an unknown repository without calling the engine`() = runTest {
        every { repoConnectionRepository.findById(any()) } returns Optional.empty()

        assertFailsWith<Exception> {
            service.fetchAndIngestAllFiles(UUID.randomUUID(), "owner", "repo", transactionId)
        }

        coVerify(exactly = 0) { ingestionEngine.ingestFileChangesSince(any(), any(), any()) }
        verify(exactly = 1) {
            eventPublisher.publishEvent(match<Any> { it is GithubFilesFetchFailedEvent })
        }
    }

    @Test
    fun `publishes a resync event when the engine fell back to a full ingest`() = runTest {
        givenIngestSucceeds(resyncedPaths = setOf("README.md"))

        service.fetchAndIngestAllFiles(connection.id, connection.owner, connection.name, transactionId)

        verify {
            eventPublisher.publishEvent(
                match<Any> {
                    it is GithubFilesResyncedEvent &&
                        it.transactionId == transactionId &&
                        it.repositoryId == connection.id &&
                        it.repositoryOwner == "owner" &&
                        it.repositoryName == "repo" &&
                        it.visitedPaths == setOf("README.md")
                },
            )
        }
    }

    @Test
    fun `reconciles deletions before storing the cursor and completing`() = runTest {
        givenIngestSucceeds(resyncedPaths = setOf("README.md"))

        service.fetchAndIngestAllFiles(connection.id, connection.owner, connection.name, transactionId)

        io.mockk.verifyOrder {
            eventPublisher.publishEvent(match<Any> { it is GithubFilesResyncedEvent })
            repoConnectionRepository.updateFileCursor(connection.id, NEW_REVISION)
            eventPublisher.publishEvent(match<Any> { it is GithubFilesFetchCompletedEvent })
        }
    }

    @Test
    fun `publishes no resync event on an incremental ingest`() = runTest {
        givenIngestSucceeds()

        service.fetchAndIngestAllFiles(connection.id, connection.owner, connection.name, transactionId)

        verify(exactly = 0) {
            eventPublisher.publishEvent(match<Any> { it is GithubFilesResyncedEvent })
        }
    }

    // ── verifyFileSyncStatus ──────────────────────────────────────────────────

    @Test
    fun `marks the repository out of date when the clone no longer matches the remote`() = runTest {
        every { coordinatesFactory.of(connection) } returns coordinates
        every { repoConnectionRepository.save(connection) } returns connection
        coEvery { ingestionEngine.isUpToDate(coordinates) } returns false

        service.verifyFileSyncStatus(connection, transactionId)

        assertThat(connection.connectionState).isEqualTo(ConnectionState.OUT_OF_DATE)
        verify { repoConnectionRepository.save(connection) }
    }

    @Test
    fun `leaves an up-to-date repository untouched`() = runTest {
        every { coordinatesFactory.of(connection) } returns coordinates
        coEvery { ingestionEngine.isUpToDate(coordinates) } returns true

        service.verifyFileSyncStatus(connection, transactionId)

        assertThat(connection.connectionState).isEqualTo(ConnectionState.UP_TO_DATE)
        verify(exactly = 0) { repoConnectionRepository.save(any()) }
        verify { eventPublisher.publishEvent(any<GithubFilesFetchStartedEvent>()) }
        verify { eventPublisher.publishEvent(any<GithubFilesFetchCompletedEvent>()) }
    }

    // ── the provider binding ──────────────────────────────────────────────────

    @Test
    fun `maps a modified file onto a github file event with a github source url`() = runTest {
        givenIngestSucceeds()
        service.fetchAndIngestAllFiles(connection.id, connection.owner, connection.name, transactionId)

        sink.captured.onBatch(
            listOf(GitFileChange.Modified("src/Main.kt", NEW_REVISION, "fun main() {}", "content-hash")),
        )

        verify {
            eventPublisher.publishEvent(
                match<Any> {
                    it is GithubFileFetchedEvent &&
                        it.path == "src/Main.kt" &&
                        it.content == "fun main() {}" &&
                        it.repositoryId == connection.id &&
                        it.repositoryOwner == "owner" &&
                        it.repositoryName == "repo" &&
                        it.sourceUrl ==
                        "https://github.com/owner/repo/blob/$NEW_REVISION/src/Main.kt"
                },
            )
        }
    }

    @Test
    fun `records a file snapshot keyed by the repository-relative path`() = runTest {
        givenIngestSucceeds()
        service.fetchAndIngestAllFiles(connection.id, connection.owner, connection.name, transactionId)

        sink.captured.onBatch(
            listOf(GitFileChange.Modified("src/Main.kt", NEW_REVISION, "fun main() {}", "content-hash")),
        )

        val snapshots = slot<Iterable<GithubFileSnapshot>>()
        verify { fileSnapshotRepository.saveAll(capture(snapshots)) }
        val snapshot = snapshots.captured.single()
        assertThat(snapshot.id.path).isEqualTo("src/Main.kt")
        assertThat(snapshot.sha).isEqualTo("content-hash")
        assertThat(snapshot.repository).isSameAs(connection)
    }

    @Test
    fun `maps a deleted file onto a deletion event without a snapshot`() = runTest {
        givenIngestSucceeds()
        service.fetchAndIngestAllFiles(connection.id, connection.owner, connection.name, transactionId)

        sink.captured.onBatch(listOf(GitFileChange.Deleted("src/Old.kt", NEW_REVISION)))

        verify {
            eventPublisher.publishEvent(
                match<Any> { it is GithubFileDeletedEvent && it.path == "src/Old.kt" },
            )
        }
        verify(exactly = 0) { fileSnapshotRepository.saveAll(any<Iterable<GithubFileSnapshot>>()) }
    }

    @Test
    fun `reports a file the engine could not read`() = runTest {
        givenIngestSucceeds()
        service.fetchAndIngestAllFiles(connection.id, connection.owner, connection.name, transactionId)

        sink.captured.onFailure("huge.json", "file is 9000000 bytes, above the ingest size limit")

        verify {
            eventPublisher.publishEvent(
                match<Any> {
                    it is GithubFileFetchFailedEvent &&
                        it.path == "huge.json" &&
                        it.reason.contains("above the ingest size limit")
                },
            )
        }
    }

    private companion object {
        const val NEW_REVISION = "abc123def456"
        const val PREVIOUS_REVISION = "000111222333"
    }
}
