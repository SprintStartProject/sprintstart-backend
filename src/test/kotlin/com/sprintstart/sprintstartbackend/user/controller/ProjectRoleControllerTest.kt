package com.sprintstart.sprintstartbackend.user.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.user.external.enums.SkillStatus
import com.sprintstart.sprintstartbackend.user.external.security.ProjectAuthorization
import com.sprintstart.sprintstartbackend.user.model.entity.ProjectRole
import com.sprintstart.sprintstartbackend.user.model.request.AcceptSkillSuggestionRequest
import com.sprintstart.sprintstartbackend.user.model.request.AssignProjectRoleRequest
import com.sprintstart.sprintstartbackend.user.model.request.CreateProjectRoleRequest
import com.sprintstart.sprintstartbackend.user.model.request.SuggestSkillsRequest
import com.sprintstart.sprintstartbackend.user.model.request.UpdateRoleSkillsRequest
import com.sprintstart.sprintstartbackend.user.model.response.skill.GetSkillResponse
import com.sprintstart.sprintstartbackend.user.model.response.skill.SkillSuggestionItemResponse
import com.sprintstart.sprintstartbackend.user.model.response.skill.UpdateRoleSkillsResponse
import com.sprintstart.sprintstartbackend.user.service.ProjectRoleService
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
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

    @MockkBean(name = "projectAuth")
    private lateinit var projectAuth: ProjectAuthorization

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private val userJwt = jwt()
        .authorities(SimpleGrantedAuthority("ROLE_USER"))

    private val adminJwt = jwt()
        .authorities(
            SimpleGrantedAuthority("ROLE_USER"),
            SimpleGrantedAuthority("ROLE_ADMIN"),
        )

    private val pmJwt = jwt()
        .authorities(
            SimpleGrantedAuthority("ROLE_USER"),
            SimpleGrantedAuthority("ROLE_PM"),
        )

    private val hrJwt = jwt()
        .authorities(
            SimpleGrantedAuthority("ROLE_USER"),
            SimpleGrantedAuthority("ROLE_HR"),
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
                    .with(adminJwt),
            ).andExpect(status().isOk)
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
                    .with(userJwt), // userJwt only has ROLE_USER, but we need ADMIN, PM, or HR
            ).andExpect(status().isForbidden)

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
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isCreated)
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
                    .with(adminJwt),
            ).andExpect(status().isNoContent)

        verify(exactly = 1) { projectRoleService.deleteRole(roleId) }
    }

    @Test
    fun `assignRoleToUser should return 200`() {
        val userId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        val request = AssignProjectRoleRequest(UUID.randomUUID())
        every { projectRoleService.assignRoleToUser(userId, projectId, request.roleId) } just Runs

        mockMvc
            .perform(
                post("/api/v1/projects/$projectId/users/$userId/project-roles")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)

        verify(exactly = 1) { projectRoleService.assignRoleToUser(userId, projectId, request.roleId) }
    }

    @Test
    fun `unassignRoleFromUser should return 204`() {
        val userId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        val roleId = UUID.randomUUID()
        every { projectRoleService.unassignRoleFromUser(userId, projectId, roleId) } just Runs

        mockMvc
            .perform(
                delete("/api/v1/projects/$projectId/users/$userId/project-roles/$roleId")
                    .with(adminJwt),
            ).andExpect(status().isNoContent)

        verify(exactly = 1) { projectRoleService.unassignRoleFromUser(userId, projectId, roleId) }
    }

    @Test
    fun `getSkillsForRole should return 200 and skills for the role`() {
        val roleId = UUID.randomUUID()
        val dto = GetSkillResponse(
            id = UUID.randomUUID(),
            name = "Kotlin",
            roleIds = listOf(roleId),
            status = SkillStatus.ACTIVE,
            category = "Languages & Paradigms",
            universal = false,
        )
        every { projectRoleService.getSkillsForRole(roleId) } returns listOf(dto)

        mockMvc
            .perform(get("/api/v1/projectRoles/$roleId/skills").with(userJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { projectRoleService.getSkillsForRole(roleId) }
    }

    @Test
    fun `getSkillsForRole should return 404 when role not found`() {
        val roleId = UUID.randomUUID()
        every { projectRoleService.getSkillsForRole(roleId) } throws ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(get("/api/v1/projectRoles/$roleId/skills").with(userJwt))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `setSkillsForRole should return 200 for admins`() {
        val roleId = UUID.randomUUID()
        val request = UpdateRoleSkillsRequest(skillIds = listOf(UUID.randomUUID()))
        val dto = UpdateRoleSkillsResponse(
            id = request.skillIds[0],
            name = "Kotlin",
            roleIds = listOf(roleId),
            status = SkillStatus.ACTIVE,
            category = "Languages & Paradigms",
            universal = false,
        )
        every { projectRoleService.setSkillsForRole(roleId, request) } returns listOf(dto)

        mockMvc
            .perform(
                put("/api/v1/projectRoles/$roleId/skills")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { projectRoleService.setSkillsForRole(roleId, request) }
    }

    @Test
    fun `setSkillsForRole should return 403 for normal users`() {
        val roleId = UUID.randomUUID()
        val request = UpdateRoleSkillsRequest(skillIds = listOf(UUID.randomUUID()))

        mockMvc
            .perform(
                put("/api/v1/projectRoles/$roleId/skills")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isForbidden)

        verify(exactly = 0) { projectRoleService.setSkillsForRole(any(), any()) }
    }

    @Test
    fun `setSkillsForRole should return 404 when role not found`() {
        val roleId = UUID.randomUUID()
        val request = UpdateRoleSkillsRequest(skillIds = listOf(UUID.randomUUID()))
        every { projectRoleService.setSkillsForRole(roleId, request) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(
                put("/api/v1/projectRoles/$roleId/skills")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isNotFound)
    }

    @Test
    fun `setSkillsForRole should return 400 when unassigning would orphan a skill`() {
        val roleId = UUID.randomUUID()
        val request = UpdateRoleSkillsRequest(skillIds = emptyList())
        every { projectRoleService.setSkillsForRole(roleId, request) } throws
            ResponseStatusException(HttpStatus.BAD_REQUEST)

        mockMvc
            .perform(
                put("/api/v1/projectRoles/$roleId/skills")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `suggestSkillsForRole should return 200 for admins without body`() {
        val roleId = UUID.randomUUID()
        val dto = SkillSuggestionItemResponse(
            skillId = UUID.randomUUID(),
            name = "Kotlin",
            category = "Languages & Paradigms",
            reason = "Used in backend",
            confidence = "high",
            isNew = false,
            chunkIds = listOf("c1"),
        )
        coEvery { projectRoleService.suggestSkillsForRole(roleId, null) } returns listOf(dto)

        val asyncResult = mockMvc
            .perform(
                post("/api/v1/projectRoles/$roleId/skills/suggest")
                    .with(adminJwt),
            ).andExpect(request().asyncStarted())
            .andReturn()

        mockMvc
            .perform(asyncDispatch(asyncResult))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        coVerify(exactly = 1) { projectRoleService.suggestSkillsForRole(roleId, null) }
    }

    @Test
    fun `suggestSkillsForRole should return 200 for PMs with authorized projectId`() {
        val roleId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        val request = SuggestSkillsRequest(projectId = projectId, industry = "Fintech")
        val dto = SkillSuggestionItemResponse(
            name = "Docker",
            category = "DevOps",
            reason = "Containerization",
            confidence = "high",
            isNew = true,
        )
        every { projectAuth.canAccessProject(any(), projectId) } returns true
        coEvery { projectRoleService.suggestSkillsForRole(roleId, request) } returns listOf(dto)

        val asyncResult = mockMvc
            .perform(
                post("/api/v1/projectRoles/$roleId/skills/suggest")
                    .with(pmJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(request().asyncStarted())
            .andReturn()

        mockMvc
            .perform(asyncDispatch(asyncResult))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        coVerify(exactly = 1) { projectRoleService.suggestSkillsForRole(roleId, request) }
    }

    @Test
    fun `suggestSkillsForRole should return 403 when PM sends unauthorized projectId`() {
        val roleId = UUID.randomUUID()
        val foreignProjectId = UUID.randomUUID()
        val request = SuggestSkillsRequest(projectId = foreignProjectId)
        every { projectAuth.canAccessProject(any(), foreignProjectId) } returns false

        val asyncResult = mockMvc
            .perform(
                post("/api/v1/projectRoles/$roleId/skills/suggest")
                    .with(pmJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(request().asyncStarted())
            .andReturn()

        mockMvc
            .perform(asyncDispatch(asyncResult))
            .andExpect(status().isForbidden)

        coVerify(exactly = 0) { projectRoleService.suggestSkillsForRole(any(), any()) }
    }

    @Test
    fun `suggestSkillsForRole should return 403 for normal users`() {
        val roleId = UUID.randomUUID()

        val asyncResult = mockMvc
            .perform(
                post("/api/v1/projectRoles/$roleId/skills/suggest")
                    .with(userJwt),
            ).andExpect(request().asyncStarted())
            .andReturn()

        mockMvc
            .perform(asyncDispatch(asyncResult))
            .andExpect(status().isForbidden)

        coVerify(exactly = 0) { projectRoleService.suggestSkillsForRole(any(), any()) }
    }

    @Test
    fun `suggestSkillsForRole should return 403 for HR`() {
        val roleId = UUID.randomUUID()

        val asyncResult = mockMvc
            .perform(
                post("/api/v1/projectRoles/$roleId/skills/suggest")
                    .with(hrJwt),
            ).andExpect(request().asyncStarted())
            .andReturn()

        mockMvc
            .perform(asyncDispatch(asyncResult))
            .andExpect(status().isForbidden)

        coVerify(exactly = 0) { projectRoleService.suggestSkillsForRole(any(), any()) }
    }

    @Test
    fun `suggestSkillsForRole should return 404 when role not found`() {
        val roleId = UUID.randomUUID()
        coEvery { projectRoleService.suggestSkillsForRole(roleId, null) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND)

        val asyncResult = mockMvc
            .perform(
                post("/api/v1/projectRoles/$roleId/skills/suggest")
                    .with(adminJwt),
            ).andExpect(request().asyncStarted())
            .andReturn()

        mockMvc
            .perform(asyncDispatch(asyncResult))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `acceptSkillSuggestion should return 200 for admins`() {
        val roleId = UUID.randomUUID()
        val request = AcceptSkillSuggestionRequest(skillId = UUID.randomUUID())
        val dto = UpdateRoleSkillsResponse(
            id = request.skillId!!,
            name = "Kotlin",
            roleIds = listOf(roleId),
            status = SkillStatus.ACTIVE,
            category = "Languages & Paradigms",
            universal = false,
        )
        every { projectRoleService.acceptSkillSuggestion(roleId, request) } returns listOf(dto)

        mockMvc
            .perform(
                post("/api/v1/projectRoles/$roleId/skills/suggestions/accept")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { projectRoleService.acceptSkillSuggestion(roleId, request) }
    }

    @Test
    fun `acceptSkillSuggestion should return 200 for PMs`() {
        val roleId = UUID.randomUUID()
        val request = AcceptSkillSuggestionRequest(name = "Docker", category = "DevOps")
        val dto = UpdateRoleSkillsResponse(
            id = UUID.randomUUID(),
            name = "Docker",
            roleIds = listOf(roleId),
            status = SkillStatus.ACTIVE,
            category = "DevOps",
            universal = false,
        )
        every { projectRoleService.acceptSkillSuggestion(roleId, request) } returns listOf(dto)

        mockMvc
            .perform(
                post("/api/v1/projectRoles/$roleId/skills/suggestions/accept")
                    .with(pmJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { projectRoleService.acceptSkillSuggestion(roleId, request) }
    }

    @Test
    fun `acceptSkillSuggestion should return 403 for normal users`() {
        val roleId = UUID.randomUUID()
        val request = AcceptSkillSuggestionRequest(name = "Docker")

        mockMvc
            .perform(
                post("/api/v1/projectRoles/$roleId/skills/suggestions/accept")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isForbidden)

        verify(exactly = 0) { projectRoleService.acceptSkillSuggestion(any(), any()) }
    }

    @Test
    fun `acceptSkillSuggestion should return 403 for HR`() {
        val roleId = UUID.randomUUID()
        val request = AcceptSkillSuggestionRequest(name = "Docker")

        mockMvc
            .perform(
                post("/api/v1/projectRoles/$roleId/skills/suggestions/accept")
                    .with(hrJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isForbidden)

        verify(exactly = 0) { projectRoleService.acceptSkillSuggestion(any(), any()) }
    }

    @Test
    fun `acceptSkillSuggestion should return 400 when request invalid or skill retired`() {
        val roleId = UUID.randomUUID()
        val request = AcceptSkillSuggestionRequest()
        every { projectRoleService.acceptSkillSuggestion(roleId, request) } throws
            ResponseStatusException(HttpStatus.BAD_REQUEST)

        mockMvc
            .perform(
                post("/api/v1/projectRoles/$roleId/skills/suggestions/accept")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `acceptSkillSuggestion should return 404 when role not found`() {
        val roleId = UUID.randomUUID()
        val request = AcceptSkillSuggestionRequest(name = "Docker")
        every { projectRoleService.acceptSkillSuggestion(roleId, request) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(
                post("/api/v1/projectRoles/$roleId/skills/suggestions/accept")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isNotFound)
    }
}
