package com.sprintstart.sprintstartbackend.onboarding.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathsResponse
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingService
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@WebMvcTest(OnboardingPathController::class)
class OnboardingPathControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockkBean
    private lateinit var onboardingService: OnboardingService

    private val pathId: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val userId: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val createdAt: Instant = Instant.parse("2026-06-01T10:00:00Z")

    @Test
    fun `getAllPaths should return 200 and all paths`() {
        val response = listOf(
            GetOnboardingPathsResponse(
                id = pathId,
                userId = userId,
                createdAt = createdAt,
                phaseCount = 2,
                stepCount = 5,
                finishedStepCount = 1,
            ),
        )

        every { onboardingService.getAllOnboardingPaths() } returns response

        mockMvc
            .get("/api/v1/onboarding/paths")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].id") { value(pathId.toString()) }
                jsonPath("$[0].userId") { value(userId.toString()) }
                jsonPath("$[0].phaseCount") { value(2) }
                jsonPath("$[0].stepCount") { value(5) }
                jsonPath("$[0].finishedStepCount") { value(1) }
            }

        verify(exactly = 1) { onboardingService.getAllOnboardingPaths() }
    }

    @Test
    fun `getPath should return 200 and path`() {
        val response = GetOnboardingPathResponse(
            id = pathId,
            userId = userId,
            createdAt = createdAt,
            phases = emptyList(),
        )

        every { onboardingService.getOnboardingPath(pathId) } returns response

        mockMvc
            .get("/api/v1/onboarding/paths/$pathId")
            .andExpect {
                status { isOk() }
                jsonPath("$.id") { value(pathId.toString()) }
                jsonPath("$.userId") { value(userId.toString()) }
                jsonPath("$.phases.length()") { value(0) }
            }

        verify(exactly = 1) { onboardingService.getOnboardingPath(pathId) }
    }

    @Test
    fun `getPath should return 404 if path not found`() {
        every {
            onboardingService.getOnboardingPath(pathId)
        } throws ResponseStatusException(HttpStatus.NOT_FOUND, "No onboarding path found")

        mockMvc
            .get("/api/v1/onboarding/paths/$pathId")
            .andExpect {
                status { isNotFound() }
            }

        verify(exactly = 1) { onboardingService.getOnboardingPath(pathId) }
    }

    @Test
    fun `getPathForUser should return 200 and path for user`() {
        val response = GetOnboardingPathForUserResponse(
            id = pathId,
            userId = userId,
            createdAt = createdAt,
            phases = emptyList(),
        )

        every { onboardingService.getOnboardingPathByUserId(userId) } returns response

        mockMvc
            .get("/api/v1/onboarding/$userId/path")
            .andExpect {
                status { isOk() }
                jsonPath("$.id") { value(pathId.toString()) }
                jsonPath("$.userId") { value(userId.toString()) }
                jsonPath("$.phases.length()") { value(0) }
            }

        verify(exactly = 1) { onboardingService.getOnboardingPathByUserId(userId) }
    }

    @Test
    fun `deletePath should return 204`() {
        every { onboardingService.deleteOnboardingPathById(pathId) } just Runs

        mockMvc
            .delete("/api/v1/onboarding/paths/$pathId")
            .andExpect {
                status { isNoContent() }
            }

        verify(exactly = 1) { onboardingService.deleteOnboardingPathById(pathId) }
    }

    @Test
    fun `deletePathByUserId should return 204`() {
        every { onboardingService.deleteOnboardingPathByUserId(userId) } just Runs

        mockMvc
            .delete("/api/v1/onboarding/$userId/path")
            .andExpect {
                status { isNoContent() }
            }

        verify(exactly = 1) { onboardingService.deleteOnboardingPathByUserId(userId) }
    }
}
