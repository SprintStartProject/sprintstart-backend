package com.sprintstart.sprintstartbackend.github.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.github.models.GithubUser
import com.sprintstart.sprintstartbackend.github.models.GithubUserPat
import com.sprintstart.sprintstartbackend.github.models.api.requests.ConnectRepositoryRequest
import com.sprintstart.sprintstartbackend.github.models.api.requests.UpdateRepositoryRequest
import com.sprintstart.sprintstartbackend.github.models.exceptions.RepositoryNotConnectedException
import com.sprintstart.sprintstartbackend.github.models.exceptions.RepositoryNotFoundException
import com.sprintstart.sprintstartbackend.github.models.exceptions.RepositoryNotInitializedException
import com.sprintstart.sprintstartbackend.github.repository.GithubUserRepository
import com.sprintstart.sprintstartbackend.github.service.GithubConnectorService
import io.mockk.coEvery
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.Optional
import java.util.UUID

@WebMvcTest(controllers = [GithubConnectorController::class])
@AutoConfigureMockMvc
@Import(ExceptionHandler::class, SecurityConfig::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GithubConnectorControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var githubConnectorService: GithubConnectorService

    @MockkBean
    private lateinit var githubUserRepository: GithubUserRepository

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private val objectMapper = jacksonObjectMapper()

    private val userJwt = jwt()
        .jwt { it.subject("mockId") }
        .authorities(SimpleGrantedAuthority("ROLE_USER"))

    private val validTokenName = "ghp_abcdefghijklmnopqrstuvwxyz0123456789"

    @Nested
    inner class ConnectRepository {
        @Test
        fun `should return 202 Accepted and transactionId when valid request is provided`() {
            val request = ConnectRepositoryRequest(
                owner = "spring-projects",
                name = "spring-modulith",
                tokenName = validTokenName,
            )
            val expectedTransactionId = UUID.randomUUID()

            coEvery {
                githubConnectorService.connectRepositoryIfExists(
                    "mockId",
                    request,
                )
            } returns expectedTransactionId

            val asyncResult = mockMvc
                .perform(
                    post("/api/v1/github/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(userJwt),
                ).andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isAccepted)
                .andExpect(jsonPath("$.transactionId").value(expectedTransactionId.toString()))
        }

        @Test
        fun `should return 404 Not Found when repository does not exist`() {
            val owner = "unknown-owner"
            val name = "unknown-repo"
            val request = ConnectRepositoryRequest(owner = owner, name = name, tokenName = validTokenName)

            coEvery {
                githubUserRepository.findById(any())
            } returns Optional.of(
                GithubUser(
                    GithubUserPat(
                        "auth-id",
                        "token-name",
                    ),
                    "test-token",
                ),
            )
            coEvery {
                githubConnectorService.connectRepositoryIfExists(any(), any())
            } throws RepositoryNotFoundException(owner, name)

            val asyncResult = mockMvc
                .perform(
                    post("/api/v1/github/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(userJwt),
                ).andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("Repository $owner/$name not found"))
        }

        // Validation failures are NOT async - they fail before the coroutine starts
        @Test
        fun `should return 400 Bad Request when owner is blank`() {
            val request = ConnectRepositoryRequest(owner = "", name = "spring-modulith", tokenName = validTokenName)

            mockMvc
                .perform(
                    post("/api/v1/github/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(userJwt),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 Bad Request when name is blank`() {
            val request = ConnectRepositoryRequest(owner = "spring-projects", name = "", tokenName = validTokenName)

            mockMvc
                .perform(
                    post("/api/v1/github/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(userJwt),
                ).andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class UpdateAllRepositories {
        @Test
        fun `should return 400 when one of the repositories is not initialized`() {
            coEvery { githubConnectorService.updateAllRepositories() } throws RepositoryNotInitializedException(
                "owner",
                "name",
            )

            val asyncResult = mockMvc
                .perform(
                    post("/api/v1/github/update-all")
                        .with(userJwt),
                ).andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("Repository owner/name is connected but not initialized."))
        }

        @Test
        fun `should return 202 Accepted when all repositories are initialized`() {
            val transactionId = UUID.randomUUID()
            coEvery { githubConnectorService.updateAllRepositories() } returns transactionId

            val asyncResult = mockMvc
                .perform(
                    post("/api/v1/github/update-all")
                        .with(userJwt),
                ).andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isAccepted)
                .andExpect(jsonPath("$.transactionId").value(transactionId.toString()))
        }
    }

    @Nested
    inner class UpdateRepository {
        @Test
        fun `should return 400 when repository not connected`() {
            val request = UpdateRepositoryRequest(owner = "owner", name = "name")
            coEvery { githubConnectorService.updateRepository(request) } throws RepositoryNotConnectedException(
                "owner",
                "name",
            )

            val asyncResult = mockMvc
                .perform(
                    post("/api/v1/github/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(userJwt),
                ).andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("Repository owner/name is not connected."))
        }

        @Test
        fun `should return 400 when repository not initialized`() {
            val request = UpdateRepositoryRequest(owner = "owner", name = "name")
            coEvery { githubConnectorService.updateRepository(request) } throws RepositoryNotInitializedException(
                "owner",
                "name",
            )

            val asyncResult = mockMvc
                .perform(
                    post("/api/v1/github/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(userJwt),
                ).andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("Repository owner/name is connected but not initialized."))
        }

        @Test
        fun `should return 202 Accepted when repository is connected and initialized`() {
            val transactionId = UUID.randomUUID()
            val request = UpdateRepositoryRequest(owner = "owner", name = "name")
            coEvery { githubConnectorService.updateRepository(request) } returns transactionId

            val asyncResult = mockMvc
                .perform(
                    post("/api/v1/github/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(userJwt),
                ).andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isAccepted)
                .andExpect(jsonPath("$.transactionId").value(transactionId.toString()))
        }

        @Test
        fun `should return 400 when owner is blank`() {
            val request = UpdateRepositoryRequest(owner = "", name = "name")

            mockMvc
                .perform(
                    post("/api/v1/github/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(userJwt),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when name is blank`() {
            val request = UpdateRepositoryRequest(owner = "owner", name = "")

            mockMvc
                .perform(
                    post("/api/v1/github/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(userJwt),
                ).andExpect(status().isBadRequest)
        }
    }
}
