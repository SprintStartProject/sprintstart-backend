package com.sprintstart.sprintstartbackend.insights

import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqClassifyRequest
import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqGroupingRequest
import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqMergeGroupsRequest
import com.sprintstart.sprintstartbackend.insights.model.exceptions.InsightsAiException
import com.sprintstart.sprintstartbackend.shared.ai.AiMockServer
import com.sprintstart.sprintstartbackend.shared.ai.assertCausedByRefusal
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InsightsAiClientTest {
    private val ai = AiMockServer()
    private val client = InsightsAiClient(ai.webClient, ai.applicationConfig)

    @AfterEach
    fun tearDown() = ai.close()

    @Test
    fun `a refused grouping request keeps the refusal as the cause`() = runTest {
        ai.rejectNext()

        val error = assertFailsWith<InsightsAiException> {
            client.groupFaqQuestions(AiFaqGroupingRequest(projectId = "p1", questions = emptyList()))
        }

        assertEquals("Failed to group FAQ questions (HTTP 502): upstream down", error.message)
        assertCausedByRefusal(error)
    }

    @Test
    fun `a refused classification keeps the refusal as the cause`() = runTest {
        ai.rejectNext()

        val error = assertFailsWith<InsightsAiException> {
            client.classifyFaqQuestion(AiFaqClassifyRequest(projectId = "p1", question = "How do we deploy?"))
        }

        assertEquals("Failed to classify FAQ question (HTTP 502): upstream down", error.message)
        assertCausedByRefusal(error)
    }

    @Test
    fun `a refused merge request keeps the refusal as the cause`() = runTest {
        ai.rejectNext()

        val error = assertFailsWith<InsightsAiException> {
            client.mergeFaqGroups(AiFaqMergeGroupsRequest(groups = emptyList(), targetMax = 10))
        }

        assertEquals("Failed to merge FAQ groups (HTTP 502): upstream down", error.message)
        assertCausedByRefusal(error)
    }
}
