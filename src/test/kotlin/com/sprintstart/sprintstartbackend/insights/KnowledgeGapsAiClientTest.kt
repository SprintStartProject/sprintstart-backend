package com.sprintstart.sprintstartbackend.insights

import com.sprintstart.sprintstartbackend.insights.model.ai.AiKnowledgeGapsRequest
import com.sprintstart.sprintstartbackend.insights.model.exceptions.KnowledgeGapsAiException
import com.sprintstart.sprintstartbackend.shared.ai.AiMockServer
import com.sprintstart.sprintstartbackend.shared.ai.assertCausedByRefusal
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KnowledgeGapsAiClientTest {
    private val ai = AiMockServer()
    private val client = KnowledgeGapsAiClient(ai.webClient, ai.applicationConfig)

    @AfterEach
    fun tearDown() = ai.close()

    @Test
    fun `a refused detection request keeps the refusal as the cause`() = runTest {
        ai.rejectNext()

        val error = assertFailsWith<KnowledgeGapsAiException> {
            client.detectKnowledgeGaps(AiKnowledgeGapsRequest(projectId = "p1"))
        }

        assertEquals("Failed to detect knowledge gaps (HTTP 502): upstream down", error.message)
        assertCausedByRefusal(error)
    }
}
