package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFileDeletedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFileFetchFailedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFileFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFilesFetchingCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFilesFetchingFailedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFilesFetchingStartedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFilesResyncedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketConnectionRepository
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.utils.BitbucketGitProvider
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

class BitbucketFileServiceTest {
    private val connectionRepository = mockk<BitbucketConnectionRepository>()
    private val coordinatesFactory = mockk<BitbucketRepositoryCoordinatesFactory>()
    private val ingestionEngine = mockk<GitIngestionEngine>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    private val service = BitbucketFileService(
        connectionRepository = connectionRepository,
        coordinatesFactory = coordinatesFactory,
        provider = BitbucketGitProvider(),
        ingestionEngine = ingestionEngine,
        eventPublisher = eventPublisher,
    )

    private val transactionId = UUID.randomUUID()
    private val sink = slot<GitFileSink>()

    private val coordinates = GitRepositoryCoordinates(
        host = "bitbucket.org",
        namespace = "sprintstart",
        name = "sprintstart-backend",
        username = "x-bitbucket-api-token-auth",
        secret = "api-token",
    )

    private val connection = BitbucketConnection(
        workspace = "sprintstart",
        slug = "sprintstart-backend",
        credentialAuthId = "auth-id",
        credentialName = "team-token",
    )

    private fun givenIngestSucceeds(
        revision: String = NEW_REVISION,
        failures: List<GitIngestFailure> = emptyList(),
    ) {
        every { connectionRepository.findById(connection.id) } returns Optional.of(connection)
        every { connectionRepository.updateFileCursor(any(), any()) } just Runs
        every { coordinatesFactory.of(connection) } returns coordinates
        coEvery {
            ingestionEngine.ingestFileChangesSince(coordinates, any(), capture(sink))
        } returns GitIngestOutcome(revision, failures)
    }

    // ── full versus incremental ───────────────────────────────────────────────

    @Test
    fun `reads a repository in full when it has never been ingested`() = runTest {
        givenIngestSucceeds()

        service.fetchAndIngestFilesOfRepository(connection.id, transactionId)

        coVerify { ingestionEngine.ingestFileChangesSince(coordinates, "", any()) }
    }

    @Test
    fun `advances from the revision the last run stored`() = runTest {
        connection.lastSha = PREVIOUS_REVISION
        givenIngestSucceeds()

        service.fetchAndIngestFilesOfRepository(connection.id, transactionId)

        coVerify { ingestionEngine.ingestFileChangesSince(coordinates, PREVIOUS_REVISION, any()) }
    }

    @Test
    fun `stores the revision the run reached`() = runTest {
        givenIngestSucceeds()

        service.fetchAndIngestFilesOfRepository(connection.id, transactionId)

        verify { connectionRepository.updateFileCursor(connection.id, NEW_REVISION) }
        verify(exactly = 0) { connectionRepository.save(any()) }
    }

    // ── terminal events ───────────────────────────────────────────────────────

    @Test
    fun `publishes exactly one started and one completed event on success`() = runTest {
        givenIngestSucceeds()

        service.fetchAndIngestFilesOfRepository(connection.id, transactionId)

        verify(exactly = 1) {
            eventPublisher.publishEvent(match<Any> { it is BitbucketFilesFetchingStartedEvent })
        }
        verify(exactly = 1) {
            eventPublisher.publishEvent(match<Any> { it is BitbucketFilesFetchingCompletedEvent })
        }
        verify(exactly = 0) {
            eventPublisher.publishEvent(match<Any> { it is BitbucketFilesFetchingFailedEvent })
        }
        verify(exactly = 0) {
            eventPublisher.publishEvent(match<Any> { it is BitbucketFilesResyncedEvent })
        }
    }

    @Test
    fun `publishes a resync event when the engine fell back to a full ingest`() = runTest {
        every { connectionRepository.findById(connection.id) } returns Optional.of(connection)
        every { coordinatesFactory.of(connection) } returns coordinates
        every { connectionRepository.updateFileCursor(any(), any()) } just Runs
        coEvery { ingestionEngine.ingestFileChangesSince(coordinates, any(), any()) } returns
            GitIngestOutcome(NEW_REVISION, emptyList(), resyncedPaths = setOf("Main.kt"))

        service.fetchAndIngestFilesOfRepository(connection.id, transactionId)

        verify {
            eventPublisher.publishEvent(
                match<Any> {
                    it is BitbucketFilesResyncedEvent &&
                        it.transactionId == transactionId &&
                        it.repositoryId == connection.id &&
                        it.workspace == "sprintstart" &&
                        it.slug == "sprintstart-backend" &&
                        it.visitedPaths == setOf("Main.kt")
                },
            )
        }
    }

