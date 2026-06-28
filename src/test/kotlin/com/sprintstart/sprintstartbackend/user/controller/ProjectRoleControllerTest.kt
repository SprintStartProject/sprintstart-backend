package com.sprintstart.sprintstartbackend.user.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.user.model.entity.ProjectRole
import com.sprintstart.sprintstartbackend.user.model.request.AssignProjectRoleRequest
import com.sprintstart.sprintstartbackend.user.model.request.CreateProjectRoleRequest
import com.sprintstart.sprintstartbackend.user.service.ProjectRoleService
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@WebMvcTest(ProjectRoleController::class)
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class ProjectRoleControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val objectMapper = jacksonObjectMapper()

    @MockkBean
    private lateinit var projectRoleService: ProjectRoleService

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private val userJwt = jwt()
        .authorities(SimpleGrantedAuthority("ROLE_USER"))

    private val adminJwt = jwt()
        .authorities(
            SimpleGrantedAuthority("ROLE_USER"),
            SimpleGrantedAuthority("ROLE_ADMIN"),
        )

    private val noUserRoleJwt = jwt()
        .authorities(SimpleGrantedAuthority("ROLE_NONE"))

    @Test
    fun `getAllRoles should return 200 and all roles`() {
        val role = ProjectRole(id = UUID.randomUUID(), name = "Developer", description = "Writes code")
        every { projectRoleService.getAllRoles() } returns listOf(role)

        mockMvc
            .perform(
                get("/api/v1/projectRoles")
                    .with(adminJwt)
            )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { projectRoleService.getAllRoles() }
    }

    @Test
    fun `getAllRoles should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/projectRoles"))
            .andExpect(status().isUnauthorized)

        verify(exactly = 0) { projectRoleService.getAllRoles() }
    }

    @Test
    fun `getAllRoles should return 403 when authenticated without proper role`() {
        mockMvc
            .perform(
                get("/api/v1/projectRoles")
                    .with(userJwt) // userJwt only has ROLE_USER, but we need ADMIN, PM, or HR
            )
            .andExpect(status().isForbidden)

        verify(exactly = 0) { projectRoleService.getAllRoles() }
    }

    @Test
    fun `createRole should return 201 and created role`() {
        val request = CreateProjectRoleRequest("Developer", "Writes code")
        val role = ProjectRole(id = UUID.randomUUID(), name = "Developer", description = "Writes code")
        every { projectRoleService.createRole(request) } returns role

        mockMvc
            .perform(
                post("/api/v1/projectRoles")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isCreated)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { projectRoleService.createRole(request) }
    }

    @Test
    fun `deleteRole should return 204`() {
        val roleId = UUID.randomUUID()
        every { projectRoleService.deleteRole(roleId) } just Runs

        mockMvc
            .perform(
                delete("/api/v1/projectRoles/$roleId")
                    .with(adminJwt)
            )
            .andExpect(status().isNoContent)

        verify(exactly = 1) { projectRoleService.deleteRole(roleId) }
    }

    @Test
    fun `assignRoleToUser should return 200`() {
        val userId = UUID.randomUUID()
        val request = AssignProjectRoleRequest(UUID.randomUUID())
        every { projectRoleService.assignRoleToUser(userId, request.roleId) } just Runs

        mockMvc
            .perform(
                post("/api/v1/users/$userId/project-roles")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isOk)

        verify(exactly = 1) { projectRoleService.assignRoleToUser(userId, request.roleId) }
    }

    @Test
    fun `unassignRoleFromUser should return 204`() {
        val userId = UUID.randomUUID()
        val roleId = UUID.randomUUID()
        every { projectRoleService.unassignRoleFromUser(userId, roleId) } just Runs

        mockMvc
            .perform(
                delete("/api/v1/users/$userId/project-roles/$roleId")
                    .with(adminJwt)
            )
            .andExpect(status().isNoContent)

        verify(exactly = 1) { projectRoleService.unassignRoleFromUser(userId, roleId) }
    }
}
