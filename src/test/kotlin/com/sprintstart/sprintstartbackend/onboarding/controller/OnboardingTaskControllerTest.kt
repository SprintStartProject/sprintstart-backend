package com.sprintstart.sprintstartbackend.onboarding.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.onboarding.model.request.task.CreateOnboardingTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.task.UpdateOnboardingTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.task.CreateOnboardingTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.task.GetOnboardingTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.task.GetOnboardingTasksResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.task.UpdateOnboardingTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingTaskService
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

@WebMvcTest(OnboardingTaskController::class)
@Import(
    SecurityConfig::class,
)
@AutoConfigureMockMvc
class OnboardingTaskControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val objectMapper = jacksonObjectMapper()

    @MockkBean
    private lateinit var onboardingTaskService: OnboardingTaskService

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private val stepId = UUID.randomUUID()
    private val taskId = UUID.randomUUID()

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

    private fun buildCreateRequest() = CreateOnboardingTaskRequest(
        position = 1,
        title = "Task 1",
        description = "task desc",
    )

    private fun buildUpdateRequest() = UpdateOnboardingTaskRequest(
        position = 1,
        title = "Updated Task",
        description = "updated desc",
        finished = true,
    )

    private fun buildGetTaskResponse() = GetOnboardingTaskResponse(
        id = taskId,
        stepId = stepId,
        position = 1,
        title = "Task 1",
        description = "task desc",
        finished = false,
    )

    private fun buildGetTasksResponse() = GetOnboardingTasksResponse(
        id = taskId,
        stepId = stepId,
        position = 1,
        title = "Task 1",
        description = "task desc",
        finished = false,
    )

    private fun buildCreateTaskResponse() = CreateOnboardingTaskResponse(
        id = taskId,
        stepId = stepId,
        position = 1,
        title = "Task 1",
        description = "task desc",
        finished = false,
    )

    private fun buildUpdateTaskResponse() = UpdateOnboardingTaskResponse(
        id = taskId,
        stepId = stepId,
        position = 1,
        title = "Updated Task",
        description = "updated desc",
        finished = true,
    )

    // ========================== /me endpoints ==========================

    @Test
    fun `getOnboardingTasksForMe should return 200 and list of tasks`() {
        every { onboardingTaskService.getOnboardingTasksForMe(authId, stepId) } returns listOf(buildGetTasksResponse())

        mockMvc
            .perform(get("/api/v1/onboarding/me/steps/$stepId/tasks").with(userJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingTaskService.getOnboardingTasksForMe(authId, stepId) }
    }

    @Test
    fun `getOnboardingTasksForMe should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/onboarding/me/steps/$stepId/tasks"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getOnboardingTasksForMe should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(get("/api/v1/onboarding/me/steps/$stepId/tasks").with(noUserRoleJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `createOnboardingTaskForMe should return 201 and created task`() {
        val request = buildCreateRequest()
        every { onboardingTaskService.createOnboardingTaskForMe(authId, stepId, request) } returns
            buildCreateTaskResponse()

        mockMvc
            .perform(
                post("/api/v1/onboarding/me/steps/$stepId/tasks")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isCreated)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingTaskService.createOnboardingTaskForMe(authId, stepId, request) }
    }

    @Test
    fun `createOnboardingTaskForMe should return 401 when not authenticated`() {
        mockMvc
            .perform(
                post("/api/v1/onboarding/me/steps/$stepId/tasks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildCreateRequest())),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `createOnboardingTaskForMe should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(
                post("/api/v1/onboarding/me/steps/$stepId/tasks")
                    .with(noUserRoleJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildCreateRequest())),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `getOnboardingTaskForMe should return 200 and task`() {
        every { onboardingTaskService.getOnboardingTaskForMe(authId, taskId) } returns buildGetTaskResponse()

        mockMvc
            .perform(get("/api/v1/onboarding/me/tasks/$taskId").with(userJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingTaskService.getOnboardingTaskForMe(authId, taskId) }
    }

    @Test
    fun `getOnboardingTaskForMe should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/onboarding/me/tasks/$taskId"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getOnboardingTaskForMe should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(get("/api/v1/onboarding/me/tasks/$taskId").with(noUserRoleJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `getOnboardingTaskForMe should return 404 when not found`() {
        every { onboardingTaskService.getOnboardingTaskForMe(authId, taskId) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(get("/api/v1/onboarding/me/tasks/$taskId").with(userJwt))
            .andExpect(status().isNotFound)

        verify(exactly = 1) { onboardingTaskService.getOnboardingTaskForMe(authId, taskId) }
    }

    @Test
    fun `updateOnboardingTaskForMe should return 200 and updated task`() {
        val request = buildUpdateRequest()
        every { onboardingTaskService.updateOnboardingTaskForMe(authId, taskId, request) } returns
            buildUpdateTaskResponse()

        mockMvc
            .perform(
                put("/api/v1/onboarding/me/tasks/$taskId")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingTaskService.updateOnboardingTaskForMe(authId, taskId, request) }
    }

    @Test
    fun `updateOnboardingTaskForMe should return 401 when not authenticated`() {
        mockMvc
            .perform(
                put("/api/v1/onboarding/me/tasks/$taskId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildUpdateRequest())),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `updateOnboardingTaskForMe should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(
                put("/api/v1/onboarding/me/tasks/$taskId")
                    .with(noUserRoleJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildUpdateRequest())),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `deleteOnboardingTaskForMe should return 204`() {
        every { onboardingTaskService.deleteOnboardingTaskForMe(authId, taskId) } just Runs

        mockMvc
            .perform(delete("/api/v1/onboarding/me/tasks/$taskId").with(userJwt))
            .andExpect(status().isNoContent)

        verify(exactly = 1) { onboardingTaskService.deleteOnboardingTaskForMe(authId, taskId) }
    }

    @Test
    fun `deleteOnboardingTaskForMe should return 401 when not authenticated`() {
        mockMvc
            .perform(delete("/api/v1/onboarding/me/tasks/$taskId"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `deleteOnboardingTaskForMe should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(delete("/api/v1/onboarding/me/tasks/$taskId").with(noUserRoleJwt))
            .andExpect(status().isForbidden)
    }

    // ========================== Admin endpoints ==========================

    @Test
    fun `getOnboardingTasksByStepId should return 200 and list of tasks`() {
        every { onboardingTaskService.getOnboardingTasksByStepId(stepId) } returns listOf(buildGetTaskResponse())

        mockMvc
            .perform(get("/api/v1/onboarding/steps/$stepId/tasks").with(adminJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingTaskService.getOnboardingTasksByStepId(stepId) }
    }

    @Test
    fun `getOnboardingTasksByStepId should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/onboarding/steps/$stepId/tasks"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getOnboardingTasksByStepId should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(get("/api/v1/onboarding/steps/$stepId/tasks").with(userJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `createOnboardingTask should return 201 and created task`() {
        val request = buildCreateRequest()
        every { onboardingTaskService.createOnboardingTaskForStepId(stepId, request) } returns buildCreateTaskResponse()

        mockMvc
            .perform(
                post("/api/v1/onboarding/steps/$stepId/tasks")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isCreated)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingTaskService.createOnboardingTaskForStepId(stepId, request) }
    }

    @Test
    fun `createOnboardingTask should return 401 when not authenticated`() {
        mockMvc
            .perform(
                post("/api/v1/onboarding/steps/$stepId/tasks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildCreateRequest())),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `createOnboardingTask should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(
                post("/api/v1/onboarding/steps/$stepId/tasks")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildCreateRequest())),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `getOnboardingTask should return 200 and task`() {
        every { onboardingTaskService.getOnboardingTaskById(taskId) } returns buildGetTaskResponse()

        mockMvc
            .perform(get("/api/v1/onboarding/tasks/$taskId").with(adminJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingTaskService.getOnboardingTaskById(taskId) }
    }

    @Test
    fun `getOnboardingTask should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/onboarding/tasks/$taskId"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getOnboardingTask should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(get("/api/v1/onboarding/tasks/$taskId").with(userJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `getOnboardingTask should return 404 when not found`() {
        every { onboardingTaskService.getOnboardingTaskById(taskId) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(get("/api/v1/onboarding/tasks/$taskId").with(adminJwt))
            .andExpect(status().isNotFound)

        verify(exactly = 1) { onboardingTaskService.getOnboardingTaskById(taskId) }
    }

    @Test
    fun `updateOnboardingTask should return 200 and updated task`() {
        val request = buildUpdateRequest()
        every { onboardingTaskService.updateOnboardingTaskById(taskId, request) } returns buildUpdateTaskResponse()

        mockMvc
            .perform(
                put("/api/v1/onboarding/tasks/$taskId")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingTaskService.updateOnboardingTaskById(taskId, request) }
    }

    @Test
    fun `updateOnboardingTask should return 401 when not authenticated`() {
        mockMvc
            .perform(
                put("/api/v1/onboarding/tasks/$taskId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildUpdateRequest())),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `updateOnboardingTask should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(
                put("/api/v1/onboarding/tasks/$taskId")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildUpdateRequest())),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `updateOnboardingTask should return 404 when not found`() {
        val request = buildUpdateRequest()
        every { onboardingTaskService.updateOnboardingTaskById(taskId, request) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(
                put("/api/v1/onboarding/tasks/$taskId")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isNotFound)

        verify(exactly = 1) { onboardingTaskService.updateOnboardingTaskById(taskId, request) }
    }

    @Test
    fun `deleteOnboardingTaskForStepId should return 204`() {
        every { onboardingTaskService.deleteOnboardingTaskById(taskId) } just Runs

        mockMvc
            .perform(delete("/api/v1/onboarding/tasks/$taskId").with(adminJwt))
            .andExpect(status().isNoContent)

        verify(exactly = 1) { onboardingTaskService.deleteOnboardingTaskById(taskId) }
    }

    @Test
    fun `deleteOnboardingTaskForStepId should return 401 when not authenticated`() {
        mockMvc
            .perform(delete("/api/v1/onboarding/tasks/$taskId"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `deleteOnboardingTaskForStepId should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(delete("/api/v1/onboarding/tasks/$taskId").with(userJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `deleteOnboardingTaskForStepId should return 404 when not found`() {
        every { onboardingTaskService.deleteOnboardingTaskById(taskId) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(delete("/api/v1/onboarding/tasks/$taskId").with(adminJwt))
            .andExpect(status().isNotFound)

        verify(exactly = 1) { onboardingTaskService.deleteOnboardingTaskById(taskId) }
    }
}
