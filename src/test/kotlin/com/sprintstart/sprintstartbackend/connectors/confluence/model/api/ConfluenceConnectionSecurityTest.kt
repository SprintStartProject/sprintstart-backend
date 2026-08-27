package com.sprintstart.sprintstartbackend.connectors.confluence.model.api

import com.sprintstart.sprintstartbackend.connectors.confluence.model.api.request.CreateConfluenceConnectionRequest
import com.sprintstart.sprintstartbackend.connectors.confluence.model.api.response.ConfluenceConnectionResponse
import com.sprintstart.sprintstartbackend.connectors.confluence.model.entity.ConfluenceSpaceConnection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import java.util.UUID

class ConfluenceConnectionSecurityTest {
    private val plaintextToken = "phase-four-secret-token"

    @Test
    fun `request and credential toString redact secrets`() {
        val request = CreateConfluenceConnectionRequest(
            baseUrl = "https://tenant.atlassian.net",
            spaceId = "123",
            email = "fake-user@example.invalid",
            apiToken = plaintextToken,
        )
        val connection = ConfluenceSpaceConnection(
            projectId = UUID.randomUUID(),
            baseUrl = request.baseUrl,
            spaceId = request.spaceId,
            spaceKey = "ENG",
        )
        connection.configureCredential(request.email, request.apiToken)

        assertThat(request.toString()).doesNotContain(plaintextToken, request.email)
        assertThat(connection.credential.toString()).doesNotContain(plaintextToken, request.email)
        assertThat(connection.toString()).doesNotContain(plaintextToken, request.email)
    }

    @Test
    fun `response serialization has no credential or authorization fields`() {
        val response = ConfluenceConnectionResponse(
            id = UUID.randomUUID(),
            projectId = UUID.randomUUID(),
            baseUrl = "https://tenant.atlassian.net",
            spaceId = "123",
            spaceKey = "ENG",
            pageAllowlist = listOf("10"),
            pageDenylist = emptyList(),
            credentialsConfigured = true,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
            version = 0,
        )

        val json = jacksonObjectMapper().writeValueAsString(response)

        assertThat(json).doesNotContain(
            "apiToken",
            "api_token",
            "token",
            "email",
            "Authorization",
            "Basic ",
            plaintextToken,
        )
        assertThat(json).contains("credentialsConfigured")
    }
}
