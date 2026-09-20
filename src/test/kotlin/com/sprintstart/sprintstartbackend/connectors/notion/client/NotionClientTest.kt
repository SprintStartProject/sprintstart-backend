package com.sprintstart.sprintstartbackend.connectors.notion.client

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Duration

internal class NotionClientTest : NotionClientTestSupport() {
    @Test
    fun `validates PAT using bearer headers and current user endpoint`() = runTest {
        enqueueJson("""{"object":"user","id":"user-id","type":"person","person":{"email":"ignored"}}""")

        client.validateConnection(NOTION_TEST_TOKEN)

        val request = takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).isEqualTo("/v1/users/me")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer $NOTION_TEST_TOKEN")
        assertThat(request.getHeader("Notion-Version")).isEqualTo("2026-03-11")
        assertThat(request.getHeader("Accept")).isEqualTo("application/json")
        assertThat(request.body.size).isZero()
        assertThat(waits).isEmpty()
    }

    @Test
    fun `gets page metadata and encodes the page id as one segment`() = runTest {
        enqueueJson(page("result-id").toString())

        val result = client.getPage(NOTION_TEST_TOKEN, "id/with?reserved#chars")

        assertThat(result.id).isEqualTo("result-id")
        assertThat(takeRequest().path).isEqualTo("/v1/pages/id%2Fwith%3Freserved%23chars")
    }

    @Test
    fun `retrieves one block batch with encoded cursor and unchanged pagination metadata`() = runTest {
        enqueueJson(batch(listOf(block("one")), nextCursor = "next"))

        val result = client.getBlockChildrenBatch(NOTION_TEST_TOKEN, "block/id", "a+b &c/?#")

        assertThat(result.results.map { it.id }).containsExactly("one")
        assertThat(result.hasMore).isTrue()
        assertThat(result.nextCursor).isEqualTo("next")
        val request = takeRequest()
        assertThat(request.requestUrl?.encodedPath).isEqualTo("/v1/blocks/block%2Fid/children")
        assertThat(request.requestUrl?.queryParameter("page_size")).isEqualTo("100")
        assertThat(request.requestUrl?.queryParameter("start_cursor")).isEqualTo("a+b &c/?#")
    }

    @Test
    fun `collects every block batch in order without traversing nested children`() = runTest {
        enqueueJson(batch(listOf(NotionJsonFixtures.block("toggle")), nextCursor = "cursor-two"))
        enqueueJson(batch(listOf(block("last"))))

        val result = client.getAllBlockChildren(NOTION_TEST_TOKEN, "page-id")

        assertThat(result).hasSize(2)
        assertThat(result[0].type).isEqualTo("toggle")
        assertThat(result[0].hasChildren).isTrue()
        assertThat(result[1].id).isEqualTo("last")
        assertThat(takeRequest().requestUrl?.queryParameter("start_cursor")).isNull()
        assertThat(takeRequest().requestUrl?.queryParameter("start_cursor")).isEqualTo("cursor-two")
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `posts page search filter and omits cursor on first request`() = runTest {
        enqueueJson(batch(listOf(page("page-one"))))

        val result = client.searchPagesBatch(NOTION_TEST_TOKEN)

        assertThat(result.results.single().id).isEqualTo("page-one")
        val request = takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/v1/search")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer $NOTION_TEST_TOKEN")
        assertThat(request.getHeader("Notion-Version")).isEqualTo("2026-03-11")
        assertThat(request.getHeader("Content-Type")).isEqualTo("application/json")
        assertThat(request.getHeader("Accept")).isEqualTo("application/json")
        val body = NotionJsonFixtures.json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertThat(body.keys).containsExactlyInAnyOrder("page_size", "filter")
        assertThat(body.getValue("page_size").jsonPrimitive.content).isEqualTo("100")
        assertThat(body.getValue("filter").jsonObject.getValue("value").jsonPrimitive.content).isEqualTo("page")
        assertThat(body.getValue("filter").jsonObject.getValue("property").jsonPrimitive.content).isEqualTo("object")
    }

    @Test
    fun `search cursor is preserved and JSON escaped rather than URL encoded`() = runTest {
        enqueueJson(batch())
        val cursor = "a+b&\"\\cursor"

        client.searchPagesBatch(NOTION_TEST_TOKEN, cursor)

        val body = NotionJsonFixtures.json.parseToJsonElement(takeRequest().body.readUtf8()).jsonObject
        assertThat(body.getValue("start_cursor").jsonPrimitive.content).isEqualTo(cursor)
    }

    @Test
    fun `discovery continues through filtered batches and excludes trashed and database rows`() = runTest {
        enqueueJson(
            batch(
                listOf(page("trashed", inTrash = true), page("row", "data_source_id")),
                nextCursor = "next",
            ),
        )
        enqueueJson(batch(listOf(page("legacy-row", "database_id"), page("visible", "page_id"))))

        val pages = client.discoverPages(NOTION_TEST_TOKEN)

        assertThat(pages.map { it.id }).containsExactly("visible")
        takeRequest()
        val body = NotionJsonFixtures.json.parseToJsonElement(takeRequest().body.readUtf8()).jsonObject
        assertThat(body.getValue("start_cursor").jsonPrimitive.content).isEqualTo("next")
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `rejects repeated cursors in both pagination loops`(search: Boolean) = runTest {
        enqueueJson(batch(nextCursor = "repeated"))
        enqueueJson(batch(nextCursor = "repeated"))

        assertThrows<NotionInvalidResponseException> {
            if (search) {
                client.discoverPages(NOTION_TEST_TOKEN)
            } else {
                client.getAllBlockChildren(NOTION_TEST_TOKEN, "page")
            }
        }

        assertThat(server.requestCount).isEqualTo(2)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `rejects missing blank and contradictory cursors for both response types`(search: Boolean) = runTest {
        val bodies = listOf(
            batch(hasMore = true),
            batch(nextCursor = "", hasMore = true),
            batch(nextCursor = "unexpected", hasMore = false),
        )
        for (body in bodies) {
            enqueueJson(body)
            assertThrows<NotionInvalidResponseException> {
                if (search) {
                    client.searchPagesBatch(NOTION_TEST_TOKEN)
                } else {
                    client.getBlockChildrenBatch(NOTION_TEST_TOKEN, "p")
                }
            }
        }
        assertThat(server.requestCount).isEqualTo(3)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `does not return a partial list when a later batch fails`(search: Boolean) = runTest {
        enqueueJson(batch(if (search) listOf(page("one")) else listOf(block("one")), nextCursor = "next"))
        enqueueError(403)

        assertThrows<NotionAccessDeniedException> {
            if (search) client.discoverPages(NOTION_TEST_TOKEN) else client.getAllBlockChildren(NOTION_TEST_TOKEN, "p")
        }

        assertThat(server.requestCount).isEqualTo(2)
    }

    @ParameterizedTest
    @ValueSource(ints = [400, 401, 403, 404, 501])
    fun `maps terminal HTTP errors without exposing raw body or retrying`(status: Int) = runTest {
        enqueueError(status)

        val error = assertThrows<NotionClientException> { client.validateConnection(NOTION_TEST_TOKEN) }

        assertThat(error.httpStatus).isEqualTo(status)
        assertThat(error.attempts).isEqualTo(1)
        assertThat(error.cause).isNull()
        assertThat(error.stackTraceToString()).doesNotContain(NOTION_TEST_TOKEN, "sensitive upstream body")
        assertThat(server.requestCount).isEqualTo(1)
        val expected = when (status) {
            401 -> NotionAuthenticationException::class.java
            403 -> NotionAccessDeniedException::class.java
            404 -> NotionResourceNotFoundException::class.java
            else -> NotionExternalServiceException::class.java
        }
        assertThat(error).isInstanceOf(expected)
    }

    @ParameterizedTest
    @ValueSource(strings = ["not JSON", "{}", "{\"object\":\"page\",\"id\":\"wrong-object\"}"])
    fun `rejects malformed validation responses without retry or raw body exposure`(body: String) = runTest {
        enqueueJson(body)

        val error = assertThrows<NotionInvalidResponseException> { client.validateConnection(NOTION_TEST_TOKEN) }

        assertThat(error.cause).isNull()
        assertThat(server.requestCount).isEqualTo(1)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "bad\ntoken"])
    fun `rejects unusable token before sending a request`(token: String) = runTest {
        val error = assertThrows<NotionAuthenticationException> { client.validateConnection(token) }

        assertThat(error.attempts).isZero()
        assertThat(server.requestCount).isZero()
    }

    @ParameterizedTest
    @ValueSource(ints = [429, 529])
    fun `retries overloaded requests after server requested delay`(status: Int) = runTest {
        enqueueError(status, "5")
        enqueueJson(batch())

        client.searchPagesBatch(NOTION_TEST_TOKEN)

        assertThat(server.requestCount).isEqualTo(2)
        assertThat(waits).containsExactly(Duration.ofSeconds(5))
        assertThat(takeRequest().body.readUtf8()).isEqualTo(takeRequest().body.readUtf8())
    }

    @Test
    fun `retries only the failed pagination request`() = runTest {
        enqueueJson(batch(listOf(block("first")), nextCursor = "second"))
        enqueueError(503)
        enqueueJson(batch(listOf(block("last"))))

        assertThat(client.getAllBlockChildren(NOTION_TEST_TOKEN, "p").map { it.id })
            .containsExactly("first", "last")

        assertThat(takeRequest().requestUrl?.queryParameter("start_cursor")).isNull()
        val failedPath = takeRequest().path
        assertThat(takeRequest().path).isEqualTo(failedPath)
        assertThat(server.requestCount).isEqualTo(3)
    }

    @Test
    fun `server cooldown remains shared across tokens after excessive wait stops retries`() = runTest {
        enqueueError(529, "60")

        assertThrows<NotionExternalServiceException> { client.validateConnection(NOTION_TEST_TOKEN) }
        assertThrows<NotionRequestDeferredException> { client.searchPagesBatch("another-pat") }

        assertThat(server.requestCount).isEqualTo(1)
        assertThat(waits).isEmpty()
    }
}
