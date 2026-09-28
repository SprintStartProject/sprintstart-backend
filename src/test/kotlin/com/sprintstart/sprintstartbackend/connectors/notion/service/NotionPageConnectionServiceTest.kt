package com.sprintstart.sprintstartbackend.connectors.notion.service

import com.sprintstart.sprintstartbackend.connectors.notion.NotionRichText
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionClient
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionPagePropertyResponse
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionPageResponse
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionParentResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class NotionPageConnectionServiceTest {
    private val notionClient = mockk<NotionClient>()
    private val credentialPersistenceService = mockk<NotionCredentialPersistenceService>()
    private val connectionPersistenceService = mockk<NotionPageConnectionPersistenceService>()
    private val service = NotionPageConnectionService(
        notionClient,
        credentialPersistenceService,
        connectionPersistenceService,
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

    private fun page(
        id: String,
        title: List<NotionRichText>? = null,
        includeTitleProperty: Boolean = true,
    ): NotionPageResponse {
        val properties = if (includeTitleProperty) {
            mapOf("Name" to NotionPagePropertyResponse(type = "title", title = title))
        } else {
            emptyMap()
        }
        return NotionPageResponse(
            id = id,
            url = "https://www.notion.so/$id",
            lastEditedTime = "2026-09-27T10:00:00.000Z",
            inTrash = false,
            parent = NotionParentResponse(type = "workspace", workspace = true),
            properties = properties,
        )
    }
}
