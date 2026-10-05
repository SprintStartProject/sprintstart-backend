package com.sprintstart.sprintstartbackend.connectors.notion.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionAuthenticationException
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.request.AddNotionCredentialRequest
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.request.ChangeNotionCredentialNameRequest
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.request.ChangeNotionCredentialTokenRequest
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.request.DeleteNotionCredentialRequest
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.response.NotionCredentialResponse
import com.sprintstart.sprintstartbackend.connectors.notion.service.NotionCredentialService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@WebMvcTest(controllers = [NotionCredentialController::class])
@AutoConfigureMockMvc
@Import(NotionExceptionHandler::class, SecurityConfig::class)
internal class NotionCredentialControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var credentialService: NotionCredentialService

    private val objectMapper = jacksonObjectMapper()
    private val adminJwt = jwt()
        .jwt { token -> token.subject("admin-id") }
        .authorities(SimpleGrantedAuthority("ROLE_ADMIN"))
    private val pmJwt = jwt()
        .jwt { token -> token.subject("pm-id") }
        .authorities(SimpleGrantedAuthority("ROLE_PM"))
    private val userJwt = jwt()
        .jwt { token -> token.subject("user-id") }
        .authorities(SimpleGrantedAuthority("ROLE_USER"))

    @Test
    fun `PM lists only their safe credential responses`() {
        every { credentialService.getCredentials("pm-id") } returns listOf(credentialResponse())

        mockMvc
            .perform(get(BASE_PATH).with(pmJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("team-token"))
            .andExpect(jsonPath("$[0].token").doesNotExist())

        verify(exactly = 1) { credentialService.getCredentials("pm-id") }
    }

    @Test
    fun `ADMIN adds a credential and receives created response`() {
        val request = AddNotionCredentialRequest("team-token", "secret-token")
        coEvery { credentialService.addCredential("admin-id", request) } returns credentialResponse()

        val asyncResult = mockMvc
            .perform(
                post(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .with(adminJwt),
            ).andExpect(request().asyncStarted())
            .andReturn()

        mockMvc
            .perform(asyncDispatch(asyncResult))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("team-token"))
            .andExpect(jsonPath("$.token").doesNotExist())

        coVerify(exactly = 1) { credentialService.addCredential("admin-id", request) }
    }

    @Test
    fun `rejected Notion token returns 422 without looking like an expired session`() {
        val request = AddNotionCredentialRequest("team-token", "rejected-token")
        coEvery { credentialService.addCredential("admin-id", request) } throws
            NotionAuthenticationException("validating the connection")

        val asyncResult = mockMvc
            .perform(
                post(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .with(adminJwt),
            ).andExpect(request().asyncStarted())
            .andReturn()

        mockMvc
            .perform(asyncDispatch(asyncResult))
            .andExpect(status().`is`(422))
            .andExpect(jsonPath("$.code").value("NOTION_AUTHENTICATION_FAILED"))
    }

    @Test
    fun `PM changes token using their own auth subject`() {
        val request = ChangeNotionCredentialTokenRequest("team-token", "new-secret")
        coEvery { credentialService.changeToken("pm-id", request) } returns credentialResponse()

        val asyncResult = mockMvc
            .perform(
                put("$BASE_PATH/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .with(pmJwt),
            ).andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(asyncResult)).andExpect(status().isOk)
        coVerify(exactly = 1) { credentialService.changeToken("pm-id", request) }
    }

    @Test
    fun `ADMIN renames and deletes credentials`() {
        val renameRequest = ChangeNotionCredentialNameRequest("team-token", "renamed-token")
        val deleteRequest = DeleteNotionCredentialRequest("renamed-token")
        every { credentialService.changeName("admin-id", renameRequest) } returns
            credentialResponse().copy(name = "renamed-token")
        every { credentialService.deleteCredential("admin-id", deleteRequest) } returns Unit

        mockMvc
            .perform(
                put("$BASE_PATH/name")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(renameRequest))
                    .with(adminJwt),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("renamed-token"))

        mockMvc
            .perform(
                delete(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(deleteRequest))
                    .with(adminJwt),
            ).andExpect(status().isNoContent)

        verify(exactly = 1) { credentialService.changeName("admin-id", renameRequest) }
        verify(exactly = 1) { credentialService.deleteCredential("admin-id", deleteRequest) }
    }

    @Test
    fun `USER cannot access credential endpoints`() {
        mockMvc.perform(get(BASE_PATH).with(userJwt)).andExpect(status().isForbidden)

        verify(exactly = 0) { credentialService.getCredentials(any()) }
    }

    @Test
    fun `invalid credential request returns bad request without invoking service`() {
        val invalidRequest = """{"name":"","token":""}"""

        mockMvc
            .perform(
                post(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidRequest)
                    .with(adminJwt),
            ).andExpect(status().isBadRequest)

        coVerify(exactly = 0) { credentialService.addCredential(any(), any()) }
    }

    private fun credentialResponse(): NotionCredentialResponse {
        val timestamp = Instant.parse("2026-09-27T10:00:00Z")
        return NotionCredentialResponse(
            name = "team-token",
            createdAt = timestamp,
            updatedAt = timestamp,
        )
    }

    private companion object {
        const val BASE_PATH = "/api/v1/notion/credentials"
    }
}
