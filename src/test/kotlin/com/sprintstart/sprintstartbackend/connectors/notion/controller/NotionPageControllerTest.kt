package com.sprintstart.sprintstartbackend.connectors.notion.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.request.CreateNotionPageConnectionRequest
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.response.NotionDiscoveredPageResponse
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.response.NotionPageConnectionResponse
import com.sprintstart.sprintstartbackend.connectors.notion.service.NotionPageConnectionService
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduleSpec
import com.sprintstart.sprintstartbackend.user.external.security.ProjectAuthorization
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

@WebMvcTest(controllers = [NotionPageController::class])
@AutoConfigureMockMvc
@Import(NotionExceptionHandler::class, SecurityConfig::class)
internal class NotionPageControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var pageConnectionService: NotionPageConnectionService

    @MockkBean(name = "projectAuth")
    private lateinit var projectAuthorization: ProjectAuthorization

    private val objectMapper = jacksonObjectMapper()
    private val projectId = UUID.randomUUID()
    private val connectionId = UUID.randomUUID()
    private val adminJwt = jwt()
        .jwt { token -> token.subject("admin-id") }
        .authorities(SimpleGrantedAuthority("ROLE_ADMIN"))
    private val pmJwt = jwt()
        .jwt { token -> token.subject("pm-id") }
        .authorities(SimpleGrantedAuthority("ROLE_PM"))
    private val userJwt = jwt()
        .jwt { token -> token.subject("user-id") }
        .authorities(SimpleGrantedAuthority("ROLE_USER"))

    @BeforeEach
    fun authorizeProject() {
        every { projectAuthorization.canManageProject(any(), projectId) } returns true
    }

    @Test
    fun `PM discovers safe pages using their auth subject`() {
        coEvery { pageConnectionService.discoverPages("pm-id", "team-token") } returns listOf(
            NotionDiscoveredPageResponse(
                id = "page-1",
                title = "Engineering",
                url = "https://www.notion.so/page-1",
                lastEditedTime = "2026-09-27T10:00:00.000Z",
            ),
        )

        val asyncResult = mockMvc
            .perform(
                get("/api/v1/notion/pages")
                    .queryParam("credentialName", "team-token")
                    .with(pmJwt),
            ).andExpect(request().asyncStarted())
            .andReturn()

        mockMvc
            .perform(asyncDispatch(asyncResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value("page-1"))
            .andExpect(jsonPath("$[0].title").value("Engineering"))
            .andExpect(jsonPath("$[0].properties").doesNotExist())
            .andExpect(jsonPath("$[0].token").doesNotExist())

        coVerify(exactly = 1) { pageConnectionService.discoverPages("pm-id", "team-token") }
    }

    @Test
    fun `USER cannot discover Notion pages`() {
        val asyncResult = mockMvc
            .perform(
                get("/api/v1/notion/pages")
                    .queryParam("credentialName", "team-token")
                    .with(userJwt),
            ).andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(asyncResult)).andExpect(status().isForbidden)
        coVerify(exactly = 0) { pageConnectionService.discoverPages(any(), any()) }
    }

    @Test
    fun `ADMIN connects a page and receives created response`() {
        val request = CreateNotionPageConnectionRequest("team-token", "page-1")
        coEvery { pageConnectionService.connectPage("admin-id", projectId, request) } returns connectionResponse()

        val asyncResult = mockMvc
            .perform(
                post(connectionsPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .with(adminJwt),
            ).andExpect(request().asyncStarted())
            .andReturn()

        mockMvc
            .perform(asyncDispatch(asyncResult))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(connectionId.toString()))
            .andExpect(jsonPath("$.pageId").value("page-1"))

        coVerify(exactly = 1) { pageConnectionService.connectPage("admin-id", projectId, request) }
    }

    @Test
    fun `PM without project management permission receives forbidden`() {
        every { projectAuthorization.canManageProject(any(), projectId) } returns false

        mockMvc.perform(get(connectionsPath()).with(pmJwt)).andExpect(status().isForbidden)

        verify(exactly = 0) { pageConnectionService.getConnections(any()) }
    }

    @Test
    fun `PM lists and deletes project scoped connections`() {
        every { pageConnectionService.getConnections(projectId) } returns listOf(connectionResponse())
        every { pageConnectionService.deleteConnection(projectId, connectionId) } returns Unit

        mockMvc
            .perform(get(connectionsPath()).with(pmJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].projectId").value(projectId.toString()))
            .andExpect(jsonPath("$[0].credentialName").value("team-token"))
            .andExpect(jsonPath("$[0].token").doesNotExist())

        mockMvc
            .perform(delete("${connectionsPath()}/$connectionId").with(pmJwt))
            .andExpect(status().isNoContent)

        verify(exactly = 1) { pageConnectionService.getConnections(projectId) }
        verify(exactly = 1) { pageConnectionService.deleteConnection(projectId, connectionId) }
    }

    private fun connectionsPath(): String {
        return "/api/v1/notion/projects/$projectId/connections"
    }

    private fun connectionResponse(): NotionPageConnectionResponse {
        val timestamp = Instant.parse("2026-09-27T10:00:00Z")
        return NotionPageConnectionResponse(
            id = connectionId,
            projectId = projectId,
            pageId = "page-1",
            pageTitle = "Engineering",
            pageUrl = "https://www.notion.so/page-1",
            credentialName = "team-token",
            sourceEnabled = true,
            autoUpdate = false,
            schedule = "0 0 2 * * *",
            scheduleSpec = ScheduleSpec.Daily(LocalTime.of(2, 0)),
            nextSyncAt = null,
            lastEditedTime = null,
            contentHash = null,
            lastSyncedAt = null,
            createdAt = timestamp,
            updatedAt = timestamp,
            version = 0,
        )
    }
}
