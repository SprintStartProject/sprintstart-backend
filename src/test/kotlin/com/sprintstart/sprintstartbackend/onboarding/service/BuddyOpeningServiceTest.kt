package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyMessageRole
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyOpenActionDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyOpenRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyOpenStreamEvent
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyStreamEvent
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyMessage
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddySession
import com.sprintstart.sprintstartbackend.onboarding.model.exceptions.OnboardingAiException
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddyMessageRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddySessionRepository
import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import com.sprintstart.sprintstartbackend.shared.ids.BuddySessionId
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.service.SessionActivityService
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID
import kotlin.collections.emptyList

/** Opening a visit to a hire's buddy conversation ([BuddyOpeningService]). */
class BuddyOpeningServiceTest {
    private val buddySessionRepository: BuddySessionRepository = mockk()
    private val buddyMessageRepository: BuddyMessageRepository = mockk()
    private val onboardingAiClient: OnboardingAiClient = mockk()
    private val buddyToolExecutor: BuddyToolExecutor = mockk()
    private val sessionActivityService: SessionActivityService = mockk()
    private val userApi: UserApi = mockk()
    private val buddyCompactionService: BuddyCompactionService = mockk(relaxed = true)

    // Unconfined so the fire-and-forget fold runs inline and can be verified without sleeping.
    private val service = BuddyOpeningService(
        buddySessionRepository,
        buddyMessageRepository,
        userApi,
        sessionActivityService,
        buddyToolExecutor,
        BuddyOpeningStream(onboardingAiClient),
        BuddyCompactionScheduler(buddyCompactionService, CoroutineScope(Dispatchers.Unconfined)),
    )

    private val userId = UUID.randomUUID()
    private val authId = "auth|test-user"

    /**
     * The whole point of the change: the greeting reaches the hire in pieces, as it is written,
     * instead of after the model has finished a memory note they never see.
     */
    @Test
    fun `emits the greeting token by token as the AI writes it`() = runTest {
        val session = BuddySession(userId = userId)

        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every {
            buddySessionRepository.findByIdAndUserId(session.id, userId)
        } returns session
        every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns emptyList()
        every { buddyToolExecutor.stateSnapshot(userId) } returns "state"
        every { onboardingAiClient.streamBuddyOpen(any()) } returns flowOf(
            BuddyOpenStreamEvent(type = "token", content = "Welcome "),
            BuddyOpenStreamEvent(type = "token", content = "back!"),
            BuddyOpenStreamEvent(type = "done", greeting = "Welcome back!", memory = "m"),
        )
        every { buddySessionRepository.save(any()) } answers { firstArg() }
        every { buddyMessageRepository.save(any()) } answers { firstArg() }
        every {
            sessionActivityService.recordActivityAndReturnLongAbsence(userId)
        } returns false
        every { buddySessionRepository.countByUserId(userId) } returns 1L

        val events = service.streamOpenForMe(AuthId(authId), BuddySessionId(session.id)).toList()

        assertThat(events.filter { it.type == "token" }.map { it.content })
            .containsExactly("Welcome ", "back!")
        assertThat(events.last().type).isEqualTo("done")
    }

    /**
     * Deliberately not `action_proposal`. That type means the buddy is offering to *do*
     * something and is gated on the hire confirming; this only fills the composer.
     */
    @Test
    fun `carries the suggested next step as its own event, not an action proposal`() = runTest {
        val session = BuddySession(userId = userId)

        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every {
            buddySessionRepository.findByIdAndUserId(session.id, userId)
        } returns session
        every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns emptyList()
        every { buddyToolExecutor.stateSnapshot(userId) } returns "state"
        every { onboardingAiClient.streamBuddyOpen(any()) } returns flowOf(
            BuddyOpenStreamEvent(type = "token", content = "Hi!"),
            BuddyOpenStreamEvent(
                type = "done",
                greeting = "Hi!",
                memory = "m",
                action = BuddyOpenActionDto(label = "Find me a task", question = "What next?"),
            ),
        )
        every { buddySessionRepository.save(any()) } answers { firstArg() }
        every { buddyMessageRepository.save(any()) } answers { firstArg() }
        every {
            sessionActivityService.recordActivityAndReturnLongAbsence(userId)
        } returns false
        every { buddySessionRepository.countByUserId(userId) } returns 1L

        val events = service.streamOpenForMe(AuthId(authId), BuddySessionId(session.id)).toList()

        val action = events.single { it.type == "opening_action" }
        assertThat(action.label).isEqualTo("Find me a task")
        assertThat(action.question).isEqualTo("What next?")
        assertThat(events.none { it.type == "action_proposal" }).isTrue()
    }

