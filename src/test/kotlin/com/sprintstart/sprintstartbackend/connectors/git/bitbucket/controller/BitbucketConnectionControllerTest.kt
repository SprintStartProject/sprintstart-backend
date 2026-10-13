package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryConnectionNotFoundException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.response.UpdateAllBitbucketRepositoriesResponse
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.BitbucketConnectionService
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.BitbucketRepositoryProjectService
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.BitbucketRepositoryVisibilityService
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.BitbucketUpdatesService
import io.mockk.coEvery
import io.mockk.coVerify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Locks the tenant scoping of the update endpoints: the caller's identity travels from the JWT
 * into the service, so a PM can only trigger repositories linked to their own projects.
 */
@WebMvcTest(controllers = [BitbucketConnectionController::class])
@AutoConfigureMockMvc
@Import(BitbucketExceptionHandler::class, SecurityConfig::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BitbucketConnectionControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var connectionService: BitbucketConnectionService

    @MockkBean
    private lateinit var projectService: BitbucketRepositoryProjectService

    @MockkBean
    private lateinit var visibilityService: BitbucketRepositoryVisibilityService

    @MockkBean
    private lateinit var updatesService: BitbucketUpdatesService

    private val adminJwt = jwt()
        .jwt { it.subject("mockId") }
        .authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

    private val userJwt = jwt()
        .jwt { it.subject("mockId") }
        .authorities(SimpleGrantedAuthority("ROLE_USER"))

    @Nested
    inner class UpdateRepository {
        @Test
        fun `passes the caller identity to the update`() {
            val repositoryId = UUID.randomUUID()
            val transactionId = UUID.randomUUID()
            coEvery { updatesService.updateRepository(any(), any()) } returns transactionId

            val asyncResult = mockMvc
                .perform(post("/api/v1/bitbucket/connections/$repositoryId/update").with(adminJwt))
                .andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isAccepted)
                .andExpect(jsonPath("$.transactionId").value(transactionId.toString()))

            coVerify { updatesService.updateRepository("mockId", repositoryId) }
        }

        @Test
        fun `answers an unreachable repository as unknown`() {
            val repositoryId = UUID.randomUUID()
            coEvery { updatesService.updateRepository(any(), any()) } throws
                BitbucketRepositoryConnectionNotFoundException(repositoryId)

            val asyncResult = mockMvc
                .perform(post("/api/v1/bitbucket/connections/$repositoryId/update").with(adminJwt))
                .andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isNotFound)
        }

        @Test
        fun `returns 403 when the user has insufficient role`() {
            val asyncResult = mockMvc
                .perform(post("/api/v1/bitbucket/connections/${UUID.randomUUID()}/update").with(userJwt))
                .andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isForbidden)
        }
    }

    @Nested
    inner class UpdateAllRepositories {
        @Test
        fun `passes the caller identity to the batch update`() {
            val transactionId = UUID.randomUUID()
            coEvery { updatesService.updateAllRepositories(any()) } returns
                UpdateAllBitbucketRepositoriesResponse(mapOf("sprintstart/backend" to transactionId))

            val asyncResult = mockMvc
                .perform(post("/api/v1/bitbucket/update-all").with(adminJwt))
                .andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isAccepted)
                .andExpect(
                    jsonPath("$.transactionIdsByRepository['sprintstart/backend']").value(transactionId.toString()),
                )

            coVerify { updatesService.updateAllRepositories("mockId") }
        }

        @Test
        fun `returns the update response of the service`() {
            coEvery { updatesService.updateAllRepositories(any()) } returns
                UpdateAllBitbucketRepositoriesResponse(emptyMap())

            val asyncResult = mockMvc
                .perform(post("/api/v1/bitbucket/update-all").with(adminJwt))
                .andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isAccepted)
                .andExpect(jsonPath("$.transactionIdsByRepository").isEmpty)
        }
    }
}
