package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyMessageRole
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyAgentMessageDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyAgentRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyAgentResponse
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyOpenActionDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyOpenRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyOpenStreamEvent
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyStreamEvent
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyMessage
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddySession
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingTrack
import com.sprintstart.sprintstartbackend.onboarding.model.exceptions.OnboardingAiException
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddyMessageRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddySessionRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.dto.ProjectDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserDto
import io.mockk.coEvery
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID

class BuddyServiceTest {
    private val buddySessionRepository: BuddySessionRepository = mockk()
    private val buddyMessageRepository: BuddyMessageRepository = mockk()
    private val onboardingAiClient: OnboardingAiClient = mockk()
    private val buddyToolExecutor: BuddyToolExecutor = mockk()
    private val buddyActionService: BuddyActionService = mockk()
    private val userApi: UserApi = mockk()

    // Every hire here is on the engineering track, so these tests describe the buddy every
    // existing hire meets; the vocabulary swap is exercised in TrackServiceTest.
    private val trackService: TrackService = mockk {
        every { forUser(any()) } returns OnboardingTrack(
            key = OnboardingTrack.DEFAULT_KEY,
            label = "Engineering",
            contributionNoun = "change",
            contributionNounPlural = "changes",
            contributionVerbPast = "merged",
        )
    }

    // Folding is somebody else's job now, and these tests assert it is *asked for*, never that it
    // happened. BuddyCompactionServiceTest owns what a fold does.
    private val buddyCompactionService: BuddyCompactionService = mockk(relaxed = true)

    // Unconfined so the fire-and-forget launch runs inline: the tests can then verify the pass was
    // triggered without sleeping, which would make them slow and flaky in equal measure.
    private val service = BuddyService(
        buddySessionRepository,
        buddyMessageRepository,
        onboardingAiClient,
        buddyToolExecutor,
        buddyActionService,
        userApi,
        trackService,
        buddyCompactionService,
        CoroutineScope(Dispatchers.Unconfined),
    )

    private val userId = UUID.randomUUID()
    private val authId = "auth|test-user"

    @BeforeEach
    fun stubActionDefaults() {
        // Default: no action tools, and every tool the AI calls is a read-only one. Tests that
        // exercise an action override these.
        every { buddyActionService.actionSpecs() } returns emptyList()
        every { buddyActionService.isAction(any()) } returns false
        // Retrieval is scoped to the hire's projects, so every turn resolves them. Default: none,
        // which means the AI narrows nothing -- the behaviour before scoping existed.
        every { userApi.getUsersByIds(listOf(userId)) } returns emptyList()
    }

    private fun finalReply(text: String) = BuddyAgentResponse(final = true, text = text)

    @Nested
    inner class GetOrCreateSession {
        @Test
        fun `returns the existing session when one exists`() {
            val session = BuddySession(userId = userId)
            every { buddySessionRepository.findByUserId(userId) } returns session

            val result = service.getOrCreateSession(userId)

            assertThat(result).isEqualTo(session)
            verify(exactly = 0) { buddySessionRepository.save(any()) }
        }

        @Test
        fun `creates a session when none exists`() {
            every { buddySessionRepository.findByUserId(userId) } returns null
            every { buddySessionRepository.save(any()) } answers { firstArg() }

            val result = service.getOrCreateSession(userId)

            assertThat(result.userId).isEqualTo(userId)
        }
    }

    @Nested
    inner class GetMessagesForMe {
        @Test
        fun `returns an empty list when the user has no session yet`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByUserId(userId) } returns null

            val result = service.getMessagesForMe(authId)

