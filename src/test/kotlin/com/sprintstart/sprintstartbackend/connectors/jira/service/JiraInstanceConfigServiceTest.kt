package com.sprintstart.sprintstartbackend.connectors.jira.service

import com.sprintstart.sprintstartbackend.connectors.jira.jiraInstanceConfig
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.request.config.ConfigureAllJiraInstancesRequest
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.request.config.ConfigureJiraInstanceRequest
import com.sprintstart.sprintstartbackend.connectors.jira.model.entity.JiraInstanceConfig
import com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions.JiraInstanceNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.jira.repository.JiraInstanceConfigRepository
import com.sprintstart.sprintstartbackend.shared.scheduler.CronBuilder
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduleSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalTime
import java.util.Optional
import kotlin.test.assertFailsWith

class JiraInstanceConfigServiceTest {
    private val configRepository = mockk<JiraInstanceConfigRepository>()
    private val cronBuilder = mockk<CronBuilder>()

    private lateinit var service: JiraInstanceConfigService

    @BeforeEach
    fun setUp() {
        service = JiraInstanceConfigService(configRepository, cronBuilder)
    }

    @Nested
    inner class CalculateNextSyncAt {
        @Test
        fun `should return a future instant for a valid cron`() {
            val result = service.calculateNextSyncAt("0 0 2 * * *")
            assertThat(result).isAfter(Instant.now())
        }

        @Test
        fun `should return null for invalid cron`() {
            val result = service.calculateNextSyncAt("not-a-cron")
            assertThat(result).isNull()
        }
    }

    @Nested
    inner class GetAll {
        @Test
        fun `should return all configs`() {
            val config = jiraInstanceConfig()
            every { configRepository.findAll() } returns listOf(config)

            val result = service.getAll()

            assertThat(result).hasSize(1)
            assertThat(result[0].instanceUrl).isEqualTo(config.id)
        }
    }

    @Nested
    inner class ConfigureAll {
        @Test
        fun `should update all configs`() {
            val config = jiraInstanceConfig(schedule = "0 0 2 * * *")
            val request = ConfigureAllJiraInstancesRequest(ScheduleSpec.Daily(LocalTime.of(3, 0)), true)
            every { configRepository.findAll() } returns listOf(config)
            every { cronBuilder.build(request.schedule) } returns "0 0 3 * * *"
            every { configRepository.saveAll(any<List<JiraInstanceConfig>>()) } answers { firstArg() }

            service.configureAll(request)

            assertThat(config.autoUpdate).isTrue()
            assertThat(config.schedule).isEqualTo("0 0 3 * * *")
            assertThat(config.spec).isEqualTo(request.schedule)
            assertThat(config.nextSyncAt).isNotNull()
        }
    }

    @Nested
    inner class GetConfigOfInstance {
        @Test
        fun `should return config for instance`() {
            val config = jiraInstanceConfig()
            every { configRepository.findById("https://jira.example.com") } returns Optional.of(config)

            val result = service.getConfigOfInstance("https://jira.example.com")

            assertThat(result.instanceUrl).isEqualTo("https://jira.example.com")
        }

        @Test
        fun `should throw when config not found`() {
            every { configRepository.findById("unknown") } returns Optional.empty()

            assertFailsWith<JiraInstanceNotConnectedException> { service.getConfigOfInstance("unknown") }
        }
    }

    @Nested
    inner class ConfigureInstance {
        @Test
        fun `should update specific config`() {
            val config = jiraInstanceConfig()
            val request = ConfigureJiraInstanceRequest(
                "https://jira.example.com",
                ScheduleSpec.Daily(LocalTime.of(4, 0)),
                false,
            )
            every { configRepository.findById(request.instanceUrl) } returns Optional.of(config)
            every { cronBuilder.build(request.schedule) } returns "0 0 4 * * *"
            every { configRepository.save(config) } answers { firstArg() }

            service.configureInstance(request)

            assertThat(config.autoUpdate).isFalse()
            assertThat(config.schedule).isEqualTo("0 0 4 * * *")
        }

        @Test
        fun `should throw when config not found`() {
            val request = ConfigureJiraInstanceRequest("unknown", ScheduleSpec.Daily(LocalTime.of(4, 0)), false)
            every { configRepository.findById("unknown") } returns Optional.empty()

            assertFailsWith<JiraInstanceNotConnectedException> { service.configureInstance(request) }
        }
    }

    @Nested
    inner class FindAllJiraInstanceConfigsDueForSync {
        @Test
        fun `should return configs due for sync`() {
            val now = Instant.now()
            val config = jiraInstanceConfig(nextSyncAt = now)
            every { configRepository.findAllByNextSyncAtIsLessThanEqual(now) } returns listOf(config)

            val result = service.findAllJiraInstanceConfigsDueForSync(now)

            assertThat(result).hasSize(1)
        }
    }

    @Nested
    inner class SaveJiraInstanceConfig {
        @Test
        fun `should save config`() {
            val config = jiraInstanceConfig()
            every { configRepository.save(config) } answers { firstArg() }

            service.saveJiraInstanceConfig(config)

            verify { configRepository.save(config) }
        }
    }
}
