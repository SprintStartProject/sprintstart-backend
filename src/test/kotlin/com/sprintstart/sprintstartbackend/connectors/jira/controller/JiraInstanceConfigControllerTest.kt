package com.sprintstart.sprintstartbackend.connectors.jira.controller

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.request.config.ConfigureAllJiraInstancesRequest
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.request.config.ConfigureJiraInstanceRequest
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.response.config.GetJiraInstanceConfigResponse
import com.sprintstart.sprintstartbackend.connectors.jira.service.JiraInstanceConfigService
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduleSpec
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
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
import java.time.Instant
import java.time.LocalTime

@WebMvcTest(controllers = [JiraInstanceConfigController::class])
@AutoConfigureMockMvc
@Import(JiraExceptionHandler::class, SecurityConfig::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(MockKExtension::class)
class JiraInstanceConfigControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var service: JiraInstanceConfigService

    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    private val adminJwt = jwt()
        .jwt { it.subject("admin-id") }
        .authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

    @Nested
    inner class ConfigureAll {
        @Test
        fun `should return 204 when configured`() {
            val request = ConfigureAllJiraInstancesRequest(ScheduleSpec.Daily(LocalTime.of(2, 0)), true)
            every { service.configureAll(request) } returns Unit

            mockMvc
                .perform(
                    put("/api/v1/jira/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(adminJwt),
                ).andExpect(status().isNoContent)

            verify { service.configureAll(request) }
        }
    }

    @Nested
    inner class GetAll {
        @Test
        fun `should return 200 with configs`() {
            val response = GetJiraInstanceConfigResponse(
                instanceUrl = "https://jira.example.com",
                autoUpdate = true,
                spec = ScheduleSpec.Daily(LocalTime.of(2, 0)),
                schedule = "0 0 2 * * *",
                nextSyncAt = Instant.now(),
            )
            every { service.getAll() } returns listOf(response)

            mockMvc
                .perform(get("/api/v1/jira/config").with(adminJwt))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].instanceUrl").value("https://jira.example.com"))
        }
    }

    @Nested
    inner class ConfigureInstance {
        @Test
        fun `should return 204 when configured`() {
            val request =
                ConfigureJiraInstanceRequest("https://jira.example.com", ScheduleSpec.Daily(LocalTime.of(2, 0)), true)
            every { service.configureInstance(request) } returns Unit

            mockMvc
                .perform(
                    put("/api/v1/jira/config/configure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(adminJwt),
                ).andExpect(status().isNoContent)

            verify { service.configureInstance(request) }
        }
    }

    @Nested
    inner class GetConfigOfInstance {
        @Test
        fun `should return 200 with config`() {
            val response = GetJiraInstanceConfigResponse(
                instanceUrl = "https://jira.example.com",
                autoUpdate = true,
                spec = null,
                schedule = "0 0 2 * * *",
                nextSyncAt = null,
            )
            every { service.getConfigOfInstance("jira-1") } returns response

            mockMvc
                .perform(get("/api/v1/jira/config/{instanceId}", "jira-1").with(adminJwt))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.instanceUrl").value("https://jira.example.com"))
        }
    }
}
