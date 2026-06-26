package com.sprintstart.sprintstartbackend.github.service.internal

import com.sprintstart.sprintstartbackend.github.GithubClient
import com.sprintstart.sprintstartbackend.github.external.events.pullrequests.GithubPullRequestComment
import com.sprintstart.sprintstartbackend.github.external.events.pullrequests.GithubPullRequestFetchedEvent
import com.sprintstart.sprintstartbackend.github.external.events.pullrequests.GithubPullRequestReview
import com.sprintstart.sprintstartbackend.github.external.events.pullrequests.GithubPullRequestReviewThreadComment
import com.sprintstart.sprintstartbackend.github.external.events.pullrequests.GithubPullRequestsFetchingCompletedEvent
import com.sprintstart.sprintstartbackend.github.external.events.pullrequests.GithubPullRequestsFetchingFailedEvent
import com.sprintstart.sprintstartbackend.github.external.events.pullrequests.GithubPullRequestsFetchingStartedEvent
import com.sprintstart.sprintstartbackend.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.github.models.client.graphql.CommentNode
import com.sprintstart.sprintstartbackend.github.models.client.graphql.CommentsConnection
import com.sprintstart.sprintstartbackend.github.models.client.graphql.GithubActor
import com.sprintstart.sprintstartbackend.github.models.client.graphql.LabelNode
import com.sprintstart.sprintstartbackend.github.models.client.graphql.LabelsConnection
import com.sprintstart.sprintstartbackend.github.models.client.graphql.PullRequest
import com.sprintstart.sprintstartbackend.github.models.client.graphql.ReviewNode
import com.sprintstart.sprintstartbackend.github.models.client.graphql.ReviewThreadNode
import com.sprintstart.sprintstartbackend.github.models.client.graphql.ReviewThreadsConnection
import com.sprintstart.sprintstartbackend.github.models.client.graphql.ReviewsConnection
import com.sprintstart.sprintstartbackend.github.models.client.graphql.ThreadCommentNode
import com.sprintstart.sprintstartbackend.github.models.client.graphql.ThreadCommentsConnection
import com.sprintstart.sprintstartbackend.github.repository.GithubRepositoryConnectionRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.Optional
import java.util.UUID

class GithubPullRequestsServiceTest {
    private val repoConnectionRepository = mockk<GithubRepositoryConnectionRepository>()
    private val githubClient = mockk<GithubClient>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    private lateinit var service: GithubPullRequestsService

    private val transactionId = UUID.randomUUID()
    private val repo = GithubRepositoryConnection(owner = "owner", name = "repo")

    @BeforeEach
    fun setUp() {
        service = GithubPullRequestsService(
            repoConnectionRepository = repoConnectionRepository,
            githubClient = githubClient,
            eventPublisher = eventPublisher,
        )
    }

    @Nested
    inner class FetchRouting {
        @Test
        fun `fetches all pull requests without since filter when since is null`() = runTest {
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repo)
            coEvery { githubClient.fetchAllPullRequests("owner", "repo") } returns emptyList()

            service.fetchAndIngestAllPullRequests(repo.id, transactionId, since = null)

            coEvery { githubClient.fetchAllPullRequests("owner", "repo") }
        }

        @Test
        fun `fetches pull requests with since filter when since is provided`() = runTest {
            val since = Instant.parse("2024-01-01T00:00:00Z")
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repo)
            coEvery {
                githubClient.fetchAllPullRequests("owner", "repo", since.toString())
            } returns emptyList()

            service.fetchAndIngestAllPullRequests(repo.id, transactionId, since = since)

