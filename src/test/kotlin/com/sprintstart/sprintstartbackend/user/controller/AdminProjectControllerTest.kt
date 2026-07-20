package com.sprintstart.sprintstartbackend.user.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.user.external.enums.Role
import com.sprintstart.sprintstartbackend.user.model.request.project.AssignProjectUsersRequest
import com.sprintstart.sprintstartbackend.user.model.request.project.CreateAdminProjectRequest
import com.sprintstart.sprintstartbackend.user.model.request.project.PatchAdminProjectRequest
import com.sprintstart.sprintstartbackend.user.model.response.project.AdminProjectDetailResponse
import com.sprintstart.sprintstartbackend.user.model.response.project.AdminProjectListResponse
import com.sprintstart.sprintstartbackend.user.model.response.project.DeleteProjectResponse
import com.sprintstart.sprintstartbackend.user.model.response.project.ProjectSourceResponse
import com.sprintstart.sprintstartbackend.user.model.response.project.ProjectUserResponse
import com.sprintstart.sprintstartbackend.user.model.response.project.ProjectUserSummaryResponse
import com.sprintstart.sprintstartbackend.user.service.AdminProjectService
import io.mockk.every
import io.mockk.just
import io.mockk.runs
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@WebMvcTest(AdminProjectController::class)
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class AdminProjectControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val objectMapper = jacksonObjectMapper()

    @MockkBean
    private lateinit var adminProjectService: AdminProjectService

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private val adminJwt = jwt().authorities(
        SimpleGrantedAuthority("ROLE_USER"),
        SimpleGrantedAuthority("ROLE_ADMIN"),
    )
    private val userJwt = jwt().authorities(SimpleGrantedAuthority("ROLE_USER"))

    @Test
    fun `getAllProjects returns projects for admins`() {
        val project = projectListResponse()
        every { adminProjectService.getAllProjects() } returns listOf(project)

        mockMvc
            .perform(get("/api/v1/admin/projects").with(adminJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("SprintStart Frontend"))
            .andExpect(jsonPath("$[0].sources[0].type").value("GITHUB"))
            .andExpect(jsonPath("$[0].users[0].username").value("max.mustermann"))

        verify(exactly = 1) { adminProjectService.getAllProjects() }
    }

    @Test
    fun `admin project endpoints reject non-admin users`() {
        mockMvc
            .perform(get("/api/v1/admin/projects").with(userJwt))
            .andExpect(status().isForbidden)

        verify(exactly = 0) { adminProjectService.getAllProjects() }
    }

    @Test
    fun `admin project endpoints reject unauthenticated users`() {
        mockMvc
            .perform(get("/api/v1/admin/projects"))
            .andExpect(status().isUnauthorized)

        verify(exactly = 0) { adminProjectService.getAllProjects() }
    }

    @Test
    fun `getProjectById returns detailed project users with global and project roles`() {
        val projectId = UUID.randomUUID()
        every { adminProjectService.getProjectById(projectId) } returns projectDetailResponse(projectId)

        mockMvc
            .perform(get("/api/v1/admin/projects/$projectId").with(adminJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.users[0].roles[0]").value("USER"))
            .andExpect(jsonPath("$.users[0].projectRoles[0]").value("MANAGER"))
            .andExpect(jsonPath("$.users[0].enabled").value(true))

        verify(exactly = 1) { adminProjectService.getProjectById(projectId) }
    }

    @Test
    fun `getProjectById returns 404 when project does not exist`() {
        val projectId = UUID.randomUUID()
        every { adminProjectService.getProjectById(projectId) } throws ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(get("/api/v1/admin/projects/$projectId").with(adminJwt))
            .andExpect(status().isNotFound)

        verify(exactly = 1) { adminProjectService.getProjectById(projectId) }
    }

    @Test
    fun `createProject returns 201`() {
        val request = CreateAdminProjectRequest(
            name = "SprintStart Frontend",
            description = "Frontend web application",
        )
        every { adminProjectService.createProject(request) } returns projectDetailResponse()

        mockMvc
            .perform(
                post("/api/v1/admin/projects")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("SprintStart Frontend"))

        verify(exactly = 1) { adminProjectService.createProject(request) }
    }

    @Test
    fun `createProject returns 400 when name is blank`() {
        val request = CreateAdminProjectRequest(name = "")

        mockMvc
            .perform(
                post("/api/v1/admin/projects")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isBadRequest)

        verify(exactly = 0) { adminProjectService.createProject(request) }
    }

    @Test
    fun `createProject returns 400 when service rejects request`() {
        val request = CreateAdminProjectRequest(name = "SprintStart Frontend")
        every { adminProjectService.createProject(request) } throws ResponseStatusException(HttpStatus.BAD_REQUEST)

        mockMvc
            .perform(
                post("/api/v1/admin/projects")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isBadRequest)

        verify(exactly = 1) { adminProjectService.createProject(request) }
    }

    @Test
    fun `patchProject returns updated project`() {
        val projectId = UUID.randomUUID()
        val request = PatchAdminProjectRequest(description = "Updated frontend web application")
        every { adminProjectService.patchProject(projectId, request) } returns projectDetailResponse(
            id = projectId,
            description = "Updated frontend web application",
        )

        mockMvc
            .perform(
                patch("/api/v1/admin/projects/$projectId")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.description").value("Updated frontend web application"))

        verify(exactly = 1) { adminProjectService.patchProject(projectId, request) }
    }

    @Test
    fun `patchProject returns 400 when service rejects request`() {
        val projectId = UUID.randomUUID()
        val request = PatchAdminProjectRequest(name = "")
        every { adminProjectService.patchProject(projectId, request) } throws
            ResponseStatusException(HttpStatus.BAD_REQUEST)

        mockMvc
            .perform(
                patch("/api/v1/admin/projects/$projectId")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isBadRequest)

        verify(exactly = 1) { adminProjectService.patchProject(projectId, request) }
    }

    @Test
    fun `patchProject returns 404 when project does not exist`() {
        val projectId = UUID.randomUUID()
        val request = PatchAdminProjectRequest(description = "Updated frontend web application")
        every { adminProjectService.patchProject(projectId, request) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(
                patch("/api/v1/admin/projects/$projectId")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isNotFound)

        verify(exactly = 1) { adminProjectService.patchProject(projectId, request) }
    }

    @Test
    fun `deleteProject returns deleted response`() {
        val projectId = UUID.randomUUID()
        every { adminProjectService.deleteProject(projectId) } returns DeleteProjectResponse(projectId)

        mockMvc
            .perform(delete("/api/v1/admin/projects/$projectId").with(adminJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(projectId.toString()))
            .andExpect(jsonPath("$.deleted").value(true))

        verify(exactly = 1) { adminProjectService.deleteProject(projectId) }
    }

    @Test
    fun `deleteProject returns 404 when project does not exist`() {
        val projectId = UUID.randomUUID()
        every { adminProjectService.deleteProject(projectId) } throws ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(delete("/api/v1/admin/projects/$projectId").with(adminJwt))
            .andExpect(status().isNotFound)

        verify(exactly = 1) { adminProjectService.deleteProject(projectId) }
    }

    @Test
    fun `getProjectUsers returns project-specific users`() {
        val projectId = UUID.randomUUID()
        every { adminProjectService.getProjectUsers(projectId) } returns listOf(projectUserResponse())

        mockMvc
            .perform(get("/api/v1/admin/projects/$projectId/users").with(adminJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].roles[0]").value("USER"))
            .andExpect(jsonPath("$[0].projectRoles[0]").value("MANAGER"))

        verify(exactly = 1) { adminProjectService.getProjectUsers(projectId) }
    }

    @Test
    fun `getProjectUsers returns 404 when project does not exist`() {
        val projectId = UUID.randomUUID()
        every { adminProjectService.getProjectUsers(projectId) } throws ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(get("/api/v1/admin/projects/$projectId/users").with(adminJwt))
            .andExpect(status().isNotFound)

        verify(exactly = 1) { adminProjectService.getProjectUsers(projectId) }
    }

    @Test
    fun `assignUsers returns assigned project users`() {
        val projectId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val request = AssignProjectUsersRequest(userIds = setOf(userId))
        every { adminProjectService.assignUsers(projectId, request) } returns listOf(projectUserResponse(id = userId))

        mockMvc
            .perform(
                post("/api/v1/admin/projects/$projectId/users")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(userId.toString()))

        verify(exactly = 1) { adminProjectService.assignUsers(projectId, request) }
    }

    @Test
    fun `assignUsers returns 400 when user id list is empty`() {
        val projectId = UUID.randomUUID()
        val request = AssignProjectUsersRequest(userIds = emptySet())

        mockMvc
            .perform(
                post("/api/v1/admin/projects/$projectId/users")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isBadRequest)

        verify(exactly = 0) { adminProjectService.assignUsers(projectId, request) }
    }

    @Test
    fun `assignUsers returns 404 when project or user does not exist`() {
        val projectId = UUID.randomUUID()
        val request = AssignProjectUsersRequest(userIds = setOf(UUID.randomUUID()))
        every { adminProjectService.assignUsers(projectId, request) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(
                post("/api/v1/admin/projects/$projectId/users")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isNotFound)

        verify(exactly = 1) { adminProjectService.assignUsers(projectId, request) }
    }

    @Test
    fun `removeUser returns 204 with no body`() {
        val projectId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        every { adminProjectService.removeUser(projectId, userId) } just runs

        mockMvc
            .perform(delete("/api/v1/admin/projects/$projectId/users/$userId").with(adminJwt))
            .andExpect(status().isNoContent)
            .andExpect(content().string(""))

        verify(exactly = 1) { adminProjectService.removeUser(projectId, userId) }
    }

    @Test
    fun `removeUser returns 404 when project or assignment does not exist`() {
        val projectId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        every { adminProjectService.removeUser(projectId, userId) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(delete("/api/v1/admin/projects/$projectId/users/$userId").with(adminJwt))
            .andExpect(status().isNotFound)

        verify(exactly = 1) { adminProjectService.removeUser(projectId, userId) }
    }

    private fun projectListResponse(
        id: UUID = UUID.randomUUID(),
    ) = AdminProjectListResponse(
        id = id,
        name = "SprintStart Frontend",
        description = "Frontend web application",
        sources = listOf(sourceResponse()),
        users = listOf(
            ProjectUserSummaryResponse(
                id = UUID.randomUUID(),
                username = "max.mustermann",
                email = "max.mustermann@example.com",
            ),
        ),
    )

    private fun projectDetailResponse(
        id: UUID = UUID.randomUUID(),
        description: String = "Frontend web application",
    ) = AdminProjectDetailResponse(
        id = id,
        name = "SprintStart Frontend",
        description = description,
        sources = listOf(sourceResponse()),
        users = listOf(projectUserResponse()),
    )

    private fun sourceResponse() = ProjectSourceResponse(
        id = UUID.randomUUID().toString(),
        name = "Frontend GitHub Repo",
        type = "GITHUB",
        status = "CONNECTED",
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
        projectRoles = listOf("MANAGER"),
        enabled = true,
    )
}