    /**
     * A stream that breaks part-way has already put words on the hire's screen. Discarding them
     * would mean a reload showed a *different* greeting than the one they just read, so what
     * arrived is kept -- while memory and cursor stay untouched, since nothing was folded.
     */
    @Test
    fun `keeps what the hire already read when the stream breaks part-way`() = runTest {
        val session = BuddySession(userId = userId, summary = "keep me", summarizedCount = 0)

        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every {
            buddySessionRepository.findByIdAndUserId(session.id, userId)
        } returns session
        every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns listOf(
            BuddyMessage(session = session, role = BuddyMessageRole.USER, content = "hi"),
        )
        every { buddyToolExecutor.stateSnapshot(userId) } returns "state"
        every { onboardingAiClient.streamBuddyOpen(any()) } returns flow {
            emit(BuddyOpenStreamEvent(type = "token", content = "Welcome ba"))
            throw OnboardingAiException(503, "", "AI went away")
        }
        every { buddyMessageRepository.save(any()) } answers { firstArg() }
        every {
            sessionActivityService.recordActivityAndReturnLongAbsence(userId)
        } returns false
        every { buddySessionRepository.countByUserId(userId) } returns 1L

        val events = service.streamOpenForMe(AuthId(authId), BuddySessionId(session.id)).toList()

        assertThat(events.filter { it.type == "token" }.map { it.content })
            .containsExactly("Welcome ba")
        assertThat(events.last().type).isEqualTo("done")
        verify {
            buddyMessageRepository.save(
                match { it.role == BuddyMessageRole.ASSISTANT && it.content == "Welcome ba" },
            )
        }
        // Nothing was folded, so nothing the buddy has not yet remembered is dropped.
        assertThat(session.summary).isEqualTo("keep me")
        assertThat(session.summarizedCount).isEqualTo(0)
        verify(exactly = 0) { buddySessionRepository.save(any()) }
    }

    /**
     * The opposite case, and it must not persist: an outage before the first token would
     * otherwise make the fallback this visit's permanent greeting, since re-opening replays
     * whatever greeting is already there.
     */
    @Test
    fun `persists nothing when the stream breaks before a single token`() = runTest {
        val session = BuddySession(userId = userId)

        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every {
            buddySessionRepository.findByIdAndUserId(session.id, userId)
        } returns session
        every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns listOf(
            BuddyMessage(session = session, role = BuddyMessageRole.USER, content = "hi"),
        )
        every { buddyToolExecutor.stateSnapshot(userId) } returns "state"
        every { onboardingAiClient.streamBuddyOpen(any()) } throws
            OnboardingAiException(503, "", "AI is down")
        every {
            sessionActivityService.recordActivityAndReturnLongAbsence(userId)
        } returns false
        every { buddySessionRepository.countByUserId(userId) } returns 1L

        val events = service.streamOpenForMe(AuthId(authId), BuddySessionId(session.id)).toList()

        // A plain welcome, so the page still works and the hire can start talking.
        assertThat(events.single { it.type == "token" }.content).isNotBlank()
        verify(exactly = 0) { buddyMessageRepository.save(any()) }
        verify(exactly = 0) { buddySessionRepository.save(any()) }
    }

