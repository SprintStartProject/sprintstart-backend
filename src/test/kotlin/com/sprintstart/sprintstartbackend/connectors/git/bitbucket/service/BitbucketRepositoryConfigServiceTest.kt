package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketRepositoryConfig
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryConfigNotFoundException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.request.ConfigureBitbucketRepositoryRequest
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketConnectionRepository
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketRepositoryConfigRepository
import com.sprintstart.sprintstartbackend.shared.scheduler.CronBuilder
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduleSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalTime
import java.util.Optional

class BitbucketRepositoryConfigServiceTest {
    private val configRepository = mockk<BitbucketRepositoryConfigRepository>()
    private val connectionRepository = mockk<BitbucketConnectionRepository>()
    private val cronBuilder = mockk<CronBuilder>()

    private val service = BitbucketRepositoryConfigService(
        configRepository = configRepository,
        connectionRepository = connectionRepository,
        cronBuilder = cronBuilder,
    )

    @Nested
    inner class CalculateNextSyncAt {
        @Test
        fun `returns a future instant for a valid cron schedule`() {
            val before = Instant.now()

            val result = BitbucketRepositoryConfigService.calculateNextSyncAt("0 0 2 * * *")

            assertThat(result).isNotNull
            assertThat(result).isAfterOrEqualTo(before)
        }

        @Test
        fun `returns null for an invalid cron schedule`() {
            assertThat(BitbucketRepositoryConfigService.calculateNextSyncAt("not a cron")).isNull()
        }
    }

    @Nested
    inner class GetAll {
        @Test
        fun `maps every config to a response`() {
            every { configRepository.findAll() } returns listOf(config("w1", "s1"), config("w2", "s2"))

            val result = service.getAll()

            assertThat(result).hasSize(2)
            assertThat(result[0].workspace).isEqualTo("w1")
            assertThat(result[0].slug).isEqualTo("s1")
            assertThat(result[1].workspace).isEqualTo("w2")
            assertThat(result[1].slug).isEqualTo("s2")
        }
    }

    @Nested
    inner class Configure {
        @Test
        fun `applies the schedule and recomputes the next sync`() {
            val config = config("w", "s")
            val spec = ScheduleSpec.Daily(time = LocalTime.of(6, 30))
            every { connectionRepository.findByWorkspaceAndSlug("w", "s") } returns config.repository
            every { configRepository.findById(config.id!!) } returns Optional.of(config)
            every { cronBuilder.build(spec) } returns "0 30 6 * * *"
            every { configRepository.save(config) } returns config

            service.configure(
                "w",
                "s",
                ConfigureBitbucketRepositoryRequest(autoUpdate = false, schedule = spec),
            )

            assertThat(config.autoUpdate).isFalse()
            assertThat(config.spec).isEqualTo(spec)
            assertThat(config.schedule).isEqualTo("0 30 6 * * *")
            assertThat(config.nextSyncAt).isNotNull
            verify { configRepository.save(config) }
        }

        @Test
        fun `fails when the repository is not connected`() {
            every { connectionRepository.findByWorkspaceAndSlug("w", "s") } returns null

            assertThrows<BitbucketRepositoryNotConnectedException> {
                service.configure("w", "s", request())
            }
        }

        @Test
        fun `fails when the connection has no config`() {
            val config = config("w", "s")
            every { connectionRepository.findByWorkspaceAndSlug("w", "s") } returns config.repository
            every { configRepository.findById(config.id!!) } returns Optional.empty()

            assertThrows<BitbucketRepositoryConfigNotFoundException> {
                service.configure("w", "s", request())
            }
        }
    }

    @Nested
    inner class ConfigureAll {
        @Test
        fun `updates every config with the given settings`() {
            val first = config("w1", "s1")
            val second = config("w2", "s2")
            val spec = ScheduleSpec.Interval(everyMinutes = 30)
            every { configRepository.findAll() } returns listOf(first, second)
            every { cronBuilder.build(spec) } returns "0 */30 * * * *"
            every { configRepository.saveAll(any<Iterable<BitbucketRepositoryConfig>>()) } returns listOf(first, second)

            service.configureAll(ConfigureBitbucketRepositoryRequest(autoUpdate = true, schedule = spec))

            assertThat(first.schedule).isEqualTo("0 */30 * * * *")
            assertThat(first.nextSyncAt).isNotNull
            assertThat(second.schedule).isEqualTo("0 */30 * * * *")
            assertThat(second.nextSyncAt).isNotNull
            verify { configRepository.saveAll(listOf(first, second)) }
        }
    }

    @Nested
    inner class GetConfigOfRepository {
        @Test
        fun `maps the config to a response`() {
            val config = config("w", "s")
            every { connectionRepository.findByWorkspaceAndSlug("w", "s") } returns config.repository
            every { configRepository.findById(config.id!!) } returns Optional.of(config)

            val response = service.getConfigOfRepository("w", "s")

            assertThat(response.workspace).isEqualTo("w")
            assertThat(response.slug).isEqualTo("s")
            assertThat(response.schedule).isEqualTo(config.schedule)
        }

        @Test
        fun `fails when the repository is not connected`() {
            every { connectionRepository.findByWorkspaceAndSlug("w", "s") } returns null

            assertThrows<BitbucketRepositoryNotConnectedException> {
                service.getConfigOfRepository("w", "s")
            }
        }
    }

    @Nested
    inner class FindConfigsDueForSync {
        @Test
        fun `returns the configs due at the given time`() {
            val config = config("w", "s")
            val now = Instant.parse("2025-06-01T00:00:00Z")
            every { configRepository.findAllByNextSyncAtIsLessThanEqual(now) } returns listOf(config)

            assertThat(service.findConfigsDueForSync(now)).containsExactly(config)
        }
    }

    @Nested
    inner class SaveRepositoryConfig {
        @Test
        fun `persists the config`() {
            val config = config("w", "s")
            every { configRepository.save(config) } returns config

            service.saveRepositoryConfig(config)

            verify { configRepository.save(config) }
        }
    }

    private fun request() = ConfigureBitbucketRepositoryRequest(
        autoUpdate = true,
        schedule = ScheduleSpec.Daily(time = LocalTime.of(2, 0)),
    )

    private fun config(workspace: String, slug: String): BitbucketRepositoryConfig {
        val connection = BitbucketConnection(
            workspace = workspace,
            slug = slug,
            credentialAuthId = "auth-id",
            credentialName = "team-token",
        )
        return BitbucketRepositoryConfig(repository = connection).apply { id = connection.id }
    }
}
