package com.sprintstart.sprintstartbackend.onboarding.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingGenerationRegistry
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingPathService
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingPersonalizationService
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.UserOnboardingProfile
import com.sprintstart.sprintstartbackend.user.external.security.ProjectAuthorization
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Optional
import java.util.UUID

@WebMvcTest(OnboardingPathController::class, ProjectOnboardingPathController::class)
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class OnboardingPathControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockkBean
    private lateinit var onboardingPathService: OnboardingPathService

    @MockkBean
    private lateinit var onboardingPersonalizationService: OnboardingPersonalizationService

    @MockkBean
    private lateinit var onboardingGenerationRegistry: OnboardingGenerationRegistry

    @MockkBean
    private lateinit var userApi: UserApi

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockkBean(name = "projectAuth")
    private lateinit var projectAuthorization: ProjectAuthorization

    private val pathId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val projectId = UUID.randomUUID()

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

    // ========================== /me personalize (project-scoped) ==========================

    @Test
    fun `personalizePath passes the selected project path variable to the generation registry`() {
        every { onboardingGenerationRegistry.status(authId) } returns null
        every { onboardingPathService.hasPathForMe(authId) } returns false
        every { onboardingGenerationRegistry.startOrAttach(authId, projectId) } throws
            ResponseStatusException(HttpStatus.BAD_REQUEST, "rejected")

        mockMvc
            .perform(
                post("/api/v1/projects/$projectId/onboarding/me/path/personalize")
                    .with(userJwt),
            ).andExpect(status().isBadRequest)

        verify(exactly = 1) {
            onboardingGenerationRegistry.startOrAttach(authId, projectId)
        }
    }

    @Test
    fun `personalizePath refuses a member rebuilding a path they already have`() {
        every { onboardingGenerationRegistry.status(authId) } returns null
        every { onboardingPathService.hasPathForMe(authId) } returns true
        every { userApi.canManageProject(authId, projectId) } returns false

        mockMvc
            .perform(
                post("/api/v1/projects/$projectId/onboarding/me/path/personalize")
                    .with(userJwt),
            ).andExpect(status().isForbidden)

        verify(exactly = 0) { onboardingGenerationRegistry.startOrAttach(any(), any()) }
    }

    @Test
    fun `personalizePath lets the project's manager rebuild their own path`() {
        every { onboardingGenerationRegistry.status(authId) } returns null
        every { onboardingPathService.hasPathForMe(authId) } returns true
        every { userApi.canManageProject(authId, projectId) } returns true
        every { onboardingGenerationRegistry.startOrAttach(authId, projectId) } throws
            ResponseStatusException(HttpStatus.BAD_REQUEST, "reached")

        mockMvc
            .perform(
                post("/api/v1/projects/$projectId/onboarding/me/path/personalize")
                    .with(userJwt),
            ).andExpect(status().isBadRequest)

        verify(exactly = 1) { onboardingGenerationRegistry.startOrAttach(authId, projectId) }
    }

    @Test
    fun `personalizePath still attaches a member to a running rebuild over their path`() {
        every { onboardingGenerationRegistry.status(authId) } returns
            OnboardingGenerationRegistry.GenerationRun(projectId = projectId, startedAt = Instant.now())
        every { onboardingGenerationRegistry.startOrAttach(authId, projectId) } throws
            ResponseStatusException(HttpStatus.BAD_REQUEST, "reached")

        mockMvc
            .perform(
                post("/api/v1/projects/$projectId/onboarding/me/path/personalize")
                    .with(userJwt),
            ).andExpect(status().isBadRequest)

        verify(exactly = 0) { onboardingPathService.hasPathForMe(any()) }
        verify(exactly = 1) { onboardingGenerationRegistry.startOrAttach(authId, projectId) }
    }

    // ========================== PM rebuild of a member's path ==========================

    @Test
    fun `personalizePathForUser starts the generation under the member's auth id`() {
        val memberAuthId = "member-auth-id"
        every { projectAuthorization.canManageProject(any(), projectId) } returns true
        every { userApi.getAuthIdByUserId(userId) } returns Optional.of(memberAuthId)
        every { onboardingGenerationRegistry.startOrAttach(memberAuthId, projectId) } throws
            ResponseStatusException(HttpStatus.BAD_REQUEST, "reached")

        mockMvc
            .perform(
                post("/api/v1/projects/$projectId/onboarding/users/$userId/path/personalize")
                    .with(adminJwt),
            ).andExpect(status().isBadRequest)

        verify(exactly = 1) { onboardingGenerationRegistry.startOrAttach(memberAuthId, projectId) }
    }

    @Test
    fun `personalizePathForUser is refused to anyone who does not manage the project`() {
        every { projectAuthorization.canManageProject(any(), projectId) } returns false

        mockMvc
            .perform(
                post("/api/v1/projects/$projectId/onboarding/users/$userId/path/personalize")
                    .with(userJwt),
            ).andExpect(status().isForbidden)

        verify(exactly = 0) { onboardingGenerationRegistry.startOrAttach(any(), any()) }
    }

    @Test
    fun `personalizePathForUser returns 404 for an unknown member`() {
        every { projectAuthorization.canManageProject(any(), projectId) } returns true
        every { userApi.getAuthIdByUserId(userId) } returns Optional.empty()

        mockMvc
            .perform(
                post("/api/v1/projects/$projectId/onboarding/users/$userId/path/personalize")
                    .with(adminJwt),
            ).andExpect(status().isNotFound)
    }

    @Test
    fun `personalizePathForUser should return 401 when not authenticated`() {
        mockMvc
            .perform(
                post("/api/v1/projects/$projectId/onboarding/users/$userId/path/personalize"),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `getGenerationStatus reports a running generation and the project's blueprint`() {
        val startedAt = Instant.parse("2026-09-15T10:00:00Z")
        every { onboardingGenerationRegistry.status(authId) } returns
            OnboardingGenerationRegistry.GenerationRun(projectId = projectId, startedAt = startedAt)
        every { userApi.getOnboardingProfileByAuthId(authId) } returns Optional.of(profileIn(projectId))
        every { onboardingGenerationRegistry.activeBlueprintCount(projectId) } returns 1

        mockMvc
            .perform(
                get("/api/v1/projects/$projectId/onboarding/me/path/generation")
                    .with(userJwt),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.running").value(true))
            .andExpect(jsonPath("$.runningProjectId").value(projectId.toString()))
            .andExpect(jsonPath("$.hasActiveBlueprint").value(true))
            .andExpect(jsonPath("$.activeBlueprintCount").value(1))
    }

    @Test
    fun `getGenerationStatus says nothing is running when no generation is`() {
        every { onboardingGenerationRegistry.status(authId) } returns null
        every { userApi.getOnboardingProfileByAuthId(authId) } returns Optional.of(profileIn(projectId))
        every { onboardingGenerationRegistry.activeBlueprintCount(projectId) } returns 0

        mockMvc
            .perform(
                get("/api/v1/projects/$projectId/onboarding/me/path/generation")
                    .with(userJwt),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.running").value(false))
            .andExpect(jsonPath("$.hasActiveBlueprint").value(false))
    }

    @Test
    fun `getGenerationStatus tells several active blueprints apart from none`() {
        every { onboardingGenerationRegistry.status(authId) } returns null
        every { userApi.getOnboardingProfileByAuthId(authId) } returns Optional.of(profileIn(projectId))
        every { onboardingGenerationRegistry.activeBlueprintCount(projectId) } returns 2

        mockMvc
            .perform(
                get("/api/v1/projects/$projectId/onboarding/me/path/generation")
                    .with(userJwt),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.hasActiveBlueprint").value(false))
            .andExpect(jsonPath("$.activeBlueprintCount").value(2))
    }

    @Test
    fun `getGenerationStatus answers only for a project the caller is assigned to`() {
        every { userApi.getOnboardingProfileByAuthId(authId) } returns Optional.of(profileIn(UUID.randomUUID()))

        mockMvc
            .perform(
                get("/api/v1/projects/$projectId/onboarding/me/path/generation")
                    .with(userJwt),
            ).andExpect(status().isForbidden)

        verify(exactly = 0) { onboardingGenerationRegistry.activeBlueprintCount(any()) }
    }

    @Test
    fun `personalizePath should return 401 when not authenticated`() {
        mockMvc
            .perform(
                post("/api/v1/projects/$projectId/onboarding/me/path/personalize"),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `personalizePath should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(
                post("/api/v1/projects/$projectId/onboarding/me/path/personalize")
                    .with(noUserRoleJwt),
            ).andExpect(status().isForbidden)
    }

    // ========================== Admin endpoints ==========================

    @Test
    fun `getOnboardingPathForUserId should return 200 and the path as its owner has it`() {
        // The hire-shaped response, not the summary: a reviewer looking at somebody's onboarding
        // needs the phases' contents and the per-question status, and every client that tried to
        // rebuild those from the summary shape got it wrong or crashed.
        val response = GetOnboardingPathForUserResponse(
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

    private fun profileIn(project: UUID) =
        UserOnboardingProfile(id = userId, projectIds = setOf(project), projectRoles = emptyMap())
}
