package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyOpenActionDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyOpenRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyOpenStreamEvent
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyStreamEvent
import com.sprintstart.sprintstartbackend.onboarding.model.exceptions.OnboardingAiException
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BuddyOpeningStreamTest {
    private val ai: OnboardingAiClient = mockk()
    private val stream = BuddyOpeningStream(ai)
    private val request = BuddyOpenRequest(memory = "note", state = "state")

    private val saved = mutableListOf<String>()
    private var afterOpeningRuns = 0
    private val conversation = OpeningConversation(
        fallbackGreeting = "Fallback hello",
        saveOpening = { saved += it },
        afterOpening = { afterOpeningRuns++ },
    )

    private fun aiSends(events: Flow<BuddyOpenStreamEvent>) {
        every { ai.streamBuddyOpen(request) } returns events
    }

    private fun token(text: String?) = BuddyOpenStreamEvent(type = BuddyOpenStreamEvent.TOKEN, content = text)

    private fun done(greeting: String?, action: BuddyOpenActionDto? = null) =
        BuddyOpenStreamEvent(type = BuddyOpenStreamEvent.DONE, greeting = greeting, action = action)

    private fun tokenEvent(text: String) = BuddyStreamEvent(type = BuddyService.TOKEN, content = text)

    private val doneEvent = BuddyStreamEvent(type = BuddyService.DONE)

    @Test
    fun `replays an existing greeting whole, then ends the stream`() = runTest {
        assertThat(stream.replay("Hi again").toList()).containsExactly(tokenEvent("Hi again"), doneEvent)
    }

    @Test
    fun `streams the greeting as it arrives and stores the one the model closed with`() = runTest {
        aiSends(flowOf(token("Wel"), token("come"), done("Welcome")))

        val events = stream.open(request, conversation).toList()

        assertThat(events).containsExactly(tokenEvent("Wel"), tokenEvent("come"), doneEvent)
        assertThat(saved).containsExactly("Welcome")
        assertThat(afterOpeningRuns).isEqualTo(1)
    }

    @Test
    fun `offers the suggested next step after the greeting`() = runTest {
        aiSends(flowOf(token("Hi"), done("Hi", BuddyOpenActionDto(label = "Find a task", question = "What next?"))))

        val events = stream.open(request, conversation).toList()

        assertThat(events).containsExactly(
            tokenEvent("Hi"),
            BuddyStreamEvent(type = BuddyService.OPENING_ACTION, label = "Find a task", question = "What next?"),
            doneEvent,
        )
    }

    @Test
    fun `skips a token without content instead of showing an empty word`() = runTest {
        aiSends(flowOf(token(null), token("Hi"), done("Hi")))

        assertThat(stream.open(request, conversation).toList()).containsExactly(tokenEvent("Hi"), doneEvent)
    }

    @Test
    fun `stores what was shown when the stream ends without a closing event`() = runTest {
        aiSends(flowOf(token("Half a "), token("greeting")))

        stream.open(request, conversation).toList()

        assertThat(saved).containsExactly("Half a greeting")
    }

    @Test
    fun `stores what was shown when the model fails part-way`() = runTest {
        aiSends(
            flow {
                emit(token("Half"))
                throw OnboardingAiException(503, "down", "AI unavailable")
            },
        )

        val events = stream.open(request, conversation).toList()

        assertThat(events).containsExactly(tokenEvent("Half"), doneEvent)
        assertThat(saved).containsExactly("Half")
    }

    @Test
    fun `shows the fallback and stores nothing when no word arrived`() = runTest {
        aiSends(flow { throw OnboardingAiException(503, "down", "AI unavailable") })

        val events = stream.open(request, conversation).toList()

        assertThat(events).containsExactly(tokenEvent("Fallback hello"), doneEvent)
        assertThat(saved).isEmpty()
        assertThat(afterOpeningRuns).isEqualTo(1)
    }

    @Test
    fun `treats a blank closing greeting as nothing and falls back to what was shown`() = runTest {
        aiSends(flowOf(token("Shown words"), done("   ")))

        stream.open(request, conversation).toList()

        assertThat(saved).containsExactly("Shown words")
    }

    @Test
    fun `the visit starts at the last opening, or at the start when there is none`() {
        val messages = listOf("a" to false, "open-1" to true, "b" to false, "open-2" to true, "c" to false)

        assertThat(messages.sinceLastOpening { it.second }.map { it.first }).containsExactly("open-2", "c")
        assertThat(listOf("a" to false, "b" to false).sinceLastOpening { it.second }.map { it.first })
            .containsExactly("a", "b")
    }
}
