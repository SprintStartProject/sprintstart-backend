package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.commits.BitbucketCommitFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.commits.BitbucketCommitsFetchingCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.commits.BitbucketCommitsFetchingFailedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.commits.BitbucketCommitsFetchingStartedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketConnectionRepository
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal.BitbucketCommitsService
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.utils.BitbucketGitProvider
import com.sprintstart.sprintstartbackend.connectors.git.utils.GitCommitSink
import com.sprintstart.sprintstartbackend.connectors.git.utils.GitIngestOutcome
import com.sprintstart.sprintstartbackend.connectors.git.utils.GitIngestionEngine
import com.sprintstart.sprintstartbackend.shared.git.GitCommit
import com.sprintstart.sprintstartbackend.shared.git.GitRepositoryCoordinates
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertFailsWith

class BitbucketCommitsServiceTest {
    private val connectionRepository = mockk<BitbucketConnectionRepository>()
    private val coordinatesFactory = mockk<BitbucketRepositoryCoordinatesFactory>()
    private val ingestionEngine = mockk<GitIngestionEngine>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    private val service = BitbucketCommitsService(
        connectionRepository = connectionRepository,
        coordinatesFactory = coordinatesFactory,
        provider = BitbucketGitProvider(),
        ingestionEngine = ingestionEngine,
        eventPublisher = eventPublisher,
    )

    private val transactionId = UUID.randomUUID()
    private val sink = slot<GitCommitSink>()

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

    private fun givenIngestSucceeds(revision: String = NEW_REVISION) {
        every { connectionRepository.findById(connection.id) } returns Optional.of(connection)
        every { connectionRepository.updateCommitsCursor(any(), any()) } just Runs
        every { coordinatesFactory.of(connection) } returns coordinates
        coEvery {
            ingestionEngine.ingestCommitsSince(coordinates, any(), capture(sink))
        } returns GitIngestOutcome(revision, emptyList())
    }

    @Test
    fun `reads the whole history when no cursor is stored`() = runTest {
        givenIngestSucceeds()

        service.fetchAndIngestCommitsOfRepository(connection.id, transactionId)

        coVerify { ingestionEngine.ingestCommitsSince(coordinates, "", any()) }
    }

    @Test
    fun `advances from the commit cursor the last run stored`() = runTest {
        connection.lastCommitsSyncedSha = PREVIOUS_REVISION
        givenIngestSucceeds()

        service.fetchAndIngestCommitsOfRepository(connection.id, transactionId)

        coVerify { ingestionEngine.ingestCommitsSince(coordinates, PREVIOUS_REVISION, any()) }
    }

    @Test
    fun `stores the revision the run reached`() = runTest {
        givenIngestSucceeds()

        service.fetchAndIngestCommitsOfRepository(connection.id, transactionId)

        verify { connectionRepository.updateCommitsCursor(connection.id, NEW_REVISION) }
        verify(exactly = 0) { connectionRepository.save(any()) }
    }

    @Test
    fun `publishes one started and one completed event on success`() = runTest {
        givenIngestSucceeds()

        service.fetchAndIngestCommitsOfRepository(connection.id, transactionId)

        verify(exactly = 1) {
            eventPublisher.publishEvent(match<Any> { it is BitbucketCommitsFetchingStartedEvent })
        }
        verify(exactly = 1) {
            eventPublisher.publishEvent(match<Any> { it is BitbucketCommitsFetchingCompletedEvent })
        }
        verify(exactly = 0) {
            eventPublisher.publishEvent(match<Any> { it is BitbucketCommitsFetchingFailedEvent })
        }
    }

    @Test
    fun `maps a commit onto a bitbucket commit event`() = runTest {
        givenIngestSucceeds()
        service.fetchAndIngestCommitsOfRepository(connection.id, transactionId)

        val committedAt = Instant.parse("2026-03-04T05:06:07Z")
        sink.captured.onCommit(GitCommit("sha-1", "Ada", committedAt, "Fix the bug"))

        verify {
            eventPublisher.publishEvent(
                match<Any> {
                    it is BitbucketCommitFetchedEvent &&
                        it.sha == "sha-1" &&
                        it.author == "Ada" &&
                        it.subject == "Fix the bug" &&
                        it.committedAt == committedAt &&
                        it.repositoryId == connection.id &&
                        it.workspace == "sprintstart" &&
                        it.sourceUrl == "https://bitbucket.org/sprintstart/sprintstart-backend/commits/sha-1"
                },
            )
        }
    }

    @Test
    fun `fails without advancing the cursor when the ingest throws`() = runTest {
        every { connectionRepository.findById(connection.id) } returns Optional.of(connection)
        every { coordinatesFactory.of(connection) } returns coordinates
        coEvery { ingestionEngine.ingestCommitsSince(coordinates, any(), any()) } throws
            RuntimeException("git fetch failed (exit 128)")

        assertFailsWith<RuntimeException> {
            service.fetchAndIngestCommitsOfRepository(connection.id, transactionId)
        }

        assertThat(connection.lastCommitsSyncedSha).isEmpty()
        verify(exactly = 0) { connectionRepository.updateCommitsCursor(any(), any()) }
        verify(exactly = 0) { connectionRepository.save(any()) }
        verify(exactly = 1) {
            eventPublisher.publishEvent(
                match<Any> { it is BitbucketCommitsFetchingFailedEvent && it.reason.contains("exit 128") },
            )
        }
    }

    @Test
    fun `reports an unknown repository without publishing anything`() = runTest {
        every { connectionRepository.findById(any()) } returns Optional.empty()

        assertFailsWith<BitbucketRepositoryNotConnectedException> {
            service.fetchAndIngestCommitsOfRepository(connection.id, transactionId)
        }

        coVerify(exactly = 0) { ingestionEngine.ingestCommitsSince(any(), any(), any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any<Any>()) }
    }

    private companion object {
        const val NEW_REVISION = "abc123def456"
        const val PREVIOUS_REVISION = "000111222333"
    }
}
