package com.sprintstart.sprintstartbackend.connectors.jira

import com.sprintstart.sprintstartbackend.connectors.jira.service.JiraInstanceConfigService
import com.sprintstart.sprintstartbackend.connectors.jira.service.JiraUpdateService
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduledExecutor
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MockKExtension::class)
class JiraScheduledExecutorTest {
    private val configService = mockk<JiraInstanceConfigService>()
    private val updateService = mockk<JiraUpdateService>()
    private val applicationScope = TestScope(UnconfinedTestDispatcher())
    private val scheduledExecutor = ScheduledExecutor(applicationScope)

    private lateinit var executor: JiraScheduledExecutor

    @BeforeEach
    fun setUp() {
        executor = JiraScheduledExecutor(scheduledExecutor, configService, updateService)
    }

    @AfterEach
    fun tearDown() {
        applicationScope.cancel()
    }

    @Test
    fun `tick should update due instances and recalculate next sync`() {
        val now = Instant.now()
        val instance = jiraInstance()
        val config = jiraInstanceConfig(instance = instance, nextSyncAt = now)
        every { configService.findAllJiraInstanceConfigsDueForSync(any()) } returns listOf(config)
        every { updateService.updateJiraInstance(instance.instanceUrl, config.autoUpdate) } returns mockk()
        every { configService.calculateNextSyncAt(config.schedule) } returns now.plusSeconds(3600)
        every { configService.saveJiraInstanceConfig(config) } answers { firstArg() }

        executor.tick()

        verify { updateService.updateJiraInstance(instance.instanceUrl, config.autoUpdate) }
        verify { configService.calculateNextSyncAt(config.schedule) }
        verify { configService.saveJiraInstanceConfig(config) }
    }
}
