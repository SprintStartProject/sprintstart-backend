package com.sprintstart.sprintstartbackend.connectors.github.service

import com.sprintstart.sprintstartbackend.connectors.ConnectionState
import com.sprintstart.sprintstartbackend.connectors.github.GithubClient
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositorySnapshot
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubUser
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubUserPat
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.PullRequestFileResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql.CommitMessage
import com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql.PullRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql.PullRequestCommitNode
import com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql.PullRequestCommitsConnection
import com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql.PullRequestFileNode
import com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql.PullRequestFilesConnection
import com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql.StatusCheckRollup
import com.sprintstart.sprintstartbackend.connectors.github.repository.GithubRepositoryConnectionRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.Optional
import java.util.UUID

class GithubRepositoryApiServiceTest {
    private val githubRepositoryConnectionRepository = mockk<GithubRepositoryConnectionRepository>()
    private val githubClient = mockk<GithubClient>()
    private val service = GithubRepositoryApiService(githubRepositoryConnectionRepository, githubClient)

    private val repositoryId = UUID.randomUUID()
    private val repository = GithubRepositoryConnection(
        id = repositoryId,
        owner = "owner",
        name = "repo",
        user = GithubUser(id = GithubUserPat("auth-id", "token-name"), token = "test-token"),
    )

    private fun pullRequest(
        statusCheckRollup: StatusCheckRollup? = StatusCheckRollup(state = "SUCCESS"),
        files: List<String> = listOf("src/Main.kt"),
        commitMessages: List<String> = listOf("fix: bug"),
    ) = PullRequest(
        number = 42,
        title = "Fix bug",
        body = "Closes #1",
        state = "MERGED",
        createdAt = "2024-01-01T00:00:00Z",
        mergedAt = "2024-01-02T00:00:00Z",
        url = "https://github.com/owner/repo/pull/42",
        author = null,
        labels = null,
        reviews = null,
        comments = null,
        reviewThreads = null,
        statusCheckRollup = statusCheckRollup,
        files = PullRequestFilesConnection(files.map { PullRequestFileNode(it) }),
        commits = PullRequestCommitsConnection(commitMessages.map { PullRequestCommitNode(CommitMessage(it)) }),
    )

    // Most of these tests are about the GraphQL half, so the diff call defaults to "GitHub
    // said nothing" -- which is also the state the budgeting tests below start from.
    @BeforeEach
    fun stubDiffs() {
        coEvery { githubClient.fetchPullRequestFiles(any(), any()) } returns emptyList()
    }

    @Test
    fun `getPullRequestEvidence maps a found pull request to evidence`() = runTest {
        every { githubRepositoryConnectionRepository.findById(repositoryId) } returns Optional.of(repository)
        coEvery { githubClient.fetchPullRequest(repository, 42) } returns pullRequest()

        val result = service.getPullRequestEvidence(repositoryId, 42)

        assertThat(result).isNotNull()
        assertThat(result?.title).isEqualTo("Fix bug")
        assertThat(result?.body).isEqualTo("Closes #1")
        assertThat(result?.state).isEqualTo("MERGED")
        assertThat(result?.filesChanged).containsExactly("src/Main.kt")
        assertThat(result?.checksPassed).isTrue()
        assertThat(result?.commitMessages).containsExactly("fix: bug")
    }

    @Test
    fun `getPullRequestEvidence returns null when the PR does not exist`() = runTest {
        every { githubRepositoryConnectionRepository.findById(repositoryId) } returns Optional.of(repository)
        coEvery { githubClient.fetchPullRequest(repository, 99) } returns null

        val result = service.getPullRequestEvidence(repositoryId, 99)

        assertThat(result).isNull()
    }

    @Test
    fun `getPullRequestEvidence maps a failing status rollup to checksPassed false`() = runTest {
        every { githubRepositoryConnectionRepository.findById(repositoryId) } returns Optional.of(repository)
        coEvery { githubClient.fetchPullRequest(repository, 42) } returns
            pullRequest(statusCheckRollup = StatusCheckRollup(state = "FAILURE"))

        val result = service.getPullRequestEvidence(repositoryId, 42)

        assertThat(result?.checksPassed).isFalse()
    }

    @Test
    fun `getPullRequestEvidence maps a missing status rollup to checksPassed null`() = runTest {
        every { githubRepositoryConnectionRepository.findById(repositoryId) } returns Optional.of(repository)
        coEvery { githubClient.fetchPullRequest(repository, 42) } returns
            pullRequest(statusCheckRollup = null)

        val result = service.getPullRequestEvidence(repositoryId, 42)

        assertThat(result?.checksPassed).isNull()
    }

    @Test
    fun `getPullRequestEvidence throws when the repository connection does not exist`() {
        every { githubRepositoryConnectionRepository.findById(repositoryId) } returns Optional.empty()

        assertThrows<NoSuchElementException> {
            runBlocking { service.getPullRequestEvidence(repositoryId, 42) }
        }
    }

