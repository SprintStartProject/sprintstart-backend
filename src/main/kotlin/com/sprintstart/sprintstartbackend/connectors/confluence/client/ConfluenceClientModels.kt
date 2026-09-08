package com.sprintstart.sprintstartbackend.connectors.confluence.client

import java.time.Instant

/**
 * Holds the credentials needed for Confluence Cloud Basic authentication.
 *
 * This transport-only value deliberately redacts both fields from [toString]. Persistence and encryption of
 * credentials belong to the connection/secrets phase rather than this HTTP client.
 */
internal class ConfluenceClientCredentials(
    val email: String,
    val apiToken: String,
) {
    init {
        require(email.isNotBlank()) { "Confluence email must not be blank" }
        require(apiToken.isNotBlank()) { "Confluence API token must not be blank" }
    }

    override fun toString(): String = "ConfluenceClientCredentials(email=<redacted>, apiToken=<redacted>)"
}

internal data class ConfluenceSpace(
    val id: String,
    val key: String,
    val name: String,
    val type: String,
    val status: String,
    val currentActiveAlias: String?,
    val webUiPath: String?,
)

internal data class ConfluencePage(
    val id: String,
    val title: String,
    val status: String,
    val spaceId: String,
    val parentId: String?,
    val parentType: String?,
    val version: ConfluencePageVersion,
    val storage: ConfluenceStorageBody,
    val webUiPath: String?,
    val restrictions: ConfluencePageRestrictions,
)

internal data class ConfluencePageVersion(
    val number: Int,
    val createdAt: Instant,
)

internal data class ConfluenceStorageBody(
    val representation: String,
    val value: String,
)

internal data class ConfluencePageRestrictions(
    val users: List<ConfluenceRestrictedUser> = emptyList(),
    val groups: List<ConfluenceRestrictedGroup> = emptyList(),
)

internal data class ConfluenceRestrictedUser(
    val accountId: String,
)

internal data class ConfluenceRestrictedGroup(
    val id: String,
)

/**
 * Returns pages that are safe to ingest together with item-level retrieval failures.
 *
 * A page is successful only after its read restrictions have been retrieved. Authentication, authorization,
 * page-list, and space failures remain terminal and are therefore not represented in [failures].
 */
internal data class ConfluencePageBatchResult(
    val successfulPages: List<ConfluencePage>,
    val failures: List<ConfluencePageFailure>,
)

internal data class ConfluencePageFailure(
    val pageId: String,
    val stage: ConfluencePageFetchStage,
    val httpStatus: Int?,
    val attempts: Int = 1,
    val message: String,
)

internal enum class ConfluencePageFetchStage {
    RESTRICTIONS,
}