    @Test
    fun `completes and advances the cursor when only some files failed`() = runTest {
        givenIngestSucceeds(failures = listOf(GitIngestFailure("huge.json", "above the ingest size limit")))

        service.fetchAndIngestFilesOfRepository(connection.id, transactionId)

        verify { connectionRepository.updateFileCursor(connection.id, NEW_REVISION) }
        verify(exactly = 1) {
            eventPublisher.publishEvent(match<Any> { it is BitbucketFilesFetchingCompletedEvent })
        }
    }

    @Test
    fun `fails without advancing the cursor when the ingest throws`() = runTest {
        every { connectionRepository.findById(connection.id) } returns Optional.of(connection)
        every { coordinatesFactory.of(connection) } returns coordinates
        coEvery { ingestionEngine.ingestFileChangesSince(coordinates, any(), any()) } throws
            RuntimeException("git clone failed (exit 128)")

        assertFailsWith<RuntimeException> {
            service.fetchAndIngestFilesOfRepository(connection.id, transactionId)
        }

        assertThat(connection.lastSha).isEmpty()
        verify(exactly = 0) { connectionRepository.updateFileCursor(any(), any()) }
        verify(exactly = 0) { connectionRepository.save(any()) }
        verify(exactly = 1) {
            eventPublisher.publishEvent(
                match<Any> { it is BitbucketFilesFetchingFailedEvent && it.reason.contains("exit 128") },
            )
        }
    }

    @Test
    fun `reports an unknown repository without publishing anything`() = runTest {
        every { connectionRepository.findById(any()) } returns Optional.empty()

        assertFailsWith<BitbucketRepositoryNotConnectedException> {
            service.fetchAndIngestFilesOfRepository(connection.id, transactionId)
        }

        coVerify(exactly = 0) { ingestionEngine.ingestFileChangesSince(any(), any(), any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any<Any>()) }
    }

    // ── the provider binding ──────────────────────────────────────────────────

    @Test
    fun `maps a modified file onto a bitbucket file event with a bitbucket source url`() = runTest {
        givenIngestSucceeds()
        service.fetchAndIngestFilesOfRepository(connection.id, transactionId)

        sink.captured.onBatch(
            listOf(GitFileChange.Modified("src/Main.kt", NEW_REVISION, "fun main() {}", "content-hash")),
        )

        verify {
            eventPublisher.publishEvent(
                match<Any> {
                    it is BitbucketFileFetchedEvent &&
                        it.path == "src/Main.kt" &&
                        it.content == "fun main() {}" &&
                        it.repositoryId == connection.id &&
                        it.workspace == "sprintstart" &&
                        it.slug == "sprintstart-backend" &&
                        it.sourceUrl ==
                        "https://bitbucket.org/sprintstart/sprintstart-backend/src/$NEW_REVISION/src/Main.kt"
                },
            )
        }
    }

    @Test
    fun `maps a deleted file onto a deletion event rather than an empty file event`() = runTest {
        givenIngestSucceeds()
        service.fetchAndIngestFilesOfRepository(connection.id, transactionId)

        sink.captured.onBatch(listOf(GitFileChange.Deleted("src/Old.kt", NEW_REVISION)))

        verify {
            eventPublisher.publishEvent(
                match<Any> { it is BitbucketFileDeletedEvent && it.path == "src/Old.kt" },
            )
        }
        verify(exactly = 0) {
            eventPublisher.publishEvent(match<Any> { it is BitbucketFileFetchedEvent })
        }
    }

    @Test
    fun `reports a file the engine could not read`() = runTest {
        givenIngestSucceeds()
        service.fetchAndIngestFilesOfRepository(connection.id, transactionId)

        sink.captured.onFailure("huge.json", "file is 9000000 bytes, above the ingest size limit")

        verify {
            eventPublisher.publishEvent(
                match<Any> {
                    it is BitbucketFileFetchFailedEvent &&
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
