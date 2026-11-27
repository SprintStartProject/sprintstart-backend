package com.sprintstart.sprintstartbackend.connectors.notion.service

import com.sprintstart.sprintstartbackend.connectors.notion.ParsedNotionBody
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionApiPageResponse
import com.sprintstart.sprintstartbackend.connectors.notion.external.ingestion.NotionCodeBlockCommand
import com.sprintstart.sprintstartbackend.connectors.notion.external.ingestion.NotionPageArtifactCommand
import com.sprintstart.sprintstartbackend.connectors.notion.external.ingestion.NotionPageEditCommand
import com.sprintstart.sprintstartbackend.connectors.notion.external.ingestion.NotionPageMetadataCommand
import com.sprintstart.sprintstartbackend.connectors.notion.external.ingestion.NotionSectionCommand
import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionWorkspaceConnection
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/** Maps one fetched and parsed Notion page into the ingestion module's write command. */
@Component
internal class NotionPageArtifactMapper {
    fun toCommand(
        connection: NotionWorkspaceConnection,
        page: NotionApiPageResponse,
        parsedBody: ParsedNotionBody,
    ): NotionPageArtifactCommand {
        val lastEditedTime = Instant.parse(page.lastEditedTime)
        val title = page.titleOr("Untitled")

        return NotionPageArtifactCommand(
            sourceId = notionPageSourceId(connection.id, page.id),
            sourceUrl = page.url,
            sourceVersion = page.lastEditedTime,
            title = title,
            bodyText = parsedBody.bodyText,
            lastEditedTime = lastEditedTime,
            workspaceName = connection.workspaceName,
            edits = editsOf(page, lastEditedTime),
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

/** Who created the page and who edited it last, as far as Notion says. */
private fun editsOf(page: NotionApiPageResponse, lastEditedTime: Instant): List<NotionPageEditCommand> {
    val created = page.createdBy?.let { by ->
        page.createdTime?.let {
            NotionPageEditCommand(
                by.id,
                Instant.parse(it),
            )
        }
    }
    val edited = page.lastEditedBy?.let { NotionPageEditCommand(it.id, lastEditedTime) }
    return listOfNotNull(created, edited).distinct()
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
