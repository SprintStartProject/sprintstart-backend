package com.sprintstart.sprintstartbackend.onboarding.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.onboarding.model.request.phase.CreateOnboardingPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.phase.UpdateOnboardingPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.CreateOnboardingPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhasesResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.UpdateOnboardingPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingPhaseService
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@WebMvcTest(OnboardingPhaseController::class)
@Import(
    SecurityConfig::class,
)
@AutoConfigureMockMvc
class OnboardingPhaseControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val objectMapper = jacksonObjectMapper()

    @MockkBean
    private lateinit var onboardingPhaseService: OnboardingPhaseService

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private val userId = UUID.randomUUID()
    private val phaseId = UUID.randomUUID()
    private val pathId = UUID.randomUUID()

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
    fun `getOnboardingPhasesForMe should return 200 and list of phases`() {
        val response = listOf(
            GetOnboardingPhasesResponse(
                id = phaseId,
                pathId = pathId,
                position = 1,
                title = "Phase 1",
                description = "desc",
            ),
        )

        every { onboardingPhaseService.getOnboardingPhasesForMe(authId) } returns response

        mockMvc
            .perform(get("/api/v1/onboarding/me/path/phases").with(userJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingPhaseService.getOnboardingPhasesForMe(authId) }
    }

    @Test
    fun `getOnboardingPhasesForMe should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/onboarding/me/path/phases"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getOnboardingPhasesForMe should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(get("/api/v1/onboarding/me/path/phases").with(noUserRoleJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `createOnboardingPhaseForMe should return 201 and created phase`() {
        val request = CreateOnboardingPhaseRequest(position = 1, title = "Phase 1", description = "desc")
        val response =
            CreateOnboardingPhaseResponse(
                id = phaseId,
                pathId = pathId,
                position = 1,
                title = "Phase 1",
                description = "desc",
            )

        every { onboardingPhaseService.createOnboardingPhaseForMe(authId, request) } returns response

        mockMvc
            .perform(
                post("/api/v1/onboarding/me/path/phases")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isCreated)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingPhaseService.createOnboardingPhaseForMe(authId, request) }
    }

    @Test
    fun `createOnboardingPhaseForMe should return 401 when not authenticated`() {
        val request = CreateOnboardingPhaseRequest(position = 1, title = "Phase 1", description = "desc")

        mockMvc
            .perform(
                post("/api/v1/onboarding/me/path/phases")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `createOnboardingPhaseForMe should return 403 when authenticated with wrong role`() {
        val request = CreateOnboardingPhaseRequest(position = 1, title = "Phase 1", description = "desc")

        mockMvc
            .perform(
                post("/api/v1/onboarding/me/path/phases")
                    .with(noUserRoleJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `getOnboardingPhaseForMe should return 200 and phase`() {
        val response =
            GetOnboardingPhaseResponse(
                id = phaseId,
                pathId = pathId,
                position = 1,
                title = "Phase 1",
                description = "desc",
                steps = emptyList(),
            )

        every { onboardingPhaseService.getOnboardingPhaseForMe(authId, phaseId) } returns response

        mockMvc
            .perform(get("/api/v1/onboarding/me/path/phases/$phaseId").with(userJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingPhaseService.getOnboardingPhaseForMe(authId, phaseId) }
    }

    @Test
    fun `getOnboardingPhaseForMe should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/onboarding/me/path/phases/$phaseId"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getOnboardingPhaseForMe should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(get("/api/v1/onboarding/me/path/phases/$phaseId").with(noUserRoleJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `getOnboardingPhaseForMe should return 404 when not found`() {
        every { onboardingPhaseService.getOnboardingPhaseForMe(authId, phaseId) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(get("/api/v1/onboarding/me/path/phases/$phaseId").with(userJwt))
            .andExpect(status().isNotFound)

        verify(exactly = 1) { onboardingPhaseService.getOnboardingPhaseForMe(authId, phaseId) }
    }

    @Test
    fun `updateOnboardingPhaseForMe should return 200 and updated phase`() {
        val request = UpdateOnboardingPhaseRequest(position = 2, title = "Updated", description = "updated desc")
        val response =
            UpdateOnboardingPhaseResponse(
                id = phaseId,
                pathId = pathId,
                position = 2,
                title = "Updated",
                description = "updated desc",
            )

        every { onboardingPhaseService.updateOnboardingPhaseForMe(authId, phaseId, request) } returns response

        mockMvc
            .perform(
                put("/api/v1/onboarding/me/path/phases/$phaseId")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingPhaseService.updateOnboardingPhaseForMe(authId, phaseId, request) }
    }

    @Test
    fun `updateOnboardingPhaseForMe should return 401 when not authenticated`() {
        val request = UpdateOnboardingPhaseRequest(position = 2, title = "Updated", description = "updated desc")

        mockMvc
            .perform(
                put("/api/v1/onboarding/me/path/phases/$phaseId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `updateOnboardingPhaseForMe should return 403 when authenticated with wrong role`() {
        val request = UpdateOnboardingPhaseRequest(position = 2, title = "Updated", description = "updated desc")

        mockMvc
            .perform(
                put("/api/v1/onboarding/me/path/phases/$phaseId")
                    .with(noUserRoleJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `deleteOnboardingPhaseForMe should return 204`() {
        every { onboardingPhaseService.deleteOnboardingPhaseForMe(authId, phaseId) } just Runs

        mockMvc
            .perform(delete("/api/v1/onboarding/me/path/phases/$phaseId").with(userJwt))
            .andExpect(status().isNoContent)

        verify(exactly = 1) { onboardingPhaseService.deleteOnboardingPhaseForMe(authId, phaseId) }
    }

    @Test
    fun `deleteOnboardingPhaseForMe should return 401 when not authenticated`() {
        mockMvc
            .perform(delete("/api/v1/onboarding/me/path/phases/$phaseId"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `deleteOnboardingPhaseForMe should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(delete("/api/v1/onboarding/me/path/phases/$phaseId").with(noUserRoleJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `deleteOnboardingPhaseForMe should return 404 when not found`() {
        every { onboardingPhaseService.deleteOnboardingPhaseForMe(authId, phaseId) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(delete("/api/v1/onboarding/me/path/phases/$phaseId").with(userJwt))
            .andExpect(status().isNotFound)

        verify(exactly = 1) { onboardingPhaseService.deleteOnboardingPhaseForMe(authId, phaseId) }
    }

    // ========================== Admin endpoints ==========================

    @Test
    fun `getAllOnboardingPhasesForUser should return 200 and list of phases`() {
        val response = listOf(
            GetOnboardingPhasesResponse(
                id = phaseId,
                pathId = pathId,
                position = 1,
                title = "Phase 1",
                description = "desc",
            ),
        )

        every { onboardingPhaseService.getOnboardingPhasesForUser(userId) } returns response

        mockMvc
            .perform(get("/api/v1/onboarding/users/$userId/path/phases").with(adminJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingPhaseService.getOnboardingPhasesForUser(userId) }
    }

    @Test
    fun `getAllOnboardingPhasesForUser should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/onboarding/users/$userId/path/phases"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getAllOnboardingPhasesForUser should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(get("/api/v1/onboarding/users/$userId/path/phases").with(userJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `createOnboardingPhaseForUser should return 201 and created phase`() {
        val request = CreateOnboardingPhaseRequest(position = 1, title = "Phase 1", description = "desc")
        val response =
            CreateOnboardingPhaseResponse(
                id = phaseId,
                pathId = pathId,
                position = 1,
                title = "Phase 1",
                description = "desc",
            )

        every { onboardingPhaseService.createOnboardingPhaseForUserId(userId, request) } returns response

        mockMvc
            .perform(
                post("/api/v1/onboarding/users/$userId/path/phases")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isCreated)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingPhaseService.createOnboardingPhaseForUserId(userId, request) }
    }

    @Test
    fun `createOnboardingPhaseForUser should return 401 when not authenticated`() {
        val request = CreateOnboardingPhaseRequest(position = 1, title = "Phase 1", description = "desc")

        mockMvc
            .perform(
                post("/api/v1/onboarding/users/$userId/path/phases")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `createOnboardingPhaseForUser should return 403 when authenticated with wrong role`() {
        val request = CreateOnboardingPhaseRequest(position = 1, title = "Phase 1", description = "desc")

        mockMvc
            .perform(
                post("/api/v1/onboarding/users/$userId/path/phases")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `getOnboardingPhaseById should return 200 and phase`() {
        val response =
            GetOnboardingPhaseResponse(
                id = phaseId,
                pathId = pathId,
                position = 1,
                title = "Phase 1",
                description = "desc",
                steps = emptyList(),
            )

        every { onboardingPhaseService.getOnboardingPhaseById(phaseId) } returns response

        mockMvc
            .perform(get("/api/v1/onboarding/phases/$phaseId").with(adminJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingPhaseService.getOnboardingPhaseById(phaseId) }
    }

    @Test
    fun `getOnboardingPhaseById should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/onboarding/phases/$phaseId"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getOnboardingPhaseById should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(get("/api/v1/onboarding/phases/$phaseId").with(userJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `getOnboardingPhaseById should return 404 when not found`() {
        every { onboardingPhaseService.getOnboardingPhaseById(phaseId) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(get("/api/v1/onboarding/phases/$phaseId").with(adminJwt))
            .andExpect(status().isNotFound)

        verify(exactly = 1) { onboardingPhaseService.getOnboardingPhaseById(phaseId) }
    }

    @Test
    fun `deleteOnboardingPhaseById should return 204`() {
        every { onboardingPhaseService.deleteOnboardingPhaseById(phaseId) } just Runs

        mockMvc
            .perform(delete("/api/v1/onboarding/phases/$phaseId").with(adminJwt))
            .andExpect(status().isNoContent)

        verify(exactly = 1) { onboardingPhaseService.deleteOnboardingPhaseById(phaseId) }
    }

    @Test
    fun `deleteOnboardingPhaseById should return 401 when not authenticated`() {
        mockMvc
            .perform(delete("/api/v1/onboarding/phases/$phaseId"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `deleteOnboardingPhaseById should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(delete("/api/v1/onboarding/phases/$phaseId").with(userJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `deleteOnboardingPhaseById should return 404 when not found`() {
        every { onboardingPhaseService.deleteOnboardingPhaseById(phaseId) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(delete("/api/v1/onboarding/phases/$phaseId").with(adminJwt))
            .andExpect(status().isNotFound)

        verify(exactly = 1) { onboardingPhaseService.deleteOnboardingPhaseById(phaseId) }
    }
}
