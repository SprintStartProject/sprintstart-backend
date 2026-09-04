package com.sprintstart.sprintstartbackend.connectors.github.service

import com.sprintstart.sprintstartbackend.connectors.github.GithubClient
import com.sprintstart.sprintstartbackend.connectors.github.external.events.initial.GithubRepositoryAlreadyConnectedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.initial.GithubRepositoryConnectionInitiatedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.initial.GithubRepositoryConnectionInitiationFailedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.projects.GithubRepositoryProjectLinkChangedEvent
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubUser
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubUserPat
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.ConnectRepositoryRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.DiscoverRepositoriesRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.DiscoverRepositoriesResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.DiscoveredRepository
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.GithubUserPatNotFoundException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.RepositoryNotFoundException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.SourceNotFoundException
import com.sprintstart.sprintstartbackend.connectors.github.repository.GithubRepositoryConfigRepository
import com.sprintstart.sprintstartbackend.connectors.github.repository.GithubRepositoryConnectionRepository
import com.sprintstart.sprintstartbackend.connectors.github.repository.GithubUserRepository
import com.sprintstart.sprintstartbackend.connectors.github.service.internal.GithubCommitsService
import com.sprintstart.sprintstartbackend.connectors.github.service.internal.GithubFileService
import com.sprintstart.sprintstartbackend.connectors.github.service.internal.GithubIssuesService
import com.sprintstart.sprintstartbackend.connectors.github.service.internal.GithubOrgService
import com.sprintstart.sprintstartbackend.connectors.github.service.internal.GithubPullRequestsService
import com.sprintstart.sprintstartbackend.connectors.overview.models.ConnectorSource
import com.sprintstart.sprintstartbackend.user.external.UserApi
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
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class GithubConnectorServiceTest {
    private val testScope = TestScope()
    private val testProjectId = UUID.randomUUID()

    private val repoConnectionRepository = mockk<GithubRepositoryConnectionRepository>()
    private val repoConfigRepository = mockk<GithubRepositoryConfigRepository>()
    private val githubUserRepository = mockk<GithubUserRepository>()
    private val fileService = mockk<GithubFileService>()
    private val commitsService = mockk<GithubCommitsService>()
    private val issuesService = mockk<GithubIssuesService>()
    private val pullRequestsService = mockk<GithubPullRequestsService>()
    private val orgService = mockk<GithubOrgService>()
    private val githubClient = mockk<GithubClient>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val userApi = mockk<UserApi>()

    private lateinit var service: GithubConnectorService

    @BeforeEach
    fun setUp() {
        every { userApi.userHasAccessToProject(any(), any()) } returns true
        // Default: not connected anywhere yet, so the full connect flow runs. Tests covering the
        // reuse path override this.
        every { repoConnectionRepository.findByOwnerAndName(any(), any()) } returns null

        service = GithubConnectorService(
            applicationScope = testScope,
            repoConnectionRepository = repoConnectionRepository,
            repoConfigRepository = repoConfigRepository,
            githubUserRepository = githubUserRepository,
            fileService = fileService,
            commitsService = commitsService,
            issuesService = issuesService,
            pullRequestsService = pullRequestsService,
            orgService = orgService,
            githubClient = githubClient,
            eventPublisher = eventPublisher,
            userApi = userApi,
        )
    }

    @Nested
    inner class ConnectRepositoryIfNecessary {
        @Test
        fun `connectRepositoryIfNecessary throws ResponseStatusException when user has no project access`() =
            runTest {
                every { userApi.userHasAccessToProject("mock-id", testProjectId) } returns false

                assertFailsWith<ResponseStatusException> {
                    service.connectRepositoryIfNecessary("mock-id", connectRequest())
                }
            }

        @Test
        fun `connectRepositoryIfNecessary throws GithubUserPatNotFoundException when PAT not found`() =
            runTest {
                every { repoConnectionRepository.findByOwnerAndName(any(), any()) } returns null
                every { userApi.getUserIdByAuthId(any()) } returns Optional.of(UUID.randomUUID())
                every { githubUserRepository.findById(any()) } returns Optional.empty()

                assertFailsWith<GithubUserPatNotFoundException> {
                    service.connectRepositoryIfNecessary("mock-id", connectRequest())
                }
            }

        @Test
        fun `connectRepositoryIfNecessary throws RepositoryNotFoundException when repo does not exist on GitHub`() =
            runTest {
                every { repoConnectionRepository.findByOwnerAndName(any(), any()) } returns null
                every { userApi.getUserIdByAuthId(any()) } returns Optional.of(UUID.randomUUID())
                every {
                    githubUserRepository.findById(any())
                } returns Optional.of(
                    GithubUser(GithubUserPat("some-id", "test-pat"), token = "test-token"),
                )
                coEvery { githubClient.repositoryExists(any()) } returns false

                assertFailsWith<RepositoryNotFoundException> {
                    service.connectRepositoryIfNecessary("mock-id", connectRequest())
                }
            }

        @Test
        fun `connectRepositoryIfNecessary returns a transactionId when repo exists`() = testScope.runTest {
            stubSuccessfulConnect()

            val outcome = service.connectRepositoryIfNecessary("auth-id", connectRequest())

            assertThat(outcome.transactionId).isNotNull()
            assertThat(outcome.wasReused).isFalse()
        }

        @Test
        fun `connectRepositoryIfNecessary links an already-connected repository instead of fetching it again`() =
            testScope.runTest {
                val existing = GithubRepositoryConnection(
                    owner = "owner",
                    name = "repo",
                    user = GithubUser(GithubUserPat("other-pm", "their-pat"), token = "their-token"),
                    projectIdsInternal = mutableSetOf(UUID.randomUUID()),
                )
                every { repoConnectionRepository.findByOwnerAndName("owner", "repo") } returns existing
                every { repoConnectionRepository.save(any()) } answers { firstArg() }

                val outcome = service.connectRepositoryIfNecessary("auth-id", connectRequest())

                assertThat(outcome.wasReused).isTrue()
                assertThat(existing.projectIds).contains(testProjectId)
                // No second connection row, no second snapshot, no second config, and above all no
                // re-fetch of everything already stored.
                verify(exactly = 0) { repoConfigRepository.save(any()) }
                coVerify(exactly = 0) { fileService.fetchAndIngestAllFiles(any(), any(), any(), any()) }
                coVerify(exactly = 0) { githubClient.repositoryExists(any()) }
            }

        @Test
        fun `reusing a connection announces the link so the artifacts follow`() = testScope.runTest {
            val existing = GithubRepositoryConnection(
                owner = "owner",
                name = "repo",
                user = GithubUser(GithubUserPat("other-pm", "their-pat"), token = "their-token"),
                projectIdsInternal = mutableSetOf(),
            )
            val event = slot<GithubRepositoryProjectLinkChangedEvent>()
            every { repoConnectionRepository.findByOwnerAndName("owner", "repo") } returns existing
            every { repoConnectionRepository.save(any()) } answers { firstArg() }
            every { eventPublisher.publishEvent(capture(event)) } returns Unit

            service.connectRepositoryIfNecessary("auth-id", connectRequest())

            assertThat(event.captured.linked).isTrue()
            assertThat(event.captured.projectId).isEqualTo(testProjectId)
        }

        @Test
        fun `reuse reports its transaction under an ingestion run of its own`() = testScope.runTest {
            val existing = GithubRepositoryConnection(
                owner = "owner",
                name = "repo",
                user = GithubUser(GithubUserPat("other-pm", "their-pat"), token = "their-token"),
                projectIdsInternal = mutableSetOf(),
            )
            val events = mutableListOf<Any>()
            every { repoConnectionRepository.findByOwnerAndName("owner", "repo") } returns existing
            every { repoConnectionRepository.save(any()) } answers { firstArg() }
            every { eventPublisher.publishEvent(capture(events)) } returns Unit

            val outcome = service.connectRepositoryIfNecessary("auth-id", connectRequest())

            // The caller is handed a transaction id, so something has to resolve it. Without this
            // event nothing records the connect and the id points at no run at all.
            val announced = events.filterIsInstance<GithubRepositoryAlreadyConnectedEvent>().single()
            assertThat(announced.transactionId).isEqualTo(outcome.transactionId)
            assertThat(announced.owner).isEqualTo("owner")
            assertThat(announced.name).isEqualTo("repo")
        }

        @Test
        fun `reuse still refuses a project the caller does not manage`() = testScope.runTest {
            every { userApi.userHasAccessToProject("auth-id", testProjectId) } returns false

            assertFailsWith<ResponseStatusException> {
                service.connectRepositoryIfNecessary("auth-id", connectRequest())
            }

            verify(exactly = 0) { repoConnectionRepository.findByOwnerAndName(any(), any()) }
        }

        @Test
        fun `connectRepositoryIfNecessary saves repository connection`() = testScope.runTest {
            stubSuccessfulConnect()

            service.connectRepositoryIfNecessary("auth-id", connectRequest())

            coVerify { repoConnectionRepository.save(match { it.owner == "owner" && it.name == "repo" }) }
        }

        @Test
        fun `connectRepositoryIfNecessary saves config with nextSyncAt set`() = testScope.runTest {
            stubSuccessfulConnect()

            service.connectRepositoryIfNecessary("auth-id", connectRequest())

            coVerify {
                repoConfigRepository.save(match { it.nextSyncAt != null })
            }
        }

        @Test
        fun `connectRepositoryIfNecessary saves config with default as source enabled`() = testScope.runTest {
            stubSuccessfulConnect()

            service.connectRepositoryIfNecessary("auth-id", connectRequest())

            coVerify {
                repoConnectionRepository.save(match { it.sourceEnabled })
            }
        }

        @Test
        fun `connectRepositoryIfNecessary launches all background ingestion jobs`() = testScope.runTest {
            stubSuccessfulConnect()

            service.connectRepositoryIfNecessary("auth-id", connectRequest())
            advanceUntilIdle()

            coVerify { fileService.fetchAndIngestAllFiles(any(), any(), any(), any()) }
            coVerify { commitsService.fetchAndIngestAllCommits(any(), any()) }
            coVerify { issuesService.fetchAndIngestAllIssues(any(), any(), any(), any()) }
            coVerify { pullRequestsService.fetchAndIngestAllPullRequests(any(), any(), any(), any()) }
            coVerify { orgService.connectGithubOrgIfNecessary("owner", "test-token", any()) }
        }

        @Test
        fun `connectRepositoryIfNecessary passes same transactionId to all background jobs`() = testScope.runTest {
            stubSuccessfulConnect()

            service.connectRepositoryIfNecessary("auth-id", connectRequest())
            advanceUntilIdle()

            val fileTransactionId = slot<UUID>()
            val commitsTransactionId = slot<UUID>()
            coVerify { fileService.fetchAndIngestAllFiles(any(), any(), any(), capture(fileTransactionId)) }
            coVerify { commitsService.fetchAndIngestAllCommits(any(), capture(commitsTransactionId)) }

            assertThat(fileTransactionId.captured).isEqualTo(commitsTransactionId.captured)
        }

        @Test
        fun `already connected repository is linked without starting ingestion`() = testScope.runTest {
            val user = GithubUser(GithubUserPat("auth-id", "pat"), token = "test-token")
            val repository = GithubRepositoryConnection(
                owner = "owner",
                name = "repo",
                user = user,
                projectIdsInternal = mutableSetOf(),
            )
            every { repoConnectionRepository.findByOwnerAndName("owner", "repo") } returns repository
            every { repoConnectionRepository.save(repository) } returns repository

            val transactionId = service.connectRepositoryIfNecessary("auth-id", connectRequest())

            assertThat(transactionId).isNotNull()
            assertThat(repository.projectIds).contains(testProjectId)
            coVerify(exactly = 0) { githubClient.repositoryExists(any()) }
            coVerify(exactly = 0) { fileService.fetchAndIngestAllFiles(any(), any(), any(), any()) }
            coVerify(exactly = 0) { commitsService.fetchAndIngestAllCommits(any(), any()) }
            coVerify(exactly = 0) { issuesService.fetchAndIngestAllIssues(any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) {
                pullRequestsService.fetchAndIngestAllPullRequests(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            }
            coVerify(exactly = 0) { orgService.connectGithubOrgIfNecessary(any(), any(), any()) }
        }
    }

    // ── event publishing ──────────────────────────────────────────────────────

    @Nested
    inner class EventPublishing {
        @Test
        fun `publishes GithubRepositoryConnectionInitiatedEvent on connect`() = testScope.runTest {
            coEvery { githubClient.repositoryExists(any()) } returns true
            stubSuccessfulConnect()

            service.connectRepositoryIfNecessary("auth-id", connectRequest())

            verify { eventPublisher.publishEvent(any<GithubRepositoryConnectionInitiatedEvent>()) }
        }

        @Test
        fun `publishes GithubRepositoryConnectionInitiationFailedEvent when repo not found`() = testScope.runTest {
            every { repoConnectionRepository.findByOwnerAndName(any(), any()) } returns null
            every { userApi.getUserIdByAuthId(any()) } returns Optional.of(UUID.randomUUID())
            every {
                githubUserRepository.findById(any())
            } returns Optional.of(
                GithubUser(GithubUserPat("some-id", "test-pat"), token = "test-token"),
            )
            coEvery { githubClient.repositoryExists(any()) } returns false

            assertFailsWith<RepositoryNotFoundException> {
                service.connectRepositoryIfNecessary("auth-id", connectRequest())
            }

            verify { eventPublisher.publishEvent(any<GithubRepositoryConnectionInitiationFailedEvent>()) }
        }
    }

    @Nested
    inner class SourceManagement {
        @Test
        fun `getAllSources returns all connections`() {
            val user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token")
            val repo = repoConnection("owner", "repo", user)
            every { repoConnectionRepository.findAll() } returns listOf(repo)

            val result = service.getAllSources()

            assertThat(result).hasSize(1)
            assertThat(result[0].owner).isEqualTo("owner")
            assertThat(result[0].name).isEqualTo("repo")
        }

        @Test
        fun `getAllSources returns empty list when no connections exist`() {
            every { repoConnectionRepository.findAll() } returns emptyList()

            val result = service.getAllSources()

            assertThat(result).isEmpty()
        }

        @Test
        fun `patchSource sets sourceEnabled on matching connection`() {
            val user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token")
            val repo = repoConnection("owner", "repo", user).apply { sourceEnabled = false }
            val source = ConnectorSource(
                id = "owner/repo",
                name = "repo",
                url = "https://github.com/owner/repo",
                enabled = false,
            )

            every { repoConnectionRepository.findAll() } returns listOf(repo)
            every { repoConnectionRepository.save(repo) } returns repo

            service.patchSource(source, true)

            assertThat(repo.sourceEnabled).isTrue()
            verify { repoConnectionRepository.save(repo) }
        }

        @Test
        fun `patchSource disables source when newStatus is false`() {
            val user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token")
            val repo = repoConnection("owner", "repo", user).apply { sourceEnabled = true }
            val source = ConnectorSource(
                id = "owner/repo",
                name = "repo",
                url = "https://github.com/owner/repo",
                enabled = true,
            )

            every { repoConnectionRepository.findAll() } returns listOf(repo)
            every { repoConnectionRepository.save(repo) } returns repo

            service.patchSource(source, false)

            assertThat(repo.sourceEnabled).isFalse()
            verify { repoConnectionRepository.save(repo) }
        }

        @Test
        fun `patchSource throws SourceNotFoundException when source not found`() {
            val source = ConnectorSource(
                id = "unknown/repo",
                name = "repo",
                url = "https://github.com/unknown/repo",
                enabled = false,
            )
            every { repoConnectionRepository.findAll() } returns emptyList()

            assertFailsWith<SourceNotFoundException> { service.patchSource(source, true) }
        }
    }

    @Nested
    inner class DiscoverRepositories {
        @Test
        fun `discoverRepositoriesOfOrg returns discovered repositories with metadata`() = testScope.runTest {
            val request = DiscoverRepositoriesRequest(
                owner = "org",
                userId = "user-id",
                tokenName = "my-pat",
                page = 0,
                pageSize = 20,
            )
            val token = GithubUser(GithubUserPat("user-id", "my-pat"), token = "pat-token")
            val discovered = listOf(
                DiscoveredRepository("repo1", false, "https://github.com/org/repo1"),
                DiscoveredRepository("repo2", true, "https://github.com/org/repo2"),
            )
            val connectedRepo = GithubRepositoryConnection(owner = "org", name = "repo1", user = token)
                .apply { sourceEnabled = true }

            every { githubUserRepository.findById(GithubUserPat("user-id", "my-pat")) } returns Optional.of(token)
            coEvery {
                githubClient.discoverRepositoriesOfOrg("org", "pat-token", 0, 20)
            } returns DiscoverRepositoriesResponse(discovered)
            every { repoConnectionRepository.findByOwnerAndName("org", "repo1") } returns connectedRepo
            every { repoConnectionRepository.findByOwnerAndName("org", "repo2") } returns null

            val result = service.discoverRepositoriesOfOrg(request)

            assertThat(result.repositories).hasSize(2)
            assertThat(result.repositories[0].alreadyConnected).isTrue()
            assertThat(result.repositories[0].isEnabled).isTrue()
            assertThat(result.repositories[1].alreadyConnected).isFalse()
            assertThat(result.repositories[1].isEnabled).isNull()
        }

        @Test
        fun `discoverRepositoriesOfOrg marks connected disabled repo with isEnabled false`() = testScope.runTest {
            val request = DiscoverRepositoriesRequest(
                owner = "org",
                userId = "user-id",
                tokenName = "my-pat",
                page = 0,
                pageSize = 20,
            )
            val token = GithubUser(GithubUserPat("user-id", "my-pat"), token = "pat-token")
            val discovered = listOf(DiscoveredRepository("repo", false, "https://github.com/org/repo"))
            val connectedRepo = GithubRepositoryConnection(owner = "org", name = "repo", user = token)
                .apply { sourceEnabled = false }

            every { githubUserRepository.findById(GithubUserPat("user-id", "my-pat")) } returns Optional.of(token)
            coEvery {
                githubClient.discoverRepositoriesOfOrg("org", "pat-token", 0, 20)
            } returns DiscoverRepositoriesResponse(discovered)
            every { repoConnectionRepository.findByOwnerAndName("org", "repo") } returns connectedRepo

            val result = service.discoverRepositoriesOfOrg(request)

            assertThat(result.repositories[0].alreadyConnected).isTrue()
            assertThat(result.repositories[0].isEnabled).isFalse()
        }

        @Test
        fun `discoverRepositoriesOfUser calls user endpoint and maps metadata`() = testScope.runTest {
            val request = DiscoverRepositoriesRequest(
                owner = "ghuser",
                userId = "user-id",
                tokenName = "my-pat",
                page = 2,
                pageSize = 10,
            )
            val token = GithubUser(GithubUserPat("user-id", "my-pat"), token = "pat-token")
            val discovered = listOf(DiscoveredRepository("repo", false, "https://github.com/ghuser/repo"))

            every { githubUserRepository.findById(GithubUserPat("user-id", "my-pat")) } returns Optional.of(token)
            coEvery {
                githubClient.discoverRepositoriesOfUser("ghuser", "pat-token", 2, 10)
            } returns DiscoverRepositoriesResponse(discovered)
            every { repoConnectionRepository.findByOwnerAndName("ghuser", "repo") } returns null

            val result = service.discoverRepositoriesOfUser(request)

            assertThat(result.repositories).hasSize(1)
            assertThat(result.repositories[0].alreadyConnected).isFalse()
            assertThat(result.repositories[0].isEnabled).isNull()
        }

        @Test
        fun `discoverRepositoriesOfOrg throws GithubUserPatNotFoundException when pat missing`() = testScope.runTest {
            val request = DiscoverRepositoriesRequest(
                owner = "org",
                userId = "user-id",
                tokenName = "missing-pat",
                page = 0,
                pageSize = 20,
            )
            every { githubUserRepository.findById(GithubUserPat("user-id", "missing-pat")) } returns Optional.empty()

            assertFailsWith<GithubUserPatNotFoundException> {
                service.discoverRepositoriesOfOrg(request)
            }
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun connectRequest() = ConnectRepositoryRequest(
        owner = "owner",
        name = "repo",
        tokenName = "ghp_abcdefghijklmnopqrstuvwxyz0123456789",
        projectId = testProjectId,
    )

    private fun repoConnection(owner: String, name: String, user: GithubUser) = GithubRepositoryConnection(
        owner = owner,
        name = name,
        user = user,
    )

    private fun stubSuccessfulConnect() {
        every { repoConnectionRepository.findByOwnerAndName(any(), any()) } returns null
        every { userApi.getUserIdByAuthId(any()) } returns Optional.of(UUID.randomUUID())
        every {
            githubUserRepository.findById(any())
        } returns Optional.of(
            GithubUser(GithubUserPat("some-id", "test-pat"), token = "test-token"),
        )
        coEvery { githubClient.repositoryExists(any()) } returns true
        every { repoConnectionRepository.save(any()) } answers { firstArg() }
        every { repoConfigRepository.save(any()) } answers { firstArg() }
        coJustRun { fileService.fetchAndIngestAllFiles(any(), any(), any(), any()) }
        coJustRun { commitsService.fetchAndIngestAllCommits(any(), any()) }
        coJustRun { issuesService.fetchAndIngestAllIssues(any(), any(), any(), any(), any()) }
        coJustRun { pullRequestsService.fetchAndIngestAllPullRequests(any(), any(), any(), any(), any()) }
        coJustRun { orgService.connectGithubOrgIfNecessary(any(), any(), any()) }
    }
}
