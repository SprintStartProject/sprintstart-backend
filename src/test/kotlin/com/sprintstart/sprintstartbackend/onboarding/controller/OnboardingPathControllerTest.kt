package com.sprintstart.sprintstartbackend.onboarding.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathResponse
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingPathService
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@WebMvcTest(OnboardingPathController::class)
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class OnboardingPathControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockkBean
    private lateinit var onboardingPathService: OnboardingPathService

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private val pathId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    private val authId = "test-auth-id"
    private val adminAuthId = "test-admin-auth-id"

    private fun jwtWithSubject(
        subject: String,
        vararg roles: String,
    ): JwtRequestPostProcessor {
        return jwt()
            .jwt { jwt ->
                jwt.subject(subject)
                jwt.claim(
                    "realm_access",
                    mapOf("roles" to roles.toList()),
                )
            }.authorities(
                roles.map { role -> SimpleGrantedAuthority("ROLE_$role") },
            )
    }

    private val userJwt = jwtWithSubject(authId, "USER")
    private val adminJwt = jwtWithSubject(adminAuthId, "USER", "ADMIN")
    private val noUserRoleJwt = jwtWithSubject(authId, "NONE")

    // ========================== /me endpoints ==========================

    @Test
    fun `getOnboardingPathForMe should return 200 and path`() {
        val response = GetOnboardingPathForUserResponse(
            id = pathId,
            userId = userId,
            createdAt = Instant.now(),
            phases = emptyList(),
        )

        every { onboardingPathService.getOnboardingPathForMe(authId) } returns response

        mockMvc
            .perform(
                get("/api/v1/onboarding/me/path")
                    .with(userJwt),
            ).andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))

        verify(exactly = 1) {
            onboardingPathService.getOnboardingPathForMe(authId)
        }
    }

    @Test
    fun `getOnboardingPathForMe should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/onboarding/me/path"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getOnboardingPathForMe should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(
                get("/api/v1/onboarding/me/path")
                    .with(noUserRoleJwt),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `getOnboardingPathForMe should return 404 when not found`() {
        every { onboardingPathService.getOnboardingPathForMe(authId) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(
                get("/api/v1/onboarding/me/path")
                    .with(userJwt),
            ).andExpect(status().isNotFound)

        verify(exactly = 1) {
            onboardingPathService.getOnboardingPathForMe(authId)
        }
    }

    @Test
    fun `deleteOnboardingPathForMe should return 204`() {
        every { onboardingPathService.deleteOnboardingPathForMe(authId) } just Runs

        mockMvc
            .perform(
                delete("/api/v1/onboarding/me/path")
                    .with(userJwt),
            ).andExpect(status().isNoContent)

        verify(exactly = 1) {
            onboardingPathService.deleteOnboardingPathForMe(authId)
        }
    }

    @Test
    fun `deleteOnboardingPathForMe should return 401 when not authenticated`() {
        mockMvc
            .perform(delete("/api/v1/onboarding/me/path"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `deleteOnboardingPathForMe should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(
                delete("/api/v1/onboarding/me/path")
                    .with(noUserRoleJwt),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `deleteOnboardingPathForMe should return 404 when not found`() {
        every { onboardingPathService.deleteOnboardingPathForMe(authId) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(
                delete("/api/v1/onboarding/me/path")
                    .with(userJwt),
            ).andExpect(status().isNotFound)

        verify(exactly = 1) {
            onboardingPathService.deleteOnboardingPathForMe(authId)
        }
    }

    // ========================== Admin endpoints ==========================

    @Test
    fun `getOnboardingPathForUserId should return 200 and path overview`() {
        val response = GetOnboardingPathResponse(
            id = pathId,
            userId = userId,
            createdAt = Instant.now(),
            phases = emptyList(),
        )

        every { onboardingPathService.getOnboardingPathByUserId(userId) } returns response

        mockMvc
            .perform(
                get("/api/v1/onboarding/users/$userId/path")
                    .with(adminJwt),
            ).andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))

        verify(exactly = 1) {
            onboardingPathService.getOnboardingPathByUserId(userId)
        }
    }

    @Test
    fun `getOnboardingPathForUserId should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/onboarding/users/$userId/path"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getOnboardingPathForUserId should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(
                get("/api/v1/onboarding/users/$userId/path")
                    .with(userJwt),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `getOnboardingPathForUserId should return 404 when not found`() {
        every { onboardingPathService.getOnboardingPathByUserId(userId) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(
                get("/api/v1/onboarding/users/$userId/path")
                    .with(adminJwt),
            ).andExpect(status().isNotFound)

        verify(exactly = 1) {
            onboardingPathService.getOnboardingPathByUserId(userId)
        }
    }

    @Test
    fun `deleteOnboardingPathByUserId should return 204`() {
        every { onboardingPathService.deleteOnboardingPathByUserId(userId) } just Runs

        mockMvc
            .perform(
                delete("/api/v1/onboarding/users/$userId/path")
                    .with(adminJwt),
            ).andExpect(status().isNoContent)

        verify(exactly = 1) {
            onboardingPathService.deleteOnboardingPathByUserId(userId)
        }
    }

    @Test
    fun `deleteOnboardingPathByUserId should return 401 when not authenticated`() {
        mockMvc
            .perform(delete("/api/v1/onboarding/users/$userId/path"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `deleteOnboardingPathByUserId should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(
                delete("/api/v1/onboarding/users/$userId/path")
                    .with(userJwt),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `deleteOnboardingPathByUserId should return 404 when not found`() {
        every { onboardingPathService.deleteOnboardingPathByUserId(userId) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(
                delete("/api/v1/onboarding/users/$userId/path")
                    .with(adminJwt),
            ).andExpect(status().isNotFound)

        verify(exactly = 1) {
            onboardingPathService.deleteOnboardingPathByUserId(userId)
        }
    }
}
