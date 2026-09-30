package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.update.BitbucketRepositoryUpdateFailedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.update.BitbucketRepositoryUpdateStartedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryConnectionNotFoundException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryNotEnabledException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketConnectionRepository
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal.BitbucketCommitsService
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal.BitbucketFileService
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal.BitbucketPullRequestsService
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal.BitbucketWorkspaceService
import com.sprintstart.sprintstartbackend.user.external.UserApi
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
import java.util.Optional
import java.util.UUID

class BitbucketUpdatesServiceTest {
    private val connectionRepository = mockk<BitbucketConnectionRepository>()
    private val fileService = mockk<BitbucketFileService>(relaxed = true)
    private val commitsService = mockk<BitbucketCommitsService>(relaxed = true)
    private val prService = mockk<BitbucketPullRequestsService>(relaxed = true)
    private val workspaceService = mockk<BitbucketWorkspaceService>(relaxed = true)
    private val connectionStateService = mockk<BitbucketConnectionStateService>(relaxed = true)
    private val userApi = mockk<UserApi>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    // Unconfined so the launched ingests run inline and can be verified without waiting.
    private val applicationScope = CoroutineScope(Dispatchers.Unconfined)

    private val service = BitbucketUpdatesService(
        connectionRepository = connectionRepository,
        fileService = fileService,
        commitsService = commitsService,
        prService = prService,
        workspaceService = workspaceService,
        connectionStateService = connectionStateService,
        userApi = userApi,
        applicationScope = applicationScope,
        eventPublisher = eventPublisher,
    )

    private val projectId = UUID.randomUUID()
    private val authId = "auth-id"
    private val connection = connection("sprintstart", "backend")

    @Test
    fun `re-ingests files, commits, pull requests and workspace metadata of the connection`() = runTest {
        every { connectionRepository.findById(connection.id) } returns Optional.of(connection)
        val fileTransaction = slot<UUID>()
        val commitTransaction = slot<UUID>()
        val prTransaction = slot<UUID>()

        val transactionId = service.updateRepository(connection.id)

        coVerify { fileService.fetchAndIngestFilesOfRepository(connection.id, capture(fileTransaction)) }
        coVerify { commitsService.fetchAndIngestCommitsOfRepository(connection.id, capture(commitTransaction)) }
        coVerify { prService.fetchAndIngestPullRequests(connection.id, capture(prTransaction)) }
        coVerify {
            workspaceService.connectWorkspaceIfNecessary(
                workspace = "sprintstart",
                authId = "auth-id",
                credentialName = "team-token",
                transactionId = transactionId,
            )
        }
        assertThat(fileTransaction.captured).isEqualTo(transactionId)
        assertThat(commitTransaction.captured).isEqualTo(transactionId)
        assertThat(prTransaction.captured).isEqualTo(transactionId)
    }

    @Test
    fun `marks the repository updating before the collectors start`() = runTest {
        every { connectionRepository.findById(connection.id) } returns Optional.of(connection)

        service.updateRepository(connection.id)

        verify(exactly = 1) { connectionStateService.markUpdating(connection.id) }
    }

    /**
     * The run has to exist from the moment the update is accepted, so a collector that dies before
     * publishing its own started event still has an attributed run to fail.
     */
    @Test
    fun `opens the run for the repository before the collectors start`() = runTest {
        every { connectionRepository.findById(connection.id) } returns Optional.of(connection)

        val transactionId = service.updateRepository(connection.id)

        verify(exactly = 1) {
            eventPublisher.publishEvent(
                BitbucketRepositoryUpdateStartedEvent(
                    transactionId = transactionId,
                    repositoryId = connection.id,
                    workspace = "sprintstart",
                    slug = "backend",
                ),
            )
        }
    }

    @Test
    fun `fails when the repository is not connected`() = runTest {
        val missingId = UUID.randomUUID()
        every { connectionRepository.findById(missingId) } returns Optional.empty()

        assertThrows<BitbucketRepositoryNotConnectedException> {
            service.updateRepository(missingId)
        }

        coVerify(exactly = 0) { fileService.fetchAndIngestFilesOfRepository(any(), any()) }
        coVerify(exactly = 0) { commitsService.fetchAndIngestCommitsOfRepository(any(), any()) }
        coVerify(exactly = 0) { prService.fetchAndIngestPullRequests(any(), any()) }
        verify(exactly = 0) { connectionStateService.markUpdating(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any<BitbucketRepositoryUpdateStartedEvent>()) }
    }

