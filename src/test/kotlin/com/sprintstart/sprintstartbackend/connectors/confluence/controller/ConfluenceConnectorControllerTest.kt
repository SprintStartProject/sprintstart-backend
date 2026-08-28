package com.sprintstart.sprintstartbackend.connectors.confluence.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.connectors.confluence.ConfluenceConnector
import com.sprintstart.sprintstartbackend.connectors.confluence.model.api.request.CreateConfluenceConnectionRequest
import com.sprintstart.sprintstartbackend.connectors.confluence.model.api.response.ConfluenceConnectionResponse
import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceConnectionConfigurationException
import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceConnectionNotFoundException
import com.sprintstart.sprintstartbackend.connectors.confluence.model.ingestion.ConfluenceIngestionResult
import com.sprintstart.sprintstartbackend.connectors.confluence.model.ingestion.ConfluenceIngestionStatus
import com.sprintstart.sprintstartbackend.connectors.confluence.service.ConfluenceConnectionService
import com.sprintstart.sprintstartbackend.user.security.ProjectAuthorization
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@WebMvcTest(controllers = [ConfluenceConnectorController::class])
@AutoConfigureMockMvc
@Import(ConfluenceExceptionHandler::class, SecurityConfig::class)
internal class ConfluenceConnectorControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var connectionService: ConfluenceConnectionService

    @MockkBean
    private lateinit var connector: ConfluenceConnector

    @MockkBean(name = "projectAuth")
    private lateinit var projectAuthorization: ProjectAuthorization

    private val objectMapper = jacksonObjectMapper()
    private val projectId = UUID.randomUUID()
    private val connectionId = UUID.randomUUID()

    private val adminJwt = jwt()
        .jwt { jwt -> jwt.subject("admin-id") }
        .authorities(SimpleGrantedAuthority("ROLE_ADMIN"))
    private val pmJwt = jwt()
        .jwt { jwt -> jwt.subject("pm-id") }
        .authorities(SimpleGrantedAuthority("ROLE_PM"))
    private val userJwt = jwt()
        .jwt { jwt -> jwt.subject("user-id") }
        .authorities(SimpleGrantedAuthority("ROLE_USER"))

    @BeforeEach
    fun authorizeProject() {
        every { projectAuthorization.canManageProject(any(), projectId) } returns true
    }

    @Test
    fun `ADMIN can connect and receives safe response`() {
        val request = connectionRequest()
        coEvery { connectionService.createConnection("admin-id", projectId, any()) } returns connectionResponse()

        val response = performConnect(request, adminJwt)

        mockMvc
            .perform(asyncDispatch(response))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(connectionId.toString()))
            .andExpect(jsonPath("$.projectId").value(projectId.toString()))
            .andExpect(jsonPath("$.credentialsConfigured").value(true))
            .andExpect(jsonPath("$.apiToken").doesNotExist())
            .andExpect(jsonPath("$.email").doesNotExist())
    }

    @Test
    fun `PM can connect a project they manage`() {
        val request = connectionRequest()
        coEvery { connectionService.createConnection("pm-id", projectId, any()) } returns connectionResponse()

        val response = performConnect(request, pmJwt)

        mockMvc.perform(asyncDispatch(response)).andExpect(status().isCreated)
    }

    @Test
    fun `USER receives forbidden`() {
        val asyncResult =
            mockMvc
                .perform(
                    post(basePath())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(connectionRequest()))
                        .with(userJwt),
                ).andExpect(request().asyncStarted())
                .andReturn()

        mockMvc.perform(asyncDispatch(asyncResult)).andExpect(status().isForbidden)

        coVerify(exactly = 0) { connectionService.createConnection(any(), any(), any()) }
    }

    @Test
    fun `unauthenticated connect receives unauthorized`() {
        mockMvc
            .perform(
                post(basePath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(connectionRequest())),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `PM without project management access receives forbidden`() {
        every { projectAuthorization.canManageProject(any(), projectId) } returns false

        mockMvc
            .perform(
                get(basePath())
                    .with(pmJwt),
            ).andExpect(status().isForbidden)

        verify(exactly = 0) { connectionService.getConnections(any(), any()) }
    }

    @Test
    fun `invalid connect request returns bad request without invoking service`() {
        val invalidRequest =
            """
            {
              "baseUrl": "",
              "spaceId": "not-numeric",
              "email": "invalid-email",
              "apiToken": "",
              "pageAllowlist": [""],
              "pageDenylist": []
            }
            """.trimIndent()

        mockMvc
            .perform(
                post(basePath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidRequest)
                    .with(adminJwt),
            ).andExpect(status().isBadRequest)

        coVerify(exactly = 0) { connectionService.createConnection(any(), any(), any()) }
    }

    @Test
    fun `validation failure response does not expose supplied token`() {
        val secret = "controller-secret-token"
        val request = connectionRequest(apiToken = secret)
        coEvery { connectionService.createConnection("admin-id", projectId, any()) } throws
            ConfluenceConnectionConfigurationException("Confluence credentials were rejected", 401)

        val asyncResult = performConnect(request, adminJwt)

        val response = mockMvc
            .perform(asyncDispatch(asyncResult))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("Confluence credentials were rejected"))
            .andReturn()
        assertThat(response.response.contentAsString)
            .doesNotContain(secret)
            .doesNotContain("Authorization")
    }

    @Test
    fun `discover returns only project-scoped safe connections`() {
        every { connectionService.getConnections("pm-id", projectId) } returns listOf(connectionResponse())

        mockMvc
            .perform(get(basePath()).with(pmJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].projectId").value(projectId.toString()))
            .andExpect(jsonPath("$[0].spaceId").value("42"))
            .andExpect(jsonPath("$[0].apiToken").doesNotExist())
    }

    @Test
    fun `foreign-project connection is returned as not found`() {
        every {
            connectionService.getConnection("pm-id", projectId, connectionId)
        } throws ConfluenceConnectionNotFoundException(connectionId, projectId)

        mockMvc
            .perform(get("${basePath()}/$connectionId").with(pmJwt))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `update delegates to existing ingestion and preserves all terminal item statuses`() {
        ConfluenceIngestionStatus.entries.forEach { ingestionStatus ->
            val result = ingestionResult(ingestionStatus)
            coEvery { connector.ingest(projectId, connectionId) } returns result

            val asyncResult = mockMvc
                .perform(post("${basePath()}/$connectionId/update").with(adminJwt))
                .andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value(ingestionStatus.name))
                .andExpect(jsonPath("$.runId").value(result.runId.toString()))
        }

        coVerify(exactly = ConfluenceIngestionStatus.entries.size) { connector.ingest(projectId, connectionId) }
    }

    private fun performConnect(
        request: CreateConfluenceConnectionRequest,
        authentication: org.springframework.test.web.servlet.request.RequestPostProcessor,
    ) = mockMvc
        .perform(
            post(basePath())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(authentication),
        ).andExpect(request().asyncStarted())
        .andReturn()

    private fun basePath(): String = "/api/v1/confluence/projects/$projectId/connections"

    private fun connectionRequest(apiToken: String = "fake-controller-token") = CreateConfluenceConnectionRequest(
        baseUrl = "https://tenant.invalid",
        spaceId = "42",
        email = "connector@example.invalid",
        apiToken = apiToken,
        pageAllowlist = listOf("100"),
        pageDenylist = listOf("200"),
    )

    private fun connectionResponse() = ConfluenceConnectionResponse(
        id = connectionId,
        projectId = projectId,
        baseUrl = "https://tenant.invalid",
        spaceId = "42",
        spaceKey = "ENG",
        pageAllowlist = listOf("100"),
        pageDenylist = listOf("200"),
        credentialsConfigured = true,
        createdAt = Instant.parse("2026-08-28T08:00:00Z"),
        updatedAt = Instant.parse("2026-08-28T08:00:00Z"),
        version = 0,
    )

    private fun ingestionResult(status: ConfluenceIngestionStatus) = ConfluenceIngestionResult(
        runId = UUID.randomUUID(),
        connectionId = connectionId,
        discovered = 2,
        eligible = 2,
        filtered = 0,
        created = if (status == ConfluenceIngestionStatus.COMPLETED) 2 else 1,
        updated = 0,
        unchanged = 0,
        failed = if (status == ConfluenceIngestionStatus.COMPLETED) 0 else 1,
        failures = emptyList(),
        status = status,
    )
}
