package com.sprintstart.sprintstartbackend.connectors.github.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.ConfigureRepositoryRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.GetRepositoryConfigRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.GetRepositoryConfigResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.RepositoryConfigNotFoundException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.RepositoryNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.github.service.GithubRepositoryConfigService
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduleSpec
import io.mockk.every
import io.mockk.just
import io.mockk.runs
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

@WebMvcTest(controllers = [GithubRepositoryConfigController::class])
@AutoConfigureMockMvc
@Import(GithubExceptionHandler::class, SecurityConfig::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GithubConfigControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var configService: GithubRepositoryConfigService

    private val objectMapper = jacksonObjectMapper()

    private val adminJwt = jwt()
        .jwt { it.subject("mockId") }
        .authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

    @Nested
    inner class ConfigureAll {
        @Test
        fun `returns 204 No Content when configuration is applied`() {
            every { configService.configureAll(any()) } just runs

            mockMvc
                .perform(
                    put("/api/v1/github/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validConfigBody())
                        .with(adminJwt),
                ).andExpect(status().isNoContent)

            verify { configService.configureAll(any()) }
        }

        @Test
        fun `returns 400 when schedule is invalid`() {
            mockMvc
                .perform(
                    put("/api/v1/github/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"schedule": {}, "autoUpdate": true}""")
                        .with(adminJwt),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `returns 403 when user has insufficient role`() {
            val userJwt = jwt()
                .jwt { it.subject("mockId") }
                .authorities(SimpleGrantedAuthority("ROLE_USER"))

            mockMvc
                .perform(
                    put("/api/v1/github/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validConfigBody())
                        .with(userJwt),
                ).andExpect(status().isForbidden)
        }
    }

    @Nested
    inner class ConfigureRepository {
        @Test
        fun `returns 204 No Content when repository is configured`() {
            every { configService.configure("owner", "repo", any()) } just runs

            mockMvc
                .perform(
                    put("/api/v1/github/config/owner/repo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validConfigBody())
                        .with(adminJwt),
                ).andExpect(status().isNoContent)

            verify { configService.configure("owner", "repo", any()) }
        }

        @Test
        fun `returns 400 when repository is not connected`() {
            every { configService.configure("owner", "repo", any()) } throws
                RepositoryNotConnectedException("owner", "repo")

            mockMvc
                .perform(
                    put("/api/v1/github/config/owner/repo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validConfigBody())
                        .with(adminJwt),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("Repository owner/repo is not connected."))
        }

        @Test
        fun `returns 404 when config does not exist`() {
            every { configService.configure("owner", "repo", any()) } throws
                RepositoryConfigNotFoundException("owner", "repo")

            mockMvc
                .perform(
                    put("/api/v1/github/config/owner/repo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validConfigBody())
                        .with(adminJwt),
                ).andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("No config for GitHub repository owner/repo found."))
        }

        @Test
        fun `returns 400 when request body is invalid`() {
            mockMvc
                .perform(
                    put("/api/v1/github/config/owner/repo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"schedule": {}, "autoUpdate": true}""")
                        .with(adminJwt),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `returns 403 when user has insufficient role`() {
            val userJwt = jwt()
                .jwt { it.subject("mockId") }
                .authorities(SimpleGrantedAuthority("ROLE_USER"))

            mockMvc
                .perform(
                    put("/api/v1/github/config/owner/repo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validConfigBody())
                        .with(userJwt),
                ).andExpect(status().isForbidden)
        }
    }

    @Nested
    inner class GetConfigOfRepository {
        @Test
        fun `returns 200 with config for connected repository`() {
            val spec = ScheduleSpec.Daily(time = LocalTime.of(2, 0))
            val response = GetRepositoryConfigResponse(
                id = UUID.randomUUID(),
                repositoryOwner = "owner",
                repositoryName = "repo",
                autoUpdate = true,
                spec = spec,
                schedule = "0 0 2 * * *",
                nextSyncAt = Instant.parse("2026-01-01T02:00:00Z"),
            )
            every {
                configService.getConfigOfRepository(GetRepositoryConfigRequest("owner", "repo"))
            } returns response

            mockMvc
                .perform(
                    get("/api/v1/github/config/owner/repo")
                        .with(adminJwt),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.repositoryOwner").value("owner"))
                .andExpect(jsonPath("$.repositoryName").value("repo"))
                .andExpect(jsonPath("$.autoUpdate").value(true))
                .andExpect(jsonPath("$.spec.type").value("DAILY"))
                .andExpect(jsonPath("$.spec.time").value("02:00:00"))
                .andExpect(jsonPath("$.schedule").value("0 0 2 * * *"))
                .andExpect(jsonPath("$.nextSyncAt").value("2026-01-01T02:00:00Z"))
        }

        @Test
        fun `returns 400 when repository is not connected`() {
            every {
                configService.getConfigOfRepository(GetRepositoryConfigRequest("owner", "repo"))
            } throws RepositoryNotConnectedException("owner", "repo")

            mockMvc
                .perform(
                    get("/api/v1/github/config/owner/repo")
                        .with(adminJwt),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("Repository owner/repo is not connected."))
        }

        @Test
        fun `returns 404 when config does not exist`() {
            every {
                configService.getConfigOfRepository(GetRepositoryConfigRequest("owner", "repo"))
            } throws RepositoryConfigNotFoundException("owner", "repo")

            mockMvc
                .perform(
                    get("/api/v1/github/config/owner/repo")
                        .with(adminJwt),
                ).andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("No config for GitHub repository owner/repo found."))
        }

        @Test
        fun `returns 403 when user has insufficient role`() {
            val userJwt = jwt()
                .jwt { it.subject("mockId") }
                .authorities(SimpleGrantedAuthority("ROLE_USER"))

            mockMvc
                .perform(
                    get("/api/v1/github/config/owner/repo")
                        .with(userJwt),
                ).andExpect(status().isForbidden)
        }
    }

    @Nested
    inner class GetAllConfigs {
        @Test
        fun `returns 200 with all configs`() {
            val response1 = GetRepositoryConfigResponse(
                id = UUID.randomUUID(),
                repositoryOwner = "owner1",
                repositoryName = "repo1",
                autoUpdate = true,
                spec = ScheduleSpec.Daily(time = LocalTime.of(2, 0)),
                schedule = "0 0 2 * * *",
                nextSyncAt = Instant.parse("2026-01-01T02:00:00Z"),
            )
            val response2 = GetRepositoryConfigResponse(
                id = UUID.randomUUID(),
                repositoryOwner = "owner2",
                repositoryName = "repo2",
                autoUpdate = false,
                spec = ScheduleSpec.Interval(everyMinutes = 60),
                schedule = "0 */60 * * * *",
                nextSyncAt = null,
            )
            every { configService.getAll() } returns listOf(response1, response2)

            mockMvc
                .perform(
                    get("/api/v1/github/config")
                        .with(adminJwt),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].repositoryOwner").value("owner1"))
                .andExpect(jsonPath("$[0].repositoryName").value("repo1"))
                .andExpect(jsonPath("$[0].autoUpdate").value(true))
                .andExpect(jsonPath("$[0].spec.type").value("DAILY"))
                .andExpect(jsonPath("$[0].spec.time").value("02:00:00"))
                .andExpect(jsonPath("$[0].schedule").value("0 0 2 * * *"))
                .andExpect(jsonPath("$[0].nextSyncAt").value("2026-01-01T02:00:00Z"))
                .andExpect(jsonPath("$[1].repositoryOwner").value("owner2"))
                .andExpect(jsonPath("$[1].repositoryName").value("repo2"))
                .andExpect(jsonPath("$[1].autoUpdate").value(false))
                .andExpect(jsonPath("$[1].spec.type").value("INTERVAL"))
                .andExpect(jsonPath("$[1].spec.everyMinutes").value(60))
                .andExpect(jsonPath("$[1].schedule").value("0 */60 * * * *"))
                .andExpect(jsonPath("$[1].nextSyncAt").doesNotExist())
        }

        @Test
        fun `returns 200 with empty list when no configs exist`() {
            every { configService.getAll() } returns emptyList()

            mockMvc
                .perform(
                    get("/api/v1/github/config")
                        .with(adminJwt),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        fun `returns 403 when user has insufficient role`() {
            val userJwt = jwt()
                .jwt { it.subject("mockId") }
                .authorities(SimpleGrantedAuthority("ROLE_USER"))

            mockMvc
                .perform(
                    get("/api/v1/github/config")
                        .with(userJwt),
                ).andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when not authenticated`() {
            mockMvc
                .perform(
                    get("/api/v1/github/config"),
                ).andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class ScheduleValidation {
        @Test
        fun `returns 400 for Interval with 0 minutes`() {
            mockMvc
                .perform(
                    put("/api/v1/github/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"autoUpdate": true, "schedule": {"type": "INTERVAL", "everyMinutes": 0}}""")
                        .with(adminJwt),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `returns 400 for Interval with negative minutes`() {
            mockMvc
                .perform(
                    put("/api/v1/github/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"autoUpdate": true, "schedule": {"type": "INTERVAL", "everyMinutes": -1}}""")
                        .with(adminJwt),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `returns 400 for Monthly with dayOfMonth 0`() {
            mockMvc
                .perform(
                    put("/api/v1/github/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {"autoUpdate": true, "schedule": {"type": "MONTHLY", "time": "10:00:00", "dayOfMonth": 0}}
                            """.trimIndent(),
                        ).with(adminJwt),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `returns 400 for Monthly with dayOfMonth 32`() {
            mockMvc
                .perform(
                    put("/api/v1/github/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {"autoUpdate": true, "schedule": {"type": "MONTHLY", "time": "10:00:00", "dayOfMonth": 32}}
                            """.trimIndent(),
                        ).with(adminJwt),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `returns 400 for Weekly with empty daysOfWeek`() {
            mockMvc
                .perform(
                    put("/api/v1/github/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {"autoUpdate": true, "schedule": {"type": "WEEKLY", "time": "10:00:00", "daysOfWeek": []}}
                            """.trimIndent(),
                        ).with(adminJwt),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `returns 400 for unknown schedule type`() {
            mockMvc
                .perform(
                    put("/api/v1/github/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"autoUpdate": true, "schedule": {"type": "UNKNOWN"}}""")
                        .with(adminJwt),
                ).andExpect(status().isBadRequest)
        }
    }

    private fun validConfigBody(): String = objectMapper.writeValueAsString(
        ConfigureRepositoryRequest(
            autoUpdate = true,
            schedule = ScheduleSpec.Interval(everyMinutes = 60),
        ),
    )
}
