package com.sprintstart.sprintstartbackend.connectors.confluence.service

import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluencePage
import com.sprintstart.sprintstartbackend.connectors.confluence.parser.ParsedConfluenceBody
import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluenceCodeBlockCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluencePageArtifactCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluencePageMetadataCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluenceRelationshipCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluenceSectionCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluenceSourceAclCommand
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
        val userAccountIds = page.restrictions.users
            .map { user -> user.accountId }
            .distinct()
            .sorted()
        val groupIds = page.restrictions.groups
            .map { group -> group.id }
            .distinct()
            .sorted()
        return ConfluencePageArtifactCommand(
            sourceId = confluencePageSourceId(connection.id.toString(), page.id),
            sourceUrl = sourceUrl,
            sourceVersion = page.version.number.toString(),
            title = page.title,
            bodyText = parsedBody.bodyText,
            versionCreatedAt = page.version.createdAt,
            metadata = ConfluencePageMetadataCommand(
                connectionId = connection.id,
                tenantBaseUrl = connection.baseUrl,
                spaceId = connection.spaceId,
                spaceKey = connection.spaceKey,
                pageId = page.id,
                versionNumber = page.version.number,
                versionCreatedAt = page.version.createdAt,
                parentId = page.parentId,
                parentType = page.parentType,
                webUiPath = sourceUrl?.let { url -> URI.create(url).rawPath },
                sections = parsedBody.sections.map { section ->
                    ConfluenceSectionCommand(section.heading, section.level)
                },
                tables = parsedBody.tables,
                codeBlocks = parsedBody.codeBlocks.map { block ->
                    ConfluenceCodeBlockCommand(block.language, block.code)
                },
                relationships = relationships,
                sourceAcl = ConfluenceSourceAclCommand(
                    userAccountIds = userAccountIds,
                    groupIds = groupIds,
                ),
            ),
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
