package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryConfigNotFoundException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.request.ConfigureBitbucketRepositoryRequest
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.response.GetBitbucketRepositoryConfigResponse
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.BitbucketRepositoryConfigService
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

@WebMvcTest(controllers = [BitbucketRepositoryConfigController::class])
@AutoConfigureMockMvc
@Import(BitbucketExceptionHandler::class, SecurityConfig::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BitbucketRepositoryConfigControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var configService: BitbucketRepositoryConfigService

    private val objectMapper = jacksonObjectMapper()

    private val adminJwt = jwt()
        .jwt { it.subject("mockId") }
        .authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

    private val userJwt = jwt()
        .jwt { it.subject("mockId") }
        .authorities(SimpleGrantedAuthority("ROLE_USER"))

    @Nested
    inner class ConfigureAll {
        @Test
        fun `returns 204 when configuration is applied`() {
            every { configService.configureAll(any(), any()) } just runs

            mockMvc
                .perform(
                    put("/api/v1/bitbucket/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validConfigBody())
                        .with(adminJwt),
                ).andExpect(status().isNoContent)

            verify { configService.configureAll("mockId", any()) }
        }

        @Test
        fun `returns 400 when the schedule is invalid`() {
            mockMvc
                .perform(
                    put("/api/v1/bitbucket/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"schedule": {}, "autoUpdate": true}""")
                        .with(adminJwt),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `returns 403 when the user has insufficient role`() {
            mockMvc
                .perform(
                    put("/api/v1/bitbucket/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validConfigBody())
                        .with(userJwt),
                ).andExpect(status().isForbidden)
        }
    }

    @Nested
    inner class GetAll {
        @Test
        fun `returns 200 with all configs`() {
            every { configService.getAll(any()) } returns listOf(response("w1", "s1"), response("w2", "s2"))

            mockMvc
                .perform(get("/api/v1/bitbucket/config").with(adminJwt))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].workspace").value("w1"))
                .andExpect(jsonPath("$[0].slug").value("s1"))
                .andExpect(jsonPath("$[1].workspace").value("w2"))

            verify { configService.getAll("mockId") }
        }

        @Test
        fun `returns 401 when not authenticated`() {
            mockMvc
                .perform(get("/api/v1/bitbucket/config"))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class ConfigureRepository {
        @Test
        fun `returns 204 when the repository is configured`() {
            every { configService.configure(any(), "w", "s", any()) } just runs

            mockMvc
                .perform(
                    put("/api/v1/bitbucket/config/w/s")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validConfigBody())
                        .with(adminJwt),
                ).andExpect(status().isNoContent)

            verify { configService.configure("mockId", "w", "s", any()) }
        }

        @Test
        fun `returns 400 when the repository is not connected`() {
            every { configService.configure(any(), "w", "s", any()) } throws
                BitbucketRepositoryNotConnectedException(workspace = "w", slug = "s")

            mockMvc
                .perform(
                    put("/api/v1/bitbucket/config/w/s")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validConfigBody())
                        .with(adminJwt),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("Repository 'w/s' not connected to this application"))
        }

        @Test
        fun `returns 404 when the config does not exist`() {
            every { configService.configure(any(), "w", "s", any()) } throws
                BitbucketRepositoryConfigNotFoundException("w", "s")

            mockMvc
                .perform(
                    put("/api/v1/bitbucket/config/w/s")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validConfigBody())
                        .with(adminJwt),
                ).andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("No config for Bitbucket repository w/s found."))
        }

        @Test
        fun `returns 403 when the user has insufficient role`() {
            mockMvc
                .perform(
                    put("/api/v1/bitbucket/config/w/s")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validConfigBody())
                        .with(userJwt),
                ).andExpect(status().isForbidden)
        }
    }

    @Nested
    inner class GetConfigOfRepository {
        @Test
        fun `returns 200 with the config of a connected repository`() {
            every { configService.getConfigOfRepository(any(), "w", "s") } returns response("w", "s")

            mockMvc
                .perform(get("/api/v1/bitbucket/config/w/s").with(adminJwt))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.workspace").value("w"))
                .andExpect(jsonPath("$.slug").value("s"))
                .andExpect(jsonPath("$.autoUpdate").value(true))
                .andExpect(jsonPath("$.spec.type").value("DAILY"))
                .andExpect(jsonPath("$.spec.time").value("02:00:00"))
                .andExpect(jsonPath("$.schedule").value("0 0 2 * * *"))
                .andExpect(jsonPath("$.nextSyncAt").value("2026-01-01T02:00:00Z"))
        }

        @Test
        fun `returns 400 when the repository is not connected`() {
            every { configService.getConfigOfRepository(any(), "w", "s") } throws
                BitbucketRepositoryNotConnectedException(workspace = "w", slug = "s")

            mockMvc
                .perform(get("/api/v1/bitbucket/config/w/s").with(adminJwt))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("Repository 'w/s' not connected to this application"))
        }

        @Test
        fun `returns 404 when the config does not exist`() {
            every { configService.getConfigOfRepository(any(), "w", "s") } throws
                BitbucketRepositoryConfigNotFoundException("w", "s")

            mockMvc
                .perform(get("/api/v1/bitbucket/config/w/s").with(adminJwt))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("No config for Bitbucket repository w/s found."))
        }
    }

    private fun validConfigBody(): String = objectMapper.writeValueAsString(
        ConfigureBitbucketRepositoryRequest(
            autoUpdate = true,
            schedule = ScheduleSpec.Interval(everyMinutes = 60),
        ),
    )

    private fun response(workspace: String, slug: String) = GetBitbucketRepositoryConfigResponse(
        id = UUID.randomUUID(),
        workspace = workspace,
        slug = slug,
        autoUpdate = true,
        spec = ScheduleSpec.Daily(time = LocalTime.of(2, 0)),
        schedule = "0 0 2 * * *",
        nextSyncAt = Instant.parse("2026-01-01T02:00:00Z"),
    )
}
