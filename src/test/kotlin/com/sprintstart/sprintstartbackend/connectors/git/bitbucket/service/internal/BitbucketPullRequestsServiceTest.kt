package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal

import com.sprintstart.sprintstartbackend.connectors.atlassian.external.AtlassianCredentialApi
import com.sprintstart.sprintstartbackend.connectors.atlassian.external.AtlassianCredentialSecret
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.exception.AtlassianCredentialNotFoundException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.BitbucketClient
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.prs.BitbucketPullRequestFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.prs.BitbucketPullRequestsFetchingCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.prs.BitbucketPullRequestsFetchingFailedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.prs.BitbucketPullRequestsFetchingStartedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.client.BitbucketAccount
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.client.BitbucketCommentContent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.client.BitbucketLink
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.client.BitbucketLinks
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.client.BitbucketMergeCommit
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.client.BitbucketParticipant
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.client.PullRequest
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.client.PullRequestComment
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketConnectionRepository
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

class BitbucketPullRequestsServiceTest {
    private val connectionRepository = mockk<BitbucketConnectionRepository>()
    private val credentialApi = mockk<AtlassianCredentialApi>()
    private val bitbucketClient = mockk<BitbucketClient>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    private val service = BitbucketPullRequestsService(
        connectionRepository = connectionRepository,
        credentialApi = credentialApi,
        bitbucketClient = bitbucketClient,
        eventPublisher = eventPublisher,
    )

    private val transactionId = UUID.randomUUID()

    private val connection = BitbucketConnection(
        workspace = "sprintstart",
        slug = "sprintstart-backend",
        credentialAuthId = "auth-id",
        credentialName = "team-token",
    )

    private fun givenFetchSucceeds(
        pullRequests: List<PullRequest> = listOf(pullRequest()),
        comments: List<PullRequestComment> = emptyList(),
    ) {
        every { connectionRepository.findById(connection.id) } returns Optional.of(connection)
        every { connectionRepository.updatePullRequestsCursor(any(), any()) } just Runs
        every { credentialApi.findSecret("auth-id", "team-token") } returns secret()
        coEvery { bitbucketClient.fetchAllPullRequests(any(), any(), any(), any()) } returns pullRequests
        coEvery { bitbucketClient.fetchAllPullRequestComments(any(), any(), any(), any()) } returns comments
    }

    // ── full versus incremental ───────────────────────────────────────────────

    @Test
    fun `reads the whole pull request history when no cursor is stored`() = runTest {
        givenFetchSucceeds()

        service.fetchAndIngestPullRequests(connection.id, transactionId)

        coVerify {
            bitbucketClient.fetchAllPullRequests("sprintstart", "sprintstart-backend", "api-token", null)
        }
    }

    @Test
    fun `advances from the pull request cursor the last run stored`() = runTest {
        val cursor = Instant.parse("2026-03-01T10:00:00Z")
        connection.lastPullRequestsSyncAt = cursor
        givenFetchSucceeds()

        service.fetchAndIngestPullRequests(connection.id, transactionId)

        coVerify {
            bitbucketClient.fetchAllPullRequests(
                "sprintstart",
                "sprintstart-backend",
                "api-token",
                cursor.toString(),
            )
        }
    }

    @Test
    fun `stores the sync timestamp after a successful run`() = runTest {
        givenFetchSucceeds()

        service.fetchAndIngestPullRequests(connection.id, transactionId)

        verify { connectionRepository.updatePullRequestsCursor(connection.id, any()) }
        verify(exactly = 0) { connectionRepository.save(any()) }
    }

    /**
     * The stored cursor must not move past the moment the fetch was issued. Bitbucket filters pull
     * requests by `updated_on >= since`, so a cursor advanced to the completion time would sit after
     * every pull request updated while the fetch was running and skip them permanently.
     */
    @Test
    fun `stores the instant the fetch started so pull requests updated during it are not skipped`() = runTest {
        givenFetchSucceeds()
        var fetchStartedAt: Instant? = null
        coEvery { bitbucketClient.fetchAllPullRequests(any(), any(), any(), any()) } answers {
            fetchStartedAt = Instant.now()
            listOf(pullRequest())
        }
        val cursor = slot<Instant>()

        service.fetchAndIngestPullRequests(connection.id, transactionId)

        verify { connectionRepository.updatePullRequestsCursor(connection.id, capture(cursor)) }
        assertThat(cursor.captured).isBeforeOrEqualTo(fetchStartedAt)
    }

