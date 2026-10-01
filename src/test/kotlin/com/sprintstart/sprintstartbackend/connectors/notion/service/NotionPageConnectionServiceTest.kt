package com.sprintstart.sprintstartbackend.connectors.notion.service

import com.sprintstart.sprintstartbackend.connectors.notion.NotionRichText
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionApiPagePropertyResponse
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionApiPageResponse
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionApiParentResponse
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionClient
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.request.ConfigureNotionScheduleRequest
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.response.NotionPageConnectionResponse
import com.sprintstart.sprintstartbackend.shared.scheduler.CronBuilder
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduleSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

internal class NotionPageConnectionServiceTest {
    private val notionClient = mockk<NotionClient>()
    private val credentialPersistenceService = mockk<NotionCredentialPersistenceService>()
    private val connectionPersistenceService = mockk<NotionPageConnectionPersistenceService>()
    private val cronBuilder = mockk<CronBuilder>()
    private val scheduleCalculator = mockk<NotionScheduleCalculator>()
    private val service = NotionPageConnectionService(
        notionClient,
        credentialPersistenceService,
        connectionPersistenceService,
        cronBuilder,
        scheduleCalculator,
    )

    @Test
    fun `discovery trims credential name and maps safe page fields`() = runTest {
        every { credentialPersistenceService.requireToken("auth-id", "team-token") } returns "secret-token"
        coEvery { notionClient.discoverPages("secret-token") } returns listOf(
            page(
                id = "page-1",
                title = listOf(NotionRichText("Sprint"), NotionRichText(" Start")),
            ),
        )

        val result = service.discoverPages("auth-id", "  team-token  ")

        assertThat(result).containsExactly(
            com.sprintstart.sprintstartbackend.connectors.notion.model.api.response.NotionDiscoveredPageResponse(
                id = "page-1",
                title = "Sprint Start",
                url = "https://www.notion.so/page-1",
                lastEditedTime = "2026-09-27T10:00:00.000Z",
            ),
        )
        verify(exactly = 1) { credentialPersistenceService.requireToken("auth-id", "team-token") }
        coVerify(exactly = 1) { notionClient.discoverPages("secret-token") }
    }

    @Test
    fun `discovery uses Untitled for missing empty and blank titles`() = runTest {
        every { credentialPersistenceService.requireToken(any(), any()) } returns "secret-token"
        coEvery { notionClient.discoverPages("secret-token") } returns listOf(
            page(id = "missing", includeTitleProperty = false),
            page(id = "empty", title = emptyList()),
            page(id = "blank", title = listOf(NotionRichText("   "))),
        )

        val result = service.discoverPages("auth-id", "team-token")

        assertThat(result.map { it.title }).containsExactly("Untitled", "Untitled", "Untitled")
    }

    @Test
    fun `schedule configuration stores generated cron and next run when enabled`() {
        val projectId = java.util.UUID.randomUUID()
        val connectionId = java.util.UUID.randomUUID()
        val request = ConfigureNotionScheduleRequest(ScheduleSpec.Interval(30), autoUpdate = true)
        val next = Instant.parse("2026-09-30T12:30:00Z")
        val expected = mockk<NotionPageConnectionResponse>()
        every { cronBuilder.build(request.schedule) } returns "0 */30 * * * *"
        every { scheduleCalculator.calculateNextSyncAt("0 */30 * * * *", any()) } returns next
        every {
            connectionPersistenceService.configureSchedule(
                projectId,
                connectionId,
                request.schedule,
                "0 */30 * * * *",
                true,
                next,
            )
        } returns expected

        val result = service.configureSchedule(projectId, connectionId, request)

        assertThat(result).isSameAs(expected)
    }

    @Test
    fun `invalid generated schedule is rejected before persistence`() {
        val request = ConfigureNotionScheduleRequest(ScheduleSpec.Custom("invalid"), autoUpdate = true)
        every { cronBuilder.build(request.schedule) } returns "invalid"
        every { scheduleCalculator.calculateNextSyncAt("invalid", any()) } returns null

        org.assertj.core.api.Assertions
            .assertThatThrownBy {
                service.configureSchedule(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), request)
            }.isInstanceOf(
                com.sprintstart.sprintstartbackend.connectors.notion.model.exception
                    .NotionPageConnectionConfigurationException::class.java,
            )
        verify(exactly = 0) { connectionPersistenceService.configureSchedule(any(), any(), any(), any(), any(), any()) }
    }

    private fun page(
        id: String,
        title: List<NotionRichText>? = null,
        includeTitleProperty: Boolean = true,
    ): NotionApiPageResponse {
        val properties = if (includeTitleProperty) {
            mapOf("Name" to NotionApiPagePropertyResponse(type = "title", title = title))
        } else {
            emptyMap()
        }
        return NotionApiPageResponse(
            id = id,
            url = "https://www.notion.so/$id",
            lastEditedTime = "2026-09-27T10:00:00.000Z",
            inTrash = false,
            parent = NotionApiParentResponse(type = "workspace", workspace = true),
            properties = properties,
        )
    }
}
