package com.sprintstart.sprintstartbackend.connectors.confluence.service

import com.sprintstart.sprintstartbackend.connectors.atlassian.external.AtlassianCredentialApi
import com.sprintstart.sprintstartbackend.connectors.atlassian.external.AtlassianCredentialSecret
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceAuthenticationException
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceClient
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceSpace
import com.sprintstart.sprintstartbackend.connectors.confluence.model.api.request.ConfigureConfluenceScheduleRequest
import com.sprintstart.sprintstartbackend.connectors.confluence.model.api.request.CreateConfluenceConnectionRequest
import com.sprintstart.sprintstartbackend.connectors.confluence.model.entity.ConfluenceSpaceConnection
import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceConnectionAlreadyExistsException
import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceConnectionConfigurationException
import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceConnectionNotFoundException
import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceCredentialNotFoundException
import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceProjectAccessDeniedException
import com.sprintstart.sprintstartbackend.connectors.confluence.repository.ConfluenceSpaceConnectionRepository
import com.sprintstart.sprintstartbackend.shared.scheduler.CronBuilder
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduleSpec
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduledExecutor
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ConfluenceConnectionServiceTest {
    private val confluenceClient = mockk<ConfluenceClient>()
    private val connectionRepository = mockk<ConfluenceSpaceConnectionRepository>()
    private val atlassianCredentialApi = mockk<AtlassianCredentialApi>()
    private val userApi = mockk<UserApi>()
    private val cronBuilder = mockk<CronBuilder>()
    private val scheduleCalculator = mockk<ConfluenceScheduleCalculator>()
    private val ingestionService = mockk<ConfluencePageIngestionService>()
    private val initialIngestionScope = TestScope()

    // The real persistence collaborator: it owns the insert, so the create-connection expectations below stay
    // meaningful instead of asserting against a mocked-away write.
    private val connectionPersistenceService = ConfluenceConnectionPersistenceService(connectionRepository)
    private val service = ConfluenceConnectionService(
        confluenceClient,
        connectionRepository,
        atlassianCredentialApi,
        userApi,
        cronBuilder,
        scheduleCalculator,
        connectionPersistenceService,
        ScheduledExecutor(initialIngestionScope),
        ingestionService,
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
        every { atlassianCredentialApi.findSecret(authId, "team-token") } returns
            AtlassianCredentialSecret(userEmail = "fake-user@example.invalid", apiToken = plaintextToken)
        coEvery { confluenceClient.getSpace(any(), any(), "123") } returns
            confluenceSpace(id = "123", key = "CANONICAL")
        every { connectionRepository.saveAndFlush(capture(saved)) } answers { firstArg() }
        coEvery { ingestionService.ingest(any(), any()) } returns mockk()

        val response = service.createConnection(authId, projectId, request())
        initialIngestionScope.advanceUntilIdle()

        assertThat(response.projectId).isEqualTo(projectId)
        assertThat(response.baseUrl).isEqualTo("https://tenant.atlassian.net")
        assertThat(response.spaceId).isEqualTo("123")
        assertThat(response.spaceKey).isEqualTo("CANONICAL")
        assertThat(response.spaceName).isEqualTo("Engineering")
        assertThat(response.credentialName).isEqualTo("team-token")
        assertThat(response.pageAllowlist).containsExactly("10", "20")
        assertThat(response.pageDenylist).containsExactly("20")
        assertThat(response.credentialsConfigured).isTrue()
        assertThat(saved.captured.credentialAuthId).isEqualTo(authId)
        assertThat(saved.captured.credentialName).isEqualTo("team-token")
        coVerify(exactly = 1) {
            confluenceClient.getSpace("https://tenant.atlassian.net", any(), "123")
        }
        coVerify(exactly = 1) { ingestionService.ingest(projectId, saved.captured.id) }
    }

    @Test
    fun `blank remote space name is stored as null`() = runTest {
        val saved = slot<ConfluenceSpaceConnection>()
        every {
            connectionRepository.existsByProjectIdAndBaseUrlAndSpaceId(
                projectId,
                "https://tenant.atlassian.net",
                "123",
            )
        } returns false
        every { atlassianCredentialApi.findSecret(authId, "team-token") } returns
            AtlassianCredentialSecret(userEmail = "fake-user@example.invalid", apiToken = plaintextToken)
        coEvery { confluenceClient.getSpace(any(), any(), "123") } returns
            confluenceSpace(id = "123", key = "CANONICAL", name = "  ")
        every { connectionRepository.saveAndFlush(capture(saved)) } answers { firstArg() }

        val response = service.createConnection(authId, projectId, request())

        assertThat(response.spaceName).isNull()
        assertThat(saved.captured.spaceName).isNull()
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
        coVerify(exactly = 0) { ingestionService.ingest(any(), any()) }
    }

    @Test
    fun `unknown credential name is rejected before contacting Confluence`() = runTest {
        every { connectionRepository.existsByProjectIdAndBaseUrlAndSpaceId(any(), any(), any()) } returns false
        every { atlassianCredentialApi.findSecret(authId, "team-token") } returns null

        val thrown = runCatching { service.createConnection(authId, projectId, request()) }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(ConfluenceCredentialNotFoundException::class.java)
        coVerify(exactly = 0) { confluenceClient.getSpace(any(), any(), any()) }
        verify(exactly = 0) { connectionRepository.saveAndFlush(any()) }
        coVerify(exactly = 0) { ingestionService.ingest(any(), any()) }
    }

    @Test
    fun `remote validation failure persists neither connection nor credential and hides token`() = runTest {
        every { connectionRepository.existsByProjectIdAndBaseUrlAndSpaceId(any(), any(), any()) } returns false
        every { atlassianCredentialApi.findSecret(authId, "team-token") } returns
            AtlassianCredentialSecret(userEmail = "fake-user@example.invalid", apiToken = plaintextToken)
        coEvery { confluenceClient.getSpace(any(), any(), any()) } throws
            ConfluenceAuthenticationException("retrieving space 123")

        val thrown = runCatching { service.createConnection(authId, projectId, request()) }.exceptionOrNull()

        assertThat(thrown)
            .isInstanceOf(ConfluenceConnectionConfigurationException::class.java)
            .hasMessage("Confluence credentials were rejected")
        assertThat(thrown.toString()).doesNotContain(plaintextToken, "Basic ", "fake-user@example.invalid")
        verify(exactly = 0) { connectionRepository.saveAndFlush(any()) }
        coVerify(exactly = 0) { ingestionService.ingest(any(), any()) }
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

    /**
     * The space lookup suspends, so a transaction opened here would be committed on the calling thread long
     * before anything is written and the creation event would end up outside any transaction.
     */
    @Test
    fun `suspending connection creation does not own a transaction`() {
        val createConnection = ConfluenceConnectionService::class.java.declaredMethods
            .single { method -> method.name == "createConnection" }

        assertThat(createConnection.getAnnotation(Transactional::class.java)).isNull()
        assertThat(ConfluenceConnectionService::class.java.getAnnotation(Transactional::class.java)).isNull()
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

    @Test
    fun `deletes a project-owned connection`() {
        val connection = connection(projectId)
        every { connectionRepository.findByIdAndProjectId(connection.id, projectId) } returns connection
        every { connectionRepository.delete(connection) } returns Unit

        service.deleteConnection(authId, projectId, connection.id)

        verify(exactly = 1) { connectionRepository.delete(connection) }
    }

    @Test
    fun `deleting another project connection is rejected as not found`() {
        val connectionId = UUID.randomUUID()
        every { connectionRepository.findByIdAndProjectId(connectionId, projectId) } returns null

        assertThatThrownBy { service.deleteConnection(authId, projectId, connectionId) }
            .isInstanceOf(ConfluenceConnectionNotFoundException::class.java)

        verify(exactly = 0) { connectionRepository.delete(any()) }
    }

    @Test
    fun `rejects project access before deleting a connection`() {
        every { userApi.userHasAccessToProject(authId, projectId) } returns false

        assertThatThrownBy { service.deleteConnection(authId, projectId, UUID.randomUUID()) }
            .isInstanceOf(ConfluenceProjectAccessDeniedException::class.java)

        verify(exactly = 0) { connectionRepository.findByIdAndProjectId(any(), any()) }
        verify(exactly = 0) { connectionRepository.delete(any()) }
    }

    private fun request(pageAllowlist: List<String> = listOf(" 10 ", "20", "10")) =
        CreateConfluenceConnectionRequest(
            baseUrl = " HTTPS://TENANT.ATLASSIAN.NET/wiki/ ",
            spaceId = " 123 ",
            credentialName = "team-token",
            pageAllowlist = pageAllowlist,
            pageDenylist = listOf(" 20 ", "20"),
        )

    private fun confluenceSpace(id: String, key: String, name: String = "Engineering") = ConfluenceSpace(
        id = id,
        key = key,
        name = name,
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
        credentialAuthId = authId,
        credentialName = "team-token",
    )
}