    @Test
    fun `does not move the cursor when the fetch fails`() = runTest {
        val cursor = Instant.parse("2026-03-01T10:00:00Z")
        connection.lastPullRequestsSyncAt = cursor
        every { connectionRepository.findById(connection.id) } returns Optional.of(connection)
        every { connectionRepository.updatePullRequestsCursor(any(), any()) } just Runs
        every { credentialApi.findSecret("auth-id", "team-token") } returns secret()
        coEvery { bitbucketClient.fetchAllPullRequests(any(), any(), any(), any()) } throws
            IllegalStateException("bitbucket is down")

        assertFailsWith<IllegalStateException> {
            service.fetchAndIngestPullRequests(connection.id, transactionId)
        }

        // A failed run must leave the cursor where it was, so the next run re-reads the same window
        // instead of skipping everything the failed run never managed to fetch.
        assertThat(connection.lastPullRequestsSyncAt).isEqualTo(cursor)
        verify(exactly = 0) { connectionRepository.updatePullRequestsCursor(any(), any()) }
        verify(exactly = 0) { connectionRepository.save(any()) }
    }

    // ── terminal events ───────────────────────────────────────────────────────

    @Test
    fun `publishes exactly one started and one completed event on success`() = runTest {
        givenFetchSucceeds()

        service.fetchAndIngestPullRequests(connection.id, transactionId)

        verify(exactly = 1) {
            eventPublisher.publishEvent(match<Any> { it is BitbucketPullRequestsFetchingStartedEvent })
        }
        verify(exactly = 1) {
            eventPublisher.publishEvent(match<Any> { it is BitbucketPullRequestsFetchingCompletedEvent })
        }
        verify(exactly = 0) {
            eventPublisher.publishEvent(match<Any> { it is BitbucketPullRequestsFetchingFailedEvent })
        }
    }

    @Test
    fun `fails without advancing the cursor when the fetch throws`() = runTest {
        every { connectionRepository.findById(connection.id) } returns Optional.of(connection)
        every { credentialApi.findSecret("auth-id", "team-token") } returns secret()
        coEvery { bitbucketClient.fetchAllPullRequests(any(), any(), any(), any()) } throws
            RuntimeException("Bitbucket is unavailable")

        assertFailsWith<RuntimeException> {
            service.fetchAndIngestPullRequests(connection.id, transactionId)
        }

        assertThat(connection.lastPullRequestsSyncAt).isNull()
        verify(exactly = 0) { connectionRepository.updatePullRequestsCursor(any(), any()) }
        verify(exactly = 0) { connectionRepository.save(any()) }
        verify(exactly = 1) {
            eventPublisher.publishEvent(
                match<Any> {
                    it is BitbucketPullRequestsFetchingFailedEvent && it.reason?.contains("unavailable") == true
                },
            )
        }
    }

    // ── error handling ────────────────────────────────────────────────────────

    @Test
    fun `reports an unknown repository without publishing anything`() = runTest {
        every { connectionRepository.findById(any()) } returns Optional.empty()

        assertFailsWith<BitbucketRepositoryNotConnectedException> {
            service.fetchAndIngestPullRequests(connection.id, transactionId)
        }

        coVerify(exactly = 0) { bitbucketClient.fetchAllPullRequests(any(), any(), any(), any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any<Any>()) }
    }

    /**
     * A credential that can no longer be resolved must still close the pull-request phase. A run is
     * only finalized once every phase has reported, so a phase that died before opening itself would
     * leave the run open forever rather than failing it.
     */
    @Test
    fun `closes the pull request phase when the connection credential is missing`() = runTest {
        every { connectionRepository.findById(connection.id) } returns Optional.of(connection)
        every { credentialApi.findSecret("auth-id", "team-token") } returns null

        assertFailsWith<AtlassianCredentialNotFoundException> {
            service.fetchAndIngestPullRequests(connection.id, transactionId)
        }

        coVerify(exactly = 0) { bitbucketClient.fetchAllPullRequests(any(), any(), any(), any()) }
        verify(exactly = 1) {
            eventPublisher.publishEvent(match<Any> { it is BitbucketPullRequestsFetchingStartedEvent })
        }
        verify(exactly = 1) {
            eventPublisher.publishEvent(match<Any> { it is BitbucketPullRequestsFetchingFailedEvent })
        }
        verify(exactly = 0) {
            eventPublisher.publishEvent(match<Any> { it is BitbucketPullRequestsFetchingCompletedEvent })
        }
    }

