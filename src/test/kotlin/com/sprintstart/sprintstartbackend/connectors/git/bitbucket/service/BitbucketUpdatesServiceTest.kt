package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service

import com.sprintstart.sprintstartbackend.connectors.ConnectionState
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketConnectionRepository
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal.BitbucketCommitsService
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal.BitbucketFileService
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal.BitbucketPullRequestsService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional
import java.util.UUID

class BitbucketUpdatesServiceTest {
    private val connectionRepository = mockk<BitbucketConnectionRepository>()
    private val fileService = mockk<BitbucketFileService>(relaxed = true)
    private val commitsService = mockk<BitbucketCommitsService>(relaxed = true)
    private val prService = mockk<BitbucketPullRequestsService>(relaxed = true)
    private val connectionStateService = mockk<BitbucketConnectionStateService>(relaxed = true)

    // Unconfined so the launched ingests run inline and can be verified without waiting.
    private val applicationScope = CoroutineScope(Dispatchers.Unconfined)

    private val service = BitbucketUpdatesService(
        connectionRepository = connectionRepository,
        fileService = fileService,
        commitsService = commitsService,
        prService = prService,
        connectionStateService = connectionStateService,
        applicationScope = applicationScope,
    )

    private val connection = connection("sprintstart", "backend")

    @Test
    fun `re-ingests files, commits and pull requests of the connection under one transaction`() = runTest {
        every { connectionRepository.findById(connection.id) } returns Optional.of(connection)
        val fileTransaction = slot<UUID>()
        val commitTransaction = slot<UUID>()
        val prTransaction = slot<UUID>()

        val transactionId = service.updateRepository(connection.id)

        coVerify { fileService.fetchAndIngestFilesOfRepository(connection.id, capture(fileTransaction)) }
        coVerify { commitsService.fetchAndIngestCommitsOfRepository(connection.id, capture(commitTransaction)) }
        coVerify { prService.fetchAndIngestPullRequests(connection.id, capture(prTransaction)) }
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
    }

    private fun connection(workspace: String, slug: String) = BitbucketConnection(
        workspace = workspace,
        slug = slug,
        credentialAuthId = "auth-id",
        credentialName = "team-token",
    )
}
