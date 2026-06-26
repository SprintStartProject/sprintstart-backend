package com.sprintstart.sprintstartbackend.github.service

import com.sprintstart.sprintstartbackend.github.GithubClient
import com.sprintstart.sprintstartbackend.github.external.events.initial.GithubRepositoryConnectionInitiatedEvent
import com.sprintstart.sprintstartbackend.github.external.events.initial.GithubRepositoryConnectionInitiationFailedEvent
import com.sprintstart.sprintstartbackend.github.external.events.update.GithubAllRepositoriesUpdateStartedEvent
import com.sprintstart.sprintstartbackend.github.external.events.update.GithubRepositoryUpdateFailedEvent
import com.sprintstart.sprintstartbackend.github.external.events.update.GithubRepositoryUpdateStartedEvent
import com.sprintstart.sprintstartbackend.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.github.models.GithubRepositorySnapshot
import com.sprintstart.sprintstartbackend.github.models.api.requests.ConnectRepositoryRequest
import com.sprintstart.sprintstartbackend.github.models.api.requests.UpdateRepositoryRequest
import com.sprintstart.sprintstartbackend.github.models.exceptions.RepositoryNotConnectedException
import com.sprintstart.sprintstartbackend.github.models.exceptions.RepositoryNotFoundException
import com.sprintstart.sprintstartbackend.github.models.exceptions.RepositoryNotInitializedException
import com.sprintstart.sprintstartbackend.github.repository.GithubRepositoryConnectionRepository
import com.sprintstart.sprintstartbackend.github.repository.GithubRepositorySnapshotRepository
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
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.util.UUID
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class GithubConnectorServiceTest {
    private val testScope = TestScope()

    private val repoConnectionRepository = mockk<GithubRepositoryConnectionRepository>()
    private val repoSnapshotRepository = mockk<GithubRepositorySnapshotRepository>()
    private val fileService = mockk<GithubFileService>()
    private val commitsService = mockk<GithubCommitsService>()
    private val issuesService = mockk<GithubIssuesService>()
    private val pullRequestsService = mockk<GithubPullRequestsService>()
    private val githubClient = mockk<GithubClient>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    private lateinit var service: GithubConnectorService

    @BeforeEach
    fun setUp() {
        service = GithubConnectorService(
            applicationScope = testScope,
            repoConnectionRepository = repoConnectionRepository,
            repoSnapshotRepository = repoSnapshotRepository,
            fileService = fileService,
            commitsService = commitsService,
            issuesService = issuesService,
            pullRequestsService = pullRequestsService,
            githubClient = githubClient,
            eventPublisher = eventPublisher,
        )
    }

    @Nested
    inner class ConnectRepositoryIfExists {
        @Test
        fun `connectRepositoryIfExists throws RepositoryNotFoundException when repo does not exist on GitHub`() =
            runTest {
                coEvery { githubClient.repositoryExists("owner", "repo") } returns false

                assertFailsWith<RepositoryNotFoundException> {
                    service.connectRepositoryIfExists(connectRequest())
                }
            }

        @Test
        fun `connectRepositoryIfExists returns a transactionId when repo exists`() = testScope.runTest {
            coEvery { githubClient.repositoryExists("owner", "repo") } returns true
            stubSuccessfulConnect()

            val transactionId = service.connectRepositoryIfExists(connectRequest())

            assertThat(transactionId).isNotNull()
            assertThat(transactionId).isInstanceOf(UUID::class.java)
        }

        @Test
        fun `connectRepositoryIfExists saves repository connection`() = testScope.runTest {
            coEvery { githubClient.repositoryExists("owner", "repo") } returns true
            stubSuccessfulConnect()

            service.connectRepositoryIfExists(connectRequest())

            coVerify { repoConnectionRepository.save(match { it.owner == "owner" && it.name == "repo" }) }
        }

        @Test
        fun `connectRepositoryIfExists launches all background ingestion jobs`() = testScope.runTest {
            coEvery { githubClient.repositoryExists("owner", "repo") } returns true
            stubSuccessfulConnect()

            service.connectRepositoryIfExists(connectRequest())
            advanceUntilIdle() // wait for all launched coroutines

            coVerify { fileService.fetchAndIngestAllFiles(any(), any()) }
            coVerify { commitsService.fetchAndIngestLatestCommits(any(), any(), true) }
            coVerify { issuesService.fetchAndIngestAllIssues(any(), any()) }
            coVerify { pullRequestsService.fetchAndIngestAllPullRequests(any(), any()) }
        }

        @Test
        fun `connectRepositoryIfExists passes same transactionId to all background jobs`() = testScope.runTest {
            coEvery { githubClient.repositoryExists("owner", "repo") } returns true
            stubSuccessfulConnect()

            service.connectRepositoryIfExists(connectRequest())
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

            val response = service.updateAllRepositories()

            assertThat(response.transactionId).isNotNull()
        }

        @Test
        fun `updateAllRepositories triggers update for each connected repository`() = testScope.runTest {
            val repo1 = repoConnection("owner", "repo1")
            val repo2 = repoConnection("owner", "repo2")

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

            val response = service.updateAllRepositories()

            assertThat(response.transactionId).isNotNull()
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
            val repo = repoConnection("owner", "repo")
            every { repoConnectionRepository.findByOwnerAndName("owner", "repo") } returns repo
            stubSuccessfulUpdate(repo)

            val response = service.updateRepository(updateRequest())

            assertThat(response.transactionId).isNotNull()
        }

        @Test
        fun `updateRepository launches all background ingestion jobs`() = testScope.runTest {
            val repo = repoConnection("owner", "repo")
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
            val repo = repoConnection("owner", "repo")
            every { repoConnectionRepository.findByOwnerAndName("owner", "repo") } returns repo
            stubSuccessfulUpdate(repo)

            service.updateRepository(updateRequest())

            coVerify { repoSnapshotRepository.save(any()) }
        }

        @Test
        fun `updateRepository throws RepositoryNotInitializedException when no snapshot exists`() =
            runTest {
                val repo = repoConnection("owner", "repo")
                every { repoConnectionRepository.findByOwnerAndName("owner", "repo") } returns repo
                every { repoSnapshotRepository.findLatestByRepository(repo.id) } returns null

                assertFailsWith<RepositoryNotInitializedException> {
                    service.updateRepository(updateRequest())
                }
            }
    }

    // ── event publishing ──────────────────────────────────────────────────────

    @Nested
    inner class EventPublishing {
        @Test
        fun `publishes GithubRepositoryConnectionInitiatedEvent on connect`() = testScope.runTest {
            coEvery { githubClient.repositoryExists("owner", "repo") } returns true
            stubSuccessfulConnect()

            service.connectRepositoryIfExists(connectRequest())

            verify { eventPublisher.publishEvent(any<GithubRepositoryConnectionInitiatedEvent>()) }
        }

        @Test
        fun `publishes GithubRepositoryConnectionInitiationFailedEvent when repo not found`() = testScope.runTest {
            coEvery { githubClient.repositoryExists("owner", "repo") } returns false

            assertFailsWith<RepositoryNotFoundException> {
                service.connectRepositoryIfExists(connectRequest())
            }

            verify { eventPublisher.publishEvent(any<GithubRepositoryConnectionInitiationFailedEvent>()) }
        }

        @Test
        fun `publishes GithubAllRepositoriesUpdateStartedEvent on updateAll`() = testScope.runTest {
            every { repoConnectionRepository.findAll() } returns emptyList()

            service.updateAllRepositories()

            verify { eventPublisher.publishEvent(any<GithubAllRepositoriesUpdateStartedEvent>()) }
        }

        @Test
        fun `publishes GithubRepositoryUpdateStartedEvent on single update`() = testScope.runTest {
            val repo = repoConnection("owner", "repo")
            every { repoConnectionRepository.findByOwnerAndName("owner", "repo") } returns repo
            stubSuccessfulUpdate(repo)

            service.updateRepository(updateRequest())

            verify { eventPublisher.publishEvent(any<GithubRepositoryUpdateStartedEvent>()) }
        }

        @Test
        fun `publishes GithubRepositoryUpdateFailedEvent when repo not found on update`() = testScope.runTest {
            every { repoConnectionRepository.findByOwnerAndName("owner", "repo") } returns null

            assertFailsWith<RepositoryNotConnectedException> {
                service.updateRepository(updateRequest())
            }

            verify { eventPublisher.publishEvent(any<GithubRepositoryUpdateFailedEvent>()) }
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun connectRequest() = ConnectRepositoryRequest(owner = "owner", name = "repo")

    private fun updateRequest() = UpdateRepositoryRequest(owner = "owner", name = "repo")

    private fun repoConnection(owner: String, name: String) = GithubRepositoryConnection(
        owner = owner,
        name = name,
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
