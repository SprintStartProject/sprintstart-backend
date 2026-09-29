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
import com.sprintstart.sprintstartbackend.user.repository.ProjectRepository
import com.sprintstart.sprintstartbackend.user.repository.ProjectUserAssignmentRepository
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.slot
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
// The real `projectAuth` bean over mocked repositories, so the manager rule itself is exercised.
@Import(SecurityConfig::class, ProjectAuthorization::class)
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

    @MockkBean
    private lateinit var projectRepository: ProjectRepository

    @MockkBean
    private lateinit var projectUserAssignmentRepository: ProjectUserAssignmentRepository

    @MockkBean
    private lateinit var userRepository: UserRepository

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
    private val pmJwt = jwtWithSubject(authId, "USER", "PM")
    private val hrJwt = jwtWithSubject(authId, "USER", "HR")
    private val managerAuthId = "test-manager-auth-id"
    private val managerJwt = jwtWithSubject(managerAuthId, "USER", "PM")
    private val memberAuthId = "member-auth-id"

    /** Stubs the registry for a start from [projectId] and hands back the start hook it was given. */
    private fun captureStart(
        forAuthId: String,
        sameProjectOnly: Boolean,
    ): io.mockk.CapturingSlot<() -> Unit> {
        val beforeStart = slot<() -> Unit>()
        every {
            onboardingGenerationRegistry.startOrAttach(forAuthId, projectId, sameProjectOnly, capture(beforeStart))
        } answers {
            beforeStart.captured()
            // Stops the request before an SSE stream opens; reaching it is what the tests check.
            throw ResponseStatusException(HttpStatus.I_AM_A_TEAPOT, "reached")
        }
        return beforeStart
    }

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
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { onboardingPathService.requireMayReplacePath(authId, userId, null) } just Runs
        every { onboardingPathService.deleteOnboardingPathForMe(authId) } just Runs

        mockMvc
            .perform(
                delete("/api/v1/onboarding/me/path")
                    .with(pmJwt),
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
    fun `deleteOnboardingPathForMe is refused to a plain member`() {
        mockMvc
            .perform(
                delete("/api/v1/onboarding/me/path")
                    .with(userJwt),
            ).andExpect(status().isForbidden)

        verify(exactly = 0) { onboardingPathService.deleteOnboardingPathForMe(any()) }
    }

    @Test
    fun `deleteOnboardingPathForMe refuses a PM whose path is from a project they do not manage`() {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { onboardingPathService.requireMayReplacePath(authId, userId, null) } throws
            ResponseStatusException(HttpStatus.FORBIDDEN)

        mockMvc
            .perform(
                delete("/api/v1/onboarding/me/path")
                    .with(pmJwt),
            ).andExpect(status().isForbidden)

        verify(exactly = 0) { onboardingPathService.deleteOnboardingPathForMe(any()) }
    }

    @Test
    fun `deleteOnboardingPathForMe should return 404 when not found`() {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.empty()
        every { onboardingPathService.deleteOnboardingPathForMe(authId) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(
                delete("/api/v1/onboarding/me/path")
                    .with(pmJwt),
            ).andExpect(status().isNotFound)

        verify(exactly = 1) {
            onboardingPathService.deleteOnboardingPathForMe(authId)
        }
    }

    // ========================== /me personalize (project-scoped) ==========================

    @Test
    fun `personalizePath passes the selected project path variable to the generation registry`() {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { onboardingPathService.requireMayReplacePath(authId, userId, projectId) } just Runs
        captureStart(authId, sameProjectOnly = false)

        mockMvc
            .perform(
                post("/api/v1/projects/$projectId/onboarding/me/path/personalize")
                    .with(userJwt),
            ).andExpect(status().isIAmATeapot)

        verify(exactly = 1) { onboardingGenerationRegistry.startOrAttach(authId, projectId, false, any()) }
    }

    @Test
    fun `personalizePath checks whether the caller may replace their path before a new run starts`() {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { onboardingPathService.requireMayReplacePath(authId, userId, projectId) } throws
            ResponseStatusException(HttpStatus.FORBIDDEN)
        captureStart(authId, sameProjectOnly = false)

        mockMvc
            .perform(
                post("/api/v1/projects/$projectId/onboarding/me/path/personalize")
                    .with(userJwt),
            ).andExpect(status().isForbidden)

        verify(exactly = 1) { onboardingPathService.requireMayReplacePath(authId, userId, projectId) }
    }

    @Test
    fun `personalizePath still attaches a member to a running rebuild over their path`() {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        // An attach never runs the start hook -- the registry is what decides that.
        every { onboardingGenerationRegistry.startOrAttach(authId, projectId, false, any()) } throws
            ResponseStatusException(HttpStatus.I_AM_A_TEAPOT, "attached")

        mockMvc
            .perform(
                post("/api/v1/projects/$projectId/onboarding/me/path/personalize")
                    .with(userJwt),
            ).andExpect(status().isIAmATeapot)

        verify(exactly = 0) { onboardingPathService.requireMayReplacePath(any(), any(), any()) }
    }

    // ========================== PM rebuild of a member's path ==========================

    @Test
    fun `personalizePathForUser starts the generation under the member's auth id`() {
        every { projectRepository.findManagerAuthId(projectId) } returns Optional.of(managerAuthId)
        every { userApi.getAuthIdByUserId(userId) } returns Optional.of(memberAuthId)
        every { userApi.getOnboardingProfileByAuthId(memberAuthId) } returns Optional.of(profileIn(projectId))
        every { onboardingPathService.requireMayReplacePath(managerAuthId, userId, projectId) } just Runs
        captureStart(memberAuthId, sameProjectOnly = true)

        mockMvc
            .perform(
                post("/api/v1/projects/$projectId/onboarding/users/$userId/path/personalize")
                    .with(managerJwt),
            ).andExpect(status().isIAmATeapot)

        verify(exactly = 1) { onboardingPathService.requireMayReplacePath(managerAuthId, userId, projectId) }
    }

    @Test
    fun `personalizePathForUser is refused to a PM who does not manage the project`() {
        every { projectRepository.findManagerAuthId(projectId) } returns Optional.of("somebody-else")

        mockMvc
            .perform(
                post("/api/v1/projects/$projectId/onboarding/users/$userId/path/personalize")
                    .with(pmJwt),
            ).andExpect(status().isForbidden)

        verify(exactly = 0) { onboardingGenerationRegistry.startOrAttach(any(), any(), any(), any()) }
    }

    @Test
    fun `personalizePathForUser refuses a member of another project before attaching to their run`() {
        every { userApi.getAuthIdByUserId(userId) } returns Optional.of(memberAuthId)
        every { userApi.getOnboardingProfileByAuthId(memberAuthId) } returns
            Optional.of(profileIn(UUID.randomUUID()))

        mockMvc
            .perform(
                post("/api/v1/projects/$projectId/onboarding/users/$userId/path/personalize")
                    .with(adminJwt),
            ).andExpect(status().isForbidden)

        verify(exactly = 0) { onboardingGenerationRegistry.startOrAttach(any(), any(), any(), any()) }
    }

    @Test
    fun `personalizePathForUser only attaches to a run for the same project`() {
        every { userApi.getAuthIdByUserId(userId) } returns Optional.of(memberAuthId)
        every { userApi.getOnboardingProfileByAuthId(memberAuthId) } returns Optional.of(profileIn(projectId))
        every { onboardingGenerationRegistry.startOrAttach(memberAuthId, projectId, true, any()) } throws
            ResponseStatusException(HttpStatus.CONFLICT)

        mockMvc
            .perform(
                post("/api/v1/projects/$projectId/onboarding/users/$userId/path/personalize")
                    .with(adminJwt),
            ).andExpect(status().isConflict)
    }

    @Test
    fun `personalizePathForUser refuses replacing a path the caller may not replace`() {
        every { userApi.getAuthIdByUserId(userId) } returns Optional.of(memberAuthId)
        every { userApi.getOnboardingProfileByAuthId(memberAuthId) } returns Optional.of(profileIn(projectId))
        every { onboardingPathService.requireMayReplacePath(adminAuthId, userId, projectId) } throws
            ResponseStatusException(HttpStatus.FORBIDDEN)
        captureStart(memberAuthId, sameProjectOnly = true)

        mockMvc
            .perform(
                post("/api/v1/projects/$projectId/onboarding/users/$userId/path/personalize")
                    .with(adminJwt),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `personalizePathForUser returns 404 for an unknown member`() {
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
        every { onboardingPathService.requireMayReplacePath(adminAuthId, userId, null) } just Runs
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
    fun `deleteOnboardingPathByUserId is no longer open to HR`() {
        mockMvc
            .perform(
                delete("/api/v1/onboarding/users/$userId/path")
                    .with(hrJwt),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `deleteOnboardingPathByUserId refuses a PM whose member's path is from a project they do not manage`() {
        every { onboardingPathService.requireMayReplacePath(authId, userId, null) } throws
            ResponseStatusException(HttpStatus.FORBIDDEN)

        mockMvc
            .perform(
                delete("/api/v1/onboarding/users/$userId/path")
                    .with(pmJwt),
            ).andExpect(status().isForbidden)

        verify(exactly = 0) { onboardingPathService.deleteOnboardingPathByUserId(any()) }
    }

    @Test
    fun `deleteOnboardingPathByUserId should return 404 when not found`() {
        every { onboardingPathService.requireMayReplacePath(adminAuthId, userId, null) } just Runs
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
