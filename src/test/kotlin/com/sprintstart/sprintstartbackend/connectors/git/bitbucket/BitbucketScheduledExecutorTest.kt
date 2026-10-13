package com.sprintstart.sprintstartbackend.connectors.git.bitbucket

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.BitbucketScheduledExecutor
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketRepositoryConfig
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.BitbucketRepositoryConfigService
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.BitbucketUpdatesService
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduledExecutor
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class BitbucketScheduledExecutorTest {
    private val scheduledExecutor = mockk<ScheduledExecutor>()
    private val configService = mockk<BitbucketRepositoryConfigService>()
    private val updateService = mockk<BitbucketUpdatesService>(relaxed = true)

    private val executor = BitbucketScheduledExecutor(scheduledExecutor, configService, updateService)

    @Test
    fun `updates a due repository and advances its next sync`() = runTest {
        val config = config()
        every { configService.findConfigsDueForSync(any()) } returns listOf(config)
        val job = slot<suspend () -> Unit>()
        every { scheduledExecutor.launch(any(), capture(job)) } returns Job()
        every { configService.saveRepositoryConfig(config) } returns config

        executor.tick()
        job.captured.invoke()

        coVerify { updateService.updateRepository(config.id!!) }
        verify { configService.saveRepositoryConfig(config) }
        assertThat(config.nextSyncAt).isNotNull()
    }

    @Test
    fun `advances the next sync without updating when auto-update is off`() {
        val config = config(autoUpdate = false)
        every { configService.findConfigsDueForSync(any()) } returns listOf(config)
        every { configService.saveRepositoryConfig(config) } returns config

        executor.tick()

        verify(exactly = 0) { scheduledExecutor.launch(any(), any()) }
        verify { configService.saveRepositoryConfig(config) }
        assertThat(config.nextSyncAt).isNotNull()
    }

    @Test
    fun `does nothing when no repository is due`() {
        every { configService.findConfigsDueForSync(any()) } returns emptyList()

        executor.tick()

        verify(exactly = 0) { scheduledExecutor.launch(any(), any()) }
        verify(exactly = 0) { configService.saveRepositoryConfig(any()) }
    }

    private fun config(autoUpdate: Boolean = true): BitbucketRepositoryConfig {
        val connection = BitbucketConnection(
            workspace = "sprintstart",
            slug = "backend",
            credentialAuthId = "auth-id",
            credentialName = "team-token",
        )
        return BitbucketRepositoryConfig(repository = connection).apply {
            id = connection.id
            this.autoUpdate = autoUpdate
            nextSyncAt = Instant.parse("2025-01-01T00:00:00Z")
        }
    }
}
