package com.sprintstart.sprintstartbackend.artifacts.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.artifacts.model.ai.AiArtifactSummaryStreamMessage
import com.sprintstart.sprintstartbackend.artifacts.service.ArtifactSummaryService
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.test.assertEquals

@WebMvcTest(ArtifactSummaryController::class)
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class ArtifactSummaryControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockkBean
    private lateinit var artifactSummaryService: ArtifactSummaryService

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private val artifactId = UUID.randomUUID()

    private fun jwtWithRoles(vararg roles: String): JwtRequestPostProcessor {
        return jwt()
            .jwt { jwt ->
                jwt.subject("test-auth-id")
                jwt.claim("realm_access", mapOf("roles" to roles.toList()))
            }.authorities(roles.map { role -> SimpleGrantedAuthority("ROLE_$role") })
    }

    private val userJwt = jwtWithRoles("USER")

    @Test
    fun `getSummary streams token citation and done events`() {
        val messages = listOf(
            AiArtifactSummaryStreamMessage(type = "token", content = "## Key points"),
            AiArtifactSummaryStreamMessage(
                type = "citation",
                artifactId = artifactId.toString(),
                filename = "README.md",
                sourceUrl = "https://github.com/example/repo",
            ),
            AiArtifactSummaryStreamMessage(type = "done"),
        )
        every { artifactSummaryService.getSummary(artifactId) } returns flowOf(*messages.toTypedArray())

        val asyncResult = mockMvc
            .perform(get("/api/v1/artifacts/$artifactId/summary").with(userJwt))
            .andExpect(request().asyncStarted())
            .andReturn()

        val mvcResult = mockMvc
            .perform(asyncDispatch(asyncResult))
            .andExpect(status().isOk)
            .andReturn()

        val actual = mvcResult.response.contentAsString
            .replace("data:", "")
            .replace("\n", "")

        val expected = messages.joinToString("") { Json.encodeToString(it) }
        assertEquals(expected, actual)

        verify(exactly = 1) { artifactSummaryService.getSummary(artifactId) }
    }

    @Test
    fun `getSummary returns 404 when the artifact does not exist`() {
        every { artifactSummaryService.getSummary(artifactId) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND, "Artifact $artifactId not found")

        val asyncResult = mockMvc
            .perform(get("/api/v1/artifacts/$artifactId/summary").with(userJwt))
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc
            .perform(asyncDispatch(asyncResult))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `getSummary returns 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/artifacts/$artifactId/summary"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getSummary forwards an error event from the service untouched`() {
        every { artifactSummaryService.getSummary(artifactId) } returns flowOf(
            AiArtifactSummaryStreamMessage(type = "error", message = "LLM backend unreachable"),
        )

        val asyncResult = mockMvc
            .perform(get("/api/v1/artifacts/$artifactId/summary").with(userJwt))
            .andExpect(request().asyncStarted())
            .andReturn()

        val mvcResult = mockMvc
            .perform(asyncDispatch(asyncResult))
            .andExpect(status().isOk)
            .andReturn()

        assert(mvcResult.response.contentAsString.contains(""""type":"error""""))
        assert(mvcResult.response.contentAsString.contains("LLM backend unreachable"))
    }
}
