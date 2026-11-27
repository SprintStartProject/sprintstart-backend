package com.sprintstart.sprintstartbackend.offboarding.external

import com.sprintstart.sprintstartbackend.offboarding.model.ai.AiInterviewFile
import com.sprintstart.sprintstartbackend.offboarding.model.ai.AiInterviewRequest
import com.sprintstart.sprintstartbackend.offboarding.model.exception.InterviewAiException
import com.sprintstart.sprintstartbackend.shared.ai.AiMockServer
import com.sprintstart.sprintstartbackend.shared.ai.assertCausedByRefusal
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class InterviewAiClientTest {
    private val ai = AiMockServer()
    private val client = InterviewAiClient(ai.webClient, ai.applicationConfig)
    private val request = AiInterviewRequest("p1", listOf(AiInterviewFile("github:acme/app", "a.kt", "ORPHANED", 0.9)))

    @AfterEach
    fun tearDown() = ai.close()

    @Test
    fun `sends the files and reads the plan`() = runTest {
        ai.respondNext(
            """
            {"questions": [{"component": "github:acme/app/(root)", "text": "Why?", "type": "rationale",
              "triggers": ["orphaned"], "files": ["a.kt"], "priority": 0.9, "coverage": "open"}],
             "answeredByRecords": []}
            """.trimIndent(),
        )

        val plan = client.planInterview(request)

        assertThat(plan.questions.single().text).isEqualTo("Why?")
        assertThat(plan.questions.single().recordAnswer).isNull()
        val sent = ai.takeRequestBody()
        assertThat(sent).contains("\"projectId\":\"p1\"", "\"path\":\"a.kt\"", "\"level\":\"ORPHANED\"")
    }

    @Test
    fun `a refused plan keeps the refusal as the cause`() = runTest {
        ai.rejectNext()

        val error = assertFailsWith<InterviewAiException> { client.planInterview(request) }

        assertThat(error.message).isEqualTo("Failed to plan the interview (HTTP 502): upstream down")
        assertCausedByRefusal(error)
    }
}
