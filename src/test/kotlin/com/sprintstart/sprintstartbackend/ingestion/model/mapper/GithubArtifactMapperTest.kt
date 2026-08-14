package com.sprintstart.sprintstartbackend.ingestion.model.mapper

import com.sprintstart.sprintstartbackend.connectors.github.external.events.commits.GithubCommitFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.files.GithubFileFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.issues.GithubIssueFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.pullrequests.GithubPullRequestComment
import com.sprintstart.sprintstartbackend.connectors.github.external.events.pullrequests.GithubPullRequestFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.pullrequests.GithubPullRequestReview
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.util.sha256
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class GithubArtifactMapperTest {
    private val mapper = GithubArtifactMapper()
    private val runId = UUID.randomUUID()
    private val repositoryId = UUID.randomUUID()

    @Test
    fun `toCommand maps github file metadata and hash`() {
        val event = GithubFileFetchedEvent(
            transactionId = runId,
            repositoryId = repositoryId,
            repositoryOwner = "owner",
            repositoryName = "repo",
            path = "src/main/App.kt",
            content = "fun main() = Unit",
            sourceUrl = "https://github.com/owner/repo/blob/main/src/main/App.kt",
        )

        val result = mapper.toCommand(event)

        assertThat(result.ingestionRunId).isEqualTo(runId)
        assertThat(result.sourceSystem).isEqualTo(SourceSystem.GITHUB)
        assertThat(result.metadata.repositoryId).isEqualTo(repositoryId)
        assertThat(result.sourceId).isEqualTo("github:owner/repo:FILE:src/main/App.kt")
        assertThat(result.sourceUrl).isEqualTo(event.sourceUrl)
        assertThat(result.metadata.repositoryFullName).isEqualTo("owner/repo")
        assertThat(result.artifactType).isEqualTo(ArtifactType.FILE)
        assertThat(result.title).isEqualTo("App.kt")
        assertThat(result.bodyText).isEqualTo("fun main() = Unit")
        assertThat(result.mime).isEqualTo("text/x-kotlin")
        assertThat(result.language).isEqualTo("Kotlin")
        assertThat(result.hash).isEqualTo(event.content.toByteArray().sha256())
    }

    @Test
    fun `toCommand maps dockerfile as dockerfile language`() {
        val event = GithubFileFetchedEvent(
            transactionId = runId,
            repositoryId = repositoryId,
            repositoryOwner = "owner",
            repositoryName = "repo",
            path = "Dockerfile",
            content = "FROM eclipse-temurin:21",
            sourceUrl = "https://github.com/owner/repo/blob/main/Dockerfile",
        )

        val result = mapper.toCommand(event)

        assertThat(result.title).isEqualTo("Dockerfile")
        assertThat(result.language).isEqualTo("Dockerfile")
        assertThat(result.mime).isNull()
    }

    @Test
    fun `toCommand maps commit source and truncates long title`() {
        val message = "a".repeat(80)
        val event = GithubCommitFetchedEvent(
            transactionId = runId,
            repositoryId = repositoryId,
            repositoryOwner = "owner",
            repositoryName = "repo",
            author = "alice",
            date = Instant.parse("2024-01-01T00:00:00Z"),
            sha = "abc123",
            msg = message,
        )

        val result = mapper.toCommand(event)

        assertThat(result.sourceId).isEqualTo("github:owner/repo:COMMIT:abc123")
        assertThat(result.metadata.repositoryId).isEqualTo(repositoryId)
        assertThat(result.sourceUrl).isEqualTo("https://github.com/owner/repo/commit/abc123")
        assertThat(result.metadata.repositoryFullName).isEqualTo("owner/repo")
        assertThat(result.artifactType).isEqualTo(ArtifactType.COMMIT)
        assertThat(result.title).hasSize(72)
        assertThat(result.bodyText).isEqualTo(message)
        assertThat(result.createdAtSource).isEqualTo(event.date)
        assertThat(result.hash).isNull()
    }

    @Test
    fun `toCommand maps issue with stable hash`() {
        val event = GithubIssueFetchedEvent(
            transactionId = runId,
            repositoryId = repositoryId,
            repositoryOwner = "owner",
            repositoryName = "repo",
            number = 42,
            title = "Bug report",
            body = "Something broke",
            state = "OPEN",
            createdAt = "2024-01-01T00:00:00Z",
            closedAt = null,
            url = "https://github.com/owner/repo/issues/42",
            author = "alice",
            labels = listOf("bug", "good first issue"),
            assignees = emptyList(),
            comments = emptyList(),
        )

        val result = mapper.toCommand(event)

        assertThat(result.sourceId).isEqualTo("github:owner/repo:ISSUE:42")
        assertThat(result.metadata.repositoryId).isEqualTo(repositoryId)
        assertThat(result.sourceUrl).isEqualTo(event.url)
        assertThat(result.metadata.repositoryFullName).isEqualTo("owner/repo")
        assertThat(result.artifactType).isEqualTo(ArtifactType.ISSUE)
        assertThat(result.title).isEqualTo("Issue #42 Bug report")
        assertThat(result.bodyText).isEqualTo("Something broke")
        assertThat(result.createdAtSource).isEqualTo(Instant.parse("2024-01-01T00:00:00Z"))
        assertThat(result.hash).isEqualTo("Bug report|Something broke".toByteArray().sha256())
        assertThat(result.state).isEqualTo("OPEN")
        assertThat(result.labels).containsExactly("bug", "good first issue")
        assertThat(result.authorLogin).isEqualTo("alice")
    }

    @Test
    fun `toCommand lower-cases the author login so it matches a declared identity`() {
        val event = GithubIssueFetchedEvent(
            transactionId = runId,
            repositoryId = repositoryId,
            repositoryOwner = "owner",
            repositoryName = "repo",
            number = 42,
            title = "Bug report",
            body = null,
            state = "OPEN",
            createdAt = "2024-01-01T00:00:00Z",
            closedAt = null,
            url = "https://github.com/owner/repo/issues/42",
            author = "OctoCat",
            labels = emptyList(),
            assignees = emptyList(),
            comments = emptyList(),
        )

        assertThat(mapper.toCommand(event).authorLogin).isEqualTo("octocat")
    }

    @Test
    fun `toCommand does not treat a commit's git author name as a GitHub login`() {
        val event = GithubCommitFetchedEvent(
            transactionId = runId,
            repositoryId = repositoryId,
            repositoryOwner = "owner",
            repositoryName = "repo",
            author = "Ada Lovelace",
            date = Instant.parse("2024-01-01T00:00:00Z"),
            sha = "abc123",
            msg = "fix: something",
        )

        // `git log --pretty=%an` yields a display name, not an account -- storing it as a login
        // would attribute the commit to whoever happens to hold that handle on GitHub.
        assertThat(mapper.toCommand(event).authorLogin).isNull()
    }

    @Test
    fun `toCommand carries a null issue state through as null`() {
        val event = GithubIssueFetchedEvent(
            transactionId = runId,
            repositoryId = repositoryId,
            repositoryOwner = "owner",
            repositoryName = "repo",
            number = 43,
            title = "Untitled",
            body = null,
            state = null,
            createdAt = "2024-01-01T00:00:00Z",
            closedAt = null,
            url = "https://github.com/owner/repo/issues/43",
            author = null,
            labels = emptyList(),
            assignees = emptyList(),
            comments = emptyList(),
        )

        val result = mapper.toCommand(event)

        assertThat(result.state).isNull()
        assertThat(result.labels).isEmpty()
    }

    @Test
    fun `toCommand maps pull request without hash`() {
        val event = GithubPullRequestFetchedEvent(
            transactionId = runId,
            repositoryId = repositoryId,
            repositoryOwner = "owner",
            repositoryName = "repo",
            number = 7,
            title = "Improve docs",
            body = null,
            state = "MERGED",
            createdAt = "2024-01-02T00:00:00Z",
            mergedAt = null,
            url = "https://github.com/owner/repo/pull/7",
            author = "bob",
            labels = null,
            reviews = null,
            comments = null,
            reviewThreads = null,
        )

        val result = mapper.toCommand(event)

        assertThat(result.sourceId).isEqualTo("github:owner/repo:PULL_REQUEST:7")
        assertThat(result.metadata.repositoryId).isEqualTo(repositoryId)
        assertThat(result.sourceUrl).isEqualTo(event.url)
        assertThat(result.metadata.repositoryFullName).isEqualTo("owner/repo")
        assertThat(result.artifactType).isEqualTo(ArtifactType.PULL_REQUEST)
        assertThat(result.title).isEqualTo("PR #7 Improve docs")
        assertThat(result.bodyText).isNull()
        assertThat(result.hash).isNull()
    }

    @Test
    fun `toCommand keeps the merge, the state and the source creation time`() {
        val result = mapper.toCommand(
            pullRequestEvent(
                state = "MERGED",
                mergedAt = "2024-01-05T10:00:00Z",
            ),
        )

        // All three were fetched from GitHub and dropped here before onboarding needed them.
        assertThat(result.state).isEqualTo("MERGED")
        assertThat(result.mergedAtSource).isEqualTo(Instant.parse("2024-01-05T10:00:00Z"))
        assertThat(result.createdAtSource).isEqualTo(Instant.parse("2024-01-02T00:00:00Z"))
    }

    @Test
    fun `toCommand takes the first response from a review or a comment, whichever came first`() {
        val result = mapper.toCommand(
            pullRequestEvent(
                reviews = listOf(
                    GithubPullRequestReview(
                        body = "looks good",
                        state = "APPROVED",
                        author = "carol",
                        submittedAt = "2024-01-04T00:00:00Z",
                    ),
                ),
                comments = listOf(
                    GithubPullRequestComment(
                        body = "one thought",
                        author = "dave",
                        createdAt = "2024-01-03T00:00:00Z",
                    ),
                ),
            ),
        )

        // A newcomer does not experience a comment and a review differently -- both are somebody
        // answering, so the earlier one is the response.
        assertThat(result.firstResponseAtSource).isEqualTo(Instant.parse("2024-01-03T00:00:00Z"))
    }

    @Test
    fun `toCommand does not count the author answering themselves as a response`() {
        val result = mapper.toCommand(
            pullRequestEvent(
                comments = listOf(
                    GithubPullRequestComment(
                        body = "bumping this",
                        author = "Bob",
                        createdAt = "2024-01-03T00:00:00Z",
                    ),
                ),
            ),
        )

        assertThat(result.firstResponseAtSource).isNull()
    }

    @Test
    fun `toCommand skips a review GitHub reported without a timestamp rather than guessing one`() {
        val result = mapper.toCommand(
            pullRequestEvent(
                reviews = listOf(
                    GithubPullRequestReview(
                        body = "no timestamp",
                        state = "COMMENTED",
                        author = "carol",
                        submittedAt = null,
                    ),
                ),
            ),
        )

        assertThat(result.firstResponseAtSource).isNull()
    }

    private fun pullRequestEvent(
        state: String = "OPEN",
        mergedAt: String? = null,
        reviews: List<GithubPullRequestReview>? = null,
        comments: List<GithubPullRequestComment>? = null,
    ) = GithubPullRequestFetchedEvent(
        transactionId = runId,
        repositoryId = repositoryId,
        repositoryOwner = "owner",
        repositoryName = "repo",
        number = 7,
        title = "Improve docs",
        body = null,
        state = state,
        createdAt = "2024-01-02T00:00:00Z",
        mergedAt = mergedAt,
        url = "https://github.com/owner/repo/pull/7",
        author = "bob",
        labels = null,
        reviews = reviews,
        comments = comments,
        reviewThreads = null,
    )
}
