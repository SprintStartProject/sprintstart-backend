package com.sprintstart.sprintstartbackend.shared.ai

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AiServiceCallsTest {
    private val ai = AiMockServer()
    private val calls = AiServiceCalls(ai.webClient, ai.applicationConfig)

    @AfterEach
    fun tearDown() = ai.close()

    @Test
    fun `a refused call says what it was doing and keeps the refusal as the cause`() = runTest {
        ai.rejectNext(status = 500, body = "model overloaded")

        val error = assertFailsWith<AiServiceException> {
            calls.post<String, String>("/api/v1/anything", "body", "assemble diagram")
        }

        assertEquals(500, error.statusCode)
        assertEquals("model overloaded", error.body)
        assertEquals("Failed to assemble diagram (HTTP 500): model overloaded", error.message)
        assertCausedByRefusal(error, status = 500, body = "model overloaded")
    }
}
