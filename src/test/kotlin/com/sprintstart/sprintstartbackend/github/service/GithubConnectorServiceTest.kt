package com.sprintstart.sprintstartbackend.github.service

import com.sprintstart.sprintstartbackend.github.GithubClient
import com.sprintstart.sprintstartbackend.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.github.models.GithubRepositorySnapshot
import com.sprintstart.sprintstartbackend.github.models.GithubUser
import com.sprintstart.sprintstartbackend.github.models.GithubUserPat
import com.sprintstart.sprintstartbackend.github.models.api.requests.ConnectRepositoryRequest
import com.sprintstart.sprintstartbackend.github.models.api.requests.UpdateRepositoryRequest
import com.sprintstart.sprintstartbackend.github.models.exceptions.RepositoryNotConnectedException
import com.sprintstart.sprintstartbackend.github.models.exceptions.RepositoryNotFoundException
import com.sprintstart.sprintstartbackend.github.models.exceptions.RepositoryNotInitializedException
import com.sprintstart.sprintstartbackend.github.repository.GithubRepositoryConnectionRepository
import com.sprintstart.sprintstartbackend.github.repository.GithubRepositorySnapshotRepository
import com.sprintstart.sprintstartbackend.github.repository.GithubUserRepository
import com.sprintstart.sprintstartbackend.github.service.internal.GithubCommitsService
import com.sprintstart.sprintstartbackend.github.service.internal.GithubFileService
import com.sprintstart.sprintstartbackend.github.service.internal.GithubIssuesService
import com.sprintstart.sprintstartbackend.github.service.internal.GithubPullRequestsService
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class GithubConnectorServiceTest {
    private val testScope = TestScope()

    private val repoConnectionRepository = mockk<GithubRepositoryConnectionRepository>()
    private val repoSnapshotRepository = mockk<GithubRepositorySnapshotRepository>()
    private val githubUserRepository = mockk<GithubUserRepository>()
    private val fileService = mockk<GithubFileService>()
    private val commitsService = mockk<GithubCommitsService>()
    private val issuesService = mockk<GithubIssuesService>()
    private val pullRequestsService = mockk<GithubPullRequestsService>()
    private val githubClient = mockk<GithubClient>()

    private lateinit var service: GithubConnectorService

    @BeforeEach
    fun setUp() {
        service = GithubConnectorService(
            applicationScope = testScope,
            repoConnectionRepository = repoConnectionRepository,
            repoSnapshotRepository = repoSnapshotRepository,
            githubUserRepository = githubUserRepository,
            fileService = fileService,
            commitsService = commitsService,
            issuesService = issuesService,
            pullRequestsService = pullRequestsService,
            githubClient = githubClient,
        )
    }

    @Nested
    inner class ConnectRepositoryIfExists {
        @Test
        fun `connectRepositoryIfExists throws RepositoryNotFoundException when repo does not exist on GitHub`() =
            runTest {
                val repository = GithubRepositoryConnection(
                    owner = "owner",
                    name = "repo",
                    user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token"),
                )
                coEvery { githubClient.repositoryExists(repository) } returns false

                assertFailsWith<RepositoryNotFoundException> {
                    service.connectRepositoryIfExists("mock-id", connectRequest())
                }
            }

        @Test
        fun `connectRepositoryIfExists returns a transactionId when repo exists`() = testScope.runTest {
            val repository = GithubRepositoryConnection(
                owner = "owner",
                name = "repo",
                user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token"),
            )
            coEvery { githubClient.repositoryExists(repository) } returns true
            stubSuccessfulConnect()

            val transactionId = service.connectRepositoryIfExists("auth-id", connectRequest())

            assertThat(transactionId).isNotNull()
            assertThat(transactionId).isInstanceOf(UUID::class.java)
        }

        @Test
        fun `connectRepositoryIfExists saves repository connection`() = testScope.runTest {
            val repository = GithubRepositoryConnection(
                owner = "owner",
                name = "repo",
                user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token"),
            )
            coEvery { githubClient.repositoryExists(repository) } returns true
            stubSuccessfulConnect()

            service.connectRepositoryIfExists("auth-id", connectRequest())

            coVerify { repoConnectionRepository.save(match { it.owner == "owner" && it.name == "repo" }) }
        }

        @Test
        fun `connectRepositoryIfExists launches all background ingestion jobs`() = testScope.runTest {
            val repository = GithubRepositoryConnection(
                owner = "owner",
                name = "repo",
                user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token"),
            )
            coEvery { githubClient.repositoryExists(repository) } returns true
            stubSuccessfulConnect()

            service.connectRepositoryIfExists("auth-id", connectRequest())
            advanceUntilIdle() // wait for all launched coroutines

            coVerify { fileService.fetchAndIngestAllFiles(any(), any()) }
            coVerify { commitsService.fetchAndIngestLatestCommits(any(), any(), true) }
            coVerify { issuesService.fetchAndIngestAllIssues(any(), any()) }
            coVerify { pullRequestsService.fetchAndIngestAllPullRequests(any(), any()) }
        }

        @Test
        fun `connectRepositoryIfExists passes same transactionId to all background jobs`() = testScope.runTest {
            val repository = GithubRepositoryConnection(
                owner = "owner",
                name = "repo",
                user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token"),
            )
            coEvery { githubClient.repositoryExists(repository) } returns true
            stubSuccessfulConnect()

            service.connectRepositoryIfExists("auth-id", connectRequest())
            advanceUntilIdle()

            val fileTransactionId = slot<UUID>()
            val commitsTransactionId = slot<UUID>()
            coVerify { fileService.fetchAndIngestAllFiles(any(), capture(fileTransactionId)) }
            coVerify { commitsService.fetchAndIngestLatestCommits(any(), capture(commitsTransactionId), any()) }

            assertThat(fileTransactionId.captured).isEqualTo(commitsTransactionId.captured)
        }
    }

    @Nested
    inner class UpdateAllRepositories {
        @Test
        fun `updateAllRepositories returns a transactionId`() = testScope.runTest {
            every { repoConnectionRepository.findAll() } returns emptyList()

            val transactionId = service.updateAllRepositories()

            assertThat(transactionId).isNotNull()
        }

        @Test
        fun `updateAllRepositories triggers update for each connected repository`() = testScope.runTest {
            val user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token")
            val repo1 = repoConnection("owner", "repo1", user)
            val repo2 = repoConnection("owner", "repo2", user)

            every { repoConnectionRepository.findAll() } returns listOf(repo1, repo2)
            stubSuccessfulUpdate(repo1)
            stubSuccessfulUpdate(repo2)

            service.updateAllRepositories()
            advanceUntilIdle()

            coVerify(exactly = 2) { repoSnapshotRepository.findLatestByRepository(any()) }
        }

        @Test
        fun `updateAllRepositories returns immediately when no repositories are connected`() = testScope.runTest {
            every { repoConnectionRepository.findAll() } returns emptyList()

            val transactionId = service.updateAllRepositories()

            assertThat(transactionId).isNotNull()
            coVerify(exactly = 0) { repoSnapshotRepository.findLatestByRepository(any()) }
        }
    }

    @Nested
    inner class UpdateRepository {
        @Test
        fun `updateRepository throws RepositoryNotConnectedException when repo is not in database`() =
            runTest {
                every { repoConnectionRepository.findByOwnerAndName("owner", "repo") } returns null

                assertFailsWith<RepositoryNotConnectedException> {
                    service.updateRepository(updateRequest())
                }
            }

        @Test
        fun `updateRepository returns a transactionId when repo is connected`() = testScope.runTest {
            val user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token")
            val repo = repoConnection("owner", "repo", user)
            every { repoConnectionRepository.findByOwnerAndName("owner", "repo") } returns repo
            stubSuccessfulUpdate(repo)

            val transactionId = service.updateRepository(updateRequest())

            assertThat(transactionId).isNotNull()
        }

        @Test
        fun `updateRepository launches all background ingestion jobs`() = testScope.runTest {
            val user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token")
            val repo = repoConnection("owner", "repo", user)
            every { repoConnectionRepository.findByOwnerAndName("owner", "repo") } returns repo
            stubSuccessfulUpdate(repo)

            service.updateRepository(updateRequest())
            advanceUntilIdle()

            coVerify { fileService.fetchAndIngestFileUpdatesIncremental(any(), any()) }
            coVerify { commitsService.fetchAndIngestLatestCommits(any(), any(), false) }
            coVerify { issuesService.fetchAndIngestAllIssues(any(), any(), any()) }
            coVerify { pullRequestsService.fetchAndIngestAllPullRequests(any(), any(), any()) }
        }

        @Test
        fun `updateRepository saves a new snapshot`() = testScope.runTest {
            val user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token")
            val repo = repoConnection("owner", "repo", user)
            every { repoConnectionRepository.findByOwnerAndName("owner", "repo") } returns repo
            stubSuccessfulUpdate(repo)

            service.updateRepository(updateRequest())

            coVerify { repoSnapshotRepository.save(any()) }
        }

        @Test
        fun `updateRepository throws RepositoryNotInitializedException when no snapshot exists`() =
            runTest {
                val user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token")
                val repo = repoConnection("owner", "repo", user)
                every { repoConnectionRepository.findByOwnerAndName("owner", "repo") } returns repo
                every { repoSnapshotRepository.findLatestByRepository(repo.id) } returns null

                assertFailsWith<RepositoryNotInitializedException> {
                    service.updateRepository(updateRequest())
                }
            }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun connectRequest() = ConnectRepositoryRequest(
        owner = "owner",
        name = "repo",
        tokenName = "test-token",
    )

    private fun updateRequest() = UpdateRepositoryRequest(owner = "owner", name = "repo")

    private fun repoConnection(owner: String, name: String, user: GithubUser) = GithubRepositoryConnection(
        owner = owner,
        name = name,
        user = user,
    )

    private fun repoSnapshot(repo: GithubRepositoryConnection) = GithubRepositorySnapshot(
        repository = repo,
    )

    private fun stubSuccessfulConnect() {
        every { repoConnectionRepository.save(any()) } answers { firstArg() }
        coJustRun { fileService.fetchAndIngestAllFiles(any(), any()) }
        coJustRun { commitsService.fetchAndIngestLatestCommits(any(), any(), any()) }
        coJustRun { issuesService.fetchAndIngestAllIssues(any(), any(), any()) }
        coJustRun { pullRequestsService.fetchAndIngestAllPullRequests(any(), any(), any()) }
    }

    private fun stubSuccessfulUpdate(repo: GithubRepositoryConnection) {
        every { repoSnapshotRepository.findLatestByRepository(repo.id) } returns repoSnapshot(repo)
        every { repoSnapshotRepository.save(any()) } answers { firstArg() }
        coJustRun { fileService.fetchAndIngestFileUpdatesIncremental(any(), any()) }
        coJustRun { commitsService.fetchAndIngestLatestCommits(any(), any(), any()) }
        coJustRun { issuesService.fetchAndIngestAllIssues(any(), any(), any()) }
        coJustRun { pullRequestsService.fetchAndIngestAllPullRequests(any(), any(), any()) }
    }
}