    /**
     * The open writes neither the memory note nor the cursor — [BuddyCompactionService] owns
     * both — so this pins the *absence*, which is the part a future change could quietly undo.
     */
    @Test
    fun `persists the greeting as the conversation's opening and touches neither memory nor cursor`() = runTest {
        val session = BuddySession(userId = userId, summary = "the note as it stands")

        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every {
            buddySessionRepository.findByIdAndUserId(session.id, userId)
        } returns session
        every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns listOf(
            BuddyMessage(session = session, role = BuddyMessageRole.USER, content = "how do I build?"),
            BuddyMessage(session = session, role = BuddyMessageRole.ASSISTANT, content = "use ./gradlew"),
        )
        every { buddyToolExecutor.stateSnapshot(userId) } returns "2 closed PRs"
        every { onboardingAiClient.streamBuddyOpen(any()) } returns flowOf(
            BuddyOpenStreamEvent(type = "token", content = "Welcome back, Sam!"),
            BuddyOpenStreamEvent(type = "done", greeting = "Welcome back, Sam!"),
        )
        every { buddyMessageRepository.save(any()) } answers { firstArg() }
        every {
            sessionActivityService.recordActivityAndReturnLongAbsence(userId)
        } returns false
        every { buddySessionRepository.countByUserId(userId) } returns 1L

        service.streamOpenForMe(AuthId(authId), BuddySessionId(session.id)).toList()

        assertThat(session.summary).isEqualTo("the note as it stands")
        assertThat(session.summarizedCount).isEqualTo(0)
        verify(exactly = 0) { buddySessionRepository.save(any()) }
        verify {
            buddyMessageRepository.save(
                match { it.content == "Welcome back, Sam!" && it.opening },
            )
        }
    }

    /**
     * Reading the greeting is when nobody is waiting, so it is when the backlog gets folded.
     */
    @Test
    fun `asks for a fold once the greeting has been persisted`() = runTest {
        val session = BuddySession(userId = userId)

        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every {
            buddySessionRepository.findByIdAndUserId(session.id, userId)
        } returns session
        every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns emptyList()
        every { buddyToolExecutor.stateSnapshot(userId) } returns "state"
        every { onboardingAiClient.streamBuddyOpen(any()) } returns flowOf(
            BuddyOpenStreamEvent(type = "done", greeting = "Hello!"),
        )
        every { buddyMessageRepository.save(any()) } answers { firstArg() }
        every {
            sessionActivityService.recordActivityAndReturnLongAbsence(userId)
        } returns false
        every { buddySessionRepository.countByUserId(userId) } returns 1L

        service.streamOpenForMe(AuthId(authId), BuddySessionId(session.id)).toList()

        coVerify { buddyCompactionService.compactIfNeeded(userId, session.id) }
    }

    /**
     * The greeting can only be specific about a previous visit if it is *sent* one. The
     * cursor still answers this question — what the note does not yet cover — which is the one
     * job it genuinely has.
     */
    @Test
    fun `sends everything the memory note does not yet cover as recent context`() = runTest {
        val session = BuddySession(userId = userId, summary = "older still", summarizedCount = 1)

        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every {
            buddySessionRepository.findByIdAndUserId(session.id, userId)
        } returns session
        every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns listOf(
            BuddyMessage(session = session, role = BuddyMessageRole.USER, content = "already folded"),
            BuddyMessage(session = session, role = BuddyMessageRole.USER, content = "not folded yet"),
        )
        every { buddyToolExecutor.stateSnapshot(userId) } returns "state"
        val requests = mutableListOf<BuddyOpenRequest>()
        every { onboardingAiClient.streamBuddyOpen(capture(requests)) } returns flowOf(
            BuddyOpenStreamEvent(type = "done", greeting = "Hello!"),
        )
        every { buddyMessageRepository.save(any()) } answers { firstArg() }
        every {
            sessionActivityService.recordActivityAndReturnLongAbsence(userId)
        } returns false
        every {
            buddySessionRepository.countByUserId(userId)
        } returns 1L

        service.streamOpenForMe(AuthId(authId), BuddySessionId(session.id)).toList()

        assertThat(requests.single().memory).isEqualTo("older still")
        assertThat(requests.single().recent.map { it.content }).containsExactly("not folded yet")
    }

    @Test
    fun `throws 404 when the authenticated user does not exist`() = runTest {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.empty()

        assertThrows<ResponseStatusException> {
            service.streamOpenForMe(AuthId(authId), BuddySessionId(UUID.randomUUID()))
        }.also { assertThat(it.statusCode.value()).isEqualTo(404) }
    }

