package com.sprintstart.sprintstartbackend.connectors.notion.service

import com.sprintstart.sprintstartbackend.connectors.notion.NotionRichText
import com.sprintstart.sprintstartbackend.connectors.notion.ParsedNotionBody
import com.sprintstart.sprintstartbackend.connectors.notion.ParsedNotionCodeBlock
import com.sprintstart.sprintstartbackend.connectors.notion.ParsedNotionSection
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionApiPagePropertyResponse
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionApiPageResponse
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionApiParentResponse
import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionPageConnection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class NotionPageArtifactMapperTest {
    private val mapper = NotionPageArtifactMapper()

    @Test
    fun `maps page provenance parsed structure and connection-scoped identity`() {
        val connection = connection()
        val page = page()
        val parsed = ParsedNotionBody(
            bodyText = "# Runbook\n\nDeploy safely.",
            sections = listOf(ParsedNotionSection("Runbook", 1)),
            tables = listOf("| Env |\n| --- |\n| prod |"),
            codeBlocks = listOf(ParsedNotionCodeBlock("bash", "kubectl apply")),
        )

        val result = mapper.toCommand(connection, page, parsed)

        assertThat(result.sourceId).isEqualTo("notion:${connection.id}:page:page-1")
        assertThat(result.sourceUrl).isEqualTo("https://www.notion.so/page-1")
        assertThat(result.sourceVersion).isEqualTo("2026-09-27T10:00:00Z")
        assertThat(result.title).isEqualTo("Sprint Start")
        assertThat(result.bodyText).isEqualTo(parsed.bodyText)
        assertThat(result.lastEditedTime).isEqualTo(Instant.parse("2026-09-27T10:00:00Z"))
        assertThat(result.metadata.connectionId).isEqualTo(connection.id)
        assertThat(result.metadata.pageId).isEqualTo("page-1")
        assertThat(result.metadata.sections.single().heading).isEqualTo("Runbook")
        assertThat(result.metadata.tables).containsExactlyElementsOf(parsed.tables)
        assertThat(result.metadata.codeBlocks.single().code).isEqualTo("kubectl apply")
    }

    @Test
    fun `falls back to stored title when Notion title is blank`() {
        val result = mapper.toCommand(
            connection(),
            page(title = listOf(NotionRichText("   "))),
            ParsedNotionBody("body"),
        )

        assertThat(result.title).isEqualTo("Stored title")
    }

    private fun connection(): NotionPageConnection {
        return NotionPageConnection(
            id = UUID.randomUUID(),
            projectId = UUID.randomUUID(),
            pageId = "page-1",
            pageTitle = "Stored title",
            pageUrl = "https://www.notion.so/page-1",
            credentialAuthId = "auth-id",
            credentialName = "team-token",
        )
    }

    private fun page(
        title: List<NotionRichText> = listOf(NotionRichText("Sprint"), NotionRichText(" Start")),
    ): NotionApiPageResponse {
        return NotionApiPageResponse(
            id = "page-1",
            url = "https://www.notion.so/page-1",
            lastEditedTime = "2026-09-27T10:00:00Z",
            inTrash = false,
            parent = NotionApiParentResponse(type = "workspace", workspace = true),
            properties = mapOf("Name" to NotionApiPagePropertyResponse("title", title)),
        )
    }
}
