package com.sprintstart.sprintstartbackend.github.service.internal

import com.sprintstart.sprintstartbackend.github.GithubClient
import com.sprintstart.sprintstartbackend.github.external.events.GithubIssueComment
import com.sprintstart.sprintstartbackend.github.external.events.GithubIssueFetchedEvent
import com.sprintstart.sprintstartbackend.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.github.models.GithubUser
import com.sprintstart.sprintstartbackend.github.models.GithubUserPat
import com.sprintstart.sprintstartbackend.github.models.client.graphql.AssigneesCollection
import com.sprintstart.sprintstartbackend.github.models.client.graphql.CommentNode
import com.sprintstart.sprintstartbackend.github.models.client.graphql.CommentsConnection
import com.sprintstart.sprintstartbackend.github.models.client.graphql.GithubActor
import com.sprintstart.sprintstartbackend.github.models.client.graphql.Issue
import com.sprintstart.sprintstartbackend.github.models.client.graphql.LabelNode
import com.sprintstart.sprintstartbackend.github.models.client.graphql.LabelsConnection
import com.sprintstart.sprintstartbackend.github.repository.GithubRepositoryConnectionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.Optional
import java.util.UUID

class GithubIssuesServiceTest {
    private val repoConnectionRepository = mockk<GithubRepositoryConnectionRepository>()
    private val githubClient = mockk<GithubClient>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    private lateinit var service: GithubIssuesService

    private val transactionId = UUID.randomUUID()
    private val user = GithubUser(
        id = GithubUserPat("auth-id", "token-name"),
        token = "test-token",
    )
    private val repo = GithubRepositoryConnection(owner = "owner", name = "repo", user = user)

    @BeforeEach
    fun setUp() {
        service = GithubIssuesService(
            repoConnectionRepository = repoConnectionRepository,
            githubClient = githubClient,
            eventPublisher = eventPublisher,
        )
    }

    @Nested
    inner class FetchRouting {
        @Test
        fun `passes null sinceTimestamp to client when since is null`() = runTest {
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repo)
            coEvery { githubClient.fetchIssues(repo, null) } returns emptyList()

            service.fetchAndIngestAllIssues(repo.id, transactionId, since = null)

            coVerify { githubClient.fetchIssues(repo, null) }
        }

        @Test
        fun `passes formatted sinceTimestamp to client when since is provided`() = runTest {
            val since = Instant.parse("2024-01-01T00:00:00Z")
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repo)
            coEvery { githubClient.fetchIssues(repo, since.toString()) } returns emptyList()

            service.fetchAndIngestAllIssues(repo.id, transactionId, since = since)

