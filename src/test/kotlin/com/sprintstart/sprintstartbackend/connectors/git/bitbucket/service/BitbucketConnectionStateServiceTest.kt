package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service

import com.sprintstart.sprintstartbackend.connectors.ConnectionState
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketConnectionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
        every { connectionRepository.existsById(repositoryId) } returns true

        val failure = assertFailsWith<IllegalStateException> {
            service.awaitCollectorsAndFinalize(
                repositoryId,
                listOf(failedDeferred(IllegalStateException("git broke"))),
            )
        }

        assertThat(failure.message).contains("failed")

        verify { connectionRepository.updateConnectionState(repositoryId, ConnectionState.FAILED) }
        verify(exactly = 0) { connectionRepository.save(any()) }
    }

    @Test
    fun `all successful collectors persist UP_TO_DATE`() = runTest {
        every { connectionRepository.existsById(repositoryId) } returns true

        service.awaitCollectorsAndFinalize(repositoryId, listOf(CompletableDeferred(Unit)))

        verify { connectionRepository.updateConnectionState(repositoryId, ConnectionState.UP_TO_DATE) }
        verify(exactly = 0) { connectionRepository.save(any()) }
    }

    @Test
    fun `a connection deleted mid-update is skipped instead of resurrected`() = runTest {
        every { connectionRepository.existsById(repositoryId) } returns false

        service.awaitCollectorsAndFinalize(repositoryId, listOf(CompletableDeferred(Unit)))

        verify(exactly = 0) { connectionRepository.updateConnectionState(any(), any()) }
        verify(exactly = 0) { connectionRepository.save(any()) }
    }

    private fun failedDeferred(error: Exception): Deferred<Unit> = CompletableDeferred<Unit>().apply {
        completeExceptionally(error)
    }
}
