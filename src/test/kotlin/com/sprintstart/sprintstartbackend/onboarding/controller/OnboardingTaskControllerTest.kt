package com.sprintstart.sprintstartbackend.onboarding.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.onboarding.model.request.task.CreateOnboardingTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.task.UpdateOnboardingTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.task.CreateOnboardingTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.task.GetOnboardingTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.task.GetOnboardingTasksResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.task.UpdateOnboardingTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingService
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.util.UUID

@WebMvcTest(OnboardingTaskController::class)
class OnboardingTaskControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val objectMapper = jacksonObjectMapper()

    @MockkBean
    private lateinit var onboardingService: OnboardingService

    private val stepId: UUID = UUID.fromString("44444444-4444-4444-4444-444444444444")
    private val taskId: UUID = UUID.fromString("55555555-5555-5555-5555-555555555555")

    @Test
    fun `createOnboardingTask should return 201 and created task`() {
        val request = CreateOnboardingTaskRequest(
            position = 1,
            title = "Create account",
            description = "Create an internal account",
        )
        val response = CreateOnboardingTaskResponse(
            id = taskId,
            stepId = stepId,
            position = 1,
            title = "Create account",
            description = "Create an internal account",
            finished = false,
        )

        every { onboardingService.createOnboardingTaskForStepId(stepId, request) } returns response

        mockMvc
            .post("/api/v1/onboarding/steps/$stepId/tasks") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.id") { value(taskId.toString()) }
                jsonPath("$.stepId") { value(stepId.toString()) }
                jsonPath("$.finished") { value(false) }
            }

        verify(exactly = 1) { onboardingService.createOnboardingTaskForStepId(stepId, request) }
    }

    @Test
    fun `getOnboardingTasks should return 200 and all tasks`() {
        val response = listOf(
            GetOnboardingTasksResponse(
                id = taskId,
                stepId = stepId,
                position = 1,
                title = "Create account",
                description = "Create an internal account",
                finished = false,
            ),
        )

        every { onboardingService.getOnboardingTasks() } returns response

        mockMvc
            .get("/api/v1/onboarding/tasks")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].id") { value(taskId.toString()) }
                jsonPath("$[0].stepId") { value(stepId.toString()) }
            }

        verify(exactly = 1) { onboardingService.getOnboardingTasks() }
    }

    @Test
    fun `getOnboardingTasksByStepId should return 200 and tasks for step`() {
        val response = listOf(
            GetOnboardingTaskResponse(
                id = taskId,
                stepId = stepId,
                position = 1,
                title = "Create account",
                description = "Create an internal account",
                finished = false,
            ),
        )

        every { onboardingService.getOnboardingTasksByStepId(stepId) } returns response

        mockMvc
            .get("/api/v1/onboarding/steps/$stepId/tasks")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].id") { value(taskId.toString()) }
                jsonPath("$[0].stepId") { value(stepId.toString()) }
            }

        verify(exactly = 1) { onboardingService.getOnboardingTasksByStepId(stepId) }
    }

    @Test
    fun `getOnboardingTask should return 200 and task`() {
        val response = GetOnboardingTaskResponse(
            id = taskId,
            stepId = stepId,
            position = 1,
            title = "Create account",
            description = "Create an internal account",
            finished = false,
        )

        every { onboardingService.getOnboardingTask(taskId) } returns response

        mockMvc
            .get("/api/v1/onboarding/tasks/$taskId")
            .andExpect {
                status { isOk() }
                jsonPath("$.id") { value(taskId.toString()) }
                jsonPath("$.stepId") { value(stepId.toString()) }
            }

        verify(exactly = 1) { onboardingService.getOnboardingTask(taskId) }
    }

    @Test
    fun `updateOnboardingTask should return 200 and updated task`() {
        val request = UpdateOnboardingTaskRequest(
            position = 2,
            title = "Updated account task",
            description = "Updated account task description",
            finished = true,
        )
        val response = UpdateOnboardingTaskResponse(
            id = taskId,
            stepId = stepId,
            position = 2,
            title = "Updated account task",
            description = "Updated account task description",
            finished = true,
        )

        every { onboardingService.updateOnboardingTask(taskId, request) } returns response

        mockMvc
            .put("/api/v1/onboarding/tasks/$taskId") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isOk() }
                jsonPath("$.id") { value(taskId.toString()) }
                jsonPath("$.position") { value(2) }
                jsonPath("$.finished") { value(true) }
            }

        verify(exactly = 1) { onboardingService.updateOnboardingTask(taskId, request) }
    }

    @Test
    fun `deleteOnboardingTask should return 204`() {
        every { onboardingService.deleteOnboardingTask(taskId) } just Runs

        mockMvc
            .delete("/api/v1/onboarding/tasks/$taskId")
            .andExpect {
                status { isNoContent() }
            }

        verify(exactly = 1) { onboardingService.deleteOnboardingTask(taskId) }
    }
}
