package com.sprintstart.sprintstartbackend.connectors.confluence.client

import java.net.URI
import java.time.DateTimeException
import java.time.OffsetDateTime

internal const val CONFLUENCE_VERSION_PAGE_LIMIT = 250

internal fun confluencePageVersionsUri(baseUrl: String, pageId: String): URI {
    val path = "/wiki/api/v2/pages/${pageId.encodeConfluencePathSegment()}/versions"
    return confluenceTenantUri(baseUrl, "$path?limit=$CONFLUENCE_VERSION_PAGE_LIMIT")
}

/** The version as an edit, or `null` when Confluence names no author for it, as for some imported pages. */
internal fun ConfluencePageVersionItemResponse.toEdit(pageId: String): ConfluencePageEdit? {
    val author = authorId?.takeIf { it.isNotBlank() } ?: return null
    val editedAt = try {
        OffsetDateTime.parse(createdAt).toInstant()
    } catch (exception: DateTimeException) {
        throw ConfluenceInvalidResponseException("mapping versions of page $pageId", exception)
    }
    return ConfluencePageEdit(number, author, editedAt)
}