    // ── mapping ───────────────────────────────────────────────────────────────

    @Test
    fun `fetches the comments of every pull request`() = runTest {
        givenFetchSucceeds(pullRequests = listOf(pullRequest(id = 7), pullRequest(id = 8)))

        service.fetchAndIngestPullRequests(connection.id, transactionId)

        coVerify { bitbucketClient.fetchAllPullRequestComments("sprintstart", "sprintstart-backend", 7, "api-token") }
        coVerify { bitbucketClient.fetchAllPullRequestComments("sprintstart", "sprintstart-backend", 8, "api-token") }
    }

    @Test
    fun `publishes one event per pull request`() = runTest {
        givenFetchSucceeds(pullRequests = listOf(pullRequest(id = 7), pullRequest(id = 8)))

        service.fetchAndIngestPullRequests(connection.id, transactionId)

        verify(exactly = 2) {
            eventPublisher.publishEvent(match<Any> { it is BitbucketPullRequestFetchedEvent })
        }
    }

    @Suppress("CyclomaticComplexMethod")
    @Test
    fun `maps a pull request onto a bitbucket event with participants, comments and source url`() = runTest {
        val url = "https://bitbucket.org/sprintstart/sprintstart-backend/pull-requests/7"
        givenFetchSucceeds(
            pullRequests = listOf(pullRequest(url = url)),
            comments = listOf(
                PullRequestComment(
                    id = 100,
                    createdOn = "2026-03-02T09:30:00Z",
                    updatedOn = "2026-03-02T09:45:00Z",
                    content = BitbucketCommentContent(raw = "the raw comment text", markup = "markdown"),
                    user = BitbucketAccount(accountId = "acc-2", nickname = "grace", displayName = "Grace Hopper"),
                    deleted = false,
                ),
            ),
        )

        service.fetchAndIngestPullRequests(connection.id, transactionId)

        verify {
            eventPublisher.publishEvent(
                match<Any> {
                    it is BitbucketPullRequestFetchedEvent &&
                        it.transactionId == transactionId &&
                        it.repositoryId == connection.id &&
                        it.workspace == "sprintstart" &&
                        it.slug == "sprintstart-backend" &&
                        it.number == 7 &&
                        it.title == "Add onboarding path" &&
                        it.state == "MERGED" &&
                        it.authorId == "acc-1" &&
                        it.authorNickname == "ada" &&
                        it.authorDisplayName == "Ada Lovelace" &&
                        it.createdOn == "2026-03-01T10:00:00Z" &&
                        it.updatedOn == "2026-03-02T10:00:00Z" &&
                        it.description == "Adds the first onboarding path" &&
                        it.closedOn == "2026-03-02T10:00:00Z" &&
                        it.mergeCommitHash == "merge-sha" &&
                        it.sourceUrl == url &&
                        it.participants.size == 1 &&
                        it.participants.first().authorId == "acc-1" &&
                        it.participants.first().role == "PARTICIPANT" &&
                        it.participants.first().approved &&
                        it.participants.first().state == "approved" &&
                        it.comments.size == 1 &&
                        it.comments.first().id == 100 &&
                        it.comments.first().content == "the raw comment text" &&
                        it.comments.first().authorId == "acc-2" &&
                        it.comments.first().displayName == "Grace Hopper" &&
                        !it.comments.first().deleted
                },
            )
        }
    }

    private fun pullRequest(id: Int = 7, url: String? = null) = PullRequest(
        id = id,
        title = "Add onboarding path",
        state = "MERGED",
        author = BitbucketAccount(accountId = "acc-1", nickname = "ada", displayName = "Ada Lovelace"),
        createdOn = "2026-03-01T10:00:00Z",
        updatedOn = "2026-03-02T10:00:00Z",
        description = "Adds the first onboarding path",
        closedOn = "2026-03-02T10:00:00Z",
        mergeCommit = BitbucketMergeCommit(hash = "merge-sha"),
        participants = listOf(
            BitbucketParticipant(
                user = BitbucketAccount(accountId = "acc-1", nickname = "ada", displayName = "Ada Lovelace"),
                role = "PARTICIPANT",
                approved = true,
                participatedOn = "2026-03-02T09:00:00Z",
                state = "approved",
            ),
        ),
        links = url?.let { BitbucketLinks(html = BitbucketLink(href = it)) },
    )

    private fun secret() = AtlassianCredentialSecret(userEmail = "user@example.com", apiToken = "api-token")
}
