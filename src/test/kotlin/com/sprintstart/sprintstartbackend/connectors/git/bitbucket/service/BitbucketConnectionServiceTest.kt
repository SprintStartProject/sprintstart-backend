package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service

import com.sprintstart.sprintstartbackend.connectors.atlassian.external.AtlassianCredentialApi
import com.sprintstart.sprintstartbackend.connectors.atlassian.external.AtlassianCredentialSecret
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.exception.AtlassianCredentialNotFoundException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.BitbucketClient
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.BitbucketRepositoryAlreadyConnectedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.BitbucketRepositoryConnectionFailedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.BitbucketRepositoryConnectionInitiatedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketRepositoryConfig
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketProjectAccessDeniedException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryDoesNotExistException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.request.ConnectBitbucketRepositoryRequest
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketConnectionRepository
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketRepositoryConfigRepository
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.BitbucketConnectionService
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal.BitbucketCommitsService
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal.BitbucketFileService
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal.BitbucketPullRequestsService
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal.BitbucketWorkspaceService
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import java.util.UUID

class BitbucketConnectionServiceTest {
    private val connectionRepository = mockk<BitbucketConnectionRepository>()
    private val configRepository = mockk<BitbucketRepositoryConfigRepository>()
    private val bitbucketClient = mockk<BitbucketClient>()
    private val fileService = mockk<BitbucketFileService>(relaxed = true)
    private val commitsService = mockk<BitbucketCommitsService>(relaxed = true)
    private val prService = mockk<BitbucketPullRequestsService>(relaxed = true)
    private val workspaceService = mockk<BitbucketWorkspaceService>(relaxed = true)
    private val credentialApi = mockk<AtlassianCredentialApi>()
    private val userApi = mockk<UserApi>()
    private val connectionStateService = mockk<BitbucketConnectionStateService>(relaxed = true)
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    // Unconfined so the launched ingestion runs inline and can be verified without waiting.
    private val applicationScope = CoroutineScope(Dispatchers.Unconfined)

    private val service = BitbucketConnectionService(
        connectionRepository = connectionRepository,
        configRepository = configRepository,
        bitbucketClient = bitbucketClient,
        fileService = fileService,
        commitsService = commitsService,
        prService = prService,
        workspaceService = workspaceService,
        credentialApi = credentialApi,
        userApi = userApi,
        connectionStateService = connectionStateService,
        applicationScope = applicationScope,
        eventPublisher = eventPublisher,
    )

    private val projectId = UUID.randomUUID()

    private val request = ConnectBitbucketRepositoryRequest(
        workspace = "sprintstart",
        slug = "sprintstart-backend",
        credentialName = "team-token",
        projectId = projectId,
    )

    @Test
    fun `stores the connection and starts ingesting on a new repository`() = runTest {
        every { userApi.userHasAccessToProject("auth-id", projectId) } returns true
        every { credentialApi.findSecret("auth-id", "team-token") } returns secret()
        coEvery { bitbucketClient.repositoryExists("sprintstart", "sprintstart-backend", "api-token") } returns true
        every { connectionRepository.findByWorkspaceAndSlug("sprintstart", "sprintstart-backend") } returns null
        val saved = slot<BitbucketConnection>()
        every { connectionRepository.save(capture(saved)) } answers { firstArg() }
        val savedConfig = slot<BitbucketRepositoryConfig>()
        every { configRepository.save(capture(savedConfig)) } answers { firstArg() }

        val response = service.connectRepositoryIfExists("auth-id", request)

        with(saved.captured) {
            assertThat(workspace).isEqualTo("sprintstart")
            assertThat(slug).isEqualTo("sprintstart-backend")
            assertThat(credentialAuthId).isEqualTo("auth-id")
            assertThat(credentialName).isEqualTo("team-token")
            // The link is what makes the repository's artifacts visible to the AI index, so a new
            // connection must carry the project it was connected for.
            assertThat(projectIds).containsExactly(projectId)
        }
        coVerify {
            fileService.fetchAndIngestFilesOfRepository(
                saved.captured.id,
                response.transactionId,
            )
        }
        // The workspace fetch joins the collectors; its own guard decides whether anything is read.
        coVerify {
            workspaceService.connectWorkspaceIfNecessary(
                workspace = "sprintstart",
                authId = "auth-id",
                credentialName = "team-token",
                transactionId = response.transactionId,
            )
        }
        assertThat(savedConfig.captured.repository).isSameAs(saved.captured)
        assertThat(savedConfig.captured.autoUpdate).isTrue()
        assertThat(savedConfig.captured.nextSyncAt).isNotNull()
        verify { eventPublisher.publishEvent(any<BitbucketRepositoryConnectionInitiatedEvent>()) }
        // A new connection must read UPDATING through its first ingest, not the UP_TO_DATE
        // default it was stored with, and settle once its collectors do.
        verify { connectionStateService.markUpdating(saved.captured.id) }
        coVerify { connectionStateService.awaitCollectorsAndFinalize(saved.captured.id, any()) }
    }

