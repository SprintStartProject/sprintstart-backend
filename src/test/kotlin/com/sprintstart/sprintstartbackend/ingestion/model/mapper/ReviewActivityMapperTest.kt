package com.sprintstart.sprintstartbackend.ingestion.model.mapper

import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.pullrequests.GithubPullRequestFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.pullrequests.GithubPullRequestFiles
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.pullrequests.GithubPullRequestReview
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.SubmittedReview
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ReviewState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ReviewActivityMapperTest {
    private val mapper = ReviewActivityMapper()
    private val submitted = "2024-01-04T00:00:00Z"

    private fun review(author: String?, state: String = "APPROVED", submittedAt: String? = submitted) =
        GithubPullRequestReview(body = "text", state = state, author = author, submittedAt = submittedAt)

    private fun event(
        reviews: List<GithubPullRequestReview>? = null,
        files: GithubPullRequestFiles = GithubPullRequestFiles.Listed(listOf("a.kt")),
        author: String? = "bob",
    ) = GithubPullRequestFetchedEvent(
        transactionId = UUID.randomUUID(),
        repositoryId = UUID.randomUUID(),
        repositoryOwner = "acme",
        repositoryName = "backend",
        number = 7,
        title = "Improve docs",
        body = null,
        state = "MERGED",
        createdAt = "2024-01-02T00:00:00Z",
        mergedAt = null,
        url = "https://github.com/acme/backend/pull/7",
        author = author,
        labels = null,
        reviews = reviews,
        comments = null,
        reviewThreads = null,
        files = files,
    )

    @Test
    fun `identifies the pull request artifact and its repository`() {
        val command = mapper.toCommand(event())

        assertThat(command.sourceId).isEqualTo("github:acme/backend:PULL_REQUEST:7")
        assertThat(command.repository).isEqualTo("github:acme/backend")
    }

    @Test
    fun `attaches the reviews to the artifact the artifact mapper stores for that pull request`() {
        val event = event()

        assertThat(mapper.toCommand(event).sourceId).isEqualTo(GithubArtifactMapper().toCommand(event).sourceId)
    }

    @Test
    fun `maps a submitted review with its reviewer in lower case`() {
        val command = mapper.toCommand(event(reviews = listOf(review("Alice", "CHANGES_REQUESTED"))))

        assertThat(command.reviews).containsExactly(
            SubmittedReview("alice", ReviewState.CHANGES_REQUESTED, Instant.parse(submitted)),
        )
    }

    @Test
    fun `maps every state that says the reviewer looked at the change`() {
        val states = listOf("APPROVED", "CHANGES_REQUESTED", "COMMENTED", "DISMISSED")

        val command = mapper.toCommand(event(reviews = states.map { review("alice", it) }))

        assertThat(command.reviews.map { it.state }).containsExactly(*ReviewState.entries.toTypedArray())
    }

    @Test
    fun `drops a review that is still a draft`() {
        val command = mapper.toCommand(event(reviews = listOf(review("alice", "PENDING", submittedAt = null))))

        assertThat(command.reviews).isEmpty()
    }

    @Test
    fun `drops a review without a submission time`() {
        assertThat(mapper.toCommand(event(reviews = listOf(review("alice", submittedAt = null)))).reviews).isEmpty()
    }

    @Test
    fun `drops a review in a state it does not know`() {
        assertThat(mapper.toCommand(event(reviews = listOf(review("alice", "SOMETHING_NEW")))).reviews).isEmpty()
    }

    @Test
    fun `drops a review by an unknown author`() {
        assertThat(mapper.toCommand(event(reviews = listOf(review(null)))).reviews).isEmpty()
    }

    @Test
    fun `drops the pull request author's own review whatever the case of the login`() {
        val command = mapper.toCommand(event(reviews = listOf(review("BOB"), review("alice")), author = "Bob"))

        assertThat(command.reviews.map { it.reviewer }).containsExactly("alice")
    }

    @Test
    fun `keeps every review when the pull request author is unknown`() {
        val command = mapper.toCommand(event(reviews = listOf(review("alice")), author = null))

        assertThat(command.reviews.map { it.reviewer }).containsExactly("alice")
    }

    @Test
    fun `has no reviews when the event carries none`() {
        assertThat(mapper.toCommand(event(reviews = null)).reviews).isEmpty()
    }

    @Test
    fun `maps the listed files to paths`() {
        val command = mapper.toCommand(event(files = GithubPullRequestFiles.Listed(listOf("a.kt", "b.kt"))))

        assertThat(command.paths).containsExactly("a.kt", "b.kt")
    }

    @Test
    fun `has no paths when the files are not all known`() {
        assertThat(mapper.toCommand(event(files = GithubPullRequestFiles.Incomplete)).paths).isEmpty()
    }
}
