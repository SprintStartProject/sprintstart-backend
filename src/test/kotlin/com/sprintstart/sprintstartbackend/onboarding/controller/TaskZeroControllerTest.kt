package com.sprintstart.sprintstartbackend.onboarding.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.onboarding.model.response.starterwork.MyTaskZeroResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.starterwork.StarterWorkTaskProposalResponse
import com.sprintstart.sprintstartbackend.onboarding.service.TaskZeroService
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.Optional
import java.util.UUID

@WebMvcTest(TaskZeroController::class)
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class TaskZeroControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockkBean
    private lateinit var taskZeroService: TaskZeroService

    @MockkBean
    private lateinit var userApi: UserApi

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private val authId = "test-auth-id"
    private val userId = UUID.randomUUID()
    private val projectId = UUID.randomUUID()
    private val proposalId = UUID.randomUUID()

    private fun jwtWithSubject(subject: String, vararg roles: String): JwtRequestPostProcessor =
        jwt()
            .jwt { jwt ->
                jwt.subject(subject)
                jwt.claim("realm_access", mapOf("roles" to roles.toList()))
            }.authorities(roles.map { SimpleGrantedAuthority("ROLE_$it") })

    private val userJwt = jwtWithSubject(authId, "USER")
    private val pmJwt = jwtWithSubject(authId, "PM")

    @Test
    fun `getMyTaskZero returns the caller's task-zero state`() {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { taskZeroService.getForHire(userId, projectId) } returns
            MyTaskZeroResponse(task = null, assignedAt = null, noneAvailable = true, loopProven = false)

        mockMvc
            .perform(get("/api/v1/onboarding/me/task-zero").param("projectId", projectId.toString()).with(userJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.noneAvailable").value(true))
    }

    @Test
    fun `getMyTaskZero requires authentication`() {
        mockMvc
            .perform(get("/api/v1/onboarding/me/task-zero").param("projectId", projectId.toString()))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `setEligibility is allowed for a PM`() {
        every { taskZeroService.setEligibility(proposalId, true) } returns
            StarterWorkTaskProposalResponse(
                id = proposalId,
                sourceId = "github:org/repo:ISSUE:1",
                title = "Fix a typo",
                summary = null,
                rationale = null,
                sourceUrl = null,
                competencyKeys = emptyList(),
                status = ProposalStatus.LIVE,
                taskZeroEligible = true,
            )

        mockMvc
            .perform(
                post("/api/v1/onboarding/starter-work/$proposalId/task-zero")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"eligible":true}""")
                    .with(pmJwt),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.taskZeroEligible").value(true))

        verify(exactly = 1) { taskZeroService.setEligibility(proposalId, true) }
    }

    @Test
    fun `setEligibility is forbidden for a plain user`() {
        mockMvc
            .perform(
                post("/api/v1/onboarding/starter-work/$proposalId/task-zero")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"eligible":true}""")
                    .with(userJwt),
            ).andExpect(status().isForbidden)
    }
}