            coVerify { githubClient.fetchIssues(repo, since.toString()) }
        }
    }

    @Nested
    inner class EventPublishing {
        @Test
        fun `publishes one event per issue plus summary`() = runTest {
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repo)
            coEvery { githubClient.fetchIssues(repo, null) } returns listOf(
                issue(number = 1),
                issue(number = 2),
                issue(number = 3),
            )

            service.fetchAndIngestAllIssues(repo.id, transactionId)

            verify(exactly = 4) {
                eventPublisher.publishEvent(any<GithubIssueFetchedEvent>())
            }
        }

        @Test
        fun `publishes no events when there are no issues`() = runTest {
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repo)
            coEvery { githubClient.fetchIssues(repo, null) } returns emptyList()

            service.fetchAndIngestAllIssues(repo.id, transactionId)

            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
        }
    }

    @Nested
    inner class EventFieldMapping {
        @Test
        fun `maps issue fields to event correctly`() = runTest {
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repo)
            coEvery { githubClient.fetchIssues(repo, null) } returns listOf(
                issue(
                    number = 42,
                    title = "Bug report",
                    body = "Something is broken",
                    state = "OPEN",
                    createdAt = "2024-01-01T00:00:00Z",
                    closedAt = null,
                    url = "https://github.com/owner/repo/issues/42",
                    authorLogin = "alice",
                ),
            )

            service.fetchAndIngestAllIssues(repo.id, transactionId)

            val eventSlot = slot<GithubIssueFetchedEvent>()
            verify { eventPublisher.publishEvent(capture(eventSlot)) }

            with(eventSlot.captured) {
                assertThat(number).isEqualTo(42)
                assertThat(title).isEqualTo("Bug report")
                assertThat(body).isEqualTo("Something is broken")
                assertThat(state).isEqualTo("OPEN")
                assertThat(createdAt).isEqualTo("2024-01-01T00:00:00Z")
                assertThat(closedAt).isNull()
                assertThat(url).isEqualTo("https://github.com/owner/repo/issues/42")
                assertThat(author).isEqualTo("alice")
                assertThat(this.transactionId).isEqualTo(transactionId)
            }
        }

        @Test
        fun `maps null author to null in event`() = runTest {
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repo)
            coEvery { githubClient.fetchIssues(repo, null) } returns listOf(
                issue(authorLogin = null),
            )

            service.fetchAndIngestAllIssues(repo.id, transactionId)

            val eventSlot = slot<GithubIssueFetchedEvent>()
            verify { eventPublisher.publishEvent(capture(eventSlot)) }

            assertThat(eventSlot.captured.author).isNull()
        }

        @Test
        fun `maps labels to list of name strings`() = runTest {
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repo)
            coEvery { githubClient.fetchIssues(repo, null) } returns listOf(
                issue(labels = listOf("bug", "help wanted")),
            )

            service.fetchAndIngestAllIssues(repo.id, transactionId)

            val eventSlot = slot<GithubIssueFetchedEvent>()
            verify { eventPublisher.publishEvent(capture(eventSlot)) }

            assertThat(eventSlot.captured.labels).containsExactly("bug", "help wanted")
        }

        @Test
        fun `maps null labels to empty list`() = runTest {
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repo)
            coEvery { githubClient.fetchIssues(repo, null) } returns listOf(
                issue(labels = null),
            )

            service.fetchAndIngestAllIssues(repo.id, transactionId)

            val eventSlot = slot<GithubIssueFetchedEvent>()
            verify { eventPublisher.publishEvent(capture(eventSlot)) }

            assertThat(eventSlot.captured.labels).isEmpty()
        }

        @Test
        fun `maps assignees to list of login strings`() = runTest {
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repo)
            coEvery { githubClient.fetchIssues(repo, null) } returns listOf(
                issue(assignees = listOf("alice", "bob")),
            )

            service.fetchAndIngestAllIssues(repo.id, transactionId)

            val eventSlot = slot<GithubIssueFetchedEvent>()
            verify { eventPublisher.publishEvent(capture(eventSlot)) }

            assertThat(eventSlot.captured.assignees).containsExactly("alice", "bob")
        }

        @Test
        fun `maps null assignees to empty list`() = runTest {
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repo)
            coEvery { githubClient.fetchIssues(repo, null) } returns listOf(
                issue(assignees = null),
            )

            service.fetchAndIngestAllIssues(repo.id, transactionId)

            val eventSlot = slot<GithubIssueFetchedEvent>()
            verify { eventPublisher.publishEvent(capture(eventSlot)) }

            assertThat(eventSlot.captured.assignees).isEmpty()
        }

        @Test
        fun `maps comments correctly`() = runTest {
            val issueWithComments = issue().copy(
                comments = CommentsConnection(
                    nodes = listOf(
                        CommentNode(
                            body = "Great issue",
                            author = GithubActor("commenter"),
                            createdAt = "2024-01-02T00:00:00Z",
                        ),
                    ),
                ),
            )
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repo)
            coEvery { githubClient.fetchIssues(repo, null) } returns listOf(issueWithComments)

            service.fetchAndIngestAllIssues(repo.id, transactionId)

            val eventSlot = slot<GithubIssueFetchedEvent>()
            verify { eventPublisher.publishEvent(capture(eventSlot)) }

            assertThat(eventSlot.captured.comments).containsExactly(
                GithubIssueComment(
                    body = "Great issue",
                    author = "commenter",
                    createdAt = "2024-01-02T00:00:00Z",
                ),
            )
        }

        @Test
        fun `maps null comments to empty list`() = runTest {
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repo)
            coEvery { githubClient.fetchIssues(repo, null) } returns listOf(
                issue().copy(comments = null),
            )

            service.fetchAndIngestAllIssues(repo.id, transactionId)

            val eventSlot = slot<GithubIssueFetchedEvent>()
            verify { eventPublisher.publishEvent(capture(eventSlot)) }

            assertThat(eventSlot.captured.comments).isEmpty()
        }

        @Test
        fun `maps comment with null author correctly`() = runTest {
            val issueWithAnonymousComment = issue().copy(
                comments = CommentsConnection(
                    nodes = listOf(
                        CommentNode(body = "Anonymous comment", author = null, createdAt = "2024-01-02T00:00:00Z"),
                    ),
                ),
            )
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repo)
            coEvery { githubClient.fetchIssues(repo, null) } returns listOf(issueWithAnonymousComment)

            service.fetchAndIngestAllIssues(repo.id, transactionId)

            val eventSlot = slot<GithubIssueFetchedEvent>()
            verify { eventPublisher.publishEvent(capture(eventSlot)) }

            assertThat(
                eventSlot.captured.comments
                    .first()
                    .author,
            ).isNull()
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun issue(
        number: Int = 1,
        title: String = "Issue $number",
        body: String = "Body of issue $number",
        state: String = "OPEN",
        createdAt: String = "2024-01-01T00:00:00Z",
        closedAt: String? = null,
        url: String = "https://github.com/owner/repo/issues/$number",
        authorLogin: String? = "author",
        labels: List<String>? = emptyList(),
        assignees: List<String>? = emptyList(),
    ) = Issue(
        number = number,
        title = title,
        body = body,
        state = state,
        createdAt = createdAt,
        updatedAt = "2024-01-01T00:00:00Z",
        closedAt = closedAt,
        url = url,
        author = authorLogin?.let { GithubActor(it) },
        labels = labels?.let { LabelsConnection(it.map { name -> LabelNode(name) }) },
        assignees = assignees?.let { AssigneesCollection(it.map { login -> GithubActor(login) }) },
        comments = null,
    )
}
