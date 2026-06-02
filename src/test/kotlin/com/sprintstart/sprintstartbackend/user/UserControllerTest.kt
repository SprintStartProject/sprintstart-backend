package com.sprintstart.sprintstartbackend.user

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.user.controller.UserController
import com.sprintstart.sprintstartbackend.user.external.enums.Role
import com.sprintstart.sprintstartbackend.user.external.enums.WorkingArea
import com.sprintstart.sprintstartbackend.user.model.dto.CreateUserRequest
import com.sprintstart.sprintstartbackend.user.model.dto.CreateUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.GetUserResponse
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
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@WebMvcTest(UserController::class)
class UserControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val objectMapper = jacksonObjectMapper()

    @MockkBean
    private lateinit var userService: UserService

    @Test
    fun `createUser should return 201 and created user`() {
        val request = CreateUserRequest(
            username = "max_backend",
            firstname = "Max",
            lastname = "Backend",
            workingArea = WorkingArea.BACKEND_DEV,
        )

        val response = CreateUserResponse(
            id = UUID.randomUUID(),
            username = "max_backend",
            firstname = "Max",
            lastname = "Backend",
            primaryRole = Role.NO_ROLE,
            secondaryRole = Role.NO_ROLE,
            workingArea = WorkingArea.BACKEND_DEV,
        )

        every {
            userService.createUser(request)
        } returns response

        mockMvc
            .perform(
                post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isCreated)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(response.id.toString()))
            .andExpect(jsonPath("$.username").value("max_backend"))
            .andExpect(jsonPath("$.firstname").value("Max"))
            .andExpect(jsonPath("$.lastname").value("Backend"))
            .andExpect(jsonPath("$.primaryRole").value("NO_ROLE"))
            .andExpect(jsonPath("$.secondaryRole").value("NO_ROLE"))
            .andExpect(jsonPath("$.workingArea").value("BACKEND_DEV"))

        verify(exactly = 1) {
            userService.createUser(request)
        }
    }

    @Test
    fun `createUser should return 400 on invalid body`() {
        val invalidRequest = CreateUserRequest(
            username = "",
            firstname = "Max",
            lastname = "",
            workingArea = WorkingArea.BACKEND_DEV,
        )

        mockMvc
            .perform(
                post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidRequest)),
            ).andExpect(status().isBadRequest)

        verify(exactly = 0) {
            userService.createUser(any())
        }
    }

    @Test
    fun `getAllUsers should return 200 and list of users`() {
        val users = listOf(
            GetUserResponse(
                id = UUID.randomUUID(),
                username = "max_backend",
                firstname = "Max",
                lastname = "Backend",
                primaryRole = Role.EXISTING_MEMBER,
                secondaryRole = Role.ADMIN,
                workingArea = WorkingArea.BACKEND_DEV,
            ),
            GetUserResponse(
                id = UUID.randomUUID(),
                username = "anna_frontend",
                firstname = "Anna",
                lastname = "Frontend",
                primaryRole = Role.EXISTING_MEMBER,
                secondaryRole = Role.NO_ROLE,
                workingArea = WorkingArea.FRONTEND_DEV,
            ),
        )

        every {
            userService.getAllUsers()
        } returns users

        mockMvc
            .perform(
                get("/api/v1/users"),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value(users[0].id.toString()))
            .andExpect(jsonPath("$[0].username").value("max_backend"))
            .andExpect(jsonPath("$[0].firstname").value("Max"))
            .andExpect(jsonPath("$[0].lastname").value("Backend"))
            .andExpect(jsonPath("$[0].primaryRole").value("EXISTING_MEMBER"))
            .andExpect(jsonPath("$[0].secondaryRole").value("ADMIN"))
            .andExpect(jsonPath("$[0].workingArea").value("BACKEND_DEV"))
            .andExpect(jsonPath("$[1].id").value(users[1].id.toString()))
            .andExpect(jsonPath("$[1].username").value("anna_frontend"))
            .andExpect(jsonPath("$[1].firstname").value("Anna"))
            .andExpect(jsonPath("$[1].lastname").value("Frontend"))
            .andExpect(jsonPath("$[1].primaryRole").value("EXISTING_MEMBER"))
            .andExpect(jsonPath("$[1].secondaryRole").value("NO_ROLE"))
            .andExpect(jsonPath("$[1].workingArea").value("FRONTEND_DEV"))

        verify(exactly = 1) {
            userService.getAllUsers()
        }
    }

    @Test
    fun `getUserById should return 200 and user`() {
        val userId = UUID.randomUUID()

        val user = GetUserResponse(
            id = userId,
            username = "max_backend",
            firstname = "Max",
            lastname = "Backend",
            primaryRole = Role.EXISTING_MEMBER,
            secondaryRole = Role.ADMIN,
            workingArea = WorkingArea.BACKEND_DEV,
        )

        every {
            userService.getUserById(userId)
        } returns user

        mockMvc
            .perform(
                get("/api/v1/users/{userId}", userId),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(userId.toString()))
            .andExpect(jsonPath("$.username").value("max_backend"))
            .andExpect(jsonPath("$.firstname").value("Max"))
            .andExpect(jsonPath("$.lastname").value("Backend"))
            .andExpect(jsonPath("$.primaryRole").value("EXISTING_MEMBER"))
            .andExpect(jsonPath("$.secondaryRole").value("ADMIN"))
            .andExpect(jsonPath("$.workingArea").value("BACKEND_DEV"))

        verify(exactly = 1) {
            userService.getUserById(userId)
        }
    }

    @Test
    fun `getUserById should return 404 if user not found`() {
        val userId = UUID.randomUUID()

        every {
            userService.getUserById(userId)
        } throws ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "User with id: $userId not found",
        )

        mockMvc
            .perform(
                get("/api/v1/users/{userId}", userId),
            ).andExpect(status().isNotFound)

        verify(exactly = 1) {
            userService.getUserById(userId)
        }
    }

    @Test
    fun `updateUserById should return 200 and updated user`() {
        val userId = UUID.randomUUID()

        val request = UpdateUserRequest(
            username = "max_backend_updated",
            firstname = "Max",
            lastname = "Backend",
            primaryRole = Role.EXISTING_MEMBER,
            secondaryRole = Role.ADMIN,
            workingArea = WorkingArea.BACKEND_DEV,
        )

        val response = UpdateUserResponse(
            id = userId,
            username = "max_backend_updated",
            firstname = "Max",
            lastname = "Backend",
            primaryRole = Role.EXISTING_MEMBER,
            secondaryRole = Role.ADMIN,
            workingArea = WorkingArea.BACKEND_DEV,
        )

        every {
            userService.updateUserById(userId, request)
        } returns response

        mockMvc
            .perform(
                put("/api/v1/users/{userId}", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(userId.toString()))
            .andExpect(jsonPath("$.username").value("max_backend_updated"))
            .andExpect(jsonPath("$.firstname").value("Max"))
            .andExpect(jsonPath("$.lastname").value("Backend"))
            .andExpect(jsonPath("$.primaryRole").value("EXISTING_MEMBER"))
            .andExpect(jsonPath("$.secondaryRole").value("ADMIN"))
            .andExpect(jsonPath("$.workingArea").value("BACKEND_DEV"))

        verify(exactly = 1) {
            userService.updateUserById(userId, request)
        }
    }

    @Test
    fun `updateUserById should return 400 on invalid body`() {
        val userId = UUID.randomUUID()

        val invalidRequest = UpdateUserRequest(
            username = "",
            firstname = "Max",
            lastname = "",
            primaryRole = Role.EXISTING_MEMBER,
            secondaryRole = Role.ADMIN,
            workingArea = WorkingArea.BACKEND_DEV,
        )

        mockMvc
            .perform(
                put("/api/v1/users/{userId}", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidRequest)),
            ).andExpect(status().isBadRequest)

        verify(exactly = 0) {
            userService.updateUserById(any(), any())
        }
    }

    @Test
    fun `updateUserById should return 404 if user not found`() {
        val userId = UUID.randomUUID()

        val request = UpdateUserRequest(
            username = "max_backend_updated",
            firstname = "Max",
            lastname = "Backend",
            primaryRole = Role.EXISTING_MEMBER,
            secondaryRole = Role.ADMIN,
            workingArea = WorkingArea.BACKEND_DEV,
        )

        every {
            userService.updateUserById(userId, request)
        } throws ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "User with id: $userId not found",
        )

        mockMvc
            .perform(
                put("/api/v1/users/{userId}", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isNotFound)

        verify(exactly = 1) {
            userService.updateUserById(userId, request)
        }
    }

    @Test
    fun `patchUserById should return 200 and patched user`() {
        val userId = UUID.randomUUID()

        val request = PatchUserRequest(
            username = "max_backend_updated",
            firstname = "Max",
            lastname = null,
            primaryRole = null,
            secondaryRole = Role.ADMIN,
            workingArea = null,
        )

        val response = PatchUserResponse(
            id = userId,
            username = "max_backend_updated",
            firstname = "Max",
            lastname = "Backend",
            primaryRole = Role.EXISTING_MEMBER,
            secondaryRole = Role.ADMIN,
            workingArea = WorkingArea.BACKEND_DEV,
        )

        every {
            userService.patchUserById(userId, request)
        } returns response

        mockMvc
            .perform(
                patch("/api/v1/users/{userId}", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(userId.toString()))
            .andExpect(jsonPath("$.username").value("max_backend_updated"))
            .andExpect(jsonPath("$.firstname").value("Max"))
            .andExpect(jsonPath("$.lastname").value("Backend"))
            .andExpect(jsonPath("$.primaryRole").value("EXISTING_MEMBER"))
            .andExpect(jsonPath("$.secondaryRole").value("ADMIN"))
            .andExpect(jsonPath("$.workingArea").value("BACKEND_DEV"))

        verify(exactly = 1) {
            userService.patchUserById(userId, request)
        }
    }

    @Test
    fun `patchUserById should return 404 if user not found`() {
        val userId = UUID.randomUUID()

        val request = PatchUserRequest(
            username = "max_backend_updated",
            firstname = null,
            lastname = null,
            primaryRole = null,
            secondaryRole = Role.ADMIN,
            workingArea = null,
        )

        every {
            userService.patchUserById(userId, request)
        } throws ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "User with id: $userId not found",
        )

        mockMvc
            .perform(
                patch("/api/v1/users/{userId}", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isNotFound)

        verify(exactly = 1) {
            userService.patchUserById(userId, request)
        }
    }

    @Test
    fun `deleteUserById should return 204`() {
        val userId = UUID.randomUUID()

        every {
            userService.deleteUserById(userId)
        } just Runs

        mockMvc
            .perform(
                delete("/api/v1/users/{userId}", userId),
            ).andExpect(status().isNoContent)
            .andExpect(content().string(""))

        verify(exactly = 1) {
            userService.deleteUserById(userId)
        }
    }

    @Test
    fun `deleteUserById should return 404 if user not found`() {
        val userId = UUID.randomUUID()

        every {
            userService.deleteUserById(userId)
        } throws ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "User with id: $userId not found",
        )

        mockMvc
            .perform(
                delete("/api/v1/users/{userId}", userId),
            ).andExpect(status().isNotFound)

        verify(exactly = 1) {
            userService.deleteUserById(userId)
        }
    }
}
