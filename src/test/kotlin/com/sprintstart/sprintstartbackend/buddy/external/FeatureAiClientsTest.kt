package com.sprintstart.sprintstartbackend.buddy.external

import com.sprintstart.sprintstartbackend.assistant.external.AgentAiClient
import com.sprintstart.sprintstartbackend.assistant.external.BuddyAgentRequest
import com.sprintstart.sprintstartbackend.assistant.external.BuddyCompactRequest
import com.sprintstart.sprintstartbackend.assistant.external.BuddyOpenRequest
import com.sprintstart.sprintstartbackend.board.external.DiagramAiClient
import com.sprintstart.sprintstartbackend.path.external.PhaseContentAiClient
import com.sprintstart.sprintstartbackend.path.external.model.AssemblePhaseRequest
import com.sprintstart.sprintstartbackend.shared.ai.AiServiceCalls
import com.sprintstart.sprintstartbackend.shared.ai.AiServiceException
import com.sprintstart.sprintstartbackend.shared.config.AiConfig
import com.sprintstart.sprintstartbackend.shared.config.ApplicationConfig
import com.sprintstart.sprintstartbackend.shared.config.BitbucketConfig
import com.sprintstart.sprintstartbackend.shared.config.CryptoConfig
import com.sprintstart.sprintstartbackend.shared.config.GithubConfig
import com.sprintstart.sprintstartbackend.shared.config.UploadConfig
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import com.sprintstart.sprintstartbackend.starterwork.external.OrientationAiClient
import com.sprintstart.sprintstartbackend.starterwork.external.StarterWorkAiClient
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.http.HttpClient
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The feature AI clients against a fake AI service: endpoint, payload, decoding and error wrapping. */
class FeatureAiClientsTest {
    private val server = MockWebServer().apply { start() }
    private val calls = AiServiceCalls(
        WebClient(HttpClient.newBuilder().build(), Json { ignoreUnknownKeys = true }),
        ApplicationConfig(
            ai = AiConfig(baseUrl = server.url("/").toString().removeSuffix("/")),
            github = GithubConfig(baseUrl = "https://github.example.com"),
            crypto = CryptoConfig(masterKey = "test-master-key", salt = "test-salt"),
            upload = UploadConfig(directory = "/tmp/uploads", maxFileSizeBytes = 100),
            bitbucket = BitbucketConfig(baseUrl = "https://bitbucket.org"),
        ),
    )
    private val projectId = UUID.randomUUID()

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun json(body: String, status: Int = 200) =
        server.enqueue(
            MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body),
        )

    private fun sse(vararg events: String) =
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(events.joinToString("") { "data: $it\n\n" }),
        )

    private fun RecordedRequest.assertPost(path: String): String {
        assertEquals("POST", method)
        assertEquals(path, this.path)
        return body.readUtf8()
    }

    @Test
    fun `a diagram is requested for the subject and projects and decoded`() = runTest {
        json("""{"status":"skipped","chunks_retrieved":3}""")

        val outcome = DiagramAiClient(calls).assembleDiagram("deploy flow", listOf(projectId), "fp-1")

        val body = server.takeRequest().assertPost("/api/v1/onboarding/diagram")
        assertTrue("deploy flow" in body && projectId.toString() in body && "fp-1" in body)
        assertEquals("skipped", outcome.status)
        assertEquals(3, outcome.chunksRetrieved)
    }

    @Test
    fun `a failed call becomes an AiServiceException with the upstream status and body`() = runTest {
        json("""{"detail":"model down"}""", status = 503)

        val error = assertThrows<AiServiceException> {
            DiagramAiClient(calls).assembleDiagram("deploy flow", listOf(projectId))
        }

        assertEquals(503, error.statusCode)
        assertTrue("model down" in error.body)
        assertTrue(error.message.orEmpty().startsWith("Failed to assemble diagram (HTTP 503)"))
    }

    @Test
    fun `an orientation is requested for the task and decoded, blocking or streamed`() = runTest {
        json("""{"status":"unchanged"}""")
        sse("""{"type":"stage","label":"retrieving"}""", """{"type":"done"}""")
        val client = OrientationAiClient(calls)

        val outcome = client.assembleOrientation("Fix login", listOf(projectId), "body", listOf("bug"), listOf("a.kt"))
        val events = client.streamOrientation("Fix login", listOf(projectId)).toList()

        val body = server.takeRequest().assertPost("/api/v1/onboarding/orientation")
        assertTrue("Fix login" in body && "bug" in body && "a.kt" in body)
        assertEquals("unchanged", outcome.status)
        server.takeRequest().assertPost("/api/v1/onboarding/orientation/stream")
        assertEquals(listOf("stage", "done"), events.map { it.type })
    }

    @Test
    fun `starter work is mined with the pool and competency keys, blocking or streamed`() = runTest {
        json("""{"status":"ok","candidates_considered":2}""")
        sse("""{"type":"item"}""")
        val client = StarterWorkAiClient(calls)

        val outcome = client.proposeStarterWork(listOf(projectId), listOf("issue-7"), listOf("kotlin"))
        val events = client.streamStarterWork(listOf(projectId)).toList()

        val body = server.takeRequest().assertPost("/api/v1/onboarding/starter-work/mine")
        assertTrue("issue-7" in body && "kotlin" in body)
        assertEquals(2, outcome.candidatesConsidered)
        server.takeRequest().assertPost("/api/v1/onboarding/starter-work/mine/stream")
        assertEquals(listOf("item"), events.map { it.type })
    }

    @Test
    fun `a malformed chunk in a progress stream is skipped, not fatal`() = runTest {
        sse("""{"type":"stage"}""", "not json", """{"type":"done"}""")

        val events = PhaseContentAiClient(calls)
            .streamPhase(
                AssemblePhaseRequest(
                    phaseTitle = "Setup",
                    phaseDescription = "d",
                    phasePrompt = "p",
                    projectId = projectId.toString(),
                ),
            ).toList()

        server.takeRequest().assertPost("/api/v1/onboarding/phase/stream")
        assertEquals(listOf("stage", "done"), events.map { it.type })
    }

    @Test
    fun `the agent endpoints stream turns and openings and compact memory`() = runTest {
        sse("""{"type":"token","content":"Hi"}""", """{"type":"result","final":true,"text":"Hi"}""")
        sse("""{"type":"done","greeting":"Welcome"}""")
        json("""{"memory":"likes Kotlin"}""")
        val client = AgentAiClient(calls)

        val turn = client.buddyAgentTurnStream(BuddyAgentRequest(messages = emptyList())).toList()
        val opening = client.streamBuddyOpen(BuddyOpenRequest()).toList()
        val compacted = client.compactBuddyMemory(BuddyCompactRequest())

        server.takeRequest().assertPost("/api/v1/onboarding/buddy/agent/stream")
        server.takeRequest().assertPost("/api/v1/onboarding/buddy/open/stream")
        server.takeRequest().assertPost("/api/v1/onboarding/buddy/compact")
        assertEquals(listOf("token", "result"), turn.map { it.type })
        assertEquals("Welcome", opening.single().greeting)
        assertEquals("likes Kotlin", compacted.memory)
    }

    @Test
    fun `a rejected agent turn becomes an AiServiceException`() = runTest {
        json("""{"detail":"overloaded"}""", status = 503)

        val error = assertThrows<AiServiceException> {
            AgentAiClient(calls).buddyAgentTurnStream(BuddyAgentRequest(messages = emptyList())).toList()
        }

        assertEquals(503, error.statusCode)
        assertTrue(error.message.orEmpty().startsWith("Failed to run buddy agent turn (HTTP 503)"))
    }
}