    /** An explicit update of a paused repository is refused, and no run is opened for it. */
    @Test
    fun `refuses to update a disabled repository`() = runTest {
        val disabled = connection("sprintstart", "backend").apply { sourceEnabled = false }
        every { connectionRepository.findById(disabled.id) } returns Optional.of(disabled)

        assertThrows<BitbucketRepositoryNotEnabledException> {
            service.updateRepository(disabled.id)
        }

        coVerify(exactly = 0) { fileService.fetchAndIngestFilesOfRepository(any(), any()) }
        coVerify(exactly = 0) { commitsService.fetchAndIngestCommitsOfRepository(any(), any()) }
        coVerify(exactly = 0) { prService.fetchAndIngestPullRequests(any(), any()) }
        verify(exactly = 0) { connectionStateService.markUpdating(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any<BitbucketRepositoryUpdateStartedEvent>()) }
    }

    @Test
    fun `fails the run when the update was accepted but could not be started`() = runTest {
        every { connectionRepository.findById(connection.id) } returns Optional.of(connection)
        every { connectionStateService.markUpdating(connection.id) } throws IllegalStateException("database down")

        assertThrows<IllegalStateException> {
            service.updateRepository(connection.id)
        }

        verify(exactly = 1) {
            eventPublisher.publishEvent(
                match<BitbucketRepositoryUpdateFailedEvent> {
                    it.repositoryId == connection.id && it.reason.contains("database down")
                },
            )
        }
    }

    @Test
    fun `update-all skips disabled repositories`() = runTest {
        val enabled = connection("sprintstart", "backend", projectIds = setOf(projectId))
        val disabled =
            connection("sprintstart", "paused", projectIds = setOf(projectId)).apply { sourceEnabled = false }
        every { connectionRepository.findAll() } returns listOf(enabled, disabled)
        every { connectionRepository.findById(enabled.id) } returns Optional.of(enabled)
        every { userApi.userHasAccessToProject(authId, projectId) } returns true

        val response = service.updateAllRepositories(authId)

        assertThat(response.transactionIdsByRepository.keys).containsExactly("sprintstart/backend")
        coVerify(exactly = 0) { fileService.fetchAndIngestFilesOfRepository(disabled.id, any()) }
    }

    @Test
    fun `update-all skips repositories linked to none of the caller's projects`() = runTest {
        val mine = connection("sprintstart", "backend", projectIds = setOf(projectId))
        val foreign = connection("other", "repo", projectIds = setOf(UUID.randomUUID()))
        every { connectionRepository.findAll() } returns listOf(mine, foreign)
        every { connectionRepository.findById(mine.id) } returns Optional.of(mine)
        every { userApi.userHasAccessToProject(authId, projectId) } returns true
        every { userApi.userHasAccessToProject(authId, foreign.projectIds.single()) } returns false

        val response = service.updateAllRepositories(authId)

        assertThat(response.transactionIdsByRepository.keys).containsExactly("sprintstart/backend")
        coVerify(exactly = 0) { fileService.fetchAndIngestFilesOfRepository(foreign.id, any()) }
    }

    /**
     * The map is named for transaction ids and must actually carry them: the caller follows those
     * ids, and handing back repository ids instead silently points every lookup at the wrong run.
     */
    @Test
    fun `update-all returns the transaction id of each updated repository`() = runTest {
        val enabled = connection("sprintstart", "backend", projectIds = setOf(projectId))
        every { connectionRepository.findAll() } returns listOf(enabled)
        every { connectionRepository.findById(enabled.id) } returns Optional.of(enabled)
        every { userApi.userHasAccessToProject(authId, projectId) } returns true

        val response = service.updateAllRepositories(authId)

        val transactionId = response.transactionIdsByRepository.getValue("sprintstart/backend")
        coVerify { fileService.fetchAndIngestFilesOfRepository(enabled.id, transactionId) }
        assertThat(transactionId).isNotEqualTo(enabled.id)
    }

    @Test
    fun `a scoped update reaches a repository through any linked project`() = runTest {
        val linked = connection("sprintstart", "backend", projectIds = setOf(projectId))
        every { connectionRepository.findById(linked.id) } returns Optional.of(linked)
        every { userApi.userHasAccessToProject(authId, projectId) } returns true

        service.updateRepository(authId, linked.id)

        coVerify { fileService.fetchAndIngestFilesOfRepository(linked.id, any()) }
    }

    @Test
    fun `a scoped update answers an unreachable repository as unknown`() = runTest {
        val foreign = connection("other", "repo", projectIds = setOf(UUID.randomUUID()))
        every { connectionRepository.findById(foreign.id) } returns Optional.of(foreign)
        every { userApi.userHasAccessToProject(authId, any()) } returns false

        assertThrows<BitbucketRepositoryConnectionNotFoundException> {
            service.updateRepository(authId, foreign.id)
        }

        coVerify(exactly = 0) { fileService.fetchAndIngestFilesOfRepository(any(), any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any<BitbucketRepositoryUpdateStartedEvent>()) }
    }

    private fun connection(workspace: String, slug: String, projectIds: Set<UUID> = emptySet()) = BitbucketConnection(
        workspace = workspace,
        slug = slug,
        credentialAuthId = "auth-id",
        credentialName = "team-token",
        projectIdsInternal = projectIds.toMutableSet(),
    )
}
