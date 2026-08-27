package com.sprintstart.sprintstartbackend.connectors.confluence.service

import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceClientCredentials
import java.util.UUID

internal data class ConfluenceConnectionSourceSnapshot(
    val id: UUID,
    val baseUrl: String,
    val spaceId: String,
    val spaceKey: String,
    val sourceEnabled: Boolean,
)

internal class ConfluenceConnectionIngestionSnapshot(
    val id: UUID,
    val projectId: UUID,
    val baseUrl: String,
    val spaceId: String,
    val spaceKey: String,
    val sourceEnabled: Boolean,
    val pageAllowlist: List<String>,
    val pageDenylist: List<String>,
    val credentials: ConfluenceClientCredentials,
) {
    fun allowsPage(pageId: String): Boolean {
        val normalizedPageId = pageId.trim()
        if (normalizedPageId.isEmpty() || normalizedPageId in pageDenylist) {
            return false
        }
        return pageAllowlist.isEmpty() || normalizedPageId in pageAllowlist
    }

    override fun toString(): String {
        return "ConfluenceConnectionIngestionSnapshot(" +
            "id=$id, projectId=$projectId, baseUrl=$baseUrl, spaceId=$spaceId, credentials=<redacted>)"
    }
}
