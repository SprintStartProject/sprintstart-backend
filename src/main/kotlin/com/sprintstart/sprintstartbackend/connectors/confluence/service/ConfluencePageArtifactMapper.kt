package com.sprintstart.sprintstartbackend.connectors.confluence.service

import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluencePage
import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluenceCodeBlockCommand
import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluencePageArtifactCommand
import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluencePageMetadataCommand
import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluenceRelationshipCommand
import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluenceSectionCommand
import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluenceSourceAclCommand
import com.sprintstart.sprintstartbackend.connectors.confluence.parser.ParsedConfluenceBody
import org.springframework.stereotype.Component
import java.net.URI

/** Maps one parsed Confluence page into the ingestion module's canonical write command. */
@Component
internal class ConfluencePageArtifactMapper {
    fun toCommand(
        connection: ConfluenceConnectionIngestionSnapshot,
        page: ConfluencePage,
        parsedBody: ParsedConfluenceBody,
        relationships: List<ConfluenceRelationshipCommand>,
    ): ConfluencePageArtifactCommand {
        val sourceUrl = safePageUrl(connection.baseUrl, page.webUiPath)
        return ConfluencePageArtifactCommand(
            sourceId = confluencePageSourceId(connection.id.toString(), page.id),
            sourceUrl = sourceUrl,
            sourceVersion = page.version.number.toString(),
            title = page.title,
            bodyText = parsedBody.bodyText,
            versionCreatedAt = page.version.createdAt,
            metadata = metadata(connection, page, parsedBody, relationships),
        )
    }

    private fun metadata(
        connection: ConfluenceConnectionIngestionSnapshot,
        page: ConfluencePage,
        parsedBody: ParsedConfluenceBody,
        relationships: List<ConfluenceRelationshipCommand>,
    ) = ConfluencePageMetadataCommand(
        connectionId = connection.id,
        tenantBaseUrl = connection.baseUrl,
        spaceId = connection.spaceId,
        spaceKey = connection.spaceKey,
        pageId = page.id,
        versionNumber = page.version.number,
        versionCreatedAt = page.version.createdAt,
        parentId = page.parentId,
        parentType = page.parentType,
        webUiPath = safePageUrl(connection.baseUrl, page.webUiPath)?.let { url -> URI.create(url).rawPath },
        sections = parsedBody.sections.map { section ->
            ConfluenceSectionCommand(section.heading, section.level)
        },
        tables = parsedBody.tables,
        codeBlocks = parsedBody.codeBlocks.map { block ->
            ConfluenceCodeBlockCommand(block.language, block.code)
        },
        relationships = relationships,
        sourceAcl = sourceAcl(page),
    )

    private fun sourceAcl(page: ConfluencePage): ConfluenceSourceAclCommand {
        val users = page.restrictions.users
        val groups = page.restrictions.groups
        return ConfluenceSourceAclCommand(
            userAccountIds = users.map { it.accountId }.distinct().sorted(),
            groupIds = groups.map { it.id }.distinct().sorted(),
        )
    }
}

internal fun confluencePageSourceId(connectionId: String, pageId: String): String {
    return "confluence:$connectionId:page:$pageId"
}

internal fun safePageUrl(baseUrl: String, webUiPath: String?): String? {
    if (webUiPath.isNullOrBlank()) {
        return null
    }
    return try {
        val tenant = URI.create(baseUrl)
        val candidate = URI.create(webUiPath)
        val resolved = if (candidate.isAbsolute || webUiPath.startsWith('/')) {
            tenant.resolve(candidate)
        } else {
            tenant.resolve("/$webUiPath")
        }
        if (resolved.hasSameOrigin(tenant) && resolved.userInfo == null) {
            URI(resolved.scheme, null, resolved.host, resolved.port, resolved.path, null, null).toString()
        } else {
            null
        }
    } catch (@Suppress("SwallowedException") exception: IllegalArgumentException) {
        null
    }
}

private fun URI.hasSameOrigin(other: URI): Boolean {
    return scheme.equals(other.scheme, ignoreCase = true) &&
        host.equals(other.host, ignoreCase = true) &&
        effectivePort() == other.effectivePort()
}

private fun URI.effectivePort(): Int {
    return if (port == -1) 443 else port
}
