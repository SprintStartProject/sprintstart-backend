package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service

import com.sprintstart.sprintstartbackend.connectors.ConnectionState
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketConnectionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional
import java.util.UUID
import kotlin.test.assertFailsWith

class BitbucketConnectionStateServiceTest {
    private val connectionRepository = mockk<BitbucketConnectionRepository>(relaxed = true)
    private val service = BitbucketConnectionStateService(connectionRepository)

    private val repositoryId = UUID.randomUUID()

    @Test
    fun `markUpdating writes the state directly`() {
        service.markUpdating(repositoryId)

        verify(exactly = 1) { connectionRepository.updateConnectionState(repositoryId, ConnectionState.UPDATING) }
    }

    @Test
    fun `a failed collector persists FAILED`() = runTest {
        val connection = connection()
        every { connectionRepository.findById(repositoryId) } returns Optional.of(connection)
        every { connectionRepository.save(any()) } returns connection

        val failure = assertFailsWith<IllegalStateException> {
            service.awaitCollectorsAndFinalize(
                repositoryId,
                listOf(failedDeferred(IllegalStateException("git broke"))),
            )
        }

        assertThat(failure.message).contains("failed")

        assertThat(connection.connectionState).isEqualTo(ConnectionState.FAILED)
    }

    @Test
    fun `all successful collectors persist UP_TO_DATE`() = runTest {
        val connection = connection()
        every { connectionRepository.findById(repositoryId) } returns Optional.of(connection)
        every { connectionRepository.save(any()) } returns connection

        service.awaitCollectorsAndFinalize(repositoryId, listOf(CompletableDeferred(Unit)))

        assertThat(connection.connectionState).isEqualTo(ConnectionState.UP_TO_DATE)
    }

    @Test
    fun `a connection deleted mid-update is skipped instead of resurrected`() = runTest {
        every { connectionRepository.findById(repositoryId) } returns Optional.empty()

        service.awaitCollectorsAndFinalize(repositoryId, listOf(CompletableDeferred(Unit)))

        verify(exactly = 0) { connectionRepository.save(any()) }
    }

    private fun failedDeferred(error: Exception): Deferred<Unit> = CompletableDeferred<Unit>().apply {
        completeExceptionally(error)
    }

    private fun connection() = BitbucketConnection(
        workspace = "sprintstart",
        slug = "backend",
        credentialAuthId = "auth-id",
        credentialName = "team-token",
    )
}
