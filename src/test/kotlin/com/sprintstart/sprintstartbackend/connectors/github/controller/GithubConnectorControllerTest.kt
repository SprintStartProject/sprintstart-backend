package com.sprintstart.sprintstartbackend.connectors.github.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubUser
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubUserPat
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.ConnectRepositoriesRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.ConnectRepositoryRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.DiscoverRepositoriesRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.UpdateRepositoryRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.ConnectRepositoriesResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.DiscoverRepositoriesResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.DiscoveredRepository
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.UpdateAllRepositoriesResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.UpdateRepositoryResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.GithubUserPatNotFoundException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.ProjectAccessDeniedException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.RepositoryNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.RepositoryNotFoundException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.RepositoryNotInitializedException
import com.sprintstart.sprintstartbackend.connectors.github.repository.GithubUserRepository
import com.sprintstart.sprintstartbackend.connectors.github.service.GithubConnectorService
import com.sprintstart.sprintstartbackend.connectors.github.service.GithubRepositoryConnectionOrchestrator
import com.sprintstart.sprintstartbackend.connectors.github.service.GithubRepositoryProjectService
import com.sprintstart.sprintstartbackend.connectors.github.service.GithubUpdatesService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.Optional
import java.util.UUID

@WebMvcTest(controllers = [GithubConnectorController::class])
@AutoConfigureMockMvc
@Import(GithubExceptionHandler::class, SecurityConfig::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GithubConnectorControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var githubConnectorService: GithubConnectorService

    @MockkBean
    private lateinit var githubRepositoryConnectionOrchestrator: GithubRepositoryConnectionOrchestrator

    @MockkBean
    private lateinit var githubUpdateService: GithubUpdatesService

    @MockkBean
    private lateinit var githubUserRepository: GithubUserRepository

    @MockkBean
    private lateinit var githubRepositoryProjectService: GithubRepositoryProjectService

    private val objectMapper = jacksonObjectMapper()

    private val pmJwt = jwt()
        .jwt { it.subject("mockId") }
        .authorities(SimpleGrantedAuthority("ROLE_PM"))

    private val adminJwt = jwt()
        .jwt { it.subject("adminId") }
        .authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

    private val validTokenName = "ghp_abcdefghijklmnopqrstuvwxyz0123456789"
    private val projectId = UUID.randomUUID()

    @Nested
    inner class ConnectRepository {
        @Test
        fun `should return 202 Accepted and transactionId when valid request is provided`() {
            val request = ConnectRepositoryRequest(
                owner = "spring-projects",
                name = "spring-modulith",
                tokenName = validTokenName,
                projectId = projectId,
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
                        .with(pmJwt),
                ).andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isAccepted)
                .andExpect(jsonPath("$.transactionId").value(expectedTransactionId.toString()))
        }

        @Test
        fun `should return 202 Accepted when authenticated as ADMIN`() {
            val request = ConnectRepositoryRequest(
                owner = "spring-projects",
                name = "spring-modulith",
                tokenName = validTokenName,
                projectId = projectId,
            )
            val expectedTransactionId = UUID.randomUUID()

            coEvery {
                githubConnectorService.connectRepositoryIfExists(
                    "adminId",
                    request,
                )
            } returns expectedTransactionId

            val asyncResult = mockMvc
                .perform(
                    post("/api/v1/github/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(adminJwt),
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
            val request = ConnectRepositoryRequest(
                owner = owner,
                name = name,
                tokenName = validTokenName,
                projectId = projectId,
            )

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
                        .with(pmJwt),
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
            val request = ConnectRepositoryRequest(
                owner = "",
                name = "spring-modulith",
                tokenName = validTokenName,
                projectId = projectId,
            )

            mockMvc
                .perform(
                    post("/api/v1/github/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(pmJwt),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 Bad Request when name is blank`() {
            val request = ConnectRepositoryRequest(
                owner = "spring-projects",
                name = "",
                tokenName = validTokenName,
                projectId = projectId,
            )

            mockMvc
                .perform(
                    post("/api/v1/github/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(pmJwt),
                ).andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class ConnectRepositories {
        @Test
        fun `should return 202 Accepted and repository map when connecting multiple repositories`() {
            val request = ConnectRepositoriesRequest(
                listOf(
                    ConnectRepositoryRequest(
                        owner = "spring-projects",
                        name = "spring-modulith",
                        tokenName = validTokenName,
                        projectId = projectId,
                    ),
                    ConnectRepositoryRequest(
                        owner = "spring-projects",
                        name = "spring-framework",
                        tokenName = validTokenName,
                        projectId = projectId,
                    ),
                ),
            )
            val transactionIds = mapOf(
                "spring-projects/spring-modulith" to UUID.randomUUID(),
                "spring-projects/spring-framework" to UUID.randomUUID(),
            )
            val response = ConnectRepositoriesResponse(transactionIds)

            coEvery {
                githubRepositoryConnectionOrchestrator.connectRepositoriesIfExist("mockId", request)
            } returns response

            val asyncResult = mockMvc
                .perform(
                    post("/api/v1/github/connect/all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(pmJwt),
                ).andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isAccepted)
                .andExpect(
                    jsonPath("$.transactionIdsByRepositoryId['spring-projects/spring-modulith']")
                        .value(transactionIds["spring-projects/spring-modulith"].toString()),
                )
        }

        @Test
        fun `should return 400 Bad Request when repository list is empty`() {
            val request = ConnectRepositoriesRequest(emptyList())

            mockMvc
                .perform(
                    post("/api/v1/github/connect/all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(pmJwt),
                ).andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class DiscoverRepositories {
        @Test
        fun `should return 200 with discovered repositories for an organization`() {
            val response = DiscoverRepositoriesResponse(
                listOf(
                    DiscoveredRepository("repo1", false, "https://github.com/org/repo1"),
                    DiscoveredRepository("repo2", true, "https://github.com/org/repo2"),
                ),
            )

            val requestSlot = slot<DiscoverRepositoriesRequest>()
            coEvery { githubConnectorService.discoverRepositoriesOfOrg(capture(requestSlot)) } returns response

            val asyncResult = mockMvc
                .perform(
                    get("/api/v1/github/discover/org/myorg")
                        .param("tokenName", validTokenName)
                        .with(pmJwt),
                ).andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.repositories[0].name").value("repo1"))
                .andExpect(jsonPath("$.repositories[1].private").value(true))

            assertThat(requestSlot.captured.userId).isEqualTo("mockId")
            assertThat(requestSlot.captured.owner).isEqualTo("myorg")
            assertThat(requestSlot.captured.tokenName).isEqualTo(validTokenName)
            assertThat(requestSlot.captured.page).isEqualTo(0)
            assertThat(requestSlot.captured.pageSize).isEqualTo(20)
        }

        @Test
        fun `should return 200 with discovered repositories for a user`() {
            val response = DiscoverRepositoriesResponse(
                listOf(DiscoveredRepository("repo", false, "https://github.com/user/repo")),
            )

            val requestSlot = slot<DiscoverRepositoriesRequest>()
            coEvery { githubConnectorService.discoverRepositoriesOfUser(capture(requestSlot)) } returns response

            val asyncResult = mockMvc
                .perform(
                    get("/api/v1/github/discover/user/ghuser")
                        .param("tokenName", validTokenName)
                        .with(pmJwt),
                ).andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.repositories[0].name").value("repo"))

            assertThat(requestSlot.captured.userId).isEqualTo("mockId")
            assertThat(requestSlot.captured.owner).isEqualTo("ghuser")
            assertThat(requestSlot.captured.tokenName).isEqualTo(validTokenName)
        }

        @Test
        fun `should return 404 when PAT is not found`() {
            coEvery { githubConnectorService.discoverRepositoriesOfOrg(any()) }
                .throws(GithubUserPatNotFoundException("missing", "mockId"))

            val asyncResult = mockMvc
                .perform(
                    get("/api/v1/github/discover/org/myorg")
                        .param("tokenName", "missing")
                        .with(pmJwt),
                ).andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isNotFound)
        }

        @Test
        fun `should return 400 when tokenName is missing`() {
            mockMvc
                .perform(
                    get("/api/v1/github/discover/org/myorg")
                        .with(pmJwt),
                ).andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class UpdateAllRepositories {
        @Test
        fun `should return 400 when one of the repositories is not initialized`() {
            every { githubUpdateService.updateAllRepositories() } throws RepositoryNotInitializedException(
                "owner",
                "name",
            )

            val asyncResult = mockMvc
                .perform(
                    post("/api/v1/github/update-all")
                        .with(pmJwt),
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
            every { githubUpdateService.updateAllRepositories() } returns UpdateAllRepositoriesResponse(
                transactionId,
            )

            val asyncResult = mockMvc
                .perform(
                    post("/api/v1/github/update-all")
                        .with(pmJwt),
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
            every { githubUpdateService.updateRepository(request, true) } throws RepositoryNotConnectedException(
                "owner",
                "name",
            )

            val asyncResult = mockMvc
                .perform(
                    post("/api/v1/github/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(pmJwt),
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
            every { githubUpdateService.updateRepository(request, true) } throws RepositoryNotInitializedException(
                "owner",
                "name",
            )

            val asyncResult = mockMvc
                .perform(
                    post("/api/v1/github/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(pmJwt),
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
            every {
                githubUpdateService.updateRepository(
                    request,
                    true,
                )
            } returns UpdateRepositoryResponse(transactionId)

            val asyncResult = mockMvc
                .perform(
                    post("/api/v1/github/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(pmJwt),
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
                        .with(pmJwt),
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
                        .with(pmJwt),
                ).andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class RemoveRepositoryFromProject {
        @Test
        fun `should return 200 with the resulting project ids`() {
            val repositoryId = UUID.randomUUID()
            val remainingProjectId = UUID.randomUUID()
            every {
                githubRepositoryProjectService.removeProjectFromRepository("mockId", repositoryId, projectId)
            } returns setOf(remainingProjectId)

            mockMvc
                .perform(
                    delete("/api/v1/github/connections/$repositoryId/projects/$projectId")
                        .with(pmJwt),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.repositoryId").value(repositoryId.toString()))
                .andExpect(jsonPath("$.projectIds[0]").value(remainingProjectId.toString()))
        }

        @Test
        fun `should return 403 when the caller has no access to the target project`() {
            val repositoryId = UUID.randomUUID()
            every {
                githubRepositoryProjectService.removeProjectFromRepository("mockId", repositoryId, projectId)
            } throws ProjectAccessDeniedException(projectId)

            mockMvc
                .perform(
                    delete("/api/v1/github/connections/$repositoryId/projects/$projectId")
                        .with(pmJwt),
                ).andExpect(status().isForbidden)
        }

        @Test
        fun `should return 404 when the repository connection does not exist`() {
            val repositoryId = UUID.randomUUID()
            every {
                githubRepositoryProjectService.removeProjectFromRepository("mockId", repositoryId, projectId)
            } throws RepositoryNotFoundException("", "", "Repository connection with id $repositoryId not found")

            mockMvc
                .perform(
                    delete("/api/v1/github/connections/$repositoryId/projects/$projectId")
                        .with(pmJwt),
                ).andExpect(status().isNotFound)
        }
    }
}
