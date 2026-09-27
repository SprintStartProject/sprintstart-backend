package com.sprintstart.sprintstartbackend.connectors.github.service.internal

import com.sprintstart.sprintstartbackend.connectors.ConnectionState
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.commits.GithubCommitFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.commits.GithubCommitsFetchCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.commits.GithubCommitsFetchFailedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.commits.GithubCommitsFetchStartedEvent
import com.sprintstart.sprintstartbackend.connectors.git.utils.GitCommitSink
import com.sprintstart.sprintstartbackend.connectors.git.utils.GitIngestOutcome
import com.sprintstart.sprintstartbackend.connectors.git.utils.GitIngestionEngine
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubUser
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubUserPat
import com.sprintstart.sprintstartbackend.connectors.github.repository.GithubRepositoryConnectionRepository
import com.sprintstart.sprintstartbackend.shared.git.GitCommit
import com.sprintstart.sprintstartbackend.shared.git.GitRepositoryCoordinates
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.UUID
import kotlin.test.assertFailsWith

class GithubCommitsServiceTest {
    private val repoConnectionRepository = mockk<GithubRepositoryConnectionRepository>()
    private val coordinatesFactory = mockk<GithubRepositoryCoordinatesFactory>()
    private val ingestionEngine = mockk<GitIngestionEngine>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    private val service = GithubCommitsService(
        repoConnectionRepository = repoConnectionRepository,
        coordinatesFactory = coordinatesFactory,
        ingestionEngine = ingestionEngine,
        eventPublisher = eventPublisher,
    )

    private val transactionId = UUID.randomUUID()
    private val sink = slot<GitCommitSink>()

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

    private fun givenIngestSucceeds(revision: String = NEW_REVISION) {
        every { coordinatesFactory.of(connection) } returns coordinates
        every { repoConnectionRepository.save(any()) } returns connection
        coEvery {
            ingestionEngine.ingestCommitsSince(coordinates, any(), capture(sink))
        } returns GitIngestOutcome(revision, emptyList())
    }

    // ── full versus incremental ───────────────────────────────────────────────

    @Test
    fun `reads the whole history when no cursor is stored`() = runTest {
        givenIngestSucceeds()

        service.fetchAndIngestCommits(connection, transactionId)

        coVerify { ingestionEngine.ingestCommitsSince(coordinates, "", any()) }
    }

    @Test
    fun `advances from the commit cursor the last run stored`() = runTest {
        connection.lastCommitsSyncedSha = PREVIOUS_REVISION
        givenIngestSucceeds()

        service.fetchAndIngestCommits(connection, transactionId)

        coVerify { ingestionEngine.ingestCommitsSince(coordinates, PREVIOUS_REVISION, any()) }
    }

    @Test
    fun `stores the revision the run reached`() = runTest {
        givenIngestSucceeds()

        service.fetchAndIngestCommits(connection, transactionId)

        assertThat(connection.lastCommitsSyncedSha).isEqualTo(NEW_REVISION)
        verify { repoConnectionRepository.save(connection) }
    }

    // ── terminal events ───────────────────────────────────────────────────────

    @Test
    fun `publishes one started and one completed event on success`() = runTest {
        givenIngestSucceeds()

        service.fetchAndIngestCommits(connection, transactionId)

        verify(exactly = 1) {
            eventPublisher.publishEvent(match<Any> { it is GithubCommitsFetchStartedEvent })
        }
        verify(exactly = 1) {
            eventPublisher.publishEvent(match<Any> { it is GithubCommitsFetchCompletedEvent })
        }
        verify(exactly = 0) {
            eventPublisher.publishEvent(match<Any> { it is GithubCommitsFetchFailedEvent })
        }
    }

    @Test
    fun `fails without advancing the cursor when the ingest throws`() = runTest {
        every { coordinatesFactory.of(connection) } returns coordinates
        coEvery { ingestionEngine.ingestCommitsSince(coordinates, any(), any()) } throws
            RuntimeException("git fetch failed (exit 128)")

        assertFailsWith<RuntimeException> {
            service.fetchAndIngestCommits(connection, transactionId)
        }

        assertThat(connection.lastCommitsSyncedSha).isEmpty()
        verify(exactly = 0) { repoConnectionRepository.save(any()) }
        verify(exactly = 1) {
            eventPublisher.publishEvent(
                match<Any> { it is GithubCommitsFetchFailedEvent && it.reason.contains("exit 128") },
            )
        }
    }

    // ── the provider binding ──────────────────────────────────────────────────

    @Test
    fun `maps a commit onto a github commit event`() = runTest {
        givenIngestSucceeds()
        service.fetchAndIngestCommits(connection, transactionId)

        val committedAt = Instant.parse("2026-03-04T05:06:07Z")
        sink.captured.onCommit(GitCommit("sha-1", "Ada", committedAt, "Fix the bug"))

        verify {
            eventPublisher.publishEvent(
                match<Any> {
                    it is GithubCommitFetchedEvent &&
                        it.sha == "sha-1" &&
                        it.author == "Ada" &&
                        it.msg == "Fix the bug" &&
                        it.date == committedAt &&
                        it.repositoryId == connection.id &&
                        it.repositoryOwner == "owner" &&
                        it.repositoryName == "repo"
                },
            )
        }
    }

    // ── verifyCommitSyncStatus ────────────────────────────────────────────────

    @Test
    fun `marks the repository out of date when the clone no longer matches the remote`() = runTest {
        every { coordinatesFactory.of(connection) } returns coordinates
        every { repoConnectionRepository.save(connection) } returns connection
        coEvery { ingestionEngine.isUpToDate(coordinates) } returns false

        service.verifyCommitSyncStatus(connection, transactionId)

        assertThat(connection.connectionState).isEqualTo(ConnectionState.OUT_OF_DATE)
        verify { repoConnectionRepository.save(connection) }
    }

    @Test
    fun `leaves an up-to-date repository untouched`() = runTest {
        every { coordinatesFactory.of(connection) } returns coordinates
        coEvery { ingestionEngine.isUpToDate(coordinates) } returns true

        service.verifyCommitSyncStatus(connection, transactionId)

        assertThat(connection.connectionState).isEqualTo(ConnectionState.UP_TO_DATE)
        verify(exactly = 0) { repoConnectionRepository.save(any()) }
        verify { eventPublisher.publishEvent(any<GithubCommitsFetchStartedEvent>()) }
        verify { eventPublisher.publishEvent(any<GithubCommitsFetchCompletedEvent>()) }
    }

    private companion object {
        const val NEW_REVISION = "abc123def456"
        const val PREVIOUS_REVISION = "000111222333"
    }
}
