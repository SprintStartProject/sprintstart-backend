package com.sprintstart.sprintstartbackend.ingestion.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactAiIndexStatus
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactAiStatusItemResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactAiStatusResponse
import com.sprintstart.sprintstartbackend.ingestion.service.ArtifactAiStatusService
import com.sprintstart.sprintstartbackend.ingestion.service.ArtifactQueryService
import com.sprintstart.sprintstartbackend.ingestion.service.ArtifactService
import io.mockk.coEvery
import io.mockk.coVerify
import org.hamcrest.Matchers.hasKey
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Web-layer contract of `GET /projects/{projectId}/artifacts/ai-status`: role guard, id cap and
 * the JSON shape the Knowledge Base chip reads. Service behaviour has its own test.
 * [SecurityConfig] is imported so `@PreAuthorize` is live; without it the slice has no method
 * security and the role guard would go untested.
 */
@WebMvcTest(controllers = [ArtifactController::class])
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class ArtifactAiStatusControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockkBean
    private lateinit var artifactAiStatusService: ArtifactAiStatusService

    @MockkBean
    private lateinit var artifactQueryService: ArtifactQueryService

    @MockkBean
    private lateinit var artifactService: ArtifactService

    private val projectId = UUID.randomUUID()
    private val path = "/api/v1/projects/$projectId/artifacts/ai-status"
    private val userJwt = jwt().jwt { it.subject("user-1") }.authorities(SimpleGrantedAuthority("ROLE_USER"))

    @Test
    fun `returns aiAvailable and one item per visible id in the contract shape`() {
        val indexed = UUID.randomUUID()
        val unknown = UUID.randomUUID()
        coEvery { artifactAiStatusService.getAiStatus("user-1", projectId, listOf(indexed, unknown)) } returns
            ArtifactAiStatusResponse(
                aiAvailable = true,
                items = listOf(
                    ArtifactAiStatusItemResponse(indexed, ArtifactAiIndexStatus.INDEXED, "2026-09-20T10:00:00Z", 7),
                    ArtifactAiStatusItemResponse(unknown, ArtifactAiIndexStatus.UNKNOWN, null, null),
                ),
            )

        performAsync(get("$path?ids=$indexed&ids=$unknown").with(userJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.aiAvailable").value(true))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].artifactId").value(indexed.toString()))
            .andExpect(jsonPath("$.items[0].status").value("INDEXED"))
            .andExpect(jsonPath("$.items[0].updatedAt").value("2026-09-20T10:00:00Z"))
            .andExpect(jsonPath("$.items[0].chunkCount").value(7))
            .andExpect(jsonPath("$.items[1].status").value("UNKNOWN"))
            .andExpect(jsonPath("$.items[1]", hasKey("updatedAt")))
            .andExpect(jsonPath("$.items[1].updatedAt").isEmpty)
            .andExpect(jsonPath("$.items[1].chunkCount").isEmpty)
    }

    @Test
    fun `treats a missing ids parameter as an empty request`() {
        coEvery { artifactAiStatusService.getAiStatus("user-1", projectId, emptyList()) } returns
            ArtifactAiStatusResponse(aiAvailable = true, items = emptyList())

        performAsync(get(path).with(userJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.aiAvailable").value(true))
            .andExpect(jsonPath("$.items").isEmpty)
    }

    @Test
    fun `rejects more than 100 ids with 400 before asking the service`() {
        val query = (1..101).joinToString("&") { "ids=${UUID.randomUUID()}" }

        performAsync(get("$path?$query").with(userJwt))
            .andExpect(status().isBadRequest)

        coVerify(exactly = 0) { artifactAiStatusService.getAiStatus(any(), any(), any()) }
    }

    @Test
    fun `accepts exactly 100 ids`() {
        val ids = List(100) { UUID.randomUUID() }
        coEvery { artifactAiStatusService.getAiStatus("user-1", projectId, ids) } returns
            ArtifactAiStatusResponse(aiAvailable = false, items = emptyList())

        performAsync(get("$path?${ids.joinToString("&") { "ids=$it" }}").with(userJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.aiAvailable").value(false))
    }

    @Test
    fun `passes the project access denial through as 403`() {
        val id = UUID.randomUUID()
        coEvery { artifactAiStatusService.getAiStatus("user-1", projectId, listOf(id)) } throws
            ResponseStatusException(HttpStatus.FORBIDDEN, "No access to project with id $projectId")

        performAsync(get("$path?ids=$id").with(userJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `rejects a caller without the USER role`() {
        // Method security on a suspend handler is evaluated inside the coroutine, hence async.
        performAsync(get("$path?ids=${UUID.randomUUID()}").with(jwt().jwt { it.subject("user-1") }))
            .andExpect(status().isForbidden)

        coVerify(exactly = 0) { artifactAiStatusService.getAiStatus(any(), any(), any()) }
    }

    @Test
    fun `rejects a malformed id with 400`() {
        mockMvc
            .perform(get("$path?ids=not-a-uuid").with(userJwt))
            .andExpect(status().isBadRequest)
    }

    /** Suspend handlers answer through an async dispatch; this runs both legs. */
    private fun performAsync(builder: MockHttpServletRequestBuilder): ResultActions {
        val started = mockMvc.perform(builder).andExpect(request().asyncStarted()).andReturn()
        return mockMvc.perform(asyncDispatch(started))
    }
}
