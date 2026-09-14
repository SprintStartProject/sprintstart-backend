package com.sprintstart.sprintstartbackend.onboarding.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.onboarding.model.response.buddy.BuddyActionResponse
import com.sprintstart.sprintstartbackend.onboarding.service.BuddyProposalService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@WebMvcTest(BuddyProposalController::class)
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class BuddyProposalControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockkBean
    private lateinit var buddyProposalService: BuddyProposalService

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private val authId = "test-auth-id"
    private val proposalId = UUID.randomUUID()

    private fun jwtWithRoles(vararg roles: String): JwtRequestPostProcessor =
        jwt()
            .jwt { it.subject(authId).claim("realm_access", mapOf("roles" to roles.toList())) }
            .authorities(roles.map { SimpleGrantedAuthority("ROLE_$it") })

    /**
     * Confirm is a suspend handler, so Spring dispatches it asynchronously. A single-step
     * `.andExpect(status())` would pass a role denial straight through; the two steps are what assert it.
     */
    private fun confirmAs(vararg roles: String): MvcResult {
        val started = mockMvc
            .perform(post("/api/v1/onboarding/me/buddy/proposals/$proposalId/confirm").with(jwtWithRoles(*roles)))
            .andExpect(request().asyncStarted())
            .andReturn()
        return started
    }

    @Test
    fun `confirm returns the outcome for the caller's proposal`() {
        coEvery { buddyProposalService.confirm(authId, proposalId) } returns
            BuddyActionResponse(ok = true, message = "Dismissed the question.")

        mockMvc
            .perform(asyncDispatch(confirmAs("USER")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ok").value(true))
            .andExpect(jsonPath("$.message").value("Dismissed the question."))
    }

    @Test
    fun `confirm returns a refusal as ok false, not as an error`() {
        coEvery { buddyProposalService.confirm(authId, proposalId) } returns
            BuddyActionResponse(ok = false, message = "This was already done.")

        mockMvc
            .perform(asyncDispatch(confirmAs("USER")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ok").value(false))
    }

    @Test
    fun `confirm returns 404 for a proposal that is not the caller's`() {
        coEvery { buddyProposalService.confirm(authId, proposalId) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND, "No proposal")

        mockMvc
            .perform(asyncDispatch(confirmAs("USER")))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `confirm returns 403 without the USER role and runs nothing`() {
        mockMvc
            .perform(asyncDispatch(confirmAs("PM")))
            .andExpect(status().isForbidden)

        coVerify(exactly = 0) { buddyProposalService.confirm(any(), any()) }
    }

    @Test
    fun `confirm returns 401 when not authenticated`() {
        mockMvc
            .perform(post("/api/v1/onboarding/me/buddy/proposals/$proposalId/confirm"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `dismiss returns the outcome for the caller's proposal`() {
        every { buddyProposalService.dismiss(authId, proposalId) } returns
            BuddyActionResponse(ok = true, message = "Dismissed — nothing changed.")

        mockMvc
            .perform(post("/api/v1/onboarding/me/buddy/proposals/$proposalId/dismiss").with(jwtWithRoles("USER")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ok").value(true))
    }

    @Test
    fun `dismiss returns 403 without the USER role`() {
        mockMvc
            .perform(post("/api/v1/onboarding/me/buddy/proposals/$proposalId/dismiss").with(jwtWithRoles("PM")))
            .andExpect(status().isForbidden)
    }
}
