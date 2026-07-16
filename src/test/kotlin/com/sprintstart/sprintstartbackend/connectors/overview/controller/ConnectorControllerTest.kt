package com.sprintstart.sprintstartbackend.connectors.overview.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.connectors.overview.models.ConnectorSource
import com.sprintstart.sprintstartbackend.connectors.overview.models.api.request.ConfigureConnectorRequest
import com.sprintstart.sprintstartbackend.connectors.overview.models.api.request.PatchSourceRequest
import com.sprintstart.sprintstartbackend.connectors.overview.models.api.request.PatchSourcesRequest
import com.sprintstart.sprintstartbackend.connectors.overview.models.api.response.ConfigureConnectorResponse
import com.sprintstart.sprintstartbackend.connectors.overview.models.api.response.ConnectorDto
import com.sprintstart.sprintstartbackend.connectors.overview.models.api.response.GetSourcesOfConnectorResponse
import com.sprintstart.sprintstartbackend.connectors.overview.models.api.response.PatchSourcesOfConnectorResponse
import com.sprintstart.sprintstartbackend.connectors.overview.models.api.response.PatchedSource
import com.sprintstart.sprintstartbackend.connectors.overview.models.exceptions.ConnectorNotFoundException
import com.sprintstart.sprintstartbackend.connectors.overview.service.ConnectorConfigurationService
import io.mockk.coEvery
import io.mockk.every
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@WebMvcTest(controllers = [ConnectorController::class])
@AutoConfigureMockMvc
@Import(ConnectorOverviewExceptionHandler::class, SecurityConfig::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConnectorControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var connectorConfigurationService: ConnectorConfigurationService

    private val objectMapper = jacksonObjectMapper()

    private val adminJwt = jwt()
        .jwt { it.subject("admin-id") }
        .authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

    private val pmJwt = jwt()
        .jwt { it.subject("pm-id") }
        .authorities(SimpleGrantedAuthority("ROLE_PM"))

    private val userJwt = jwt()
        .jwt { it.subject("user-id") }
        .authorities(SimpleGrantedAuthority("ROLE_USER"))

    private val githubId = "github"
    private val githubName = "Github Repository Connector"

    private fun connectorDto(
        id: String = githubId,
        name: String = githubName,
        enabled: Boolean = false,
        firstConfiguredAt: Instant? = null,
        lastConfiguredAt: Instant? = null,
    ) = ConnectorDto(id, name, enabled, firstConfiguredAt, lastConfiguredAt)

    @Nested
    inner class ListAll {
        @Test
        fun `should return 200 with list of connectors when authenticated as ADMIN`() {
            every { connectorConfigurationService.findAllConnectors() } returns listOf(connectorDto())

            mockMvc
                .perform(get("/api/v1/connectors").with(adminJwt))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].id").value(githubId))
                .andExpect(jsonPath("$[0].name").value(githubName))
                .andExpect(jsonPath("$[0].enabled").value(false))
        }

        @Test
        fun `should return 200 with list of connectors when authenticated as PM`() {
            every { connectorConfigurationService.findAllConnectors() } returns listOf(connectorDto())

            mockMvc
                .perform(get("/api/v1/connectors").with(pmJwt))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].id").value(githubId))
        }

        @Test
        fun `should return 200 with empty list when no connectors configured`() {
            every { connectorConfigurationService.findAllConnectors() } returns emptyList()

            mockMvc
                .perform(get("/api/v1/connectors").with(adminJwt))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isEmpty())
        }

        @Test
        fun `should return 401 when not authenticated`() {
            mockMvc
                .perform(get("/api/v1/connectors"))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `should return 403 when authenticated as USER`() {
            mockMvc
                .perform(get("/api/v1/connectors").with(userJwt))
                .andExpect(status().isForbidden)
        }

        @Test
        fun `should return 404 when service throws ConnectorNotFoundException`() {
            every { connectorConfigurationService.findAllConnectors() } throws
                ConnectorNotFoundException("db/soft state desynced")

            mockMvc
                .perform(get("/api/v1/connectors").with(adminJwt))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("db/soft state desynced"))
        }
    }

    @Nested
    inner class ConfigureConnector {
        @Test
        fun `should return 200 when enabling a connector as ADMIN`() {
            val id = githubId
            val request = ConfigureConnectorRequest(enabled = true)
            val response = ConfigureConnectorResponse(
                id = id,
                enabled = true,
                firstConfiguredAt = Instant.now(),
                lastConfiguredAt = Instant.now(),
            )
            coEvery { connectorConfigurationService.configure(id, request) } returns response

            val asyncResult = mockMvc
                .perform(
                    patch("/api/v1/connectors/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(adminJwt),
                ).andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.enabled").value(true))
        }

        @Test
        fun `should return 200 when disabling a connector as PM`() {
            val id = githubId
            val request = ConfigureConnectorRequest(enabled = false)
            val response = ConfigureConnectorResponse(
                id = id,
                enabled = false,
                firstConfiguredAt = Instant.now(),
                lastConfiguredAt = Instant.now(),
            )
            coEvery { connectorConfigurationService.configure(id, request) } returns response

            val asyncResult = mockMvc
                .perform(
                    patch("/api/v1/connectors/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(pmJwt),
                ).andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.enabled").value(false))
        }

        @Test
        fun `should return 401 when not authenticated`() {
            mockMvc
                .perform(
                    patch("/api/v1/connectors/{id}", githubId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ConfigureConnectorRequest(true))),
                ).andExpect(status().isUnauthorized)
        }

        @Test
        fun `should return 403 when authenticated as USER`() {
            val asyncResult = mockMvc
                .perform(
                    patch("/api/v1/connectors/{id}", githubId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ConfigureConnectorRequest(true)))
                        .with(userJwt),
                ).andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isForbidden)
        }

        @Test
        fun `should return 404 when connector not found`() {
            val id = "unknown"
            val request = ConfigureConnectorRequest(enabled = true)
            coEvery { connectorConfigurationService.configure(id, request) } throws
                ConnectorNotFoundException("No connector with id $id")

            val asyncResult = mockMvc
                .perform(
                    patch("/api/v1/connectors/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(adminJwt),
                ).andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("No connector with id $id"))
        }

        @Test
        fun `should return 400 when connector id contains uppercase letters`() {
            val asyncResult = mockMvc
                .perform(
                    patch("/api/v1/connectors/{id}", "GITHUB")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ConfigureConnectorRequest(true)))
                        .with(adminJwt),
                ).andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when connector id contains spaces`() {
            val asyncResult = mockMvc
                .perform(
                    patch("/api/v1/connectors/{id}", "my connector")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ConfigureConnectorRequest(true)))
                        .with(adminJwt),
                ).andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class GetSourcesOfConnector {
        private val sources = listOf(
            ConnectorSource(
                id = "spring-projects/spring-boot",
                name = "spring-boot",
                url = "https://github.com/spring-projects/spring-boot",
                enabled = true,
            ),
        )

        @Test
        fun `should return 200 with sources when authenticated as ADMIN`() {
            every { connectorConfigurationService.getSourcesOfConnector(githubId) } returns
                GetSourcesOfConnectorResponse(githubId, sources)

            mockMvc
                .perform(get("/api/v1/connectors/{id}/sources", githubId).with(adminJwt))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.connectorId").value(githubId))
                .andExpect(jsonPath("$.sources[0].id").value("spring-projects/spring-boot"))
                .andExpect(jsonPath("$.sources[0].enabled").value(true))
        }

        @Test
        fun `should return 200 with sources when authenticated as PM`() {
            every { connectorConfigurationService.getSourcesOfConnector(githubId) } returns
                GetSourcesOfConnectorResponse(githubId, sources)

            mockMvc
                .perform(get("/api/v1/connectors/{id}/sources", githubId).with(pmJwt))
                .andExpect(status().isOk)
        }

        @Test
        fun `should return 401 when not authenticated`() {
            mockMvc
                .perform(get("/api/v1/connectors/{id}/sources", githubId))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `should return 403 when authenticated as USER`() {
            mockMvc
                .perform(get("/api/v1/connectors/{id}/sources", githubId).with(userJwt))
                .andExpect(status().isForbidden)
        }

        @Test
        fun `should return 404 when connector not found`() {
            val id = "unknown"
            every { connectorConfigurationService.getSourcesOfConnector(id) } throws
                ConnectorNotFoundException("Unable to load up connector with id $id")

            mockMvc
                .perform(get("/api/v1/connectors/{id}/sources", id).with(adminJwt))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("Unable to load up connector with id $id"))
        }

        @Test
        fun `should return 400 when connector id contains uppercase letters`() {
            mockMvc
                .perform(get("/api/v1/connectors/{id}/sources", "GITHUB").with(adminJwt))
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class PatchSourcesOfConnector {
        private val patchedSources = listOf(
            PatchedSource(
                id = "spring-projects/spring-boot",
                name = "spring-boot",
                url = "https://github.com/spring-projects/spring-boot",
                enabled = true,
            ),
        )

        @Test
        fun `should return 200 with patched sources when authenticated as ADMIN`() {
            val request = PatchSourcesRequest(
                sources = listOf(PatchSourceRequest(sourceId = "spring-projects/spring-boot", enabled = true)),
            )
            coEvery {
                connectorConfigurationService.patchSourcesIfConnectorExists(githubId, request)
            } returns PatchSourcesOfConnectorResponse(githubId, patchedSources)

            val asyncResult = mockMvc
                .perform(
                    patch("/api/v1/connectors/{id}/sources/status", githubId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(adminJwt),
                ).andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.connectorId").value(githubId))
                .andExpect(jsonPath("$.sources[0].id").value("spring-projects/spring-boot"))
                .andExpect(jsonPath("$.sources[0].enabled").value(true))
        }

        @Test
        fun `should return 200 with patched sources when authenticated as PM`() {
            val request = PatchSourcesRequest(
                sources = listOf(PatchSourceRequest(sourceId = "spring-projects/spring-boot", enabled = true)),
            )
            coEvery {
                connectorConfigurationService.patchSourcesIfConnectorExists(githubId, request)
            } returns PatchSourcesOfConnectorResponse(githubId, patchedSources)

            val asyncResult = mockMvc
                .perform(
                    patch("/api/v1/connectors/{id}/sources/status", githubId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(pmJwt),
                ).andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk)
        }

        @Test
        fun `should return 404 when connector not found`() {
            val id = "unknown"
            val request = PatchSourcesRequest(
                sources = listOf(PatchSourceRequest(sourceId = "spring-projects/spring-boot", enabled = true)),
            )
            coEvery {
                connectorConfigurationService.patchSourcesIfConnectorExists(id, request)
            } throws ConnectorNotFoundException("Unable to find connector with id $id")

            val asyncResult = mockMvc
                .perform(
                    patch("/api/v1/connectors/{id}/sources/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(adminJwt),
                ).andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("Unable to find connector with id $id"))
        }

        @Test
        fun `should return 400 when sources list is empty`() {
            val request = PatchSourcesRequest(sources = emptyList())

            mockMvc
                .perform(
                    patch("/api/v1/connectors/{id}/sources/status", githubId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(adminJwt),
                ).andExpect(status().isBadRequest)
        }
    }
}
