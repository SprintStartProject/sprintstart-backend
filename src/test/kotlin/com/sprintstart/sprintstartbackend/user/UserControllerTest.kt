package com.sprintstart.sprintstartbackend.user

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.user.controller.AdminUserController
import com.sprintstart.sprintstartbackend.user.controller.UserSelfController
import com.sprintstart.sprintstartbackend.user.external.enums.Role
import com.sprintstart.sprintstartbackend.user.external.enums.WorkingArea
import com.sprintstart.sprintstartbackend.user.model.dto.DeleteUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.GetUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.PatchMeRequest
import com.sprintstart.sprintstartbackend.user.model.dto.PatchUserRequest
import com.sprintstart.sprintstartbackend.user.model.dto.UpdateUserEnabledRequest
import com.sprintstart.sprintstartbackend.user.service.UserService
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

private const val TEST_AUTH_ID = "testAuthId"

// Todo: update this test with error paths

@WebMvcTest(controllers = [UserSelfController::class, AdminUserController::class])
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class UserControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val objectMapper = jacksonObjectMapper()

    @MockkBean
    private lateinit var userService: UserService

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private val userJwt = jwt().authorities(SimpleGrantedAuthority("ROLE_USER"))
    private val adminJwt = jwt().authorities(
        SimpleGrantedAuthority("ROLE_USER"),
        SimpleGrantedAuthority("ROLE_ADMIN"),
    )

    @Test
    fun `getMe returns current user`() {
        every { userService.getMe(any()) } returns userResponse()

        mockMvc
            .perform(get("/api/v1/users/me").with(userJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.firstName").value("Alice"))
            .andExpect(jsonPath("$.workingArea").value("BACKEND_DEV"))
            .andExpect(jsonPath("$.permissionGroup").value("USER"))

        verify(exactly = 1) { userService.getMe(any()) }
    }

    @Test
    fun `patchMe updates self-service profile fields`() {
        val request = PatchMeRequest(
            email = "new@mail.de",
            firstName = "Alicia",
            profileIcon = "icon-star",
            workingArea = WorkingArea.FRONTEND_DEV,
        )
        every { userService.patchMe("user", request) } returns userResponse(email = "new@mail.de", firstName = "Alicia")

        mockMvc
            .perform(
                patch("/api/v1/users/me")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("new@mail.de"))
            .andExpect(jsonPath("$.firstName").value("Alicia"))

        verify(exactly = 1) { userService.patchMe("user", request) }
    }

    @Test
    fun `get admin users uses admin namespace`() {
        every { userService.getAllUsers() } returns listOf(userResponse())

        mockMvc
            .perform(get("/api/v1/admin/users").with(adminJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].username").value("alice"))

        verify(exactly = 1) { userService.getAllUsers() }
    }

    @Test
    fun `admin namespace rejects non-admin user`() {
        mockMvc
            .perform(get("/api/v1/admin/users").with(userJwt))
            .andExpect(status().isForbidden)

        verify(exactly = 0) { userService.getAllUsers() }
    }

    @Test
    fun `patch admin user updates base fields and permission group`() {
        val id = UUID.randomUUID()
        val request = PatchUserRequest(
            email = "new@mail.de",
            firstName = "Alicia",
            workingArea = WorkingArea.FRONTEND_DEV,
            permissionGroup = Role.ADMIN,
        )
        every {
            userService.patchAdminUserById(id, request)
        } returns userResponse(
            id = id,
            permissionGroup = Role.ADMIN,
        )

        mockMvc
            .perform(
                patch("/api/v1/admin/users/$id")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.permissionGroup").value("ADMIN"))

        verify(exactly = 1) { userService.patchAdminUserById(id, request) }
    }

    @Test
    fun `patch admin user enabled updates account state`() {
        val id = UUID.randomUUID()
        val request = UpdateUserEnabledRequest(enabled = false)
        every { userService.updateUserEnabledById(id, request) } returns userResponse(id = id, enabled = false)

        mockMvc
            .perform(
                patch("/api/v1/admin/users/$id/enabled")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.enabled").value(false))

        verify(exactly = 1) { userService.updateUserEnabledById(id, request) }
    }

    @Test
    fun `delete admin user returns deleted response`() {
        val id = UUID.randomUUID()
        every { userService.deleteAdminUserById(id) } returns DeleteUserResponse(id = id)

        mockMvc
            .perform(delete("/api/v1/admin/users/$id").with(adminJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.deleted").value(true))

        verify(exactly = 1) { userService.deleteAdminUserById(id) }
    }

    private fun userResponse(
        id: UUID = UUID.randomUUID(),
        email: String = "alice@mail.de",
        firstName: String = "Alice",
        workingArea: WorkingArea = WorkingArea.BACKEND_DEV,
        enabled: Boolean = true,
        permissionGroup: Role = Role.USER,
    ) = GetUserResponse(
        id = id,
        authId = TEST_AUTH_ID,
        username = "alice",
        email = email,
        firstName = firstName,
        lastName = "Developer",
        workingArea = workingArea,
        permissionGroup = permissionGroup,
        enabled = enabled,
        profileIcon = "icon-star",
        hasCompletedOnboarding = true,
    )
}
