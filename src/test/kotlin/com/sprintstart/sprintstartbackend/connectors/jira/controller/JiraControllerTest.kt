package com.sprintstart.sprintstartbackend.connectors.jira.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.request.ConnectJiraInstanceRequest
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.request.UpdateJiraInstanceRequest
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.response.JiraInstanceDto
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.response.UpdateJiraInstanceResponse
import com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions.JiraAuthException
import com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions.JiraCredentialAlreadyExistsException
import com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions.JiraCredentialNotFoundException
import com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions.JiraInstanceNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions.JiraInstanceUnavailableException
import com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions.JiraResourceNotFoundException
import com.sprintstart.sprintstartbackend.connectors.jira.service.JiraService
import com.sprintstart.sprintstartbackend.connectors.jira.service.JiraUpdateService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
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

@WebMvcTest(controllers = [JiraController::class])
@AutoConfigureMockMvc
@Import(JiraExceptionHandler::class, SecurityConfig::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(MockKExtension::class)
class JiraControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var service: JiraService

    @MockkBean
    private lateinit var updateService: JiraUpdateService

    private val objectMapper = jacksonObjectMapper()

    private val adminJwt = jwt()
        .jwt { it.subject("admin-id") }
        .authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

    private val pmJwt = jwt()
        .jwt { it.subject("pm-id") }
        .authorities(SimpleGrantedAuthority("ROLE_PM"))

    private fun instanceDto(instanceUrl: String = "https://jira.example.com") = JiraInstanceDto(
        instanceUrl = instanceUrl,
        displayName = "Test",
        lastUpdate = Instant.now(),
        projectIds = mutableSetOf(UUID.randomUUID()),
        sourceEnabled = true,
        status = "UP_TO_DATE",
        updateCredentialName = "token",
        updateCredentialUserEmail = "user@example.com",
    )

    @Nested
    inner class GetInstances {
        @Test
        fun `should return 200 with instances when authenticated as ADMIN`() {
            every { service.getInstances() } returns listOf(instanceDto())

            mockMvc
                .perform(get("/api/v1/jira/instances").with(adminJwt))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].instanceUrl").value("https://jira.example.com"))
        }

        @Test
        fun `should return 200 with instances when authenticated as PM`() {
            every { service.getInstances() } returns listOf(instanceDto())

            mockMvc
                .perform(get("/api/v1/jira/instances").with(pmJwt))
                .andExpect(status().isOk)
        }

        @Test
        fun `should filter by project id`() {
            val projectId = UUID.randomUUID()
            every { service.getInstances(projectId) } returns listOf(instanceDto())

            mockMvc
                .perform(get("/api/v1/jira/instances").param("projectId", projectId.toString()).with(adminJwt))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].instanceUrl").value("https://jira.example.com"))
        }

        @Test
        fun `should return 401 when not authenticated`() {
            mockMvc
                .perform(get("/api/v1/jira/instances"))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class ConnectInstance {
        @Test
        fun `should return 202 when authenticated as ADMIN`() {
            val request = ConnectJiraInstanceRequest(
                displayName = "Test",
                url = "https://jira.example.com",
                userEmail = "user@example.com",
                tokenName = "token",
                projectId = UUID.randomUUID(),
            )
            coEvery { service.connectInstanceIfNeeded(request) } returns UUID.randomUUID()

            val asyncResult = mockMvc
                .perform(
                    post("/api/v1/jira/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(adminJwt),
                ).andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isAccepted)

            coVerify { service.connectInstanceIfNeeded(request) }
        }

        @Test
        fun `should return 400 for invalid request`() {
            val request =
                """
                {
                    "displayName": "",
                    "url": "",
                    "userEmail": "not-an-email",
                    "tokenName": "",
                    "projectId": null
                }
                """.trimIndent()

            mockMvc
                .perform(
                    post("/api/v1/jira/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request)
                        .with(adminJwt),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 401 when not authenticated`() {
            val request = ConnectJiraInstanceRequest(
                displayName = "Test",
                url = "https://jira.example.com",
                userEmail = "user@example.com",
                tokenName = "token",
                projectId = UUID.randomUUID(),
            )

            mockMvc
                .perform(
                    post("/api/v1/jira/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class UpdateInstance {
        @Test
        fun `should return 202 with response`() {
            val request = UpdateJiraInstanceRequest("https://jira.example.com")
            val response = UpdateJiraInstanceResponse(UUID.randomUUID())
            coEvery { updateService.updateJiraInstance(request.instanceUrl, true) } returns response

            val asyncResult = mockMvc
                .perform(
                    post("/api/v1/jira/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(adminJwt),
                ).andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isAccepted)
                .andExpect(jsonPath("$.transactionId").value(response.transactionId.toString()))
        }
    }

    @Nested
    inner class ExceptionHandling {
        private val request = ConnectJiraInstanceRequest(
            displayName = "Test",
            url = "https://jira.example.com",
            userEmail = "user@example.com",
            tokenName = "token",
            projectId = UUID.randomUUID(),
        )

        @Test
        fun `should return 404 when instance not connected`() {
            coEvery { service.connectInstanceIfNeeded(request) } throws JiraInstanceNotConnectedException(request.url)

            val asyncResult = mockMvc
                .perform(
                    post("/api/v1/jira/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(adminJwt),
                ).andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isNotFound)
        }

        @Test
        fun `should return 404 when credentials not found`() {
            coEvery { service.connectInstanceIfNeeded(request) } throws JiraCredentialNotFoundException(
                request.userEmail,
                request.tokenName,
            )

            val asyncResult = mockMvc
                .perform(
                    post("/api/v1/jira/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(adminJwt),
                ).andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isNotFound)
        }

        @Test
        fun `should return 401 when jira auth fails`() {
            coEvery { service.connectInstanceIfNeeded(request) } throws JiraAuthException(
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                "Unauthorized",
            )

            val asyncResult = mockMvc
                .perform(
                    post("/api/v1/jira/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(adminJwt),
                ).andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `should return 502 when instance unavailable`() {
            coEvery { service.connectInstanceIfNeeded(request) } throws JiraInstanceUnavailableException(request.url)

            val asyncResult = mockMvc
                .perform(
                    post("/api/v1/jira/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(adminJwt),
                ).andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isBadGateway)
        }

        @Test
        fun `should return 404 when resource not found`() {
            coEvery { service.connectInstanceIfNeeded(request) } throws JiraResourceNotFoundException("Issue not found")

            val asyncResult = mockMvc
                .perform(
                    post("/api/v1/jira/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(adminJwt),
                ).andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isNotFound)
        }

        @Test
        fun `should return 400 when credential already exists`() {
            coEvery { service.connectInstanceIfNeeded(request) } throws JiraCredentialAlreadyExistsException(
                request.userEmail,
                request.tokenName,
            )

            val asyncResult = mockMvc
                .perform(
                    post("/api/v1/jira/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(adminJwt),
                ).andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class UpdateAllInstances {
        @Test
        fun `should return 202 with list of responses`() {
            val response = UpdateJiraInstanceResponse(UUID.randomUUID())
            coEvery { updateService.updateAllJiraInstances() } returns listOf(response)

            val asyncResult = mockMvc
                .perform(post("/api/v1/jira/update-all").with(adminJwt))
                .andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isAccepted)
                .andExpect(jsonPath("$[0].transactionId").value(response.transactionId.toString()))
        }
    }
}
