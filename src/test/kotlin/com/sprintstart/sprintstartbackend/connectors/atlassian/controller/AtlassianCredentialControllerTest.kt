package com.sprintstart.sprintstartbackend.connectors.atlassian.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.api.request.AddAtlassianCredentialRequest
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.api.request.ChangeAtlassianCredentialNameRequest
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.api.request.ChangeAtlassianCredentialTokenRequest
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.api.request.DeleteAtlassianCredentialRequest
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.api.response.AtlassianCredentialDto
import com.sprintstart.sprintstartbackend.connectors.atlassian.service.AtlassianCredentialService
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

@WebMvcTest(controllers = [AtlassianCredentialController::class])
@AutoConfigureMockMvc
@Import(AtlassianCredentialExceptionHandler::class, SecurityConfig::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(MockKExtension::class)
class AtlassianCredentialControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var service: AtlassianCredentialService

    private val objectMapper = jacksonObjectMapper()

    private val adminJwt = jwt()
        .jwt { it.subject("admin-id") }
        .authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

    @Nested
    inner class AddCredentials {
        @Test
        fun `should return 204 when authenticated as ADMIN`() {
            val request = AddAtlassianCredentialRequest("user@example.com", "token", "secret")
            every { service.addCredentials("admin-id", request) } returns Unit

            mockMvc
                .perform(
                    post("/api/v1/atlassian/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(adminJwt),
                ).andExpect(status().isNoContent)

            verify { service.addCredentials("admin-id", request) }
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
                    post("/api/v1/atlassian/credentials")
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
            every { service.getCredentialsOfUser("admin-id") } returns listOf(
                AtlassianCredentialDto(
                    "user@example.com",
                    "token",
                ),
            )

            mockMvc
                .perform(
                    get("/api/v1/atlassian/credentials").with(adminJwt),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$[0].userEmail").value("user@example.com"))
        }
    }

    @Nested
    inner class RemoveCredential {
        @Test
        fun `should return 204 when credential removed`() {
            val request = DeleteAtlassianCredentialRequest("user@example.com", "token")
            every { service.removeCredential("admin-id", request) } returns Unit

            mockMvc
                .perform(
                    delete("/api/v1/atlassian/credentials")
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
            val request = ChangeAtlassianCredentialNameRequest("user@example.com", "token", "newToken")
            every {
                service.changeCredentialName("admin-id", request)
            } returns AtlassianCredentialDto("user@example.com", "newToken")

            mockMvc
                .perform(
                    patch("/api/v1/atlassian/credentials/patch/name")
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
            val request = ChangeAtlassianCredentialTokenRequest("user@example.com", "token", "newSecret")
            every {
                service.changeCredentialToken("admin-id", request)
            } returns AtlassianCredentialDto("user@example.com", "token")

            mockMvc
                .perform(
                    patch("/api/v1/atlassian/credentials/patch/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(adminJwt),
                ).andExpect(status().isOk)
        }
    }
}
