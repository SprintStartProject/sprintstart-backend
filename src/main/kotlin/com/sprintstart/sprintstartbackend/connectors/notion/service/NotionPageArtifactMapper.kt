package com.sprintstart.sprintstartbackend.connectors.notion.service

import com.sprintstart.sprintstartbackend.connectors.notion.ParsedNotionBody
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionApiPageResponse
import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionPageConnection
import com.sprintstart.sprintstartbackend.ingestion.external.model.NotionCodeBlockCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.NotionPageArtifactCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.NotionPageMetadataCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.NotionSectionCommand
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/** Maps one fetched and parsed Notion page into the ingestion module's write command. */
@Component
internal class NotionPageArtifactMapper {
    fun toCommand(
        connection: NotionPageConnection,
        page: NotionApiPageResponse,
        parsedBody: ParsedNotionBody,
    ): NotionPageArtifactCommand {
        val lastEditedTime = Instant.parse(page.lastEditedTime)
        val title = page.titleOr(connection.pageTitle)

        return NotionPageArtifactCommand(
            sourceId = notionPageSourceId(connection.id, page.id),
            sourceUrl = page.url,
            sourceVersion = page.lastEditedTime,
            title = title,
            bodyText = parsedBody.bodyText,
            lastEditedTime = lastEditedTime,
            metadata = NotionPageMetadataCommand(
                connectionId = connection.id,
                pageId = page.id,
                sections = parsedBody.sections.map { section ->
                    NotionSectionCommand(section.heading, section.level)
                },
                tables = parsedBody.tables,
                codeBlocks = parsedBody.codeBlocks.map { block ->
                    NotionCodeBlockCommand(block.language, block.code)
                },
            ),
        )
    }
}

internal fun NotionApiPageResponse.titleOr(fallback: String): String {
    return properties.values
        .firstOrNull { property -> property.type == "title" }
        ?.title
        ?.joinToString(separator = "") { richText -> richText.plainText }
        ?.trim()
        .orEmpty()
        .ifBlank { fallback }
}

internal fun notionPageSourceId(connectionId: UUID, pageId: String): String {
    return "notion:$connectionId:page:$pageId"
}
