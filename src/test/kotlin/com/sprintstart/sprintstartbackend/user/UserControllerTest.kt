package com.sprintstart.sprintstartbackend.user

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.user.controller.UserController
import com.sprintstart.sprintstartbackend.user.external.enums.WorkingArea
import com.sprintstart.sprintstartbackend.user.model.dto.CreateUserRequest
import com.sprintstart.sprintstartbackend.user.model.dto.CreateUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.GetUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.PatchUserRequest
import com.sprintstart.sprintstartbackend.user.model.dto.PatchUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.SyncUserRequest
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
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val objectMapper = jacksonObjectMapper()

    @MockkBean
    private lateinit var userService: UserService

    @Test
    fun `createUser should return 201 and created user`() {
        val request = CreateUserRequest(
            authId = "keycloak-subject-1",
            username = "max_backend",
            firstname = "Max",
            lastname = "Backend",
            workingArea = WorkingArea.BACKEND_DEV,
        )
        val response = GetUserFixtures.createResponse(
            id = UUID.randomUUID(),
            authId = request.authId,
            username = request.username,
            firstname = request.firstname,
            lastname = request.lastname,
            workingArea = request.workingArea,
        )

        every {
            userService.createUser(request)
        } returns CreateUserResponse(
            id = response.id,
            authId = response.authId,
            username = response.username,
            firstname = response.firstname,
            lastname = response.lastname,
            workingArea = response.workingArea,
        )

        mockMvc.perform(
            post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isCreated)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(response.id.toString()))
            .andExpect(jsonPath("$.authId").value(request.authId))
            .andExpect(jsonPath("$.username").value(request.username))
            .andExpect(jsonPath("$.firstname").value(request.firstname))
            .andExpect(jsonPath("$.lastname").value(request.lastname))
            .andExpect(jsonPath("$.workingArea").value("BACKEND_DEV"))

        verify(exactly = 1) {
            userService.createUser(request)
        }
    }

    @Test
    fun `createUser should return 400 on invalid body`() {
        val invalidRequest = CreateUserRequest(
            authId = "",
            username = "",
            firstname = "Max",
            lastname = "",
            workingArea = WorkingArea.BACKEND_DEV,
        )

        mockMvc.perform(
            post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)),
        )
            .andExpect(status().isBadRequest)

        verify(exactly = 0) {
            userService.createUser(any())
        }
    }

    @Test
    fun `getAllUsers should return 200 and list of users`() {
        val users = listOf(
            GetUserFixtures.getResponse(
                authId = "keycloak-subject-1",
                username = "max_backend",
                firstname = "Max",
                lastname = "Backend",
                workingArea = WorkingArea.BACKEND_DEV,
            ),
            GetUserFixtures.getResponse(
                authId = "keycloak-subject-2",
                username = "anna_frontend",
                firstname = "Anna",
                lastname = "Frontend",
                workingArea = WorkingArea.FRONTEND_DEV,
            ),
        )

        every {
            userService.getAllUsers()
        } returns users

        mockMvc.perform(get("/api/v1/users"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].authId").value("keycloak-subject-1"))
            .andExpect(jsonPath("$[0].workingArea").value("BACKEND_DEV"))
            .andExpect(jsonPath("$[1].authId").value("keycloak-subject-2"))
            .andExpect(jsonPath("$[1].workingArea").value("FRONTEND_DEV"))

        verify(exactly = 1) {
            userService.getAllUsers()
        }
    }

    @Test
    fun `getUserById should return 200 and user`() {
        val userId = UUID.randomUUID()
        val user = GetUserFixtures.getResponse(
            id = userId,
            authId = "keycloak-subject-1",
            username = "max_backend",
            firstname = "Max",
            lastname = "Backend",
            workingArea = WorkingArea.BACKEND_DEV,
        )

        every {
            userService.getUserById(userId)
        } returns user

        mockMvc.perform(get("/api/v1/users/{userId}", userId))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(userId.toString()))
            .andExpect(jsonPath("$.authId").value("keycloak-subject-1"))
            .andExpect(jsonPath("$.username").value("max_backend"))

        verify(exactly = 1) {
            userService.getUserById(userId)
        }
    }

    @Test
    fun `getUserByAuthId should return 200 and user`() {
        val authId = "keycloak-subject-1"
        val user = GetUserFixtures.getResponse(
            authId = authId,
            username = "max_backend",
            firstname = "Max",
            lastname = "Backend",
            workingArea = WorkingArea.BACKEND_DEV,
        )

        every {
            userService.getUserByAuthId(authId)
        } returns user

        mockMvc.perform(get("/api/v1/users/auth-subject/{authId}", authId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authId").value(authId))

        verify(exactly = 1) {
            userService.getUserByAuthId(authId)
        }
    }

    @Test
    fun `updateUserById should return 200 and updated user`() {
        val userId = UUID.randomUUID()
        val request = UpdateUserRequest(
            username = "max_backend_updated",
            firstname = "Max",
            lastname = "Backend",
            workingArea = WorkingArea.BACKEND_DEV,
        )
        val response = UpdateUserResponse(
            id = userId,
            authId = "keycloak-subject-1",
            username = request.username,
            firstname = request.firstname,
            lastname = request.lastname,
            workingArea = request.workingArea,
        )

        every {
            userService.updateUserById(userId, request)
        } returns response

        mockMvc.perform(
            put("/api/v1/users/{userId}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authId").value("keycloak-subject-1"))
            .andExpect(jsonPath("$.username").value("max_backend_updated"))

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
        )
        val response = PatchUserResponse(
            id = userId,
            authId = "keycloak-subject-1",
            username = "max_backend_updated",
            firstname = "Max",
            lastname = "Backend",
            workingArea = WorkingArea.BACKEND_DEV,
        )

        every {
            userService.patchUserById(userId, request)
        } returns response

        mockMvc.perform(
            patch("/api/v1/users/{userId}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authId").value("keycloak-subject-1"))
            .andExpect(jsonPath("$.username").value("max_backend_updated"))

        verify(exactly = 1) {
            userService.patchUserById(userId, request)
        }
    }

    @Test
    fun `syncUser should return 200 and synced user`() {
        val request = SyncUserRequest(
            authId = "keycloak-subject-1",
            username = "max_backend",
            firstname = "Max",
            lastname = "Backend",
        )
        val response = GetUserFixtures.getResponse(
            authId = request.authId,
            username = request.username,
            firstname = request.firstname,
            lastname = request.lastname,
            workingArea = WorkingArea.NO_WORKING_AREA,
        )

        every {
            userService.syncUser(request)
        } returns response

        mockMvc.perform(
            post("/api/v1/users/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authId").value(request.authId))
            .andExpect(jsonPath("$.workingArea").value("NO_WORKING_AREA"))

        verify(exactly = 1) {
            userService.syncUser(request)
        }
    }

    @Test
    fun `deleteUserById should return 204`() {
        val userId = UUID.randomUUID()

        every {
            userService.deleteUserById(userId)
        } just Runs

        mockMvc.perform(delete("/api/v1/users/{userId}", userId))
            .andExpect(status().isNoContent)
            .andExpect(content().string(""))

        verify(exactly = 1) {
            userService.deleteUserById(userId)
        }
    }
}

private object GetUserFixtures {
    fun createResponse(
        id: UUID,
        authId: String,
        username: String,
        firstname: String,
        lastname: String,
        workingArea: WorkingArea,
    ): GetUserResponse {
        return GetUserResponse(
            id = id,
            authId = authId,
            username = username,
            firstname = firstname,
            lastname = lastname,
            workingArea = workingArea,
        )
    }

    fun getResponse(
        id: UUID = UUID.randomUUID(),
        authId: String,
        username: String,
        firstname: String,
        lastname: String,
        workingArea: WorkingArea,
    ): GetUserResponse {
        return GetUserResponse(
            id = id,
            authId = authId,
            username = username,
            firstname = firstname,
            lastname = lastname,
            workingArea = workingArea,
        )
    }
}
