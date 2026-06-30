package com.sprintstart.sprintstartbackend.github.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.connectors.github.controller.GithubUserController
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.AddPatRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.RemovePatRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.UpdatePatNameRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.UpdatePatRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.GithubUserPatNameAlreadyExistsException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.GithubUserPatNotFoundException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.GithubUserPatStillInUseException
import com.sprintstart.sprintstartbackend.connectors.github.service.GithubUserService
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(GithubUserController::class)
@AutoConfigureMockMvc
@Import(SecurityConfig::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GithubUserControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var githubUserService: GithubUserService

    private val objectMapper = jacksonObjectMapper()

    private val authId = "test-auth-id"
    private val validTokenName = "ghp_abcdefghijklmnopqrstuvwxyz0123456789"

    private val userJwt = jwt()
        .jwt { it.subject(authId) }
        .authorities(SimpleGrantedAuthority("ROLE_USER"))

    private val noUserRoleJwt = jwt()
        .jwt { it.subject(authId) }
        .authorities(SimpleGrantedAuthority("ROLE_NONE"))

    @Nested
    inner class GetAllPats {
        @Test
        fun `should return 200 and list of PATs`() {
            every { githubUserService.getAllPATNames(authId) } returns listOf(validTokenName)

            mockMvc
                .perform(
                    get("/api/v1/github/pat")
                        .with(userJwt),
                ).andExpect(status().isOk)
                .andExpect(content().json("[\"$validTokenName\"]"))

            verify(exactly = 1) { githubUserService.getAllPATNames(authId) }
        }

        @Test
        fun `should return 200 with empty list when no PATs`() {
            every { githubUserService.getAllPATNames(authId) } returns emptyList()

            mockMvc
                .perform(
                    get("/api/v1/github/pat")
                        .with(userJwt),
                ).andExpect(status().isOk)
                .andExpect(content().json("[]"))
        }

        @Test
        fun `should return 401 when not authenticated`() {
            mockMvc
                .perform(get("/api/v1/github/pat"))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `should return 403 when authenticated without USER role`() {
            mockMvc
                .perform(
                    get("/api/v1/github/pat")
                        .with(noUserRoleJwt),
                ).andExpect(status().isForbidden)
        }
    }

    @Nested
    inner class AddPat {
        @Test
        fun `should return 201 when PAT is added`() {
            val request = AddPatRequest(validTokenName, validTokenName)
            every { githubUserService.addPAT(authId, any()) } just Runs

            mockMvc
                .perform(
                    post("/api/v1/github/pat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(userJwt),
                ).andExpect(status().isCreated)
        }

        @Test
        fun `should return 400 when PAT name already exists`() {
            val request = AddPatRequest(validTokenName, validTokenName)
            every { githubUserService.addPAT(authId, any()) } throws
                GithubUserPatNameAlreadyExistsException(validTokenName)

            mockMvc
                .perform(
                    post("/api/v1/github/pat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(userJwt),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when token pattern is invalid`() {
            val request = AddPatRequest(validTokenName, "invalid-token")

            mockMvc
                .perform(
                    post("/api/v1/github/pat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(userJwt),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when name is blank`() {
            val request = AddPatRequest("", validTokenName)

            mockMvc
                .perform(
                    post("/api/v1/github/pat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(userJwt),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 401 when not authenticated`() {
            val request = AddPatRequest("pat", validTokenName)

            mockMvc
                .perform(
                    post("/api/v1/github/pat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isUnauthorized)
        }

        @Test
        fun `should return 403 when authenticated without USER role`() {
            val request = AddPatRequest("pat", validTokenName)

            mockMvc
                .perform(
                    post("/api/v1/github/pat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(noUserRoleJwt),
                ).andExpect(status().isForbidden)
        }
    }

    @Nested
    inner class UpdatePat {
        @Test
        fun `should return 200 when PAT is updated`() {
            val request = UpdatePatRequest(validTokenName, validTokenName)
            every { githubUserService.updatePAT(authId, any()) } just Runs

            mockMvc
                .perform(
                    put("/api/v1/github/pat/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(userJwt),
                ).andExpect(status().isOk)
        }

        @Test
        fun `should return 404 when PAT not found`() {
            val request = UpdatePatRequest(validTokenName, validTokenName)
            every { githubUserService.updatePAT(authId, any()) } throws
                GithubUserPatNotFoundException(validTokenName, authId)

            mockMvc
                .perform(
                    put("/api/v1/github/pat/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(userJwt),
                ).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 400 when new token pattern is invalid`() {
            val request = UpdatePatRequest(validTokenName, "invalid-token")

            mockMvc
                .perform(
                    put("/api/v1/github/pat/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(userJwt),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 401 when not authenticated`() {
            val request = UpdatePatRequest(validTokenName, "invalid-token")

            mockMvc
                .perform(
                    put("/api/v1/github/pat/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isUnauthorized)
        }

        @Test
        fun `should return 403 when authenticated without USER role`() {
            val request = UpdatePatRequest(validTokenName, validTokenName)

            mockMvc
                .perform(
                    put("/api/v1/github/pat/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(noUserRoleJwt),
                ).andExpect(status().isForbidden)
        }
    }

    @Nested
    inner class UpdatePatName {
        @Test
        fun `should return 200 when PAT name is updated`() {
            val newName = "ghp_new_name_123456789012345678901234567890"
            val request = UpdatePatNameRequest(validTokenName, newName)
            every { githubUserService.updatePATName(authId, any()) } just Runs

            mockMvc
                .perform(
                    put("/api/v1/github/pat/update/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(userJwt),
                ).andExpect(status().isOk)
        }

        @Test
        fun `should return 404 when PAT not found`() {
            val newName = "ghp_new_name_123456789012345678901234567890"
            val request = UpdatePatNameRequest(validTokenName, newName)
            every { githubUserService.updatePATName(authId, any()) } throws
                GithubUserPatNotFoundException(validTokenName, authId)

            mockMvc
                .perform(
                    put("/api/v1/github/pat/update/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(userJwt),
                ).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 400 when new name contains spaces`() {
            val request = UpdatePatNameRequest(validTokenName, "invalid name")

            mockMvc
                .perform(
                    put("/api/v1/github/pat/update/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(userJwt),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 401 when not authenticated`() {
            val request = UpdatePatNameRequest(validTokenName, "invalid name")

            mockMvc
                .perform(
                    put("/api/v1/github/pat/update/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isUnauthorized)
        }

        @Test
        fun `should return 403 when authenticated without USER role`() {
            val request = UpdatePatNameRequest(validTokenName, "name")

            mockMvc
                .perform(
                    put("/api/v1/github/pat/update/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(noUserRoleJwt),
                ).andExpect(status().isForbidden)
        }
    }

    @Nested
    inner class DeletePat {
        @Test
        fun `should return 200 when PAT is deleted`() {
            val request = RemovePatRequest(validTokenName)
            every { githubUserService.removePAT(authId, any()) } just Runs

            mockMvc
                .perform(
                    put("/api/v1/github/pat/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(userJwt),
                ).andExpect(status().isOk)
        }

        @Test
        fun `should return 404 when PAT not found`() {
            val request = RemovePatRequest(validTokenName)
            every { githubUserService.removePAT(authId, any()) } throws
                GithubUserPatNotFoundException(validTokenName, authId)

            mockMvc
                .perform(
                    put("/api/v1/github/pat/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(userJwt),
                ).andExpect(status().isNotFound)
        }

        @Test
        fun `should return 400 when PAT is still in use`() {
            val request = RemovePatRequest(validTokenName)
            every { githubUserService.removePAT(authId, any()) } throws GithubUserPatStillInUseException(validTokenName)
            mockMvc
                .perform(
                    put("/api/v1/github/pat/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(userJwt),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 401 when not authenticated`() {
            val request = RemovePatRequest(validTokenName)
            every { githubUserService.removePAT(authId, any()) } throws
                GithubUserPatStillInUseException(validTokenName)

            mockMvc
                .perform(
                    put("/api/v1/github/pat/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(status().isUnauthorized)
        }

        @Test
        fun `should return 403 when authenticated without user role`() {
            val request = RemovePatRequest(validTokenName)
            every { githubUserService.removePAT(authId, any()) } throws
                GithubUserPatStillInUseException(validTokenName)

            mockMvc
                .perform(
                    put("/api/v1/github/pat/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(noUserRoleJwt),
                ).andExpect(status().isForbidden)
        }
    }
}
