package com.sprintstart.sprintstartbackend.github.service

import com.sprintstart.sprintstartbackend.connectors.github.external.events.GithubRepositoryResourcesFetchingStartedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.update.GithubAllRepositoriesUpdateStartedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.update.GithubRepositoryUpdateFailedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.update.GithubRepositoryUpdateStartedEvent
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositorySnapshot
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubUser
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubUserPat
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.UpdateRepositoryRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.RepositoryNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.RepositoryNotInitializedException
import com.sprintstart.sprintstartbackend.connectors.github.repository.GithubRepositoryConnectionRepository
import com.sprintstart.sprintstartbackend.connectors.github.repository.GithubRepositorySnapshotRepository
import com.sprintstart.sprintstartbackend.connectors.github.service.GithubUpdatesService
import com.sprintstart.sprintstartbackend.connectors.github.service.internal.GithubCommitsService
import com.sprintstart.sprintstartbackend.connectors.github.service.internal.GithubFileService
import com.sprintstart.sprintstartbackend.connectors.github.service.internal.GithubIssuesService
import com.sprintstart.sprintstartbackend.connectors.github.service.internal.GithubPullRequestsService
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
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
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class GithubUpdatesServiceTest {
    private val testScope = TestScope()

    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val repoConnectionRepository = mockk<GithubRepositoryConnectionRepository>()
    private val repoSnapshotRepository = mockk<GithubRepositorySnapshotRepository>()
    private val fileService = mockk<GithubFileService>()
    private val commitsService = mockk<GithubCommitsService>()
    private val issuesService = mockk<GithubIssuesService>()
    private val pullRequestsService = mockk<GithubPullRequestsService>()

    private lateinit var service: GithubUpdatesService

    private val user = GithubUser(
        id = GithubUserPat("auth-id", "token-name"),
        token = "test-token",
    )

    @BeforeEach
    fun setUp() {
        service = GithubUpdatesService(
            eventPublisher = eventPublisher,
            repoConnectionRepository = repoConnectionRepository,
            repoSnapshotRepository = repoSnapshotRepository,
            applicationScope = testScope,
            fileService = fileService,
            commitsService = commitsService,
            issuesService = issuesService,
            pullRequestsService = pullRequestsService,
        )
    }

    @Nested
    inner class UpdateAllRepositories {
        @Test
        fun `returns a transactionId`() {
            every { repoConnectionRepository.findAll() } returns emptyList()

            val response = service.updateAllRepositories()

            assertThat(response.transactionId).isNotNull()
        }

        @Test
        fun `publishes GithubAllRepositoriesUpdateStartedEvent`() {
            every { repoConnectionRepository.findAll() } returns emptyList()

            service.updateAllRepositories()

            verify { eventPublisher.publishEvent(any<GithubAllRepositoriesUpdateStartedEvent>()) }
        }

        @Test
        fun `does nothing when no repositories are connected`() {
            every { repoConnectionRepository.findAll() } returns emptyList()

            service.updateAllRepositories()

            verify(exactly = 1) { eventPublisher.publishEvent(any<GithubAllRepositoriesUpdateStartedEvent>()) }
        }

        @Test
        fun `updates each connected repository`() = runTest {
            val repo1 = repoConnection("owner1", "repo1").apply {
                snapshot = GithubRepositorySnapshot(repository = this)
            }
            val repo2 = repoConnection("owner2", "repo2").apply {
                snapshot = GithubRepositorySnapshot(repository = this)
            }
            every { repoConnectionRepository.findAll() } returns listOf(repo1, repo2)
            every { repoConnectionRepository.save(repo1) } returns repo1
            every { repoConnectionRepository.save(repo2) } returns repo2
            coJustRun { fileService.fetchAndIngestFileUpdatesIncremental(any(), any()) }
            coJustRun { commitsService.fetchAndIngestLatestCommits(any(), any()) }
            coJustRun { issuesService.fetchAndIngestAllIssues(any(), any(), any(), any(), any(), any()) }
            coJustRun { pullRequestsService.fetchAndIngestAllPullRequests(any(), any(), any(), any(), any(), any()) }
            every { repoSnapshotRepository.updateSyncTimestamps(any(), any()) } just runs

            service.updateAllRepositories()
            testScope.advanceUntilIdle()

            coVerify(exactly = 2) { fileService.fetchAndIngestFileUpdatesIncremental(any(), any()) }
        }

        @Test
        fun `throws RepositoryNotInitializedException when a repo has no snapshot`() {
            val repo = repoConnection("owner", "repo")
            every { repoConnectionRepository.findAll() } returns listOf(repo)

            assertFailsWith<RepositoryNotInitializedException> {
                service.updateAllRepositories()
            }
        }
    }

    @Nested
    inner class UpdateRepository {
        @Test
        fun `throws RepositoryNotConnectedException when repo is not found`() {
            every { repoConnectionRepository.findByOwnerAndName("owner", "repo") } returns null

            assertFailsWith<RepositoryNotConnectedException> {
                service.updateRepository(UpdateRepositoryRequest("owner", "repo"), true)
            }
        }

        @Test
        fun `publishes GithubRepositoryUpdateFailedEvent when repo not found`() {
            every { repoConnectionRepository.findByOwnerAndName("owner", "repo") } returns null

            assertFailsWith<RepositoryNotConnectedException> {
                service.updateRepository(UpdateRepositoryRequest("owner", "repo"), true)
            }

            verify { eventPublisher.publishEvent(any<GithubRepositoryUpdateFailedEvent>()) }
        }

        @Test
        fun `returns a transactionId when repo is connected`() {
            val repo = repoConnection("owner", "repo").apply {
                snapshot = GithubRepositorySnapshot(repository = this)
            }
            every { repoConnectionRepository.findByOwnerAndName("owner", "repo") } returns repo
            every { repoConnectionRepository.save(any()) } returns repo
            coJustRun { fileService.fetchAndIngestFileUpdatesIncremental(any(), any()) }
            coJustRun { commitsService.fetchAndIngestLatestCommits(any(), any()) }
            coJustRun { issuesService.fetchAndIngestAllIssues(any(), any(), any(), any(), any(), any()) }
            coJustRun { pullRequestsService.fetchAndIngestAllPullRequests(any(), any(), any(), any(), any(), any()) }
            every { repoSnapshotRepository.updateSyncTimestamps(any(), any()) } just runs

            val response = service.updateRepository(UpdateRepositoryRequest("owner", "repo"), true)

            assertThat(response.transactionId).isNotNull()
        }

        @Test
        fun `publishes GithubRepositoryUpdateStartedEvent`() {
            val repo = repoConnection("owner", "repo").apply {
                snapshot = GithubRepositorySnapshot(repository = this)
            }
            every { repoConnectionRepository.findByOwnerAndName("owner", "repo") } returns repo
            every { repoConnectionRepository.save(any()) } returns repo
            coJustRun { fileService.fetchAndIngestFileUpdatesIncremental(any(), any()) }
            coJustRun { commitsService.fetchAndIngestLatestCommits(any(), any()) }
            coJustRun { issuesService.fetchAndIngestAllIssues(any(), any(), any(), any(), any(), any()) }
            coJustRun { pullRequestsService.fetchAndIngestAllPullRequests(any(), any(), any(), any(), any(), any()) }
            every { repoSnapshotRepository.updateSyncTimestamps(any(), any()) } just runs

            service.updateRepository(UpdateRepositoryRequest("owner", "repo"), true)

            verify { eventPublisher.publishEvent(any<GithubRepositoryUpdateStartedEvent>()) }
        }

        @Test
        fun `throws RepositoryNotInitializedException when repo has no snapshot`() {
            val repo = repoConnection("owner", "repo")
            every { repoConnectionRepository.findByOwnerAndName("owner", "repo") } returns repo

            assertFailsWith<RepositoryNotInitializedException> {
                service.updateRepository(UpdateRepositoryRequest("owner", "repo"), true)
            }
        }
    }

    @Nested
    inner class PerformUpdate {
        @Test
        fun `launches file incremental update when performUpdate is true`() = runTest {
            val repo = repoConnection("owner", "repo").apply {
                snapshot = GithubRepositorySnapshot(repository = this)
            }
            every { repoConnectionRepository.findByOwnerAndName("owner", "repo") } returns repo
            every { repoConnectionRepository.save(any()) } returns repo
            coJustRun { fileService.fetchAndIngestFileUpdatesIncremental(any(), any()) }
            coJustRun { commitsService.fetchAndIngestLatestCommits(any(), any()) }
            coJustRun { issuesService.fetchAndIngestAllIssues(any(), any(), any(), any(), any(), any()) }
            coJustRun { pullRequestsService.fetchAndIngestAllPullRequests(any(), any(), any(), any(), any(), any()) }
            every { repoSnapshotRepository.updateSyncTimestamps(any(), any()) } just runs

            service.updateRepository(UpdateRepositoryRequest("owner", "repo"), true)
            testScope.advanceUntilIdle()

            coVerify { fileService.fetchAndIngestFileUpdatesIncremental(repo, any()) }
            coVerify { commitsService.fetchAndIngestLatestCommits(repo.snapshot!!, any()) }
            coVerify { issuesService.fetchAndIngestAllIssues(repo.id, "owner", "repo", any(), true, any()) }
            coVerify {
                pullRequestsService.fetchAndIngestAllPullRequests(
                    repo.id,
                    "owner",
                    "repo",
                    any(),
                    true,
                    any(),
                )
            }
            verify { repoSnapshotRepository.updateSyncTimestamps(repo.id, any()) }
        }

        @Test
        fun `launches verify checks when performUpdate is false`() = runTest {
            val repo = repoConnection("owner", "repo").apply {
                snapshot = GithubRepositorySnapshot(repository = this)
            }
            every { repoConnectionRepository.findByOwnerAndName("owner", "repo") } returns repo
            coJustRun { fileService.verifyFileSyncStatus(any(), any()) }
            coJustRun { commitsService.verifyCommitSyncStatus(any(), any()) }
            coJustRun { issuesService.fetchAndIngestAllIssues(any(), any(), any(), any(), any(), any()) }
            coJustRun { pullRequestsService.fetchAndIngestAllPullRequests(any(), any(), any(), any(), any(), any()) }

            service.updateRepository(UpdateRepositoryRequest("owner", "repo"), false)
            testScope.advanceUntilIdle()

            coVerify { fileService.verifyFileSyncStatus(repo, any()) }
            coVerify { commitsService.verifyCommitSyncStatus(repo, any()) }
            coVerify { issuesService.fetchAndIngestAllIssues(repo.id, "owner", "repo", any(), false, any()) }
            coVerify {
                pullRequestsService.fetchAndIngestAllPullRequests(
                    repo.id,
                    "owner",
                    "repo",
                    any(),
                    false,
                    any(),
                )
            }
            verify(exactly = 0) { repoSnapshotRepository.updateSyncTimestamps(any(), any()) }
        }
    }

    @Nested
    inner class EventPublishing {
        @Test
        fun `publishes GithubRepositoryResourcesFetchingStartedEvent on update`() {
            val repo = repoConnection("owner", "repo").apply {
                snapshot = GithubRepositorySnapshot(repository = this)
            }
            every { repoConnectionRepository.findByOwnerAndName("owner", "repo") } returns repo
            every { repoConnectionRepository.save(any()) } returns repo
            coJustRun { fileService.fetchAndIngestFileUpdatesIncremental(any(), any()) }
            coJustRun { commitsService.fetchAndIngestLatestCommits(any(), any()) }
            coJustRun { issuesService.fetchAndIngestAllIssues(any(), any(), any(), any(), any(), any()) }
            coJustRun { pullRequestsService.fetchAndIngestAllPullRequests(any(), any(), any(), any(), any(), any()) }
            every { repoSnapshotRepository.updateSyncTimestamps(any(), any()) } just runs

            service.updateRepository(UpdateRepositoryRequest("owner", "repo"), true)

            verify { eventPublisher.publishEvent(any<GithubRepositoryResourcesFetchingStartedEvent>()) }
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun repoConnection(owner: String, name: String) = GithubRepositoryConnection(
        owner = owner,
        name = name,
        user = user,
    )
}
