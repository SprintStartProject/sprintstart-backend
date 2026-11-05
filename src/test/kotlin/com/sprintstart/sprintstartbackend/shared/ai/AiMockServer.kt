package com.sprintstart.sprintstartbackend.shared.ai

import com.sprintstart.sprintstartbackend.shared.config.AiConfig
import com.sprintstart.sprintstartbackend.shared.config.ApplicationConfig
import com.sprintstart.sprintstartbackend.shared.config.BitbucketConfig
import com.sprintstart.sprintstartbackend.shared.config.CryptoConfig
import com.sprintstart.sprintstartbackend.shared.config.GithubConfig
import com.sprintstart.sprintstartbackend.shared.config.UploadConfig
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import com.sprintstart.sprintstartbackend.shared.web.WebClientException
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.net.http.HttpClient
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * A stand-in for the AI service: a real [WebClient] pointed at a local server that can be told to refuse
 * the next request, so a client's error translation is exercised end to end.
 */
class AiMockServer : AutoCloseable {
    private val server = MockWebServer().also { it.start() }

    val webClient = WebClient(HttpClient.newBuilder().build(), Json { ignoreUnknownKeys = true })

    val applicationConfig = ApplicationConfig(
        ai = AiConfig(baseUrl = server.url("/").toString().removeSuffix("/")),
        github = GithubConfig(baseUrl = "https://github.example.com"),
        crypto = CryptoConfig(masterKey = "test-master-key", salt = "test-salt"),
        upload = UploadConfig(directory = "/tmp/uploads", maxFileSizeBytes = 100),
        bitbucket = BitbucketConfig(baseUrl = "https://bitbucket.org"),
    )

    /** Makes the next request fail with [status] and [body]. */
    fun rejectNext(status: Int = 502, body: String = "upstream down") {
        server.enqueue(MockResponse().setResponseCode(status).setBody(body))
    }

    override fun close() = server.shutdown()
}

/** Asserts that [error] kept the refusal it was made from: a [WebClientException] with that status and body. */
fun assertCausedByRefusal(error: Throwable, status: Int = 502, body: String = "upstream down") {
    val cause = error.cause
    assertIs<WebClientException>(cause)
    assertEquals(status, cause.statusCode)
    assertEquals(body, cause.body)
}
