package com.sprintstart.sprintstartbackend.user.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.user.external.enums.Role
import com.sprintstart.sprintstartbackend.user.model.request.project.AssignProjectUsersRequest
import com.sprintstart.sprintstartbackend.user.model.response.project.AdminProjectDetailResponse
import com.sprintstart.sprintstartbackend.user.model.response.project.ManagedProjectResponse
import com.sprintstart.sprintstartbackend.user.model.response.project.ProjectUserResponse
import com.sprintstart.sprintstartbackend.user.security.ProjectAuthorization
import com.sprintstart.sprintstartbackend.user.service.AdminProjectService
import com.sprintstart.sprintstartbackend.user.service.ProjectManagerService
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Covers the project-manager access boundary: a manager may act only on the projects they are
 * assigned to, an admin may act on any project, and everyone else is rejected.
 *
 * The `projectAuth` bean must be mocked explicitly. It is referenced by name from the
 * `@PreAuthorize` expressions, so without a bean of that name the expressions fail to resolve when
 * a request is handled rather than when the context is built.
 */
@WebMvcTest(ProjectController::class)
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class ProjectControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val objectMapper = jacksonObjectMapper()

    @MockkBean
    private lateinit var adminProjectService: AdminProjectService

    @MockkBean
    private lateinit var projectManagerService: ProjectManagerService

    @MockkBean(name = "projectAuth")
    private lateinit var projectAuth: ProjectAuthorization

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private val managedProjectId: UUID = UUID.randomUUID()
    private val foreignProjectId: UUID = UUID.randomUUID()

    private val pmJwt = jwt().authorities(
        SimpleGrantedAuthority("ROLE_USER"),
        SimpleGrantedAuthority("ROLE_PM"),
    )
    private val adminJwt = jwt().authorities(
        SimpleGrantedAuthority("ROLE_USER"),
        SimpleGrantedAuthority("ROLE_ADMIN"),
    )
    private val userJwt = jwt().authorities(SimpleGrantedAuthority("ROLE_USER"))

    @BeforeEach
    fun setUpAuthorization() {
        every { projectAuth.canManageProject(any(), managedProjectId) } returns true
        every { projectAuth.canManageProject(any(), foreignProjectId) } returns false
        every { projectAuth.canAccessProject(any(), managedProjectId) } returns true
        every { projectAuth.canAccessProject(any(), foreignProjectId) } returns false
        every { projectAuth.isAdmin(any()) } returns false
    }

    @Test
    fun `getManagedProjects returns the projects a manager is assigned to`() {
        every { projectManagerService.getManagedProjects(any(), false) } returns listOf(managedProjectResponse())

        mockMvc
            .perform(get("/api/v1/projects/managed").with(pmJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(managedProjectId.toString()))
            .andExpect(jsonPath("$[0].memberCount").value(3))

        verify(exactly = 1) { projectManagerService.getManagedProjects(any(), false) }
    }

    @Test
    fun `getManagedProjects returns every project for admins`() {
        every { projectAuth.isAdmin(any()) } returns true
        every { projectManagerService.getManagedProjects(any(), true) } returns listOf(managedProjectResponse())

        mockMvc
            .perform(get("/api/v1/projects/managed").with(adminJwt))
            .andExpect(status().isOk)

        verify(exactly = 1) { projectManagerService.getManagedProjects(any(), true) }
    }

    @Test
    fun `getManagedProjects rejects users without a management role`() {
        mockMvc
            .perform(get("/api/v1/projects/managed").with(userJwt))
            .andExpect(status().isForbidden)

        verify(exactly = 0) { projectManagerService.getManagedProjects(any(), any()) }
    }

    @Test
    fun `getManagedProjects rejects unauthenticated users`() {
        mockMvc
            .perform(get("/api/v1/projects/managed"))
            .andExpect(status().isUnauthorized)

        verify(exactly = 0) { projectManagerService.getManagedProjects(any(), any()) }
    }

    @Test
    fun `getProjectById returns a project the caller may access`() {
        every { adminProjectService.getProjectById(managedProjectId) } returns projectDetailResponse()

        mockMvc
            .perform(get("/api/v1/projects/$managedProjectId").with(pmJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(managedProjectId.toString()))

        verify(exactly = 1) { adminProjectService.getProjectById(managedProjectId) }
    }

    @Test
    fun `getProjectById rejects projects the caller has no access to`() {
        mockMvc
            .perform(get("/api/v1/projects/$foreignProjectId").with(pmJwt))
            .andExpect(status().isForbidden)

        verify(exactly = 0) { adminProjectService.getProjectById(any()) }
    }

    @Test
    fun `getProjectUsers returns users of a managed project`() {
        every { adminProjectService.getProjectUsers(managedProjectId) } returns listOf(projectUserResponse())

        mockMvc
            .perform(get("/api/v1/projects/$managedProjectId/users").with(pmJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].username").value("max.mustermann"))

        verify(exactly = 1) { adminProjectService.getProjectUsers(managedProjectId) }
    }

    @Test
    fun `getProjectUsers rejects projects the caller does not manage`() {
        mockMvc
            .perform(get("/api/v1/projects/$foreignProjectId/users").with(pmJwt))
            .andExpect(status().isForbidden)

        verify(exactly = 0) { adminProjectService.getProjectUsers(any()) }
    }

    @Test
    fun `assignUsers maps a user to a managed project`() {
        val userId = UUID.randomUUID()
        val request = AssignProjectUsersRequest(userIds = setOf(userId))
        every { adminProjectService.assignUsers(managedProjectId, request) } returns
            listOf(projectUserResponse(id = userId))

        mockMvc
            .perform(
                post("/api/v1/projects/$managedProjectId/users")
                    .with(pmJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(userId.toString()))

        verify(exactly = 1) { adminProjectService.assignUsers(managedProjectId, request) }
    }

    @Test
    fun `assignUsers rejects projects the caller does not manage`() {
        val request = AssignProjectUsersRequest(userIds = setOf(UUID.randomUUID()))

        mockMvc
            .perform(
                post("/api/v1/projects/$foreignProjectId/users")
                    .with(pmJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isForbidden)

        verify(exactly = 0) { adminProjectService.assignUsers(any(), any()) }
    }

    @Test
    fun `assignUsers allows admins on projects they do not manage`() {
        every { projectAuth.canManageProject(any(), foreignProjectId) } returns true
        val request = AssignProjectUsersRequest(userIds = setOf(UUID.randomUUID()))
        every { adminProjectService.assignUsers(foreignProjectId, request) } returns listOf(projectUserResponse())

        mockMvc
            .perform(
                post("/api/v1/projects/$foreignProjectId/users")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)

        verify(exactly = 1) { adminProjectService.assignUsers(foreignProjectId, request) }
    }

    @Test
    fun `removeUser removes a user from a managed project`() {
        val userId = UUID.randomUUID()
        every { adminProjectService.removeUser(managedProjectId, userId) } just runs

        mockMvc
            .perform(delete("/api/v1/projects/$managedProjectId/users/$userId").with(pmJwt))
            .andExpect(status().isNoContent)
            .andExpect(content().string(""))

        verify(exactly = 1) { adminProjectService.removeUser(managedProjectId, userId) }
    }

    @Test
    fun `removeUser rejects projects the caller does not manage`() {
        mockMvc
            .perform(delete("/api/v1/projects/$foreignProjectId/users/${UUID.randomUUID()}").with(pmJwt))
            .andExpect(status().isForbidden)

        verify(exactly = 0) { adminProjectService.removeUser(any(), any()) }
    }

    @Test
    fun `removeUser returns 409 when the user manages the project`() {
        val userId = UUID.randomUUID()
        every { adminProjectService.removeUser(managedProjectId, userId) } throws
            ResponseStatusException(HttpStatus.CONFLICT)

        mockMvc
            .perform(delete("/api/v1/projects/$managedProjectId/users/$userId").with(pmJwt))
            .andExpect(status().isConflict)

        verify(exactly = 1) { adminProjectService.removeUser(managedProjectId, userId) }
    }

    private fun managedProjectResponse() = ManagedProjectResponse(
        id = managedProjectId,
        name = "SprintStart Frontend",
        description = "Frontend web application",
        memberCount = 3,
    )

    private fun projectDetailResponse() = AdminProjectDetailResponse(
        id = managedProjectId,
        name = "SprintStart Frontend",
        description = "Frontend web application",
        manager = null,
        sources = emptyList(),
        users = listOf(projectUserResponse()),
    )

    private fun projectUserResponse(
        id: UUID = UUID.randomUUID(),
    ) = ProjectUserResponse(
        id = id,
        username = "max.mustermann",
        email = "max.mustermann@example.com",
        firstName = "Max",
        lastName = "Mustermann",
        roles = setOf(Role.USER),
        projectRoles = emptyList(),
        enabled = true,
    )
}
