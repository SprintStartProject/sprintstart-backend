package com.sprintstart.sprintstartbackend.buddy.client

import com.sprintstart.sprintstartbackend.buddy.model.exceptions.AiResponseException
import com.sprintstart.sprintstartbackend.buddy.model.request.buddy.AiGenerateSessionTitleRequest
import com.sprintstart.sprintstartbackend.shared.ai.AiMockServer
import com.sprintstart.sprintstartbackend.shared.ai.assertCausedByRefusal
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BuddyAiClientTest {
    private val ai = AiMockServer()
    private val client = BuddyAiClient(ai.webClient, ai.applicationConfig)

    @AfterEach
    fun tearDown() = ai.close()

    @Test
    fun `a refused title request keeps the refusal as the cause`() = runTest {
        ai.rejectNext()

        val error = assertFailsWith<AiResponseException> {
            client.getSessionTitle(AiGenerateSessionTitleRequest("How do we deploy?"))
        }

        assertEquals("Failed to generate session title (HTTP 502): upstream down", error.message)
        assertCausedByRefusal(error)
    }
}
