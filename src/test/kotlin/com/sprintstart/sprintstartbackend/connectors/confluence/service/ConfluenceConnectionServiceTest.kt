package com.sprintstart.sprintstartbackend.connectors.confluence.service

import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceAuthenticationException
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceClient
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceSpace
import com.sprintstart.sprintstartbackend.connectors.confluence.model.api.request.ConfigureConfluenceScheduleRequest
import com.sprintstart.sprintstartbackend.connectors.confluence.model.api.request.CreateConfluenceConnectionRequest
import com.sprintstart.sprintstartbackend.connectors.confluence.model.entity.ConfluenceCredential
import com.sprintstart.sprintstartbackend.connectors.confluence.model.entity.ConfluenceSpaceConnection
import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceConnectionAlreadyExistsException
import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceConnectionConfigurationException
import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceConnectionNotFoundException
import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceProjectAccessDeniedException
import com.sprintstart.sprintstartbackend.connectors.confluence.repository.ConfluenceCredentialRepository
import com.sprintstart.sprintstartbackend.connectors.confluence.repository.ConfluenceSpaceConnectionRepository
import com.sprintstart.sprintstartbackend.shared.scheduler.CronBuilder
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduleSpec
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ConfluenceConnectionServiceTest {
    private val confluenceClient = mockk<ConfluenceClient>()
    private val connectionRepository = mockk<ConfluenceSpaceConnectionRepository>()
    private val credentialRepository = mockk<ConfluenceCredentialRepository>()
    private val userApi = mockk<UserApi>()
    private val cronBuilder = mockk<CronBuilder>()
    private val scheduleCalculator = mockk<ConfluenceScheduleCalculator>()
    private val service = ConfluenceConnectionService(
        confluenceClient,
        connectionRepository,
        credentialRepository,
        userApi,
        cronBuilder,
        scheduleCalculator,
    )
    private val authId = "auth-subject"
    private val projectId = UUID.randomUUID()
    private val plaintextToken = "phase-four-secret-token"

    @BeforeEach
    fun setUp() {
        every { userApi.userHasAccessToProject(authId, projectId) } returns true
    }

    @Test
    fun `validates then stores canonical space and normalized configuration`() = runTest {
        val saved = slot<ConfluenceSpaceConnection>()
        every {
            connectionRepository.existsByProjectIdAndBaseUrlAndSpaceId(
                projectId,
                "https://tenant.atlassian.net",
                "123",
            )
        } returns false
        coEvery { confluenceClient.getSpace(any(), any(), "123") } returns
            confluenceSpace(id = "123", key = "CANONICAL")
        every { connectionRepository.saveAndFlush(capture(saved)) } answers { firstArg() }

        val response = service.createConnection(authId, projectId, request())

        assertThat(response.projectId).isEqualTo(projectId)
        assertThat(response.baseUrl).isEqualTo("https://tenant.atlassian.net")
        assertThat(response.spaceId).isEqualTo("123")
        assertThat(response.spaceKey).isEqualTo("CANONICAL")
        assertThat(response.pageAllowlist).containsExactly("10", "20")
        assertThat(response.pageDenylist).containsExactly("20")
        assertThat(response.credentialsConfigured).isTrue()
        assertThat(saved.captured.credential.email).isEqualTo("fake-user@example.invalid")
        assertThat(saved.captured.credential.apiToken).isEqualTo(plaintextToken)
        coVerify(exactly = 1) {
            confluenceClient.getSpace("https://tenant.atlassian.net", any(), "123")
        }
    }

    @Test
    fun `equivalent normalized URL detects duplicate before validation`() = runTest {
        every {
            connectionRepository.existsByProjectIdAndBaseUrlAndSpaceId(
                projectId,
                "https://tenant.atlassian.net",
                "123",
            )
        } returns true

        val thrown = runCatching { service.createConnection(authId, projectId, request()) }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(ConfluenceConnectionAlreadyExistsException::class.java)

        coVerify(exactly = 0) { confluenceClient.getSpace(any(), any(), any()) }
        verify(exactly = 0) { connectionRepository.saveAndFlush(any()) }
    }

    @Test
    fun `remote validation failure persists neither connection nor credential and hides token`() = runTest {
        every { connectionRepository.existsByProjectIdAndBaseUrlAndSpaceId(any(), any(), any()) } returns false
        coEvery { confluenceClient.getSpace(any(), any(), any()) } throws
            ConfluenceAuthenticationException("retrieving space 123")

        val thrown = runCatching { service.createConnection(authId, projectId, request()) }.exceptionOrNull()

        assertThat(thrown)
            .isInstanceOf(ConfluenceConnectionConfigurationException::class.java)
            .hasMessage("Confluence credentials were rejected")
        assertThat(thrown.toString()).doesNotContain(plaintextToken, "Basic ", "fake-user@example.invalid")
        verify(exactly = 0) { connectionRepository.saveAndFlush(any()) }
        verify(exactly = 0) { credentialRepository.save(any()) }
    }

    @Test
    fun `project-scoped lookup cannot return another project connection`() {
        val connectionId = UUID.randomUUID()
        every { connectionRepository.findByIdAndProjectId(connectionId, projectId) } returns null

        assertThatThrownBy { service.getConnection(authId, projectId, connectionId) }
            .isInstanceOf(ConfluenceConnectionNotFoundException::class.java)

        verify(exactly = 1) { connectionRepository.findByIdAndProjectId(connectionId, projectId) }
        verify(exactly = 0) { connectionRepository.findById(any()) }
    }

    @Test
    fun `rejects project access before reading or writing connections`() {
        every { userApi.userHasAccessToProject(authId, projectId) } returns false

        assertThatThrownBy { service.getConnections(authId, projectId) }
            .isInstanceOf(ConfluenceProjectAccessDeniedException::class.java)

        verify(exactly = 0) { connectionRepository.findAllByProjectIdOrderByCreatedAtAsc(any()) }
    }

    @Test
    fun `returns decrypted credential through project-scoped internal lookup`() {
        val connectionId = UUID.randomUUID()
        val connection = connection(projectId)
        val credential = ConfluenceCredential(
            email = "fake-user@example.invalid",
            apiToken = plaintextToken,
            connection = connection,
        )
        every {
            credentialRepository.findByConnectionIdAndConnectionProjectId(connectionId, projectId)
        } returns credential

        val result = service.getClientCredentials(authId, projectId, connectionId)

        assertThat(result.email).isEqualTo("fake-user@example.invalid")
        assertThat(result.apiToken).isEqualTo(plaintextToken)
        assertThat(result.toString()).doesNotContain(plaintextToken, result.email)
    }

    @Test
    fun `blank filter failure is sanitized and does not validate or persist`() = runTest {
        val badRequest = request(pageAllowlist = listOf("10", " "))

        val thrown = runCatching { service.createConnection(authId, projectId, badRequest) }.exceptionOrNull()

        assertThat(thrown)
            .isInstanceOf(ConfluenceConnectionConfigurationException::class.java)
            .hasMessage("Confluence page allowlist must not contain blank page IDs")
        assertThat(thrown.toString()).doesNotContain(plaintextToken)
        coVerify(exactly = 0) { confluenceClient.getSpace(any(), any(), any()) }
        verify(exactly = 0) { connectionRepository.saveAndFlush(any()) }
    }

    @Test
    fun `configures project scoped schedule and calculates next sync`() {
        val connection = connection(projectId)
        val scheduleSpec = ScheduleSpec.Interval(30)
        val cron = "0 */30 * * * *"
        val nextSyncAt = Instant.parse("2026-08-28T13:00:00Z")
        every { connectionRepository.findByIdAndProjectId(connection.id, projectId) } returns connection
        every { cronBuilder.build(scheduleSpec) } returns cron
        every { scheduleCalculator.calculateNextSyncAt(cron, any()) } returns nextSyncAt

        val response = service.configureSchedule(
            authId,
            projectId,
            connection.id,
            ConfigureConfluenceScheduleRequest(scheduleSpec, autoUpdate = true),
        )

        assertThat(connection.autoUpdate).isTrue()
        assertThat(connection.spec).isEqualTo(scheduleSpec)
        assertThat(connection.schedule).isEqualTo(cron)
        assertThat(connection.nextSyncAt).isEqualTo(nextSyncAt)
        assertThat(response.autoUpdate).isTrue()
        assertThat(response.nextSyncAt).isEqualTo(nextSyncAt)
    }

    @Test
    fun `invalid schedule is rejected without mutating connection`() {
        val connection = connection(projectId)
        val scheduleSpec = ScheduleSpec.Custom("invalid")
        every { connectionRepository.findByIdAndProjectId(connection.id, projectId) } returns connection
        every { cronBuilder.build(scheduleSpec) } returns "invalid"
        every { scheduleCalculator.calculateNextSyncAt("invalid", any()) } returns null

        assertThatThrownBy {
            service.configureSchedule(
                authId,
                projectId,
                connection.id,
                ConfigureConfluenceScheduleRequest(scheduleSpec, autoUpdate = true),
            )
        }.isInstanceOf(ConfluenceConnectionConfigurationException::class.java)
            .hasMessage("Confluence schedule is invalid")

        assertThat(connection.autoUpdate).isFalse()
        assertThat(connection.nextSyncAt).isNull()
    }

    private fun request(pageAllowlist: List<String> = listOf(" 10 ", "20", "10")) =
        CreateConfluenceConnectionRequest(
            baseUrl = " HTTPS://TENANT.ATLASSIAN.NET/wiki/ ",
            spaceId = " 123 ",
            email = " fake-user@example.invalid ",
            apiToken = " $plaintextToken ",
            pageAllowlist = pageAllowlist,
            pageDenylist = listOf(" 20 ", "20"),
        )

    private fun confluenceSpace(id: String, key: String) = ConfluenceSpace(
        id = id,
        key = key,
        name = "Engineering",
        type = "global",
        status = "current",
        currentActiveAlias = "eng",
        webUiPath = "/spaces/eng",
    )

    private fun connection(ownerProjectId: UUID) = ConfluenceSpaceConnection(
        projectId = ownerProjectId,
        baseUrl = "https://tenant.atlassian.net",
        spaceId = "123",
        spaceKey = "ENG",
    )
}