            coEvery { githubClient.fetchAllPullRequests("owner", "repo", since.toString()) }
        }
    }

    @Nested
    inner class EventPublishing {
        @Test
        fun `publishes one event per pull request plus summary`() = runTest {
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repo)
            coEvery { githubClient.fetchAllPullRequests("owner", "repo") } returns listOf(
                pullRequest(number = 1),
                pullRequest(number = 2),
                pullRequest(number = 3),
            )

            service.fetchAndIngestAllPullRequests(repo.id, transactionId)

            val events = mutableListOf<Any>()
            io.mockk.verify(exactly = 5) { eventPublisher.publishEvent(capture(events)) }
        }

        @Test
        fun `publishes lifecycle events when there are no pull requests`() = runTest {
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repo)
            coEvery { githubClient.fetchAllPullRequests("owner", "repo") } returns emptyList()

            service.fetchAndIngestAllPullRequests(repo.id, transactionId)

            val events = mutableListOf<Any>()
            io.mockk.verify(exactly = 2) { eventPublisher.publishEvent(capture(events)) }
            assertThat(events).anyMatch { it is GithubPullRequestsFetchingStartedEvent }
            assertThat(events).anyMatch { it is GithubPullRequestsFetchingCompletedEvent }
        }

        @Test
        fun `publishes GithubPullRequestsFetchingStartedEvent on start`() = runTest {
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repo)
            coEvery { githubClient.fetchAllPullRequests("owner", "repo") } returns emptyList()

            service.fetchAndIngestAllPullRequests(repo.id, transactionId)

            io.mockk.verify { eventPublisher.publishEvent(any<GithubPullRequestsFetchingStartedEvent>()) }
        }

        @Test
        fun `publishes GithubPullRequestsFetchingCompletedEvent on completion`() = runTest {
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repo)
            coEvery { githubClient.fetchAllPullRequests("owner", "repo") } returns emptyList()

            service.fetchAndIngestAllPullRequests(repo.id, transactionId)

            io.mockk.verify { eventPublisher.publishEvent(any<GithubPullRequestsFetchingCompletedEvent>()) }
        }

        @Test
        fun `publishes GithubPullRequestsFetchingFailedEvent when API fails`() = runTest {
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repo)
            coEvery { githubClient.fetchAllPullRequests("owner", "repo") } throws RuntimeException("API error")

            assertThrows<RuntimeException> {
                service.fetchAndIngestAllPullRequests(repo.id, transactionId)
            }

            io.mockk.verify { eventPublisher.publishEvent(any<GithubPullRequestsFetchingFailedEvent>()) }
        }
    }

    @Nested
    inner class EventFieldMapping {
        @Test
        fun `maps pull request fields to event correctly`() = runTest {
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repo)
            coEvery { githubClient.fetchAllPullRequests("owner", "repo") } returns listOf(
                pullRequest(
                    number = 42,
                    body = "PR body",
                    state = "MERGED",
                    createdAt = "2024-01-01T00:00:00Z",
                    mergedAt = "2024-01-02T00:00:00Z",
                    url = "https://github.com/owner/repo/pull/42",
                    authorLogin = "alice",
                ),
            )
            coEvery {
                repoConnectionRepository.findById(any())
            } returns Optional.of(repo)

            service.fetchAndIngestAllPullRequests(repo.id, transactionId)

            val eventSlot = slot<GithubPullRequestFetchedEvent>()
            io.mockk.verify { eventPublisher.publishEvent(capture(eventSlot)) }

            io.mockk.verify { eventPublisher.publishEvent(capture(eventSlot)) }
            with(eventSlot.captured) {
                assertThat(number).isEqualTo(42)
                assertThat(body).isEqualTo("PR body")
                assertThat(state).isEqualTo("MERGED")
                assertThat(createdAt).isEqualTo("2024-01-01T00:00:00Z")
                assertThat(mergedAt).isEqualTo("2024-01-02T00:00:00Z")
                assertThat(url).isEqualTo("https://github.com/owner/repo/pull/42")
                assertThat(author).isEqualTo("alice")
                assertThat(this.transactionId).isEqualTo(transactionId)
            }
        }

        @Test
        fun `maps null author to null in event`() = runTest {
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repo)
            coEvery { githubClient.fetchAllPullRequests("owner", "repo") } returns listOf(
                pullRequest(authorLogin = null),
            )

            val eventSlot = slot<GithubPullRequestFetchedEvent>()
            service.fetchAndIngestAllPullRequests(repo.id, transactionId)
            io.mockk.verify { eventPublisher.publishEvent(capture(eventSlot)) }

            assertThat(eventSlot.captured.author).isNull()
        }

        @Test
        fun `maps labels to list of name strings`() = runTest {
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repo)
            coEvery { githubClient.fetchAllPullRequests("owner", "repo") } returns listOf(
                pullRequest(labels = listOf("bug", "enhancement")),
            )

            val eventSlot = slot<GithubPullRequestFetchedEvent>()
            service.fetchAndIngestAllPullRequests(repo.id, transactionId)
            io.mockk.verify { eventPublisher.publishEvent(capture(eventSlot)) }

            assertThat(eventSlot.captured.labels).containsExactly("bug", "enhancement")
        }

        @Test
        fun `maps null labels to null in event`() = runTest {
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repo)
            coEvery { githubClient.fetchAllPullRequests("owner", "repo") } returns listOf(
                pullRequest(labels = null),
            )

            val eventSlot = slot<GithubPullRequestFetchedEvent>()
            service.fetchAndIngestAllPullRequests(repo.id, transactionId)
            io.mockk.verify { eventPublisher.publishEvent(capture(eventSlot)) }

            assertThat(eventSlot.captured.labels).isNull()
        }

        @Test
        fun `maps reviews correctly`() = runTest {
            val pr = pullRequest().copy(
                reviews = ReviewsConnection(
                    nodes = listOf(
                        ReviewNode(body = "LGTM", state = "APPROVED", author = GithubActor("reviewer")),
                    ),
                ),
            )
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repo)
            coEvery { githubClient.fetchAllPullRequests("owner", "repo") } returns listOf(pr)

            val eventSlot = slot<GithubPullRequestFetchedEvent>()
            service.fetchAndIngestAllPullRequests(repo.id, transactionId)
            io.mockk.verify { eventPublisher.publishEvent(capture(eventSlot)) }

            assertThat(eventSlot.captured.reviews).containsExactly(
                GithubPullRequestReview(body = "LGTM", state = "APPROVED", author = "reviewer"),
            )
        }

        @Test
        fun `maps comments correctly`() = runTest {
            val pr = pullRequest().copy(
                comments = CommentsConnection(
                    nodes = listOf(
                        CommentNode(
                            body = "Nice work",
                            author = GithubActor("commenter"),
                            createdAt = "2024-01-01T00:00:00Z",
                        ),
                    ),
                ),
            )
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repo)
            coEvery { githubClient.fetchAllPullRequests("owner", "repo") } returns listOf(pr)

            val eventSlot = slot<GithubPullRequestFetchedEvent>()
            service.fetchAndIngestAllPullRequests(repo.id, transactionId)
            io.mockk.verify { eventPublisher.publishEvent(capture(eventSlot)) }

            assertThat(eventSlot.captured.comments).containsExactly(
                GithubPullRequestComment(body = "Nice work", author = "commenter", createdAt = "2024-01-01T00:00:00Z"),
            )
        }

        @Test
        fun `maps review threads with nested comments correctly`() = runTest {
            val pr = pullRequest().copy(
                reviewThreads = ReviewThreadsConnection(
                    nodes = listOf(
                        ReviewThreadNode(
                            comments = ThreadCommentsConnection(
                                nodes = listOf(
                                    ThreadCommentNode(
                                        body = "Nit",
                                        author = GithubActor("reviewer"),
                                        path = "src/Main.kt",
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            )
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repo)
            coEvery { githubClient.fetchAllPullRequests("owner", "repo") } returns listOf(pr)

            val eventSlot = slot<GithubPullRequestFetchedEvent>()
            service.fetchAndIngestAllPullRequests(repo.id, transactionId)
            io.mockk.verify { eventPublisher.publishEvent(capture(eventSlot)) }

            val thread = eventSlot.captured.reviewThreads!!.first()
            assertThat(thread.comments).containsExactly(
                GithubPullRequestReviewThreadComment(body = "Nit", author = "reviewer", path = "src/Main.kt"),
            )
        }

        @Test
        fun `maps null review thread comments to empty list`() = runTest {
            val pr = pullRequest().copy(
                reviewThreads = ReviewThreadsConnection(
                    nodes = listOf(ReviewThreadNode(comments = null)),
                ),
            )
            coEvery { repoConnectionRepository.findById(any()) } returns Optional.of(repo)
            coEvery { githubClient.fetchAllPullRequests("owner", "repo") } returns listOf(pr)

            val eventSlot = slot<GithubPullRequestFetchedEvent>()
            service.fetchAndIngestAllPullRequests(repo.id, transactionId)
            io.mockk.verify { eventPublisher.publishEvent(capture(eventSlot)) }

            assertThat(
                eventSlot.captured.reviewThreads!!
                    .first()
                    .comments,
            ).isEmpty()
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun pullRequest(
        number: Int = 1,
        body: String? = "body",
        state: String = "OPEN",
        createdAt: String = "2024-01-01T00:00:00Z",
        mergedAt: String? = null,
        url: String = "https://github.com/owner/repo/pull/$number",
        authorLogin: String? = "author",
        labels: List<String>? = emptyList(),
    ) = PullRequest(
        number = number,
        title = "PR $number",
        body = body,
        state = state,
        createdAt = createdAt,
        mergedAt = mergedAt,
        url = url,
        author = authorLogin?.let { GithubActor(it) },
        labels = labels?.let { LabelsConnection(it.map { name -> LabelNode(name) }) },
        reviews = null,
        comments = null,
        reviewThreads = null,
    )
}
