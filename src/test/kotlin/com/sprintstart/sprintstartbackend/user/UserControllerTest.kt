package com.sprintstart.sprintstartbackend.user

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.user.controller.UserController
import com.sprintstart.sprintstartbackend.user.external.enums.WorkingArea
import com.sprintstart.sprintstartbackend.user.model.dto.GetUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.PatchMeRequest
import com.sprintstart.sprintstartbackend.user.model.dto.PatchUserRequest
import com.sprintstart.sprintstartbackend.user.model.dto.PatchUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.UpdateUserRequest
import com.sprintstart.sprintstartbackend.user.model.dto.UpdateUserResponse
import com.sprintstart.sprintstartbackend.user.service.UserService
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@WebMvcTest(UserController::class)
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
    fun `getAllUsers should return 200 and all users`() {
        val response1 = GetUserResponse(
            id = UUID.randomUUID(),
            authId = "auth-1",
            username = "alice",
            email = "alice.dev@mail.de",
            firstname = "Alice",
            lastname = "Developer",
            workingArea = WorkingArea.BACKEND_DEV,
        )
        val response2 = GetUserResponse(
            id = UUID.randomUUID(),
            authId = "auth-2",
            username = "bob",
            email = "bob.front@mail.de",
            firstname = "Bob",
            lastname = "Frontend",
            workingArea = WorkingArea.FRONTEND_DEV,
        )

        every { userService.getAllUsers() } returns listOf(response1, response2)

        mockMvc
            .perform(
                get("/api/v1/users")
                    .with(adminJwt),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { userService.getAllUsers() }
    }

    @Test
    fun `getAllUsers should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/users"))
            .andExpect(status().isUnauthorized)

        verify(exactly = 0) { userService.getAllUsers() }
    }

    @Test
    fun `getAllUsers should return 403 when authenticated without admin PM or HR role`() {
        mockMvc
            .perform(
                get("/api/v1/users")
                    .with(userJwt),
            ).andExpect(status().isForbidden)

        verify(exactly = 0) { userService.getAllUsers() }
    }

    @Test
    fun `getMe should return 200 and current user`() {
        val response = GetUserResponse(
            id = UUID.randomUUID(),
            authId = "auth-1",
            username = "alice",
            email = "alice.dev@mail.de",
            firstname = "Alice",
            lastname = "Developer",
            workingArea = WorkingArea.BACKEND_DEV,
        )

        every { userService.getMe("user") } returns response

        mockMvc
            .perform(
                get("/api/v1/users/me")
                    .with(userJwt),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { userService.getMe("user") }
    }

    @Test
    fun `getMe should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized)

        verify(exactly = 0) { userService.getMe(any()) }
    }

    @Test
    fun `getMe should return 403 when authenticated without user role`() {
        mockMvc
            .perform(
                get("/api/v1/users/me")
                    .with(noUserRoleJwt),
            ).andExpect(status().isForbidden)

        verify(exactly = 0) { userService.getMe(any()) }
    }

    @Test
    fun `patchMe should return 200 and patched current user`() {
        val request = PatchMeRequest(workingArea = WorkingArea.FRONTEND_DEV)
        val response = GetUserResponse(
            id = UUID.randomUUID(),
            authId = "auth-1",
            username = "alice",
            email = "alice.dev@mail.de",
            firstname = "Alice",
            lastname = "Developer",
            workingArea = WorkingArea.FRONTEND_DEV,
        )

        every { userService.patchMe("user", request) } returns response

        mockMvc
            .perform(
                patch("/api/v1/users/me")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { userService.patchMe("user", request) }
    }

    @Test
    fun `patchMe should return 401 when not authenticated`() {
        val request = PatchMeRequest(workingArea = WorkingArea.FRONTEND_DEV)

        mockMvc
            .perform(
                patch("/api/v1/users/me")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isUnauthorized)

        verify(exactly = 0) { userService.patchMe(any(), any()) }
    }

    @Test
    fun `patchMe should return 403 when authenticated without user role`() {
        val request = PatchMeRequest(workingArea = WorkingArea.FRONTEND_DEV)

        mockMvc
            .perform(
                patch("/api/v1/users/me")
                    .with(noUserRoleJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isForbidden)

        verify(exactly = 0) { userService.patchMe(any(), any()) }
    }

    @Test
    fun `getUserById should return 200 and user`() {
        val id = UUID.randomUUID()
        val response = GetUserResponse(
            id = id,
            authId = "auth-1",
            username = "alice",
            email = "alice.dev@mail.de",
            firstname = "Alice",
            lastname = "Developer",
            workingArea = WorkingArea.BACKEND_DEV,
        )

        every { userService.getUserById(id) } returns response

        mockMvc
            .perform(
                get("/api/v1/users/$id")
                    .with(adminJwt),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { userService.getUserById(id) }
    }

    @Test
    fun `getUserById should return 401 when not authenticated`() {
        val id = UUID.randomUUID()

        mockMvc
            .perform(get("/api/v1/users/$id"))
            .andExpect(status().isUnauthorized)

        verify(exactly = 0) { userService.getUserById(any()) }
    }

    @Test
    fun `getUserById should return 403 when authenticated without admin PM or HR role`() {
        val id = UUID.randomUUID()

        mockMvc
            .perform(
                get("/api/v1/users/$id")
                    .with(userJwt),
            ).andExpect(status().isForbidden)

        verify(exactly = 0) { userService.getUserById(any()) }
    }

    @Test
    fun `getUserById should return 404 when not found`() {
        val id = UUID.randomUUID()

        every { userService.getUserById(id) } throws ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(
                get("/api/v1/users/$id")
                    .with(adminJwt),
            ).andExpect(status().isNotFound)

        verify(exactly = 1) { userService.getUserById(id) }
    }

    @Test
    fun `updateUserById should return 200 and updated user`() {
        val id = UUID.randomUUID()
        val request = UpdateUserRequest(workingArea = WorkingArea.BACKEND_DEV)
        val response = UpdateUserResponse(
            id = id,
            authId = "auth-1",
            username = "alice",
            email = "alice.dev@mail.de",
            firstname = "Alice",
            lastname = "Developer",
            workingArea = WorkingArea.BACKEND_DEV,
        )

        every { userService.updateUserById(id, request) } returns response

        mockMvc
            .perform(
                put("/api/v1/users/$id")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { userService.updateUserById(id, request) }
    }

    @Test
    fun `updateUserById should return 401 when not authenticated`() {
        val id = UUID.randomUUID()
        val request = UpdateUserRequest(workingArea = WorkingArea.BACKEND_DEV)

        mockMvc
            .perform(
                put("/api/v1/users/$id")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isUnauthorized)

        verify(exactly = 0) { userService.updateUserById(any(), any()) }
    }

    @Test
    fun `updateUserById should return 403 when authenticated without admin PM or HR role`() {
        val id = UUID.randomUUID()
        val request = UpdateUserRequest(workingArea = WorkingArea.BACKEND_DEV)

        mockMvc
            .perform(
                put("/api/v1/users/$id")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isForbidden)

        verify(exactly = 0) { userService.updateUserById(any(), any()) }
    }

    @Test
    fun `updateUserById should return 404 when not found`() {
        val id = UUID.randomUUID()
        val request = UpdateUserRequest(workingArea = WorkingArea.BACKEND_DEV)

        every { userService.updateUserById(id, request) } throws ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(
                put("/api/v1/users/$id")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isNotFound)

        verify(exactly = 1) { userService.updateUserById(id, request) }
    }

    @Test
    fun `patchUserById should return 200 and patched user`() {
        val id = UUID.randomUUID()
        val request = PatchUserRequest(workingArea = WorkingArea.FRONTEND_DEV)
        val response = PatchUserResponse(
            id = id,
            authId = "auth-1",
            username = "alice",
            email = "alice.dev@mail.de",
            firstname = "Alice",
            lastname = "Developer",
            workingArea = WorkingArea.FRONTEND_DEV,
        )

        every { userService.patchUserById(id, request) } returns response

        mockMvc
            .perform(
                patch("/api/v1/users/$id")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { userService.patchUserById(id, request) }
    }

    @Test
    fun `patchUserById should return 401 when not authenticated`() {
        val id = UUID.randomUUID()
        val request = PatchUserRequest(workingArea = WorkingArea.FRONTEND_DEV)

        mockMvc
            .perform(
                patch("/api/v1/users/$id")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isUnauthorized)

        verify(exactly = 0) { userService.patchUserById(any(), any()) }
    }

    @Test
    fun `patchUserById should return 403 when authenticated without admin PM or HR role`() {
        val id = UUID.randomUUID()
        val request = PatchUserRequest(workingArea = WorkingArea.FRONTEND_DEV)

        mockMvc
            .perform(
                patch("/api/v1/users/$id")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isForbidden)

        verify(exactly = 0) { userService.patchUserById(any(), any()) }
    }

    @Test
    fun `patchUserById should return 404 when not found`() {
        val id = UUID.randomUUID()
        val request = PatchUserRequest(workingArea = WorkingArea.FRONTEND_DEV)

        every { userService.patchUserById(id, request) } throws ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(
                patch("/api/v1/users/$id")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isNotFound)

        verify(exactly = 1) { userService.patchUserById(id, request) }
    }

    @Test
    fun `deleteUserById should return 204`() {
        val id = UUID.randomUUID()

        every { userService.deleteUserById(id) } just Runs

        mockMvc
            .perform(
                delete("/api/v1/users/$id")
                    .with(adminJwt),
            ).andExpect(status().isNoContent)

        verify(exactly = 1) { userService.deleteUserById(id) }
    }

    @Test
    fun `deleteUserById should return 401 when not authenticated`() {
        val id = UUID.randomUUID()

        mockMvc
            .perform(delete("/api/v1/users/$id"))
            .andExpect(status().isUnauthorized)

        verify(exactly = 0) { userService.deleteUserById(any()) }
    }

    @Test
    fun `deleteUserById should return 403 when authenticated without admin PM or HR role`() {
        val id = UUID.randomUUID()

        mockMvc
            .perform(
                delete("/api/v1/users/$id")
                    .with(userJwt),
            ).andExpect(status().isForbidden)

        verify(exactly = 0) { userService.deleteUserById(any()) }
    }

    @Test
    fun `deleteUserById should return 404 when not found`() {
        val id = UUID.randomUUID()

        every { userService.deleteUserById(id) } throws ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(
                delete("/api/v1/users/$id")
                    .with(adminJwt),
            ).andExpect(status().isNotFound)

        verify(exactly = 1) { userService.deleteUserById(id) }
    }
}
