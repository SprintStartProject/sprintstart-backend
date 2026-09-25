package com.sprintstart.sprintstartbackend.connectors.bitbucket.service.internal

import com.sprintstart.sprintstartbackend.connectors.bitbucket.external.events.files.BitbucketFilesFetchingCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.bitbucket.external.events.files.BitbucketFilesFetchingFailedEvent
import com.sprintstart.sprintstartbackend.connectors.bitbucket.external.events.files.BitbucketFilesFetchingStartedEvent
import com.sprintstart.sprintstartbackend.connectors.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.connectors.bitbucket.model.exceptions.BitbucketRepositoryNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.bitbucket.repository.BitbucketConnectionRepository
import com.sprintstart.sprintstartbackend.shared.git.GitRepositoryCoordinates
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import java.nio.file.Path
import java.util.Optional
import java.util.UUID

class BitbucketFileServiceTest {
    private val connectionRepository = mockk<BitbucketConnectionRepository>()
    private val coordinatesFactory = mockk<BitbucketRepositoryCoordinatesFactory>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val customCache = mockk<ICustomOnDiskCache>()

    private val service = BitbucketFileService(
        connectionRepository = connectionRepository,
        coordinatesFactory = coordinatesFactory,
        eventPublisher = eventPublisher,
        customCache = customCache,
    )

    private val transactionId = UUID.randomUUID()
    private val coordinates = GitRepositoryCoordinates(
        host = "bitbucket.org",
        namespace = "sprintstart",
        name = "sprintstart-backend",
        username = "x-bitbucket-api-token-auth",
        secret = "api-token",
    )
    private val connection = BitbucketConnection(
        workspace = coordinates.namespace,
        slug = coordinates.name,
        credentialAuthId = "auth-id",
        credentialName = "team-token",
    )

    @Test
    fun `clones the shared cache path and reports completion`() = runTest {
        every { connectionRepository.findById(connection.id) } returns Optional.of(connection)
        every { coordinatesFactory.of(connection) } returns coordinates
        coEvery { customCache.getLocalRepositoryPath(coordinates) } returns Path.of("/repos/bitbucket.org/x/y")

        service.fetchAndIngestFilesOfRepository(connection.id, transactionId)

        verify { eventPublisher.publishEvent(match<Any> { it is BitbucketFilesFetchingStartedEvent }) }
        coVerify(exactly = 1) { customCache.getLocalRepositoryPath(coordinates) }
        verify {
            eventPublisher.publishEvent(
                match<Any> { it is BitbucketFilesFetchingCompletedEvent && it.transactionId == transactionId },
            )
        }
    }

    @Test
    fun `fails and rethrows when no connection with that id exists`() = runTest {
        every { connectionRepository.findById(any()) } returns Optional.empty()

        assertThrows<BitbucketRepositoryNotConnectedException> {
            service.fetchAndIngestFilesOfRepository(connection.id, transactionId)
        }

        coVerify(exactly = 0) { customCache.getLocalRepositoryPath(any()) }
        verify {
            eventPublisher.publishEvent(
                match<Any> { it is BitbucketFilesFetchingFailedEvent && it.transactionId == transactionId },
            )
        }
    }

    @Test
    fun `fails and rethrows when the repository cannot be cloned`() = runTest {
        every { connectionRepository.findById(connection.id) } returns Optional.of(connection)
        every { coordinatesFactory.of(connection) } returns coordinates
        coEvery { customCache.getLocalRepositoryPath(coordinates) } throws
            RuntimeException("git clone failed (exit 128)")

        assertThrows<RuntimeException> {
            service.fetchAndIngestFilesOfRepository(connection.id, transactionId)
        }

        verify {
            eventPublisher.publishEvent(
                match<Any> { it is BitbucketFilesFetchingFailedEvent && it.reason.contains("exit 128") },
            )
        }
        verify(exactly = 0) {
            eventPublisher.publishEvent(match<Any> { it is BitbucketFilesFetchingCompletedEvent })
        }
    }
}
