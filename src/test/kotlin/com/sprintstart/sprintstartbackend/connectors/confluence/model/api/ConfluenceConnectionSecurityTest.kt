package com.sprintstart.sprintstartbackend.connectors.confluence.model.api

import com.sprintstart.sprintstartbackend.connectors.confluence.model.api.request.CreateConfluenceConnectionRequest
import com.sprintstart.sprintstartbackend.connectors.confluence.model.api.response.ConfluenceConnectionResponse
import com.sprintstart.sprintstartbackend.connectors.confluence.model.entity.ConfluenceSpaceConnection
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduleSpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

class ConfluenceConnectionSecurityTest {
    @Test
    fun `request and connection toString carry no secret material`() {
        val request = CreateConfluenceConnectionRequest(
            baseUrl = "https://tenant.atlassian.net",
            spaceId = "123",
            credentialName = "team-token",
        )
        val connection = ConfluenceSpaceConnection(
            projectId = UUID.randomUUID(),
            baseUrl = request.baseUrl,
            spaceId = request.spaceId,
            spaceKey = "ENG",
            credentialAuthId = "auth-id",
            credentialName = request.credentialName,
        )

        assertThat(request.toString()).doesNotContain("apiToken", "email", "Authorization", "Basic ")
        assertThat(connection.toString()).doesNotContain("apiToken", "email", "Authorization", "Basic ")
    }

    @Test
    fun `response serialization has no credential or authorization fields`() {
        val response = ConfluenceConnectionResponse(
            id = UUID.randomUUID(),
            projectId = UUID.randomUUID(),
            baseUrl = "https://tenant.atlassian.net",
            spaceId = "123",
            spaceKey = "ENG",
            spaceName = "Engineering",
            credentialName = "team-token",
            pageAllowlist = listOf("10"),
            pageDenylist = emptyList(),
            credentialsConfigured = true,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
            version = 0,
            spec = ScheduleSpec.Daily(LocalTime.of(2, 0)),
            schedule = "0 0 2 * * *",
            nextSyncAt = null,
        )

        val json = jacksonObjectMapper().writeValueAsString(response)

        assertThat(json).doesNotContain(
            "apiToken",
            "api_token",
            "email",
            "Authorization",
            "Basic ",
        )
        assertThat(json).contains("credentialsConfigured", "credentialName")
    }
}