    @Test
    fun `getRepositoryIdByOwnerAndName returns the connection id`() {
        val repositoryId = UUID.randomUUID()
        every { githubRepositoryConnectionRepository.findByOwnerAndName("owner", "repo") } returns
            mockk { every { id } returns repositoryId }

        assertThat(service.getRepositoryIdByOwnerAndName("owner", "repo")).isEqualTo(repositoryId)
    }

    @Test
    fun `getRepositoryIdByOwnerAndName returns null when no connection exists`() {
        every { githubRepositoryConnectionRepository.findByOwnerAndName("owner", "repo") } returns null

        assertThat(service.getRepositoryIdByOwnerAndName("owner", "repo")).isNull()
    }

    @Test
    fun `getRepositoryIdsByProject maps connections to their ids`() {
        val projectId = UUID.randomUUID()
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        every { githubRepositoryConnectionRepository.findAllByProjectId(projectId) } returns listOf(
            mockk { every { id } returns first },
            mockk { every { id } returns second },
        )

        assertThat(service.getRepositoryIdsByProject(projectId)).containsExactly(first, second)
    }

    @Test
    fun `getSourceInstances maps enabled connection with snapshot timestamps and sorts by owner and name`() {
        val commitsAt = Instant.parse("2026-07-06T10:00:00Z")
        val issuesAt = Instant.parse("2026-07-06T11:00:00Z")
        val prAt = Instant.parse("2026-07-06T12:00:00Z")
        val connected = connection(
            id = UUID.randomUUID(),
            owner = "beta",
            name = "repo",
            sourceEnabled = true,
            connectionState = ConnectionState.UP_TO_DATE,
            snapshot = mockk {
                every { lastCommitsSyncAt } returns commitsAt
                every { lastIssuesSyncAt } returns issuesAt
                every { lastPullRequestsSyncAt } returns prAt
            },
        )
        val disabled = connection(
            id = UUID.randomUUID(),
            owner = "alpha",
            name = "repo",
            sourceEnabled = false,
            connectionState = ConnectionState.UP_TO_DATE,
            snapshot = null,
        )
        every { githubRepositoryConnectionRepository.findAll() } returns listOf(connected, disabled)

        val result = service.getSourceInstances()

        assertThat(result.map { it.owner }).containsExactly("alpha", "beta")
        val alpha = result.first()
        assertThat(alpha.status).isEqualTo("DISABLED")
        assertThat(alpha.enabled).isFalse()
        assertThat(alpha.lastCommitsSyncAt).isNull()
        val beta = result.last()
        assertThat(beta.status).isEqualTo("CONNECTED")
        assertThat(beta.enabled).isTrue()
        assertThat(beta.lastCommitsSyncAt).isEqualTo(commitsAt)
        assertThat(beta.lastIssuesSyncAt).isEqualTo(issuesAt)
        assertThat(beta.lastPullRequestsSyncAt).isEqualTo(prAt)
    }

    @Test
    fun `getSourceInstances filters by project id when provided`() {
        val projectId = UUID.randomUUID()
        val connection = connection(
            id = UUID.randomUUID(),
            owner = "owner",
            name = "repo",
            sourceEnabled = true,
            connectionState = ConnectionState.OUT_OF_DATE,
            snapshot = null,
        )
        every { githubRepositoryConnectionRepository.findAllByProjectId(projectId) } returns listOf(connection)

        val result = service.getSourceInstances(projectId).single()

        assertThat(result.status).isEqualTo("OUT_OF_DATE")
    }

    @Test
    fun `removeProjectFromAllRepositories drops the project from every linked connection`() {
        val projectId = UUID.randomUUID()
        val otherProjectId = UUID.randomUUID()
        val firstProjects = mutableSetOf(projectId, otherProjectId)
        val secondProjects = mutableSetOf(projectId)
        val first = mockk<GithubRepositoryConnection> { every { projectIdsInternal } returns firstProjects }
        val second = mockk<GithubRepositoryConnection> { every { projectIdsInternal } returns secondProjects }
        every { githubRepositoryConnectionRepository.findAllByProjectId(projectId) } returns listOf(first, second)
        val saved = slot<List<GithubRepositoryConnection>>()
        every { githubRepositoryConnectionRepository.saveAll(capture(saved)) } answers { firstArg() }

        service.removeProjectFromAllRepositories(projectId)

        assertThat(firstProjects).containsExactly(otherProjectId)
        assertThat(secondProjects).isEmpty()
        assertThat(saved.captured).containsExactly(first, second)
    }

    private fun connection(
        id: UUID,
        owner: String,
        name: String,
        sourceEnabled: Boolean,
        connectionState: ConnectionState,
        snapshot: GithubRepositorySnapshot?,
    ): GithubRepositoryConnection =
        mockk {
            every { this@mockk.id } returns id
            every { this@mockk.owner } returns owner
            every { this@mockk.name } returns name
            every { this@mockk.sourceEnabled } returns sourceEnabled
            every { this@mockk.connectionState } returns connectionState
            every { this@mockk.snapshot } returns snapshot
        }

