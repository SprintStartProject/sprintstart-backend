package com.sprintstart.sprintstartbackend.onboarding.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.ProjectPmActionsResponse
import com.sprintstart.sprintstartbackend.onboarding.service.ProjectPmActionsService
import com.sprintstart.sprintstartbackend.user.external.security.ProjectAuthorization
import com.sprintstart.sprintstartbackend.user.repository.ProjectRepository
import com.sprintstart.sprintstartbackend.user.repository.ProjectUserAssignmentRepository
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID

@WebMvcTest(ProjectPmActionsController::class)
@Import(SecurityConfig::class, ProjectAuthorization::class)
@AutoConfigureMockMvc
class ProjectPmActionsControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockkBean
    private lateinit var service: ProjectPmActionsService

    @MockkBean
    private lateinit var projectRepository: ProjectRepository

    @MockkBean
    private lateinit var assignmentRepository: ProjectUserAssignmentRepository

    @MockkBean
    private lateinit var userRepository: UserRepository

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private val projectId = UUID.randomUUID()
    private val managerAuthId = "assigned-manager"
    private val endpoint = "/api/v1/onboarding/projects/$projectId/pm-actions"

    @BeforeEach
    fun setUp() {
        every { projectRepository.findManagerAuthId(projectId) } returns Optional.of(managerAuthId)
    }

    @Test
    fun `assigned manager receives both categories and their total`() {
        every { service.getPmActions(projectId) } returns ProjectPmActionsResponse(2, 3)

        mockMvc
            .perform(get(endpoint).with(asUser(managerAuthId, "PM")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pendingSkipRequests").value(2))
            .andExpect(jsonPath("$.unreadFeedback").value(3))
            .andExpect(jsonPath("$.total").value(5))

        verify(exactly = 1) { service.getPmActions(projectId) }
    }

    @Test
    fun `admin may read a project managed by somebody else including a zero count`() {
        every { service.getPmActions(projectId) } returns ProjectPmActionsResponse(0, 0)

        mockMvc
            .perform(get(endpoint).with(asUser("admin", "ADMIN")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pendingSkipRequests").value(0))
            .andExpect(jsonPath("$.unreadFeedback").value(0))
            .andExpect(jsonPath("$.total").value(0))
    }

    @ParameterizedTest
    @ValueSource(strings = ["PM", "HR", "USER"])
    fun `a global role or membership does not grant management of the selected project`(role: String) {
        mockMvc
            .perform(get(endpoint).with(asUser("other-member", role)))
            .andExpect(status().isForbidden)

        verify(exactly = 0) { service.getPmActions(any()) }
    }

    @Test
    fun `authentication is required`() {
        mockMvc
            .perform(get(endpoint))
            .andExpect(status().isUnauthorized)

        verify(exactly = 0) { service.getPmActions(any()) }
    }

    @Test
    fun `admin receives 404 when the project does not exist`() {
        every { service.getPmActions(projectId) } throws ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(get(endpoint).with(asUser("admin", "ADMIN")))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `invalid project identifier returns 400`() {
        mockMvc
            .perform(get("/api/v1/onboarding/projects/invalid/pm-actions").with(asUser("admin", "ADMIN")))
            .andExpect(status().isBadRequest)

        verify(exactly = 0) { service.getPmActions(any()) }
    }

    private fun asUser(authId: String, role: String) = jwt()
        .jwt { it.subject(authId) }
        .authorities(SimpleGrantedAuthority("ROLE_$role"))
}
