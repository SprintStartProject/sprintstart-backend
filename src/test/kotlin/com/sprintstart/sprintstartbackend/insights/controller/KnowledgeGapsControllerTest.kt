package com.sprintstart.sprintstartbackend.insights.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.insights.model.dto.request.SetComponentOwnersRequest
import com.sprintstart.sprintstartbackend.insights.model.dto.response.KnowledgeGapOwnerResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.KnowledgeGapResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.KnowledgeGapsOverviewResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.RefreshKnowledgeGapsResponse
import com.sprintstart.sprintstartbackend.insights.service.KnowledgeGapsService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@WebMvcTest(KnowledgeGapsController::class)
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class KnowledgeGapsControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockkBean
    private lateinit var knowledgeGapsService: KnowledgeGapsService

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private val gapId = UUID.randomUUID()
    private val objectMapper = jacksonObjectMapper()

    private fun jwtWithRoles(vararg roles: String): JwtRequestPostProcessor {
        return jwt()
            .jwt { jwt ->
                jwt.subject("test-auth-id")
                jwt.claim("realm_access", mapOf("roles" to roles.toList()))
            }.authorities(roles.map { role -> SimpleGrantedAuthority("ROLE_$role") })
    }

    private val pmJwt = jwtWithRoles("PM")
    private val adminJwt = jwtWithRoles("ADMIN")
    private val userJwt = jwtWithRoles("USER")

    private fun buildGap() = KnowledgeGapResponse(
        id = gapId,
        component = "auth-service",
        missingTypes = listOf("runbook", "adr"),
        presentTypes = listOf("readme"),
        lastIngested = Instant.parse("2025-05-01T00:00:00Z"),
        firstIngested = Instant.parse("2025-01-10T00:00:00Z"),
        refreshedAt = Instant.parse("2025-05-02T00:00:00Z"),
        owners = emptyList(),
        severity = "high",
    )

    // ========================== Overview ==========================

    @Test
    fun `getKnowledgeGaps should return 200 and gaps for a PM`() {
        every { knowledgeGapsService.getKnowledgeGaps() } returns KnowledgeGapsOverviewResponse(listOf(buildGap()))

        mockMvc
            .perform(get("/api/v1/insights/knowledge-gaps").with(pmJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { knowledgeGapsService.getKnowledgeGaps() }
    }

    @Test
    fun `getKnowledgeGaps should return 200 for an admin`() {
        every { knowledgeGapsService.getKnowledgeGaps() } returns KnowledgeGapsOverviewResponse(listOf(buildGap()))

        mockMvc
            .perform(get("/api/v1/insights/knowledge-gaps").with(adminJwt))
            .andExpect(status().isOk)
    }

    @Test
    fun `getKnowledgeGaps should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/insights/knowledge-gaps"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getKnowledgeGaps should return 403 for a non-PM role`() {
        mockMvc
            .perform(get("/api/v1/insights/knowledge-gaps").with(userJwt))
            .andExpect(status().isForbidden)
    }

    // ========================== Detail ==========================

    @Test
    fun `getKnowledgeGap should return 200 and detail for a PM`() {
        every { knowledgeGapsService.getKnowledgeGap(gapId) } returns buildGap()

        mockMvc
            .perform(get("/api/v1/insights/knowledge-gaps/$gapId").with(pmJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { knowledgeGapsService.getKnowledgeGap(gapId) }
    }

    @Test
    fun `getKnowledgeGap should return 404 when the gap does not exist`() {
        every { knowledgeGapsService.getKnowledgeGap(gapId) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge gap with id $gapId not found")

        mockMvc
            .perform(get("/api/v1/insights/knowledge-gaps/$gapId").with(pmJwt))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `getKnowledgeGap should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/insights/knowledge-gaps/$gapId"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getKnowledgeGap should return 403 for a non-PM role`() {
        mockMvc
            .perform(get("/api/v1/insights/knowledge-gaps/$gapId").with(userJwt))
            .andExpect(status().isForbidden)
    }

    // ========================== Refresh ==========================

    @Test
    fun `refreshKnowledgeGaps should return 200 and the gap count for a PM`() {
        coEvery { knowledgeGapsService.refreshKnowledgeGaps() } returns RefreshKnowledgeGapsResponse(gapCount = 5)

        val asyncResult = mockMvc
            .perform(post("/api/v1/insights/knowledge-gaps/refresh").with(pmJwt))
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc
            .perform(asyncDispatch(asyncResult))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        coVerify(exactly = 1) { knowledgeGapsService.refreshKnowledgeGaps() }
    }

    @Test
    fun `refreshKnowledgeGaps should return 401 when not authenticated`() {
        mockMvc
            .perform(post("/api/v1/insights/knowledge-gaps/refresh"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `refreshKnowledgeGaps should return 403 for a non-PM role`() {
        // The endpoint is a coroutine handler, so the security denial surfaces through the async
        // dispatch rather than on the initial response.
        val asyncResult = mockMvc
            .perform(post("/api/v1/insights/knowledge-gaps/refresh").with(userJwt))
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc
            .perform(asyncDispatch(asyncResult))
            .andExpect(status().isForbidden)

        coVerify(exactly = 0) { knowledgeGapsService.refreshKnowledgeGaps() }
    }

    // ========================== Component owners ==========================

    private fun buildOwner() = KnowledgeGapOwnerResponse(
        id = UUID.randomUUID().toString(),
        username = "jdoe",
        firstname = "John",
        lastname = "Doe",
        role = "Backend Developer",
    )

    @Test
    fun `getComponentOwners should return 200 and owners for a PM`() {
        every { knowledgeGapsService.getComponentOwners("owner/repo") } returns listOf(buildOwner())

        mockMvc
            .perform(
                get("/api/v1/insights/knowledge-gaps/component-owners")
                    .param("component", "owner/repo")
                    .with(pmJwt),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { knowledgeGapsService.getComponentOwners("owner/repo") }
    }

    @Test
    fun `getComponentOwners should return 403 for a non-PM role`() {
        mockMvc
            .perform(
                get("/api/v1/insights/knowledge-gaps/component-owners")
                    .param("component", "owner/repo")
                    .with(userJwt),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `setComponentOwners should return 200 and the resolved owners for a PM`() {
        val request = SetComponentOwnersRequest(component = "owner/repo", userIds = listOf(UUID.randomUUID()))
        every { knowledgeGapsService.setComponentOwners(request) } returns listOf(buildOwner())

        mockMvc
            .perform(
                put("/api/v1/insights/knowledge-gaps/component-owners")
                    .with(pmJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { knowledgeGapsService.setComponentOwners(request) }
    }

    @Test
    fun `setComponentOwners should return 400 when the component is blank`() {
        mockMvc
            .perform(
                put("/api/v1/insights/knowledge-gaps/component-owners")
                    .with(pmJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"component": "", "userIds": []}"""),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `setComponentOwners should return 403 for a non-PM role`() {
        mockMvc
            .perform(
                put("/api/v1/insights/knowledge-gaps/component-owners")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"component": "owner/repo", "userIds": []}"""),
            ).andExpect(status().isForbidden)
    }
}
