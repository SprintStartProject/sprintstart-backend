package com.sprintstart.sprintstartbackend.user.external

import com.sprintstart.sprintstartbackend.shared.ai.AiMockServer
import com.sprintstart.sprintstartbackend.shared.ai.assertCausedByRefusal
import com.sprintstart.sprintstartbackend.user.model.exceptions.ProjectIndustryAiException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProjectIndustryAiClientTest {
    private val ai = AiMockServer()
    private val client = ProjectIndustryAiClient(ai.webClient, ai.applicationConfig)

    @AfterEach
    fun tearDown() = ai.close()

    @Test
    fun `a refused evaluation keeps the status, the body and the refusal as the cause`() = runTest {
        ai.rejectNext(status = 504, body = "timed out")

        val error = assertFailsWith<ProjectIndustryAiException> { client.evaluateIndustry(UUID.randomUUID()) }

        assertEquals(504, error.statusCode)
        assertEquals("timed out", error.body)
        assertEquals("Failed to evaluate project industry (HTTP 504): timed out", error.message)
        assertCausedByRefusal(error, status = 504, body = "timed out")
    }
}