    /**
     * ⚠️ The whole point of the slice. A filename says a hire *touched* AuthService.kt; only the
     * diff says whether they fixed anything in it. Without this the judge was told "claims are not
     * evidence, changed files are" — and a changed file is a very weak thing to rest that on.
     */
    @Test
    fun `evidence carries the diff, not just the filename`() = runTest {
        every { githubRepositoryConnectionRepository.findById(repositoryId) } returns Optional.of(repository)
        coEvery { githubClient.fetchPullRequest(repository, 42) } returns pullRequest()
        coEvery { githubClient.fetchPullRequestFiles(repository, 42) } returns listOf(
            PullRequestFileResponse(
                filename = "src/Main.kt",
                additions = 2,
                deletions = 1,
                patch = "@@ -1 +1 @@\n-old\n+new",
            ),
        )

        val result = service.getPullRequestEvidence(repositoryId, 42)

        assertThat(result?.fileDiffs).singleElement().satisfies({
            assertThat(it.path).isEqualTo("src/Main.kt")
            assertThat(it.patch).contains("+new")
            assertThat(it.truncated).isFalse()
        })
        assertThat(result?.omittedFileCount).isZero()
    }

    /**
     * ⚠️ **A cut patch must not read as a small one.** The flag is what lets the judge tell "this
     * file changed a little" from "this file changed more than I was shown".
     */
    @Test
    fun `an oversized patch is cut and says so`() = runTest {
        every { githubRepositoryConnectionRepository.findById(repositoryId) } returns Optional.of(repository)
        coEvery { githubClient.fetchPullRequest(repository, 42) } returns pullRequest()
        coEvery { githubClient.fetchPullRequestFiles(repository, 42) } returns listOf(
            PullRequestFileResponse(filename = "huge.lock", patch = "x".repeat(9_000)),
        )

        val diff = service.getPullRequestEvidence(repositoryId, 42)?.fileDiffs?.single()

        assertThat(diff?.truncated).isTrue()
        assertThat(diff?.patch).hasSize(4_000)
    }

    /**
     * ⚠️ **What did not fit is counted, never silently dropped.** A judge shown a partial diff and
     * not told it is partial reads absence as proof the work was not done — and fails a hire for
     * the part it was never given.
     */
    @Test
    fun `files past the budget are counted rather than dropped in silence`() = runTest {
        every { githubRepositoryConnectionRepository.findById(repositoryId) } returns Optional.of(repository)
        coEvery { githubClient.fetchPullRequest(repository, 42) } returns pullRequest()
        coEvery { githubClient.fetchPullRequestFiles(repository, 42) } returns
            (1..6).map { PullRequestFileResponse(filename = "f$it.kt", patch = "y".repeat(4_000)) }

        val result = service.getPullRequestEvidence(repositoryId, 42)

        // 12k total at 4k a file: three fit, three are named as missing.
        assertThat(result?.fileDiffs).hasSize(3)
        assertThat(result?.omittedFileCount).isEqualTo(3)
    }

    /**
     * A binary or over-large file has no patch, and that is evidence in itself — "this changed and
     * I cannot show you how" is a different statement from "this did not change". It costs no
     * budget, so it never displaces a diff that could have been read.
     */
    @Test
    fun `a file with no patch is kept without spending budget`() = runTest {
        every { githubRepositoryConnectionRepository.findById(repositoryId) } returns Optional.of(repository)
        coEvery { githubClient.fetchPullRequest(repository, 42) } returns pullRequest()
        coEvery { githubClient.fetchPullRequestFiles(repository, 42) } returns listOf(
            PullRequestFileResponse(filename = "logo.png", patch = null),
            PullRequestFileResponse(filename = "src/Main.kt", patch = "@@ -1 +1 @@"),
        )

        val result = service.getPullRequestEvidence(repositoryId, 42)

        assertThat(result?.fileDiffs).hasSize(2)
        assertThat(result?.fileDiffs?.first()?.patch).isNull()
        assertThat(result?.omittedFileCount).isZero()
    }

    /**
     * ⚠️ **An unavailable diff is not an empty one.** `fetchPullRequestFiles` swallows a transport
     * failure into an empty list, so evidence still carries the pull request itself — a network
     * blip must not turn into a hire's work being failed.
     */
    @Test
    fun `evidence survives a diff GitHub would not give`() = runTest {
        every { githubRepositoryConnectionRepository.findById(repositoryId) } returns Optional.of(repository)
        coEvery { githubClient.fetchPullRequest(repository, 42) } returns pullRequest()
        coEvery { githubClient.fetchPullRequestFiles(repository, 42) } returns emptyList()

        val result = service.getPullRequestEvidence(repositoryId, 42)

        assertThat(result?.title).isEqualTo("Fix bug")
        assertThat(result?.fileDiffs).isEmpty()
        assertThat(result?.omittedFileCount).isZero()
    }
}
