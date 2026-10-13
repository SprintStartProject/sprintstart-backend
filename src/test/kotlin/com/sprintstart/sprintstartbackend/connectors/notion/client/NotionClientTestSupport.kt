package com.sprintstart.sprintstartbackend.connectors.notion.client

import com.sprintstart.sprintstartbackend.NotionRetryConfig
import com.sprintstart.sprintstartbackend.NotionThrottleConfig
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.net.http.HttpClient
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

internal const val NOTION_TEST_TOKEN = "test-pat-do-not-log"

internal abstract class NotionClientTestSupport {
    protected val server = MockWebServer()
    protected lateinit var client: NotionClient
    protected lateinit var retryExecutor: NotionRetryExecutor
    protected lateinit var webClient: WebClient
    protected val waits = mutableListOf<Duration>()
    protected var now: Instant = Instant.parse("2026-01-01T00:00:00Z")

    @BeforeEach
    fun setUp() {
        server.start()
        val clock = NotionRetryClock { now }
        val sleeper = NotionRetrySleeper { wait ->
            waits += wait
            now = now.plus(wait)
        }
        val throttle = NotionRequestThrottle(
            NotionThrottleConfig(minInterval = Duration.ofMillis(10)),
            sleeper,
            clock,
        )
        retryExecutor = NotionRetryExecutor(
            NotionRetryConfig(maxAttempts = 3, initialDelay = Duration.ofMillis(100), jitter = Duration.ZERO),
            sleeper,
            clock,
            throttle,
        )
        webClient = WebClient(
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
            NotionJsonFixtures.json,
        )
        client = NotionClient(webClient, retryExecutor, server.url("/").toUri())
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    protected fun enqueueJson(body: String) {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(body))
    }

    protected fun enqueueError(status: Int, retryAfter: String? = null) {
        val response = MockResponse().setResponseCode(status).setBody("sensitive upstream body $NOTION_TEST_TOKEN")
        if (retryAfter != null) response.setHeader("Retry-After", retryAfter)
        server.enqueue(response)
    }

    protected fun takeRequest(): RecordedRequest {
        return checkNotNull(server.takeRequest(2, TimeUnit.SECONDS)) { "Expected a recorded HTTP request" }
    }

    protected fun page(id: String, parentType: String = "workspace", inTrash: Boolean = false): JsonObject {
        val fixture = NotionJsonFixtures.json.parseToJsonElement(NotionJsonFixtures.read("page.json")).jsonObject
        return JsonObject(
            fixture + mapOf(
                "id" to JsonPrimitive(id),
                "parent" to buildJsonObject { put("type", parentType) },
                "in_trash" to JsonPrimitive(inTrash),
            ),
        )
    }

    protected fun block(id: String): JsonObject {
        return buildJsonObject {
            put("id", id)
            put("type", "unsupported")
            put("has_children", false)
        }
    }

    protected fun batch(
        results: List<JsonObject> = emptyList(),
        nextCursor: String? = null,
        hasMore: Boolean = nextCursor != null,
    ): String {
        return buildJsonObject {
            put("results", JsonArray(results))
            put("next_cursor", nextCursor)
            put("has_more", hasMore)
        }.toString()
    }
}
