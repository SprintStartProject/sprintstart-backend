package com.sprintstart.sprintstartbackend.connectors.notion.client

import com.sprintstart.sprintstartbackend.shared.web.WebClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeParseException

internal class NotionClient(
    private val webClient: WebClient,
    private val retryExecutor: NotionRetryExecutor,
    private val baseUri: URI = URI.create("https://api.notion.com"),
    private val apiVersion: String = "2026-03-11",
) {
    init {
        require(
            baseUri.isAbsolute &&
                baseUri.scheme in setOf("https", "http") &&
                baseUri.host != null &&
                baseUri.userInfo == null &&
                baseUri.query == null &&
                baseUri.fragment == null &&
                baseUri.path in setOf("", "/"),
        ) { "Notion base URI must be an HTTP origin" }
        try {
            LocalDate.parse(apiVersion)
        } catch (@Suppress("SwallowedException") exception: DateTimeParseException) {
            throw IllegalArgumentException("Notion API version must use YYYY-MM-DD")
        }
    }

    suspend fun validateConnection(token: String) {
        val response = performGet<NotionUserResponse>(
            notionCurrentUserUri(baseUri),
            token,
            "validating the connection",
        )
        if (response.objectType != "user" || response.id.isBlank()) {
            throw NotionInvalidResponseException("validating the connection")
        }
    }

    suspend fun getBlockChildrenBatch(
        token: String,
        blockId: String,
        startCursor: String? = null,
    ): NotionBlocksResponse {
        val response = performGet<NotionBlocksResponse>(
            notionBlockChildrenUri(baseUri, blockId, startCursor),
            token,
            "retrieving block children",
        )
        validateNotionPagination(response.hasMore, response.nextCursor, "retrieving block children")
        return response
    }

    suspend fun getAllBlockChildren(token: String, blockId: String): List<NotionBlockResponse> {
        val blocks = mutableListOf<NotionBlockResponse>()
        val usedCursors = mutableSetOf<String>()
        var cursor: String? = null
        while (true) {
            val response = getBlockChildrenBatch(token, blockId, cursor)
            blocks.addAll(response.results)
            if (!response.hasMore) return blocks
            cursor = nextNotionCursor(response.nextCursor, usedCursors, "retrieving block children")
        }
    }

    /**
     * Loads a complete block tree without crossing child-page or database boundaries.
     *
     * Pagination is completed at every level. Failed descendants, cyclic responses and
     * trees deeper than 128 containers fail the operation instead of returning partial content.
     */
    suspend fun getBlockTree(token: String, blockId: String): List<NotionBlockNode> {
        return loadBlockTree(token, blockId, mutableSetOf())
    }

    private suspend fun loadBlockTree(
        token: String,
        blockId: String,
        ancestors: MutableSet<String>,
    ): List<NotionBlockNode> {
        if (ancestors.size >= 128 || !ancestors.add(blockId)) {
            throw NotionInvalidResponseException("loading the block tree")
        }
        val nodes = mutableListOf<NotionBlockNode>()
        for (response in getAllBlockChildren(token, blockId)) {
            if (response.id.isBlank() || response.id in ancestors) {
                throw NotionInvalidResponseException("loading the block tree")
            }
            var children = emptyList<NotionBlockNode>()
            if (
                response.hasChildren &&
                response.type != "child_page" &&
                response.type != "child_database"
            ) {
                children = loadBlockTree(token, response.id, ancestors)
            }
            nodes.add(NotionBlockNode(response, children))
        }
        ancestors.remove(blockId)
        return nodes
    }

    suspend fun getPage(token: String, pageId: String): NotionPageResponse {
        return performGet(notionPageUri(baseUri, pageId), token, "retrieving page")
    }

    suspend fun searchPagesBatch(token: String, startCursor: String? = null): NotionSearchResponse {
        val body = buildJsonObject {
            put("page_size", PAGE_SIZE)
            putJsonObject("filter") {
                put("property", "object")
                put("value", "page")
            }
            if (startCursor != null) put("start_cursor", startCursor)
        }
        val response = performPost<NotionSearchResponse>(
            notionSearchUri(baseUri),
            token,
            body,
            "discovering pages",
        )
        validateNotionPagination(response.hasMore, response.nextCursor, "discovering pages")
        return response
    }

    suspend fun discoverPages(token: String): List<NotionPageResponse> {
        val pages = mutableListOf<NotionPageResponse>()
        val usedCursors = mutableSetOf<String>()
        var cursor: String? = null
        while (true) {
            val response = searchPagesBatch(token, cursor)
            pages += response.results.filter { page ->
                !page.inTrash && page.parent.type !in setOf("data_source_id", "database_id")
            }
            if (!response.hasMore) return pages
            cursor = nextNotionCursor(response.nextCursor, usedCursors, "discovering pages")
        }
    }

    private suspend inline fun <reified T> performGet(uri: URI, token: String, requestContext: String): T {
        validateToken(token, requestContext)
        return retryExecutor.execute(requestContext) {
            webClient
                .get()
                .uri(uri)
                .header("Authorization", "Bearer $token")
                .header("Notion-Version", apiVersion)
                .header("Accept", "application/json")
                .sync()
                .perform<T>()
        }
    }

    private suspend inline fun <reified T> performPost(
        uri: URI,
        token: String,
        body: JsonObject,
        requestContext: String,
    ): T {
        validateToken(token, requestContext)
        return retryExecutor.execute(requestContext) {
            webClient
                .post()
                .uri(uri)
                .header("Authorization", "Bearer $token")
                .header("Notion-Version", apiVersion)
                .header("Accept", "application/json")
                .body(body)
                .sync()
                .perform<T>()
        }
    }

    private fun validateToken(token: String, requestContext: String) {
        if (token.isBlank() || token.any { it <= ' ' || it >= '\u007f' }) {
            throw NotionAuthenticationException(requestContext, attempts = 0)
        }
    }
}

private fun validateNotionPagination(hasMore: Boolean, nextCursor: String?, requestContext: String) {
    if ((hasMore && nextCursor.isNullOrBlank()) || (!hasMore && nextCursor != null)) {
        throw NotionInvalidResponseException(requestContext)
    }
}

private fun nextNotionCursor(
    cursor: String?,
    usedCursors: MutableSet<String>,
    requestContext: String,
): String {
    if (cursor.isNullOrBlank() || !usedCursors.add(cursor)) {
        throw NotionInvalidResponseException(requestContext)
    }
    return cursor
}
