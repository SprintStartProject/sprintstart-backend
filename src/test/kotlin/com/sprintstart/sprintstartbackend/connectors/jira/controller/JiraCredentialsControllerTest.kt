package com.sprintstart.sprintstartbackend.connectors.jira.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.request.credentials.AddCredentialRequest
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.request.credentials.ChangeJiraCredentialNameRequest
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.request.credentials.ChangeJiraCredentialTokenRequest
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.request.credentials.DeleteJiraCredentialRequest
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.response.credentials.JiraCredentialsDto
import com.sprintstart.sprintstartbackend.connectors.jira.service.JiraCredentialsService
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.verify
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [JiraCredentialsController::class])
@AutoConfigureMockMvc
@Import(JiraExceptionHandler::class, SecurityConfig::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(MockKExtension::class)
class JiraCredentialsControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var service: JiraCredentialsService

    private val objectMapper = jacksonObjectMapper()

    private val adminJwt = jwt()
        .jwt { it.subject("admin-id") }
        .authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

    @Nested
    inner class AddCredentials {
        @Test
        fun `should return 204 when authenticated as ADMIN`() {
            val request = AddCredentialRequest("user@example.com", "token", "secret")
            every { service.addCredentials(request) } returns Unit

            mockMvc
                .perform(
                    post("/api/v1/jira/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(adminJwt),
                ).andExpect(status().isNoContent)

            verify { service.addCredentials(request) }
        }

        @Test
        fun `should return 400 for invalid request`() {
            val request =
                """
                {
                    "userEmail": "not-an-email",
                    "tokenName": "",
                    "authToken": ""
                }
                """.trimIndent()

            mockMvc
                .perform(
                    post("/api/v1/jira/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request)
                        .with(adminJwt),
                ).andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class GetCredentialsOfUser {
        @Test
        fun `should return 200 with credentials`() {
            every { service.getCredentialsOfUser("user@example.com") } returns listOf(
                JiraCredentialsDto(
                    "user@example.com",
                    "token",
                ),
            )

            mockMvc
                .perform(
                    get(
                        "/api/v1/jira/credentials/{userEmail}",
                        "user@example.com",
                    ).with(adminJwt),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$[0].userEmail").value("user@example.com"))
        }
    }

    @Nested
    inner class RemoveCredential {
        @Test
        fun `should return 204 when credential removed`() {
            val request = DeleteJiraCredentialRequest("user@example.com", "token")
            every { service.removeCredential(request) } returns Unit

            mockMvc
                .perform(
                    delete("/api/v1/jira/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(adminJwt),
                ).andExpect(status().isNoContent)
        }
    }

    @Nested
    inner class ChangeCredentialName {
        @Test
        fun `should return 200 with updated credential`() {
            val request = ChangeJiraCredentialNameRequest("user@example.com", "token", "newToken")
            every { service.changeCredentialName(request) } returns JiraCredentialsDto("user@example.com", "newToken")

            mockMvc
                .perform(
                    patch("/api/v1/jira/credentials/patch/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(adminJwt),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.displayName").value("newToken"))
        }
    }

    @Nested
    inner class ChangeCredentialToken {
        @Test
        fun `should return 200 with updated credential`() {
            val request = ChangeJiraCredentialTokenRequest("user@example.com", "token", "newSecret")
            every { service.changeCredentialToken(request) } returns JiraCredentialsDto("user@example.com", "token")

            mockMvc
                .perform(
                    patch("/api/v1/jira/credentials/patch/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(adminJwt),
                ).andExpect(status().isOk)
        }
    }
}