            assertThat(result).isEmpty()
        }

        @Test
        fun `returns the session's messages oldest first`() {
            val session = BuddySession(userId = userId)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByUserId(userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns listOf(
                BuddyMessage(session = session, role = BuddyMessageRole.USER, content = "Hi"),
                BuddyMessage(session = session, role = BuddyMessageRole.ASSISTANT, content = "Hello!"),
            )

            val result = service.getMessagesForMe(authId)

            assertThat(result).hasSize(2)
            assertThat(result[0].content).isEqualTo("Hi")
            assertThat(result[1].role).isEqualTo(BuddyMessageRole.ASSISTANT)
        }

        @Test
        fun `throws 404 when the authenticated user does not exist`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.getMessagesForMe(authId)
            }.also { assertThat(it.statusCode.value()).isEqualTo(404) }
        }
    }

    @Nested
    inner class StreamOpenForMe {
        /**
         * The whole point of the change: the greeting reaches the hire in pieces, as it is written,
         * instead of after the model has finished a memory note they never see.
         */
        @Test
        fun `emits the greeting token by token as the AI writes it`() = runTest {
            val session = BuddySession(userId = userId)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByUserId(userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns emptyList()
            every { buddyToolExecutor.stateSnapshot(userId) } returns "state"
            every { onboardingAiClient.streamBuddyOpen(any()) } returns flowOf(
                BuddyOpenStreamEvent(type = "token", content = "Welcome "),
                BuddyOpenStreamEvent(type = "token", content = "back!"),
                BuddyOpenStreamEvent(type = "done", greeting = "Welcome back!", memory = "m"),
            )
            every { buddySessionRepository.save(any()) } answers { firstArg() }
            every { buddyMessageRepository.save(any()) } answers { firstArg() }

            val events = service.streamOpenForMe(authId).toList()

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
            every { buddySessionRepository.findByUserId(userId) } returns session
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

            val events = service.streamOpenForMe(authId).toList()

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
            every { buddySessionRepository.findByUserId(userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns listOf(
                BuddyMessage(session = session, role = BuddyMessageRole.USER, content = "hi"),
            )
            every { buddyToolExecutor.stateSnapshot(userId) } returns "state"
            every { onboardingAiClient.streamBuddyOpen(any()) } returns flow {
                emit(BuddyOpenStreamEvent(type = "token", content = "Welcome ba"))
                throw OnboardingAiException(503, "", "AI went away")
            }
            every { buddyMessageRepository.save(any()) } answers { firstArg() }

            val events = service.streamOpenForMe(authId).toList()

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
            val session = BuddySession(userId = userId, summary = "keep me")
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByUserId(userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns listOf(
                BuddyMessage(session = session, role = BuddyMessageRole.USER, content = "hi"),
            )
            every { buddyToolExecutor.stateSnapshot(userId) } returns "state"
            every { onboardingAiClient.streamBuddyOpen(any()) } throws
                OnboardingAiException(503, "", "AI is down")

            val events = service.streamOpenForMe(authId).toList()

            // A plain welcome, so the page still works and the hire can start talking.
            assertThat(events.single { it.type == "token" }.content).isNotBlank()
            verify(exactly = 0) { buddyMessageRepository.save(any()) }
            verify(exactly = 0) { buddySessionRepository.save(any()) }
        }

        /**
         * ⚠️ The open used to fold the previous visit into the memory note itself, because the AI
         * service returned the greeting and a rewritten note from one model call. It no longer
         * writes either the note or the cursor — [BuddyCompactionService] owns both — so this pins
         * the *absence*, which is the part a future change could quietly undo.
         */
        @Test
        fun `persists the greeting as the visit's opening and touches neither memory nor cursor`() = runTest {
            val session = BuddySession(userId = userId, summary = "the note as it stands")
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByUserId(userId) } returns session
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

            service.streamOpenForMe(authId).toList()

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
            every { buddySessionRepository.findByUserId(userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns emptyList()
            every { buddyToolExecutor.stateSnapshot(userId) } returns "state"
            every { onboardingAiClient.streamBuddyOpen(any()) } returns flowOf(
                BuddyOpenStreamEvent(type = "done", greeting = "Hello!"),
            )
            every { buddyMessageRepository.save(any()) } answers { firstArg() }

            service.streamOpenForMe(authId).toList()

            coVerify { buddyCompactionService.compactIfNeeded(userId) }
        }

        /**
         * The visit ends when the hire speaks: once they have, the next open is genuinely new.
         * Without this the greeting would freeze permanently.
         *
         * ⚠️ Note the last message is an assistant reply, not an opening. "An opening exists" would
         * wrongly replay here; "the last message is an opening" is the rule.
         */
        @Test
        fun `opening again after the hire has spoken generates a fresh greeting`() = runTest {
            val session = BuddySession(userId = userId)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByUserId(userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns listOf(
                BuddyMessage(
                    session = session,
                    role = BuddyMessageRole.ASSISTANT,
                    content = "Hi!",
                    opening = true,
                ),
                BuddyMessage(session = session, role = BuddyMessageRole.USER, content = "where do I start?"),
                BuddyMessage(session = session, role = BuddyMessageRole.ASSISTANT, content = "Here."),
            )
            every { buddyToolExecutor.stateSnapshot(userId) } returns "state"
            every { onboardingAiClient.streamBuddyOpen(any()) } returns flowOf(
                BuddyOpenStreamEvent(type = "token", content = "Welcome back!"),
                BuddyOpenStreamEvent(type = "done", greeting = "Welcome back!"),
            )
            every { buddyMessageRepository.save(any()) } answers { firstArg() }

            val events = service.streamOpenForMe(authId).toList()

            assertThat(events.single { it.type == "token" }.content).isEqualTo("Welcome back!")
        }

        /**
         * ⚠️ The greeting can only be specific about a previous visit if it is *sent* one. The
         * cursor still answers this question — what the note does not yet cover — which is the one
         * job it genuinely has.
         */
        @Test
        fun `sends everything the memory note does not yet cover as recent context`() = runTest {
            val session = BuddySession(userId = userId, summary = "older still", summarizedCount = 1)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByUserId(userId) } returns session
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

            service.streamOpenForMe(authId).toList()

            assertThat(requests.single().memory).isEqualTo("older still")
            assertThat(requests.single().recent.map { it.content }).containsExactly("not folded yet")
        }

        @Test
        fun `throws 404 when the authenticated user does not exist`() = runTest {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.streamOpenForMe(authId)
            }.also { assertThat(it.statusCode.value()).isEqualTo(404) }
        }

        /**
         * A greeting already written has nothing left to wait for, so it arrives whole. Typing it
         * out again would be theatre, and it must not cost a model call either.
         *
         * ⚠️ This is what makes a refresh the same visit rather than a new one. Opening twice with
         * nothing said in between used to generate a second greeting -- and the window sent for
         * folding was the previous *greeting*, which the memory prompt is explicitly told to drop,
         * so each reload paid for a model call to compress something it then discarded.
         */
        @Test
        fun `replays an existing greeting in one piece without calling the model`() = runTest {
            val session = BuddySession(userId = userId, summary = "keep me")
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByUserId(userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns listOf(
                BuddyMessage(
                    session = session,
                    role = BuddyMessageRole.ASSISTANT,
                    content = "Hi again!",
                    opening = true,
                ),
            )

            val events = service.streamOpenForMe(authId).toList()

            assertThat(events.filter { it.type == "token" }.map { it.content })
                .containsExactly("Hi again!")
            verify(exactly = 0) { onboardingAiClient.streamBuddyOpen(any()) }
            verify(exactly = 0) { buddyMessageRepository.save(any()) }
        }
    }

    @Nested
    inner class SendMessageForMe {
        @Test
        fun `persists the user message before calling the AI client`() = runTest {
            val session = BuddySession(userId = userId)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByUserId(userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns emptyList()
            every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()
            val saved = mutableListOf<BuddyMessage>()
            every { buddyMessageRepository.save(capture(saved)) } answers { firstArg() }
            coEvery { onboardingAiClient.buddyAgentTurn(any()) } returns finalReply("Set up like so.")

            service.sendMessageForMe(authId, "How do I get set up?").toList()

            val userMessage = saved.first { it.role == BuddyMessageRole.USER }
            assertThat(userMessage.content).isEqualTo("How do I get set up?")
        }

        @Test
        fun `scopes retrieval to every project the hire is on`() = runTest {
            // A hire onboarding on two projects should find material from both, and from neither of
            // anybody else's. Narrowing to one of theirs would hide their own work; narrowing to
            // none would show them everybody's -- which is what happened before this existed.
            val alpha = UUID.randomUUID()
            val beta = UUID.randomUUID()
            val session = BuddySession(userId = userId)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { userApi.getUsersByIds(listOf(userId)) } returns listOf(
                UserDto(
                    id = userId,
                    username = "hire",
                    firstname = "Sam",
                    lastname = "Hire",
                    avatarUrl = null,
                    profileIcon = null,
                    projects = setOf(
                        ProjectDto(projectId = alpha, name = "Alpha", description = ""),
                        ProjectDto(projectId = beta, name = "Beta", description = ""),
                    ),
                    projectRoles = emptyList(),
                ),
            )
            every { buddySessionRepository.findByUserId(userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns emptyList()
            every { buddyMessageRepository.save(any()) } answers { firstArg() }
            every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()
            val requests = mutableListOf<BuddyAgentRequest>()
            coEvery { onboardingAiClient.buddyAgentTurn(capture(requests)) } returns finalReply("Here.")

            service.sendMessageForMe(authId, "how do we deploy?").toList()

            assertThat(requests.first().projectIds)
                .containsExactlyInAnyOrder(alpha.toString(), beta.toString())
        }

        @Test
        fun `a hire on no project narrows nothing rather than hiding everything`() = runTest {
            // An empty scope means "search it all", which is the honest answer for somebody not on
            // a project yet -- there is nothing narrower that would be true.
            val session = BuddySession(userId = userId)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByUserId(userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns emptyList()
            every { buddyMessageRepository.save(any()) } answers { firstArg() }
            every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()
            val requests = mutableListOf<BuddyAgentRequest>()
            coEvery { onboardingAiClient.buddyAgentTurn(capture(requests)) } returns finalReply("Here.")

            service.sendMessageForMe(authId, "hello?").toList()

            assertThat(requests.first().projectIds).isEmpty()
        }

        @Test
        fun `threads prior messages and the new question as the running conversation`() = runTest {
            val session = BuddySession(userId = userId)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByUserId(userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns listOf(
                BuddyMessage(session = session, role = BuddyMessageRole.USER, content = "Hi"),
                BuddyMessage(session = session, role = BuddyMessageRole.ASSISTANT, content = "Hello!"),
            )
            every { buddyMessageRepository.save(any()) } answers { firstArg() }
            every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()
            val requests = mutableListOf<BuddyAgentRequest>()
            coEvery { onboardingAiClient.buddyAgentTurn(capture(requests)) } returns finalReply("More detail.")

            service.sendMessageForMe(authId, "Can you say more?").toList()

            assertThat(requests.first().messages).containsExactly(
                BuddyAgentMessageDto(role = "user", content = "Hi"),
                BuddyAgentMessageDto(role = "assistant", content = "Hello!"),
                BuddyAgentMessageDto(role = "user", content = "Can you say more?"),
            )
        }

        @Test
        fun `sends the hire's own track vocabulary on every hop, not just the first`() = runTest {
            val session = BuddySession(userId = userId)
            every { trackService.forUser(userId) } returns OnboardingTrack(
                key = "delivery",
                label = "Agile delivery",
                contributionNoun = "ceremony",
                contributionNounPlural = "ceremonies",
                contributionVerbPast = "facilitated",
            )
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByUserId(userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns emptyList()
            every { buddyMessageRepository.save(any()) } answers { firstArg() }
            every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()
            every { buddyActionService.isAction(any()) } returns false
            val requests = mutableListOf<BuddyAgentRequest>()
            coEvery { onboardingAiClient.buddyAgentTurn(capture(requests)) } returnsMany listOf(
                BuddyAgentResponse(
                    final = false,
                    text = "",
                    pendingToolCalls = listOf(BuddyToolCallDto(id = "c0", name = "get_my_metrics")),
                ),
                finalReply("Nice work on that retro."),
            )
            every { buddyToolExecutor.execute(any(), userId) } returns "no metrics"

            service.sendMessageForMe(authId, "how am I doing?").toList()

            // Every hop, unlike the summary: the persona is rebuilt whenever the running
            // conversation carries no system message, so a resumed turn that omitted the
            // vocabulary would silently fall back to the engineering wording mid-conversation.
            assertThat(requests).hasSize(2)
            assertThat(requests.map { it.vocabulary.contributionNounPlural })
                .containsExactly("ceremonies", "ceremonies")
            assertThat(requests.first().vocabulary.contributionVerbPast).isEqualTo("facilitated")
        }

        @Test
        fun `reuses the same session across repeated messages`() = runTest {
            val session = BuddySession(userId = userId)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByUserId(userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns emptyList()
            every { buddyMessageRepository.save(any()) } answers { firstArg() }
            every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()
            coEvery { onboardingAiClient.buddyAgentTurn(any()) } returns finalReply("ok")

            service.sendMessageForMe(authId, "First").toList()
            service.sendMessageForMe(authId, "Second").toList()

            verify(exactly = 0) { buddySessionRepository.save(any()) }
        }

        @Test
        fun `persists the final answer once the agent loop completes`() = runTest {
            val session = BuddySession(userId = userId)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByUserId(userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns emptyList()
            every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()
            val saved = mutableListOf<BuddyMessage>()
            every { buddyMessageRepository.save(capture(saved)) } answers { firstArg() }
            coEvery { onboardingAiClient.buddyAgentTurn(any()) } returns finalReply("No question is too basic.")

            service.sendMessageForMe(authId, "Hi").toList()

            val assistantMessage = saved.first { it.role == BuddyMessageRole.ASSISTANT }
            assertThat(assistantMessage.content).isEqualTo("No question is too basic.")
        }

        @Test
        fun `runs a backend tool the AI asks for and feeds the result back`() = runTest {
            val session = BuddySession(userId = userId)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByUserId(userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns emptyList()
            every { buddyMessageRepository.save(any()) } answers { firstArg() }
            every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()

            val toolCall = BuddyToolCallDto(id = "call_0", name = "get_my_metrics")
            val paused = BuddyAgentResponse(
                final = false,
                messages = listOf(
                    BuddyAgentMessageDto(role = "assistant", content = "", toolCalls = listOf(toolCall)),
                ),
                pendingToolCalls = listOf(toolCall),
            )
            val requests = mutableListOf<BuddyAgentRequest>()
            coEvery { onboardingAiClient.buddyAgentTurn(capture(requests)) } returnsMany listOf(
                paused,
                finalReply("Your PR has waited 52 hours — that's on the reviewer."),
            )
            every { buddyToolExecutor.execute(toolCall, userId) } returns "openContributionCount=1"

            val events = service.sendMessageForMe(authId, "is my PR stuck?").toList()

            // The tool is executed on the caller's behalf...
            coVerify(exactly = 1) { buddyToolExecutor.execute(toolCall, userId) }
            // ...its result is appended to the conversation carried into the resume call...
            assertThat(requests[1].messages).contains(
                BuddyAgentMessageDto(role = "tool", content = "openContributionCount=1", toolCallId = "call_0"),
            )
            // ...the hire sees the tool run, and the final answer streams out in chunks whose
            // concatenation is the whole answer.
            assertThat(events.map { it.type }).contains("tool_use", "token", "done")
            val streamed = events.filter { it.type == "token" }.joinToString("") { it.content ?: "" }
            assertThat(streamed).contains("52 hours")
        }

        @Test
        fun `proposes an action the AI asks for as an event, and never runs it as a tool`() = runTest {
            val session = BuddySession(userId = userId)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByUserId(userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns emptyList()
            every { buddyMessageRepository.save(any()) } answers { firstArg() }
            every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()

            val actionCall = BuddyToolCallDto(id = "call_0", name = "claim_task_zero")
            val paused = BuddyAgentResponse(
                final = false,
                messages = listOf(
                    BuddyAgentMessageDto(role = "assistant", content = "", toolCalls = listOf(actionCall)),
                ),
                pendingToolCalls = listOf(actionCall),
            )
            val requests = mutableListOf<BuddyAgentRequest>()
            coEvery { onboardingAiClient.buddyAgentTurn(capture(requests)) } returnsMany listOf(
                paused,
                finalReply("I can start Task 0 for you — confirm below."),
            )
            every { buddyActionService.isAction("claim_task_zero") } returns true
            every { buddyActionService.propose(actionCall, userId) } returns
                BuddyActionService.ProposeOutcome(
                    toolResult = "Proposed to the hire; awaiting confirmation.",
                    proposal = BuddyActionService.BuddyActionProposal(
                        action = "claim_task_zero",
                        label = "Start Task 0",
                        question = null,
                    ),
                )

            val events = service.sendMessageForMe(authId, "help me start my first task").toList()

            // The proposal is emitted as its own gate-able event, carrying the action + button label...
            val proposal = events.first { it.type == "action_proposal" }
            assertThat(proposal.action).isEqualTo("claim_task_zero")
            assertThat(proposal.label).isEqualTo("Start Task 0")
            // ...the tool result (not a mutation) is threaded back into the resume conversation...
            assertThat(requests[1].messages).contains(
                BuddyAgentMessageDto(
                    role = "tool",
                    content = "Proposed to the hire; awaiting confirmation.",
                    toolCallId = "call_0",
                ),
            )
            // ...and an action tool is never executed as a read tool (that would mutate on a call).
            verify(exactly = 0) { buddyToolExecutor.execute(any(), any()) }
        }

        /**
         * ⚠️ Regression. Every confirm payload a proposal carries has to reach the stream, and
         * `title`/`attesterId` did not — so `request_attestation` arrived at the confirm endpoint
         * with nothing to act on and came back as "I need to know what work to confirm and who to
         * ask", **every single time**. The action could not succeed at all.
         *
         * It hid because that message reads like a precondition the hire failed rather than a wire
         * that drops fields, and because the payload was declared on the event all along.
         */
        @Test
        fun `an attestation proposal carries what to confirm and who to ask`() =
            runTest {
                val session = BuddySession(userId = userId)
                every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
                every { buddySessionRepository.findByUserId(userId) } returns session
                every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns emptyList()
                every { buddyMessageRepository.save(any()) } answers { firstArg() }
                every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()

                val attesterId = UUID.randomUUID()
                val actionCall = BuddyToolCallDto(id = "call_0", name = "request_attestation")
                val paused = BuddyAgentResponse(
                    final = false,
                    messages = listOf(
                        BuddyAgentMessageDto(role = "assistant", content = "", toolCalls = listOf(actionCall)),
                    ),
                    pendingToolCalls = listOf(actionCall),
                )
                coEvery { onboardingAiClient.buddyAgentTurn(any()) } returnsMany listOf(
                    paused,
                    finalReply("I can ask them to confirm it — confirm below."),
                )
                every { buddyActionService.isAction("request_attestation") } returns true
                every { buddyActionService.propose(actionCall, userId) } returns
                    BuddyActionService.ProposeOutcome(
                        toolResult = "Proposed to the hire; awaiting confirmation.",
                        proposal = BuddyActionService.BuddyActionProposal(
                            action = "request_attestation",
                            label = "Ask them to confirm this",
                            question = null,
                            title = "Facilitated the sprint retro",
                            attesterId = attesterId.toString(),
                        ),
                    )

                val events = service.sendMessageForMe(authId, "can Ana confirm my retro?").toList()

                val proposal = events.first { it.type == "action_proposal" }
                assertThat(proposal.title).isEqualTo("Facilitated the sprint retro")
                assertThat(proposal.attesterId).isEqualTo(attesterId.toString())
            }

        @Test
        fun `does not persist an assistant message when the agent turn fails`() = runTest {
            val session = BuddySession(userId = userId)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByUserId(userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns emptyList()
            every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()
            val saved = mutableListOf<BuddyMessage>()
            every { buddyMessageRepository.save(capture(saved)) } answers { firstArg() }
            coEvery { onboardingAiClient.buddyAgentTurn(any()) } throws
                OnboardingAiException(502, "", "AI buddy responded with error: boom")

            assertThrows<OnboardingAiException> {
                service.sendMessageForMe(authId, "Hi").toList()
            }

            assertThat(saved.map { it.role }).containsExactly(BuddyMessageRole.USER)
        }

        @Test
        fun `emits a BuddyStreamEvent done terminator`() = runTest {
            val session = BuddySession(userId = userId)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByUserId(userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns emptyList()
            every { buddyMessageRepository.save(any()) } answers { firstArg() }
            every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()
            coEvery { onboardingAiClient.buddyAgentTurn(any()) } returns finalReply("done")

            val events: List<BuddyStreamEvent> = service.sendMessageForMe(authId, "Hi").toList()

            assertThat(events.last().type).isEqualTo("done")
        }

        @Test
        fun `sends only the window after the summary cursor, with the prior summary standing in`() = runTest {
            val session = BuddySession(userId = userId).apply {
                summary = "Earlier we got the repo building."
                summarizedCount = 2
            }
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByUserId(userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns listOf(
                BuddyMessage(session = session, role = BuddyMessageRole.USER, content = "summarized 1"),
                BuddyMessage(session = session, role = BuddyMessageRole.ASSISTANT, content = "summarized 2"),
                BuddyMessage(session = session, role = BuddyMessageRole.USER, content = "recent question"),
                BuddyMessage(session = session, role = BuddyMessageRole.ASSISTANT, content = "recent answer"),
            )
            every { buddyMessageRepository.save(any()) } answers { firstArg() }
            every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()
            val requests = mutableListOf<BuddyAgentRequest>()
            coEvery { onboardingAiClient.buddyAgentTurn(capture(requests)) } returns finalReply("More detail.")

            service.sendMessageForMe(authId, "Can you say more?").toList()

            // The summarized prefix stays out of the prompt; the summary stands in for it...
            assertThat(requests.first().messages.map { it.content }).containsExactly(
                "recent question",
                "recent answer",
                "Can you say more?",
            )
            assertThat(requests.first().priorSummary).isEqualTo("Earlier we got the repo building.")
        }

        /**
         * ⚠️ **A turn folds nothing, however far over the window it is.**
         *
         * The request no longer carries a field that could ask for one, so what is left to pin is
         * the consequence: an over-long window is sent as it stands and the session is untouched.
         * That is the honest cost of keeping the fold off the answering path — a fold performed
         * during the turn happens *before* the reply is composed, and since the cursor advances by
         * exactly what it folds, the window would sit at the limit forever once it first filled,
         * making it an extra serialized model call on every turn past ~10 exchanges.
         */
        @Test
        fun `sends the whole over-long window and folds nothing`() = runTest {
            val session = BuddySession(userId = userId)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByUserId(userId) } returns session
            // 25 persisted messages + the new one: 6 over the window of 20.
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns
                (1..25).map {
                    BuddyMessage(
                        session = session,
                        role = if (it % 2 == 1) BuddyMessageRole.USER else BuddyMessageRole.ASSISTANT,
                        content = "m$it",
                    )
                }
            every { buddyMessageRepository.save(any()) } answers { firstArg() }
            every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()
            val requests = mutableListOf<BuddyAgentRequest>()
            coEvery { onboardingAiClient.buddyAgentTurn(capture(requests)) } returns
                finalReply("Picking up where we were.")

            service.sendMessageForMe(authId, "m26").toList()

            // The over-long window goes to the AI as it stands, rather than being trimmed by a fold
            // performed on the way.
            assertThat(requests.first().messages).hasSize(26)
            // A turn writes nothing to the session, so a fold that has not happened yet cannot
            // half-happen here either.
            assertThat(session.summarizedCount).isEqualTo(0)
            verify(exactly = 0) { buddySessionRepository.save(any()) }
        }

        /**
         * The whole point: the fold is asked for *after* the reply is persisted, so the hire is
         * reading it rather than waiting on it.
         */
        @Test
        fun `asks for a fold once the reply has been persisted`() = runTest {
            val session = BuddySession(userId = userId)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByUserId(userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns emptyList()
            every { buddyMessageRepository.save(any()) } answers { firstArg() }
            every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()
            coEvery { onboardingAiClient.buddyAgentTurn(any()) } returns finalReply("Here you go.")

            service.sendMessageForMe(authId, "Hi").toList()

            coVerify { buddyCompactionService.compactIfNeeded(userId) }
        }

        /**
         * ⚠️ A stream that dies part-way must not fold: the reply was never persisted, so folding
         * would advance the note past a turn the transcript does not contain.
         */
        @Test
        fun `asks for no fold when the agent turn fails`() = runTest {
            val session = BuddySession(userId = userId)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByUserId(userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns emptyList()
            every { buddyMessageRepository.save(any()) } answers { firstArg() }
            every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()
            coEvery { onboardingAiClient.buddyAgentTurn(any()) } throws
                OnboardingAiException(500, "boom", "AI down")

            assertThrows<OnboardingAiException> { service.sendMessageForMe(authId, "Hi").toList() }

            coVerify(exactly = 0) { buddyCompactionService.compactIfNeeded(any()) }
        }

        @Test
        fun `sends the prior summary on the first hop only, never re-sent on a resume`() = runTest {
            val session = BuddySession(userId = userId).apply { summary = "Earlier notes." }
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByUserId(userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns emptyList()
            every { buddyMessageRepository.save(any()) } answers { firstArg() }
            every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()
            val toolCall = BuddyToolCallDto(id = "call_0", name = "get_my_metrics")
            val requests = mutableListOf<BuddyAgentRequest>()
            coEvery { onboardingAiClient.buddyAgentTurn(capture(requests)) } returnsMany listOf(
                BuddyAgentResponse(
                    final = false,
                    messages = listOf(
                        BuddyAgentMessageDto(role = "assistant", content = "", toolCalls = listOf(toolCall)),
                    ),
                    pendingToolCalls = listOf(toolCall),
                ),
                finalReply("Your PR is waiting on a review."),
            )
            every { buddyToolExecutor.execute(toolCall, userId) } returns "openContributionCount=1"

            service.sendMessageForMe(authId, "m26").toList()

            assertThat(requests[0].priorSummary).isEqualTo("Earlier notes.")
            // The resume carries none: the summary is already folded into the running conversation
            // the AI returned, and re-sending would double-fold it.
            assertThat(requests[1].priorSummary).isNull()
        }
    }
}
