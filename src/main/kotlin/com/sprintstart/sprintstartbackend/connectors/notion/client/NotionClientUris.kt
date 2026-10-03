package com.sprintstart.sprintstartbackend.connectors.notion.client

import java.net.URI
import java.net.URLEncoder

internal const val PAGE_SIZE = 100

internal fun notionBlockChildrenUri(
    baseUri: URI,
    blockId: String,
    startCursor: String? = null,
): URI {
    val encodedBlockId = blockId.encodeNotionPathSegment()
    if (startCursor == null) {
        return baseUri.resolve("/v1/blocks/$encodedBlockId/children?page_size=$PAGE_SIZE")
    }
    val encodedCursor = URLEncoder.encode(startCursor, Charsets.UTF_8)
    return baseUri.resolve("/v1/blocks/$encodedBlockId/children?page_size=$PAGE_SIZE&start_cursor=$encodedCursor")
}

internal fun notionPageUri(baseUri: URI, pageId: String): URI {
    val encodedPageId = pageId.encodeNotionPathSegment()
    return baseUri.resolve("/v1/pages/$encodedPageId")
}

internal fun notionSearchUri(baseUri: URI): URI {
    return baseUri.resolve("/v1/search")
}

internal fun notionCurrentUserUri(baseUri: URI): URI {
    return baseUri.resolve("/v1/users/me")
}

private fun String.encodeNotionPathSegment(): String {
    return URLEncoder.encode(this, Charsets.UTF_8).replace("+", "%20")
}