    @Test
    fun `reports an already connected repository without storing it twice`() = runTest {
        every { userApi.userHasAccessToProject("auth-id", projectId) } returns true
        every { credentialApi.findSecret("auth-id", "team-token") } returns secret()
        coEvery { bitbucketClient.repositoryExists(any(), any(), any()) } returns true
        every { connectionRepository.findByWorkspaceAndSlug("sprintstart", "sprintstart-backend") } returns
            existingConnection(projectIdsInternal = mutableSetOf(projectId))

        service.connectRepositoryIfExists("auth-id", request)

        verify { eventPublisher.publishEvent(any<BitbucketRepositoryAlreadyConnectedEvent>()) }
        verify(exactly = 0) { connectionRepository.save(any()) }
        verify(exactly = 0) { configRepository.save(any()) }
        coVerify(exactly = 0) { fileService.fetchAndIngestFilesOfRepository(any(), any()) }
    }

    /**
     * A repository that is already connected is the same row for every project, so connecting it to a
     * further project links that project instead of storing a duplicate connection and re-collecting
     * everything under a second id.
     */
    @Test
    fun `links a further project to an already connected repository`() = runTest {
        val existing = existingConnection()
        every { userApi.userHasAccessToProject("auth-id", projectId) } returns true
        every { credentialApi.findSecret("auth-id", "team-token") } returns secret()
        coEvery { bitbucketClient.repositoryExists(any(), any(), any()) } returns true
        every { connectionRepository.findByWorkspaceAndSlug("sprintstart", "sprintstart-backend") } returns existing
        every { connectionRepository.save(any<BitbucketConnection>()) } answers { firstArg() }

        service.connectRepositoryIfExists("auth-id", request)

        assertThat(existing.projectIds).containsExactly(projectId)
        verify(exactly = 1) { connectionRepository.save(existing) }
        verify { eventPublisher.publishEvent(any<BitbucketRepositoryAlreadyConnectedEvent>()) }
        coVerify(exactly = 0) { fileService.fetchAndIngestFilesOfRepository(any(), any()) }
    }

    /**
     * Re-connecting a repository to a project it is already linked to must not dirty the row: the
     * connect is idempotent, and a pointless write would show up as an update on every repeated call.
     */
    @Test
    fun `writes nothing when the project is already linked`() = runTest {
        every { userApi.userHasAccessToProject("auth-id", projectId) } returns true
        every { credentialApi.findSecret("auth-id", "team-token") } returns secret()
        coEvery { bitbucketClient.repositoryExists(any(), any(), any()) } returns true
        every { connectionRepository.findByWorkspaceAndSlug("sprintstart", "sprintstart-backend") } returns
            existingConnection(projectIdsInternal = mutableSetOf(projectId))

        service.connectRepositoryIfExists("auth-id", request)

        verify(exactly = 0) { connectionRepository.save(any()) }
    }

    private fun existingConnection(projectIdsInternal: MutableSet<UUID> = mutableSetOf()) =
        BitbucketConnection(
            workspace = "sprintstart",
            slug = "sprintstart-backend",
            credentialAuthId = "auth-id",
            credentialName = "team-token",
            projectIdsInternal = projectIdsInternal,
        )

    /**
     * The access check runs before any Bitbucket call, so a caller without project access cannot
     * use this endpoint to probe which credentials or repositories exist.
     */
    @Test
    fun `refuses a caller without access to the target project`() = runTest {
        every { userApi.userHasAccessToProject("auth-id", projectId) } returns false

        assertThrows<BitbucketProjectAccessDeniedException> {
            service.connectRepositoryIfExists("auth-id", request)
        }

        coVerify(exactly = 0) { bitbucketClient.repositoryExists(any(), any(), any()) }
        verify(exactly = 0) { connectionRepository.save(any()) }
        verify(exactly = 1) {
            eventPublisher.publishEvent(match<Any> { it is BitbucketRepositoryConnectionFailedEvent })
        }
    }

    @Test
    fun `stores nothing when the named credential does not exist`() = runTest {
        every { userApi.userHasAccessToProject("auth-id", projectId) } returns true
        every { credentialApi.findSecret(any(), any()) } returns null

        assertThrows<AtlassianCredentialNotFoundException> {
            service.connectRepositoryIfExists("auth-id", request)
        }

        verify(exactly = 0) { connectionRepository.save(any()) }
        coVerify(exactly = 0) { fileService.fetchAndIngestFilesOfRepository(any(), any()) }
    }

    @Test
    fun `stores nothing when the repository is out of reach`() = runTest {
        every { userApi.userHasAccessToProject("auth-id", projectId) } returns true
        every { credentialApi.findSecret("auth-id", "team-token") } returns secret()
        coEvery { bitbucketClient.repositoryExists(any(), any(), any()) } returns false

        assertThrows<BitbucketRepositoryDoesNotExistException> {
            service.connectRepositoryIfExists("auth-id", request)
        }

        verify(exactly = 0) { connectionRepository.save(any()) }
        coVerify(exactly = 0) { fileService.fetchAndIngestFilesOfRepository(any(), any()) }
    }

    private fun secret() = AtlassianCredentialSecret(userEmail = "user@example.com", apiToken = "api-token")
}
