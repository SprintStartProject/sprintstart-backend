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
import com.sprintstart.sprintstartbackend.user.external.UserApi
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
import java.util.UUID

class BitbucketRepositoryConfigServiceTest {
    private val configRepository = mockk<BitbucketRepositoryConfigRepository>()
    private val connectionRepository = mockk<BitbucketConnectionRepository>()
    private val cronBuilder = mockk<CronBuilder>()
    private val userApi = mockk<UserApi>()

    private val service = BitbucketRepositoryConfigService(
        configRepository = configRepository,
        connectionRepository = connectionRepository,
        cronBuilder = cronBuilder,
        userApi = userApi,
    )

    private val projectId = UUID.randomUUID()
    private val authId = "auth-id"

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
        fun `maps every reachable config to a response`() {
            every { configRepository.findAll() } returns listOf(config("w1", "s1"), config("w2", "s2"))
            every { userApi.userHasAccessToProject(authId, projectId) } returns true

            val result = service.getAll(authId)

            assertThat(result).hasSize(2)
            assertThat(result[0].workspace).isEqualTo("w1")
            assertThat(result[0].slug).isEqualTo("s1")
            assertThat(result[1].workspace).isEqualTo("w2")
            assertThat(result[1].slug).isEqualTo("s2")
        }

        @Test
        fun `hides configs linked to none of the caller's projects`() {
            every { configRepository.findAll() } returns listOf(config("w1", "s1"), config("w2", "s2"))
            every { userApi.userHasAccessToProject(authId, projectId) } returns false

            assertThat(service.getAll(authId)).isEmpty()
        }
    }

    @Nested
    inner class Configure {
        @Test
        fun `applies the schedule and recomputes the next sync`() {
            val config = config("w", "s")
            val spec = ScheduleSpec.Daily(time = LocalTime.of(6, 30))
            every { connectionRepository.findByWorkspaceAndSlug("w", "s") } returns config.repository
            every { userApi.userHasAccessToProject(authId, projectId) } returns true
            every { configRepository.findById(config.id!!) } returns Optional.of(config)
            every { cronBuilder.build(spec) } returns "0 30 6 * * *"
            every { configRepository.save(config) } returns config

            service.configure(
                authId,
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
        fun `refuses a repository linked to none of the caller's projects`() {
            val config = config("w", "s")
            every { connectionRepository.findByWorkspaceAndSlug("w", "s") } returns config.repository
            every { userApi.userHasAccessToProject(authId, projectId) } returns false

            assertThrows<BitbucketRepositoryNotConnectedException> {
                service.configure(authId, "w", "s", request())
            }

            verify(exactly = 0) { configRepository.save(any()) }
        }

        @Test
        fun `fails when the repository is not connected`() {
            every { connectionRepository.findByWorkspaceAndSlug("w", "s") } returns null

            assertThrows<BitbucketRepositoryNotConnectedException> {
                service.configure(authId, "w", "s", request())
            }
        }

        @Test
        fun `fails when the connection has no config`() {
            val config = config("w", "s")
            every { connectionRepository.findByWorkspaceAndSlug("w", "s") } returns config.repository
            every { userApi.userHasAccessToProject(authId, projectId) } returns true
            every { configRepository.findById(config.id!!) } returns Optional.empty()

            assertThrows<BitbucketRepositoryConfigNotFoundException> {
                service.configure(authId, "w", "s", request())
            }
        }
    }

    @Nested
    inner class ConfigureAll {
        @Test
        fun `updates every reachable config with the given settings`() {
            val first = config("w1", "s1")
            val second = config("w2", "s2")
            val spec = ScheduleSpec.Interval(everyMinutes = 30)
            every { configRepository.findAll() } returns listOf(first, second)
            every { userApi.userHasAccessToProject(authId, projectId) } returns true
            every { cronBuilder.build(spec) } returns "0 */30 * * * *"
            every { configRepository.saveAll(any<Iterable<BitbucketRepositoryConfig>>()) } returns listOf(first, second)

            service.configureAll(authId, ConfigureBitbucketRepositoryRequest(autoUpdate = true, schedule = spec))

            assertThat(first.schedule).isEqualTo("0 */30 * * * *")
            assertThat(first.nextSyncAt).isNotNull
            assertThat(second.schedule).isEqualTo("0 */30 * * * *")
            assertThat(second.nextSyncAt).isNotNull
            verify { configRepository.saveAll(listOf(first, second)) }
        }

        @Test
        fun `leaves configs linked to none of the caller's projects alone`() {
            val reachable = config("w1", "s1")
            val foreign = config("w2", "s2", projectIds = mutableSetOf(UUID.randomUUID()))
            val spec = ScheduleSpec.Interval(everyMinutes = 30)
            every { configRepository.findAll() } returns listOf(reachable, foreign)
            every { userApi.userHasAccessToProject(authId, projectId) } returns true
            every { userApi.userHasAccessToProject(authId, foreign.repository.projectIds.single()) } returns false
            every { cronBuilder.build(spec) } returns "0 */30 * * * *"
            every { configRepository.saveAll(any<Iterable<BitbucketRepositoryConfig>>()) } returns listOf(reachable)

            service.configureAll(authId, ConfigureBitbucketRepositoryRequest(autoUpdate = true, schedule = spec))

            assertThat(reachable.schedule).isEqualTo("0 */30 * * * *")
            assertThat(foreign.schedule).isNotEqualTo("0 */30 * * * *")
            verify { configRepository.saveAll(listOf(reachable)) }
        }
    }

    @Nested
    inner class GetConfigOfRepository {
        @Test
        fun `maps the config to a response`() {
            val config = config("w", "s")
            every { connectionRepository.findByWorkspaceAndSlug("w", "s") } returns config.repository
            every { userApi.userHasAccessToProject(authId, projectId) } returns true
            every { configRepository.findById(config.id!!) } returns Optional.of(config)

            val response = service.getConfigOfRepository(authId, "w", "s")

            assertThat(response.workspace).isEqualTo("w")
            assertThat(response.slug).isEqualTo("s")
            assertThat(response.schedule).isEqualTo(config.schedule)
        }

        @Test
        fun `refuses a repository linked to none of the caller's projects`() {
            val config = config("w", "s")
            every { connectionRepository.findByWorkspaceAndSlug("w", "s") } returns config.repository
            every { userApi.userHasAccessToProject(authId, projectId) } returns false

            assertThrows<BitbucketRepositoryNotConnectedException> {
                service.getConfigOfRepository(authId, "w", "s")
            }
        }

        @Test
        fun `fails when the repository is not connected`() {
            every { connectionRepository.findByWorkspaceAndSlug("w", "s") } returns null

            assertThrows<BitbucketRepositoryNotConnectedException> {
                service.getConfigOfRepository(authId, "w", "s")
            }
        }
    }

    @Nested
    inner class FindConfigsDueForSync {
        @Test
        fun `returns the configs of enabled repositories due at the given time`() {
            val config = config("w", "s")
            val now = Instant.parse("2025-06-01T00:00:00Z")
            every { configRepository.findEnabledConfigsDueForSync(now) } returns listOf(config)

            assertThat(service.findConfigsDueForSync(now)).containsExactly(config)
        }

        /**
         * The gate lives in the query, so a paused repository is not merely skipped by the executor —
         * it never reaches it. This is the Bitbucket counterpart of Confluence's
         * `...AndSourceEnabledTrue` claim query.
         */
        @Test
        fun `returns nothing when only disabled repositories are due`() {
            val now = Instant.parse("2025-06-01T00:00:00Z")
            every { configRepository.findEnabledConfigsDueForSync(now) } returns emptyList()

            assertThat(service.findConfigsDueForSync(now)).isEmpty()
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

    private fun config(
        workspace: String,
        slug: String,
        projectIds: MutableSet<UUID> = mutableSetOf(projectId),
    ): BitbucketRepositoryConfig {
        val connection = BitbucketConnection(
            workspace = workspace,
            slug = slug,
            credentialAuthId = "auth-id",
            credentialName = "team-token",
            projectIdsInternal = projectIds,
        )
        return BitbucketRepositoryConfig(repository = connection).apply { id = connection.id }
    }
}