    /**
     * A greeting already written has nothing left to wait for, so it arrives whole. Typing it
     * out again would be theatre, and it must not cost a model call either.
     *
     * This is what makes a refresh the same visit rather than a new one. Opening twice with
     * nothing said in between must not generate a second greeting: the window sent for folding
     * would be the previous *greeting*, which the memory prompt is explicitly told to drop, so
     * each reload would pay for a model call to compress something it then discards.
     */
    @Test
    fun `replays an existing greeting in one piece without calling the model`() = runTest {
        val session = BuddySession(userId = userId, summary = "keep me")
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every {
            buddySessionRepository.findByIdAndUserId(session.id, userId)
        } returns session
        every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns listOf(
            BuddyMessage(
                session = session,
                role = BuddyMessageRole.ASSISTANT,
                content = "Hi again!",
                opening = true,
            ),
        )
        every { buddySessionRepository.countByUserId(userId) } returns 2L
        every {
            sessionActivityService.recordActivityAndReturnLongAbsence(userId)
        } returns false

        val events = service.streamOpenForMe(AuthId(authId), BuddySessionId(session.id)).toList()

        assertThat(events.filter { it.type == "token" }.map { it.content })
            .containsExactly("Hi again!")
        verify(exactly = 0) { onboardingAiClient.streamBuddyOpen(any()) }
        verify(exactly = 0) { buddyMessageRepository.save(any()) }
    }

    @Test
    fun `throws 400 when no session is supplied`() = runTest {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)

        assertThrows<ResponseStatusException> {
            service.streamOpenForMe(AuthId(authId), null).toList()
        }.also {
            assertThat(it.statusCode.value()).isEqualTo(400)
        }
    }

    @Test
    fun `does not greet when opening a new conversation after an existing one`() = runTest {
        val session = BuddySession(userId = userId)

        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every {
            buddySessionRepository.findByIdAndUserId(session.id, userId)
        } returns session
        every {
            buddySessionRepository.countByUserId(userId)
        } returns 2L
        every {
            sessionActivityService.recordActivityAndReturnLongAbsence(userId)
        } returns false
        every {
            buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id)
        } returns emptyList()

        val events = service.streamOpenForMe(AuthId(authId), BuddySessionId(session.id)).toList()

        assertThat(events.filter { it.type == "TOKEN" }).isEmpty()
        verify(exactly = 0) {
            onboardingAiClient.streamBuddyOpen(any())
        }
    }

    @Test
    fun `generates a new greeting after a long absence`() = runTest {
        val session = BuddySession(userId = userId)

        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every {
            buddySessionRepository.findByIdAndUserId(session.id, userId)
        } returns session
        every {
            buddySessionRepository.countByUserId(userId)
        } returns 2L
        every {
            sessionActivityService.recordActivityAndReturnLongAbsence(userId)
        } returns true
        every {
            buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id)
        } returns listOf(
            BuddyMessage(
                session = session,
                role = BuddyMessageRole.ASSISTANT,
                content = "Hi again!",
                opening = true,
            ),
        )

        every { buddyToolExecutor.stateSnapshot(userId) } returns "state"

        every {
            onboardingAiClient.streamBuddyOpen(any())
        } returns flowOf(
            BuddyOpenStreamEvent(type = "token", content = "Welcome back!"),
            BuddyOpenStreamEvent(type = "done", greeting = "Welcome back!"),
        )

        every { buddyMessageRepository.save(any()) } answers { firstArg() }

        val events = service.streamOpenForMe(AuthId(authId), BuddySessionId(session.id)).toList()

        assertThat(events.single { it.type == "token" }.content)
            .isEqualTo("Welcome back!")

        verify {
            onboardingAiClient.streamBuddyOpen(any())
        }
    }

    @Test
    fun `does not greet again when an existing conversation has messages`() = runTest {
        val sessionId = UUID.randomUUID()

        val session = BuddySession(
            id = sessionId,
            userId = userId,
        )

        val messages = listOf(
            BuddyMessage(
                id = UUID.randomUUID(),
                session = session,
                role = BuddyMessageRole.USER,
                content = "Hello",
                opening = false,
            ),
        )

        every {
            userApi.getUserIdByAuthId(any())
        } returns Optional.of(userId)

        every {
            buddySessionRepository.findByIdAndUserId(sessionId, userId)
        } returns session

        every {
            buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId)
        } returns messages

        every {
            sessionActivityService.recordActivityAndReturnLongAbsence(userId)
        } returns false

        every {
            buddySessionRepository.countByUserId(userId)
        } returns 2L

        val events = service
            .streamOpenForMe(AuthId(authId), BuddySessionId(sessionId))
            .toList()

        assertThat(events).containsExactly(
            BuddyStreamEvent(type = "done"),
        )

        verify(exactly = 0) {
            onboardingAiClient.streamBuddyOpen(any())
        }
    }
}
