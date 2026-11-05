package com.sprintstart.sprintstartbackend.path.external

import com.sprintstart.sprintstartbackend.path.external.model.GradeAnswerItem
import com.sprintstart.sprintstartbackend.shared.ai.AiMockServer
import com.sprintstart.sprintstartbackend.shared.ai.AiServiceException
import com.sprintstart.sprintstartbackend.shared.ai.assertCausedByRefusal
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PhaseCheckAiClientTest {
    private val ai = AiMockServer()
    private val client = PhaseCheckAiClient(ai.webClient, ai.applicationConfig)

    @AfterEach
    fun tearDown() = ai.close()

    @Test
    fun `a refused grading request keeps the status, the body and the refusal as the cause`() = runTest {
        ai.rejectNext(status = 503, body = "busy")

        val error = assertFailsWith<AiServiceException> {
            client.gradeAnswers(
                listOf(GradeAnswerItem(id = "q1", question = "Q", referenceAnswer = "A", userAnswer = "B")),
            )
        }

        assertEquals(503, error.statusCode)
        assertEquals("busy", error.body)
        assertEquals("Failed to grade short-text answers (HTTP 503): busy", error.message)
        assertCausedByRefusal(error, status = 503, body = "busy")
    }
}
