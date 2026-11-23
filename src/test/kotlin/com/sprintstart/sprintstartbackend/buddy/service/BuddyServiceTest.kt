package com.sprintstart.sprintstartbackend.buddy.service

import com.sprintstart.sprintstartbackend.assistant.external.AgentAiClient
import com.sprintstart.sprintstartbackend.assistant.external.ArtifactLookupService
import com.sprintstart.sprintstartbackend.assistant.external.BuddyAgentMessageDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyAgentRequest
import com.sprintstart.sprintstartbackend.assistant.external.BuddyAgentResponse
import com.sprintstart.sprintstartbackend.assistant.external.BuddyAgentStreamEvent
import com.sprintstart.sprintstartbackend.assistant.external.BuddyCitationDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddySessionFilters
import com.sprintstart.sprintstartbackend.assistant.external.BuddyStreamEvent
import com.sprintstart.sprintstartbackend.assistant.external.BuddyStreamEventType
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyTurnOptions
import com.sprintstart.sprintstartbackend.assistant.external.ConversationCompaction
import com.sprintstart.sprintstartbackend.assistant.external.FALLBACK_REPLY
import com.sprintstart.sprintstartbackend.assistant.external.HireActionOutcome
import com.sprintstart.sprintstartbackend.assistant.external.HireActionProposal
import com.sprintstart.sprintstartbackend.assistant.external.ResolvedArtifact
import com.sprintstart.sprintstartbackend.buddy.client.BuddyAiClient
import com.sprintstart.sprintstartbackend.buddy.external.enums.BuddyMessageRole
import com.sprintstart.sprintstartbackend.buddy.model.entity.BuddyCitation
import com.sprintstart.sprintstartbackend.buddy.model.entity.BuddyMessage
import com.sprintstart.sprintstartbackend.buddy.model.entity.BuddySession
import com.sprintstart.sprintstartbackend.buddy.model.entity.BuddySessionStatus
import com.sprintstart.sprintstartbackend.buddy.model.response.buddy.AiGenerateSessionTitleResponse
import com.sprintstart.sprintstartbackend.buddy.repository.BuddyCitationRepository
import com.sprintstart.sprintstartbackend.buddy.repository.BuddyMessageRepository
import com.sprintstart.sprintstartbackend.buddy.repository.BuddySessionRepository
import com.sprintstart.sprintstartbackend.knowledge.external.QuestionLogApi
import com.sprintstart.sprintstartbackend.shared.ai.AiServiceException
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.QuestionId
import com.sprintstart.sprintstartbackend.shared.model.SourceSystem
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.dto.ProjectDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.collections.emptyList

class BuddyServiceTest {
    private val buddySessionRepository: BuddySessionRepository = mockk()
    private val buddyMessageRepository: BuddyMessageRepository = mockk()
    private val buddyCitationRepository: BuddyCitationRepository = mockk()
    private val agentAiClient: AgentAiClient = mockk()
    private val buddyToolExecutor: BuddyToolExecutor = mockk()
    private val buddyActionService: BuddyActionService = mockk()
    private val artifactLookupService: ArtifactLookupService = mockk()
    private val userApi: UserApi = mockk()
    private val buddyAiClient: BuddyAiClient = mockk()
    private val questionLog: QuestionLogApi = mockk(relaxed = true)
    private val clock: Clock = mockk()

    // Folding is somebody else's job now, and these tests assert it is *asked for*, never that it
    // happened. BuddyCompactionServiceTest owns what a fold does.
    private val buddyCompactionService: BuddyCompactionService = mockk(relaxed = true)

    // Unconfined so the fire-and-forget launch runs inline: the tests can then verify the pass was
    // triggered without sleeping, which would make them slow and flaky in equal measure.
    private val service = BuddyService(
        buddySessionRepository,
        buddyMessageRepository,
        buddyCitationRepository,
        agentAiClient,
        buddyToolExecutor,
        buddyActionService,
        userApi,
        artifactLookupService,
        BuddyCompactionScheduler(
            buddyCompactionService,
            ConversationCompaction(
                mockk(relaxed = true),
                CoroutineScope(Dispatchers.Unconfined),
                mockk(relaxed = true),
            ),
        ),
        buddyAiClient,
        questionLog,
        clock,
    )

    private val userId = UUID.randomUUID()
    private val authId = "auth|test-user"

    // The ordinary case: a hire on one project. Retrieval is scoped to their projects and an
    // empty scope is refused, so a send test that cares about neither should still resolve to a
    // project rather than to none.
    private val defaultProjectId = UUID.randomUUID()

    @BeforeEach
    fun stubActionDefaults() {
        // Default: no action tools, and every tool the AI calls is a read-only one. Tests that
        // exercise an action override these.
        every { buddyActionService.actionSpecs(any()) } returns emptyList()
        every { buddyActionService.isAction(any()) } returns false
        // Retrieval is scoped to the hire's projects, so every turn resolves them. Tests about
        // scope override this with their own set.
        every { userApi.getUsersByIds(listOf(userId)) } returns listOf(userOn(defaultProjectId))
    }

    private fun finalReply(text: String) = BuddyAgentResponse(final = true, text = text)

    /** A resolvable hire who is on [projectIds], for stubbing the project lookup. */
    private fun userOn(vararg projectIds: UUID) = UserDto(
        id = userId,
        username = "hire",
        firstname = "Sam",
        lastname = "Hire",
        avatarUrl = null,
        profileIcon = null,
        projects = projectIds
            .map { ProjectDto(projectId = it, name = it.toString(), description = "") }
            .toSet(),
        projectRoles = emptyList(),
    )

    /** A resolvable hire with an existing, empty session — the starting point for a sent message. */
    private fun stageConversation(session: BuddySession) {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every {
            buddySessionRepository.findByIdAndUserId(session.id, userId)
        } returns session
        every {
            buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id)
        } returns emptyList()
        every { buddyMessageRepository.save(any()) } answers { firstArg() }
        every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()
    }

    @Nested
    inner class CreateSession {
        @Test
        fun `creates a session for the user and binds their only project`() {
            every { buddySessionRepository.save(any()) } answers { firstArg() }
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)

            val result = service.createSession(authId, null)

            assertThat(result.id).isNotNull()
            verify {
                buddySessionRepository.save(
                    match {
                        it.userId == userId && it.projectId == defaultProjectId
                    },
                )
            }
        }

        @Test
        fun `leaves the session without a project when the user is on several`() {
            every { buddySessionRepository.save(any()) } answers { firstArg() }
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { userApi.getUsersByIds(listOf(userId)) } returns
                listOf(userOn(UUID.randomUUID(), UUID.randomUUID()))

            service.createSession(authId, null)

            verify { buddySessionRepository.save(match { it.userId == userId && it.projectId == null }) }
        }

        @Test
        fun `leaves the session without a project when the user is on none`() {
            every { buddySessionRepository.save(any()) } answers { firstArg() }
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { userApi.getUsersByIds(listOf(userId)) } returns listOf(userOn())

            service.createSession(authId, null)

            verify { buddySessionRepository.save(match { it.userId == userId && it.projectId == null }) }
        }

        @Test
        fun `does not override an explicit project`() {
            every { buddySessionRepository.save(any()) } answers { firstArg() }
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            val explicit = UUID.randomUUID()

            service.createSession(authId, explicit)

            verify { buddySessionRepository.save(match { it.projectId == explicit }) }
        }

        @Test
        fun `creates a project-scoped session`() {
            every { buddySessionRepository.save(any()) } answers { firstArg() }
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)

            val projectId = UUID.randomUUID()

            service.createSession(authId, projectId)

            verify {
                buddySessionRepository.save(
                    match {
                        it.userId == userId && it.projectId == projectId
                    },
                )
            }
        }
    }

    @Nested
    inner class GetMessagesForMe {
        @Test
        fun `throws 400 when no session is supplied`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)

            assertThrows<ResponseStatusException> {
                service.getMessagesForMe(authId, null)
            }.also {
                assertThat(it.statusCode.value()).isEqualTo(400)
            }
        }

        @Test
        fun `returns the requested session's messages oldest first`() {
            val session = BuddySession(userId = userId)

            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every {
                buddySessionRepository.findByIdAndUserId(session.id, userId)
            } returns session
            every {
                buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id)
            } returns listOf(
                BuddyMessage(session = session, role = BuddyMessageRole.USER, content = "Hi"),
                BuddyMessage(session = session, role = BuddyMessageRole.ASSISTANT, content = "Hello!"),
            )

            val result = service.getMessagesForMe(authId, session.id)

            assertThat(result).hasSize(2)
            assertThat(result[0].content).isEqualTo("Hi")
            assertThat(result[1].role).isEqualTo(BuddyMessageRole.ASSISTANT)
        }

        @Test
        fun `throws 404 when the session does not belong to the authenticated user`() {
            val sessionId = UUID.randomUUID()

            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every {
                buddySessionRepository.findByIdAndUserId(sessionId, userId)
            } returns null

            assertThrows<ResponseStatusException> {
                service.getMessagesForMe(authId, sessionId)
            }.also {
                assertThat(it.statusCode.value()).isEqualTo(404)
            }
        }

        @Test
        fun `throws 404 when the authenticated user does not exist`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.getMessagesForMe(authId, UUID.randomUUID())
            }.also { assertThat(it.statusCode.value()).isEqualTo(404) }
        }

        @Test
        fun `returns messages starting with last opening`() {
            val session = BuddySession(userId = userId)

            val oldUserMessage = BuddyMessage(
                id = UUID.randomUUID(),
                session = session,
                role = BuddyMessageRole.USER,
                content = "old question",
                opening = false,
            )
            val firstOpening = BuddyMessage(
                id = UUID.randomUUID(),
                session = session,
                role = BuddyMessageRole.ASSISTANT,
                content = "first greeting",
                opening = true,
            )
            val messageAfterFirstOpening = BuddyMessage(
                id = UUID.randomUUID(),
                session = session,
                role = BuddyMessageRole.USER,
                content = "first question",
                opening = false,
            )
            val secondOpening = BuddyMessage(
                id = UUID.randomUUID(),
                session = session,
                role = BuddyMessageRole.ASSISTANT,
                content = "second greeting",
                opening = true,
            )
            val messageAfterSecondOpening = BuddyMessage(
                id = UUID.randomUUID(),
                session = session,
                role = BuddyMessageRole.USER,
                content = "second question",
                opening = false,
            )

            every { buddySessionRepository.findByIdAndUserId(session.id, userId) } returns session
            every {
                buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id)
            } returns listOf(
                oldUserMessage,
                firstOpening,
                messageAfterFirstOpening,
                secondOpening,
                messageAfterSecondOpening,
            )
            every {
                userApi.getUserIdByAuthId(any())
            } returns Optional.of(userId)

            val result = service.getMessagesForMe(authId, session.id)

            assertThat(result).extracting<String> { it.content }.containsExactly(
                "second greeting",
                "second question",
            )
        }

        @Test
        fun `returns all messages when there is no opening`() {
            val session = BuddySession(userId = userId)

            val first = BuddyMessage(
                id = UUID.randomUUID(),
                session = session,
                role = BuddyMessageRole.USER,
                content = "first",
            )
            val second = BuddyMessage(
                id = UUID.randomUUID(),
                session = session,
                role = BuddyMessageRole.ASSISTANT,
                content = "second",
            )

            every { buddySessionRepository.findByIdAndUserId(session.id, userId) } returns session
            every {
                buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id)
            } returns listOf(first, second)
            every {
                userApi.getUserIdByAuthId(any())
            } returns Optional.of(userId)

            val result = service.getMessagesForMe(authId, session.id)

            assertThat(result).extracting<String> { it.content }.containsExactly(
                "first",
                "second",
            )
        }

        @Test
        fun `get sessions only returns active sessions`() {
            val activeSession = BuddySession(
                id = UUID.randomUUID(),
                userId = userId,
                status = BuddySessionStatus.ACTIVE,
            )

            every {
                buddySessionRepository.findByUserIdAndStatusOrderByCreatedAtDesc(
                    userId,
                    BuddySessionStatus.ACTIVE,
                )
            } returns listOf(activeSession)
            every {
                userApi.getUserIdByAuthId(any())
            } returns Optional.of(userId)

            val result = service.getSessions(authId)

            assertThat(result.sessions)
                .extracting<UUID> { it.id }
                .containsExactly(activeSession.id)

            verify {
                buddySessionRepository.findByUserIdAndStatusOrderByCreatedAtDesc(
                    userId,
                    BuddySessionStatus.ACTIVE,
                )
            }
        }
    }

    @Nested
    @Suppress("LargeClass")
    inner class SendMessageForMe {
        @Test
        fun `persists the user message before calling the AI client`() = runTest {
            val session = BuddySession(userId = userId, title = "session")
            stageConversation(session)

            val saved = mutableListOf<BuddyMessage>()
            every { buddyCitationRepository.saveAll(emptyList<BuddyCitation>()) } returns emptyList()
            every { buddyMessageRepository.save(capture(saved)) } answers { firstArg() }
            coEvery {
                agentAiClient.buddyAgentTurnStream(any())
            } returnsStream finalReply("Set up like so.")

            service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "How do I get set up?",
                    BuddyTurnOptions(capabilitiesEnabled = true, filters = null),
                ).toList()

            val userMessage = saved.first { it.role == BuddyMessageRole.USER }
            assertThat(userMessage.content).isEqualTo("How do I get set up?")
        }

        @Test
        fun `scopes retrieval to every project the hire is on`() = runTest {
            // A hire onboarding on two projects should find material from both, and from neither of
            // anybody else's. Narrowing to one of theirs would hide their own work; narrowing to
            // none would show them everybody's.
            val alpha = UUID.randomUUID()
            val beta = UUID.randomUUID()
            val session = BuddySession(userId = userId, title = "session")
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
            every { buddySessionRepository.findByIdAndUserId(session.id, userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns emptyList()
            every { buddyMessageRepository.save(any()) } answers { firstArg() }
            every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()
            every { buddyCitationRepository.saveAll(emptyList<BuddyCitation>()) } returns emptyList()
            val requests = mutableListOf<BuddyAgentRequest>()
            coEvery { agentAiClient.buddyAgentTurnStream(capture(requests)) } returnsStream finalReply("Here.")

            service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "how do we deploy?",
                    BuddyTurnOptions(capabilitiesEnabled = true, filters = null),
                ).toList()

            assertThat(requests.first().projectIds)
                .containsExactlyInAnyOrder(alpha.toString(), beta.toString())
        }

        @Test
        fun `scopes retrieval to the one project the hire is on`() = runTest {
            val session = BuddySession(userId = userId, title = "session")
            stageConversation(session)
            val only = UUID.randomUUID()
            every { userApi.getUsersByIds(listOf(userId)) } returns listOf(userOn(only))
            val requests = mutableListOf<BuddyAgentRequest>()
            coEvery { agentAiClient.buddyAgentTurnStream(capture(requests)) } returnsStream finalReply("Here.")
            every { buddyCitationRepository.saveAll(emptyList<BuddyCitation>()) } returns emptyList()

            service.sendMessageForMe(authId, session.id, "how do we deploy?", BuddyTurnOptions(filters = null)).toList()

            assertThat(requests.first().projectIds).containsExactly(only.toString())
        }

        /**
         * The empty scope is the whole issue: the AI fails closed on it and admits nothing, so a
         * turn would search nothing and answer as though the project simply had no material. That is
         * the worst moment to sound confident and the hardest state for the hire to diagnose, so the
         * turn is refused and the hire is told the state and what resolves it, rather than handed a
         * confident empty answer. Asserting the consequence, not just the payload: no AI call at
         * all, and a reply that names the missing project.
         */
        @Test
        fun `a hire on no project is told the buddy cannot search their material yet`() = runTest {
            val session = BuddySession(userId = userId, title = "session")
            stageConversation(session)
            every { userApi.getUsersByIds(listOf(userId)) } returns listOf(userOn())

            val events = service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "how do we deploy?",
                    BuddyTurnOptions(filters = null),
                ).toList()

            // Nothing was searched, because there was nothing to search.
            coVerify(exactly = 0) { agentAiClient.buddyAgentTurnStream(any()) }
            val streamed = events.filter { it.type == "token" }.joinToString("") { it.content ?: "" }
            assertThat(streamed).contains("not on a project")
            assertThat(events.last().type).isEqualTo("done")
        }

        @Test
        fun `a session whose project the hire left is rebound to their new project`() = runTest {
            val oldProject = UUID.randomUUID()
            val newProject = UUID.randomUUID()
            val session = BuddySession(userId = userId, projectId = oldProject, title = "session")
            stageConversation(session)
            every { userApi.getUsersByIds(listOf(userId)) } returns listOf(userOn(newProject))
            every { buddySessionRepository.save(any()) } answers { firstArg() }
            every { questionLog.record(any()) } just runs
            every { buddyCitationRepository.saveAll(emptyList<BuddyCitation>()) } returns emptyList()
            val requests = mutableListOf<BuddyAgentRequest>()
            coEvery { agentAiClient.buddyAgentTurnStream(capture(requests)) } returnsStream finalReply("Here.")

            service.sendMessageForMe(authId, session.id, "how do we deploy?", BuddyTurnOptions(filters = null)).toList()

            assertThat(session.projectId).isEqualTo(newProject)
            verify { buddySessionRepository.save(match { it.projectId == newProject }) }
            verify { questionLog.record(match { it.project == ProjectId(newProject) }) }
            assertThat(requests.first().projectIds).containsExactly(newProject.toString())
        }

        @Test
        fun `a session whose project the hire left is unbound when they have no project left`() = runTest {
            val session = BuddySession(userId = userId, projectId = UUID.randomUUID(), title = "session")
            stageConversation(session)
            every { userApi.getUsersByIds(listOf(userId)) } returns listOf(userOn())
            every { buddySessionRepository.save(any()) } answers { firstArg() }

            val events = service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "how do we deploy?",
                    BuddyTurnOptions(filters = null),
                ).toList()

            assertThat(session.projectId).isNull()
            verify { buddySessionRepository.save(match { it.projectId == null }) }
            verify(exactly = 0) { questionLog.record(any()) }
            coVerify(exactly = 0) { agentAiClient.buddyAgentTurnStream(any()) }
            val streamed = events.filter { it.type == "token" }.joinToString("") { it.content ?: "" }
            assertThat(streamed).contains("not on a project")
        }

        @Test
        fun `a session that is still on one of the hire's projects is left alone`() = runTest {
            val session = BuddySession(userId = userId, projectId = defaultProjectId, title = "session")
            stageConversation(session)
            every { questionLog.record(any()) } just runs
            every { buddyCitationRepository.saveAll(emptyList<BuddyCitation>()) } returns emptyList()
            coEvery { agentAiClient.buddyAgentTurnStream(any()) } returnsStream finalReply("Here.")

            service.sendMessageForMe(authId, session.id, "how do we deploy?", BuddyTurnOptions(filters = null)).toList()

            assertThat(session.projectId).isEqualTo(defaultProjectId)
            verify(exactly = 0) { buddySessionRepository.save(any()) }
        }

        @Test
        fun `a project scoped session restricts retrieval to that project`() = runTest {
            val projectId = UUID.randomUUID()
            val otherProjectId = UUID.randomUUID()
            val session = BuddySession(
                userId = userId,
                projectId = projectId,
                title = "session",
            )

            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every {
                buddySessionRepository.findByIdAndUserId(session.id, userId)
            } returns session
            every {
                buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id)
            } returns emptyList()
            every { buddyMessageRepository.save(any()) } answers { firstArg() }
            every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()
            every {
                userApi.getUsersByIds(listOf(userId))
            } returns listOf(
                UserDto(
                    id = userId,
                    username = "hire",
                    firstname = "Sam",
                    lastname = "Hire",
                    avatarUrl = null,
                    profileIcon = null,
                    projects = setOf(
                        ProjectDto(projectId = projectId, name = "Alpha", description = ""),
                        ProjectDto(projectId = otherProjectId, name = "Beta", description = ""),
                    ),
                    projectRoles = emptyList(),
                ),
            )
            every { questionLog.record(any()) } just runs
            every { buddyCitationRepository.saveAll(emptyList<BuddyCitation>()) } returns emptyList()

            val requests = mutableListOf<BuddyAgentRequest>()
            coEvery {
                agentAiClient.buddyAgentTurnStream(capture(requests))
            } returnsStream finalReply("Here.")

            service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "how do we deploy?",
                    BuddyTurnOptions(capabilitiesEnabled = true, filters = null),
                ).toList()

            assertThat(requests.first().projectIds)
                .containsExactly(projectId.toString())
        }

        @Test
        fun `passes filters to the agent request`() = runTest {
            val session = BuddySession(userId = userId, title = "session")
            stageConversation(session)

            val filters = BuddySessionFilters(
                sourceSystems = listOf(SourceSystem.GITHUB, SourceSystem.JIRA),
                from = Instant.parse("2026-07-01T00:00:00Z").toString(),
                to = Instant.parse("2026-07-31T23:59:59Z").toString(),
            )

            val requests = mutableListOf<BuddyAgentRequest>()
            coEvery {
                agentAiClient.buddyAgentTurnStream(capture(requests))
            } returnsStream finalReply("Here.")
            every { buddyCitationRepository.saveAll(emptyList<BuddyCitation>()) } returns emptyList()

            service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "what happened?",
                    BuddyTurnOptions(filters = filters),
                ).toList()

            assertThat(requests.first().filters).isEqualTo(filters)
            assertThat(requests).allMatch { it.filters == filters }
        }

        @Test
        fun `relays reasoning and words as the AI streams them`() = runTest {
            val session = BuddySession(userId = userId, title = "session")
            stageConversation(session)
            val saved = mutableListOf<BuddyMessage>()
            every { buddyMessageRepository.save(capture(saved)) } answers { firstArg() }

            every {
                agentAiClient.buddyAgentTurnStream(any())
            } returns streamOf(
                reasoning("I checked "),
                reasoning("the relevant context."),
                token("Here is "),
                token("the answer."),
                result(finalReply("Here is the answer.")),
            )
            every { buddyCitationRepository.saveAll(emptyList<BuddyCitation>()) } returns emptyList()

            val events = service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "How does this work?",
                    BuddyTurnOptions(capabilitiesEnabled = true, filters = null),
                ).toList()

            assertThat(events.map { it.type })
                .containsExactly("reasoning", "reasoning", "token", "token", "done")
            assertThat(events.filter { it.type == "reasoning" }.map { it.reasoning })
                .containsExactly("I checked ", "the relevant context.")
            assertThat(events.filter { it.type == "token" }.map { it.content })
                .containsExactly("Here is ", "the answer.")
            assertThat(saved.single { it.role == BuddyMessageRole.ASSISTANT }.content)
                .isEqualTo("Here is the answer.")
        }

        /**
         * A hop that wrote something before a tool call is shown at once, not held until the loop
         * ends, and the next hop's words are set apart from it: the AI cannot do that at this seam, as
         * a resumed call starts with no memory of what the one before it wrote.
         */
        @Test
        fun `shows each hop before the next call to the AI starts and stores all of it`() = runTest {
            val session = BuddySession(userId = userId, title = "session")
            stageConversation(session)
            val saved = mutableListOf<BuddyMessage>()
            every { buddyMessageRepository.save(capture(saved)) } answers { firstArg() }
            every { buddyCitationRepository.saveAll(emptyList<BuddyCitation>()) } returns emptyList()

            val toolCall = BuddyToolCallDto(id = "call_0", name = "get_my_metrics")
            val paused = BuddyAgentResponse(
                final = false,
                messages = listOf(
                    BuddyAgentMessageDto(role = "assistant", content = "", toolCalls = listOf(toolCall)),
                ),
                pendingToolCalls = listOf(toolCall),
            )
            val shown = mutableListOf<BuddyStreamEvent>()
            var shownWhenResumed: List<BuddyStreamEvent> = emptyList()
            var calls = 0
            every { agentAiClient.buddyAgentTurnStream(any()) } answers {
                if (++calls == 1) {
                    streamOf(token("Let me check. "), toolUse("search_docs"), result(paused))
                } else {
                    flow {
                        shownWhenResumed = shown.toList()
                        emit(token("Here you go."))
                        emit(result(finalReply("Here you go.")))
                    }
                }
            }
            every { buddyToolExecutor.execute(toolCall, userId) } returns "openContributionCount=1"

            service
                .sendMessageForMe(authId, session.id, "is my PR stuck?", BuddyTurnOptions(capabilitiesEnabled = true))
                .collect { shown.add(it) }

            assertThat(shownWhenResumed.map { it.type }).containsExactly("token", "tool_use", "tool_use")
            assertThat(shownWhenResumed.mapNotNull { it.name }).containsExactly("search_docs", "get_my_metrics")
            assertThat(shown.filter { it.type == "token" }.map { it.content })
                .containsExactly("Let me check. ", "\n\n", "Here you go.")
            assertThat(shown.last().type).isEqualTo("done")
            assertThat(saved.single { it.role == BuddyMessageRole.ASSISTANT }.content)
                .isEqualTo("Let me check. \n\nHere you go.")
        }

        @Test
        fun `keeps what was shown when the turn is stopped between hops`() = runTest {
            val session = BuddySession(userId = userId, title = "session")
            stageConversation(session)

            val toolCall = BuddyToolCallDto(id = "call_0", name = "get_my_metrics")
            val paused = BuddyAgentResponse(
                final = false,
                messages = listOf(
                    BuddyAgentMessageDto(role = "assistant", content = "", toolCalls = listOf(toolCall)),
                ),
                pendingToolCalls = listOf(toolCall),
            )
            every {
                agentAiClient.buddyAgentTurnStream(any())
            } returns streamOf(token("Let me check. "), toolUse("search_docs"), result(paused))
            every { buddyToolExecutor.execute(any(), any()) } returns "m"

            val job = launch {
                service
                    .sendMessageForMe(
                        authId,
                        session.id,
                        "is my PR stuck?",
                        BuddyTurnOptions(capabilitiesEnabled = true),
                    ).collect { event ->
                        if (event.type == "tool_use") {
                            cancel()
                        }
                    }
            }
            job.join()

            verify(exactly = 1) { agentAiClient.buddyAgentTurnStream(any()) }
            verify(exactly = 0) { buddyToolExecutor.execute(any(), any()) }
            verify {
                buddyMessageRepository.save(
                    match<BuddyMessage> {
                        it.role == BuddyMessageRole.ASSISTANT && it.isIncomplete && it.content == "Let me check. "
                    },
                )
            }
        }

        @Test
        fun `keeps what was shown and ends the turn with an error event when the AI reports an error`() = runTest {
            val session = BuddySession(userId = userId, title = "session")
            stageConversation(session)
            every { agentAiClient.buddyAgentTurnStream(any()) } returns streamOf(
                token("Part of "),
                BuddyAgentStreamEvent(type = BuddyAgentStreamEvent.ERROR, message = "model unavailable"),
            )

            val events = service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "hi",
                    BuddyTurnOptions(capabilitiesEnabled = true),
                ).toList()

            assertThat(events.map { it.type }).containsExactly(BuddyStreamEventType.TOKEN, BuddyStreamEventType.ERROR)
            // The provider's text stays in the log; the client gets a fixed sentence.
            assertThat(events.last().message).isNotBlank().doesNotContain("model unavailable")
            verify {
                buddyMessageRepository.save(
                    match<BuddyMessage> {
                        it.role == BuddyMessageRole.ASSISTANT && it.isIncomplete && it.content == "Part of "
                    },
                )
            }
        }

        @Test
        fun `ends the turn with an error event when the AI ends its stream without a result`() = runTest {
            val session = BuddySession(userId = userId, title = "session")
            stageConversation(session)
            every { agentAiClient.buddyAgentTurnStream(any()) } returns streamOf(token("Part of "))

            val events = service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "hi",
                    BuddyTurnOptions(capabilitiesEnabled = true),
                ).toList()

            assertThat(events.last().type).isEqualTo(BuddyStreamEventType.ERROR)
            assertThat(events.map { it.type }).doesNotContain(BuddyStreamEventType.DONE)
            verify {
                buddyMessageRepository.save(
                    match<BuddyMessage> { it.isIncomplete && it.content == "Part of " },
                )
            }
        }

        @Test
        fun `shows the final text itself when the AI streamed no words`() = runTest {
            val session = BuddySession(userId = userId, title = "session")
            stageConversation(session)
            val saved = mutableListOf<BuddyMessage>()
            every { buddyMessageRepository.save(capture(saved)) } answers { firstArg() }
            every { buddyCitationRepository.saveAll(emptyList<BuddyCitation>()) } returns emptyList()
            every { agentAiClient.buddyAgentTurnStream(any()) } returns
                streamOf(result(finalReply("Whole answer here.")))

            val events = service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "hi",
                    BuddyTurnOptions(capabilitiesEnabled = true),
                ).toList()

            assertThat(events.filter { it.type == "token" }.joinToString("") { it.content.orEmpty() })
                .isEqualTo("Whole answer here.")
            assertThat(saved.single { it.role == BuddyMessageRole.ASSISTANT }.content)
                .isEqualTo("Whole answer here.")
        }

        @Test
        fun `ends in the fallback after what was shown when the step budget runs out`() = runTest {
            val session = BuddySession(userId = userId, title = "session")
            stageConversation(session)
            val saved = mutableListOf<BuddyMessage>()
            every { buddyMessageRepository.save(capture(saved)) } answers { firstArg() }
            every { buddyCitationRepository.saveAll(emptyList<BuddyCitation>()) } returns emptyList()

            val toolCall = BuddyToolCallDto(id = "call_0", name = "get_my_metrics")
            val paused = BuddyAgentResponse(
                final = false,
                messages = listOf(
                    BuddyAgentMessageDto(role = "assistant", content = "", toolCalls = listOf(toolCall)),
                ),
                pendingToolCalls = listOf(toolCall),
            )
            every {
                agentAiClient.buddyAgentTurnStream(any())
            } answers { streamOf(token("Looking."), result(paused)) }
            every { buddyToolExecutor.execute(any(), any()) } returns "m"

            service.sendMessageForMe(authId, session.id, "hi", BuddyTurnOptions(capabilitiesEnabled = true)).toList()

            val reply = saved.single { it.role == BuddyMessageRole.ASSISTANT }
            assertThat(reply.content).startsWith("Looking.\n\nLooking.")
            assertThat(reply.content).endsWith("\n\n${FALLBACK_REPLY}")
            assertThat(reply.isIncomplete).isFalse()
        }

        @Test
        fun `generates and persists session title for the first message`() = runTest {
            val session = BuddySession(userId = userId)

            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every {
                buddySessionRepository.findByIdAndUserId(session.id, userId)
            } returns session
            every {
                buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id)
            } returns emptyList()
            every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()

            every { buddySessionRepository.nameIfUntitled(session.id, any()) } returns 1
            every { buddyMessageRepository.save(any()) } answers { firstArg() }
            every { buddyCitationRepository.saveAll(emptyList<BuddyCitation>()) } returns emptyList()

            coEvery {
                buddyAiClient.getSessionTitle(any())
            } returns AiGenerateSessionTitleResponse("A new beginning")

            coEvery {
                agentAiClient.buddyAgentTurnStream(any())
            } returnsStream finalReply("Set up like so.")

            service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "How do I get set up?",
                    BuddyTurnOptions(capabilitiesEnabled = false, filters = null),
                ).toList()

            coVerify(exactly = 1) {
                buddyAiClient.getSessionTitle(any())
            }

            verify {
                buddySessionRepository.nameIfUntitled(session.id, "A new beginning")
            }
            verify(exactly = 0) { buddySessionRepository.save(any()) }
        }

        @Test
        fun `does not regenerate session title when session already has a title`() = runTest {
            val session = BuddySession(
                userId = userId,
                title = "New beginnings ahead",
            )

            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByIdAndUserId(session.id, userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns emptyList()
            every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()
            every { buddyMessageRepository.save(any()) } answers { firstArg() }
            every { buddyCitationRepository.saveAll(emptyList<BuddyCitation>()) } returns emptyList()

            coEvery {
                agentAiClient.buddyAgentTurnStream(any())
            } returnsStream finalReply("Set up like so.")

            service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "How do I get set up?",
                    BuddyTurnOptions(capabilitiesEnabled = false, filters = null),
                ).toList()

            assertThat(session.title).isEqualTo("New beginnings ahead")
            coVerify(exactly = 0) {
                buddyAiClient.getSessionTitle(any())
            }
        }

        /**
         * The mode is enforced by what the model was handed, not by what the prompt asked of it.
         * A tool the reasoner never received is one it cannot call, however the conversation goes.
         */
        @Test
        fun `capabilities off mounts no tools at all`() = runTest {
            val session = BuddySession(userId = userId, title = "session")
            stageConversation(session)

            every { buddyToolExecutor.toolSpecs(any()) } returns listOf(
                BuddyToolSpecDto(
                    name = "get_arrival_steps",
                    description = "",
                    parameters = JsonObject(emptyMap()),
                ),
            )
            every { buddyActionService.actionSpecs(any()) } returns listOf(
                BuddyToolSpecDto(
                    name = "escalate",
                    description = "",
                    parameters = JsonObject(emptyMap()),
                ),
            )
            every { buddyCitationRepository.saveAll(emptyList<BuddyCitation>()) } returns emptyList()

            val requests = mutableListOf<BuddyAgentRequest>()
            coEvery {
                agentAiClient.buddyAgentTurnStream(capture(requests))
            } returnsStream finalReply("Here.")

            service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "how do we deploy?",
                    BuddyTurnOptions(capabilitiesEnabled = false, filters = null),
                ).toList()

            assertThat(requests.first().backendTools).isEmpty()
        }

        @Test
        fun `capabilities on mounts the read tools and the action tools`() = runTest {
            val session = BuddySession(userId = userId, title = "session")
            stageConversation(session)
            every { buddyToolExecutor.toolSpecs(any()) } returns listOf(
                BuddyToolSpecDto(name = "get_arrival_steps", description = "", parameters = JsonObject(emptyMap())),
            )
            every { buddyActionService.actionSpecs(any()) } returns listOf(
                BuddyToolSpecDto(name = "escalate", description = "", parameters = JsonObject(emptyMap())),
            )
            every { buddyCitationRepository.saveAll(emptyList<BuddyCitation>()) } returns emptyList()
            val requests = mutableListOf<BuddyAgentRequest>()
            coEvery { agentAiClient.buddyAgentTurnStream(capture(requests)) } returnsStream finalReply("Here.")

            service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "how do we deploy?",
                    BuddyTurnOptions(capabilitiesEnabled = true, filters = null),
                ).toList()

            assertThat(requests.first().backendTools.map { it.name })
                .containsExactlyInAnyOrder("get_arrival_steps", "escalate")
        }

        /**
         * An empty tool list is a fact the model would have to invent a reason for. Saying which
         * mode it is in is the reason -- without it the persona offers what it cannot do, and the
         * refusal reads as a bug rather than as the mode the hire chose.
         */
        @Test
        fun `the persona is told which mode it is in`() = runTest {
            val session = BuddySession(userId = userId, title = "session")
            stageConversation(session)
            val requests = mutableListOf<BuddyAgentRequest>()
            every { buddyCitationRepository.saveAll(emptyList<BuddyCitation>()) } returns emptyList()
            coEvery { agentAiClient.buddyAgentTurnStream(capture(requests)) } returnsStream finalReply("Here.")

            service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "how do we deploy?",
                    BuddyTurnOptions(capabilitiesEnabled = false, filters = null),
                ).toList()

            assertThat(requests.first().capabilitiesEnabled).isFalse()
        }

        /**
         * A resumed turn that lost the mode would rebuild a mentor offering to act, mid-answer.
         *
         * The tool call here is contrived — with no tools mounted the reasoner has none to call —
         * but the loop is what is under test, not the tool: the mode has to survive a second hop
         * however that hop came about.
         */
        @Test
        fun `every hop of a turn carries the mode`() = runTest {
            val session = BuddySession(userId = userId, title = "session")
            stageConversation(session)
            val requests = mutableListOf<BuddyAgentRequest>()
            coEvery { agentAiClient.buddyAgentTurnStream(capture(requests)) } returnsStreams listOf(
                BuddyAgentResponse(
                    final = false,
                    messages = listOf(BuddyAgentMessageDto(role = "assistant")),
                    pendingToolCalls = listOf(BuddyToolCallDto(id = "call-1", name = "get_arrival_steps")),
                ),
                finalReply("Here."),
            )
            every { buddyToolExecutor.execute(any(), any()) } returns "nothing outstanding"
            every { buddyCitationRepository.saveAll(emptyList<BuddyCitation>()) } returns emptyList()

            service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "how do we deploy?",
                    BuddyTurnOptions(capabilitiesEnabled = false, filters = null),
                ).toList()

            assertThat(requests).hasSizeGreaterThan(1)
            assertThat(requests).allMatch { !it.capabilitiesEnabled }
        }

        /**
         * The switch is a mood, not a setting: it is sent per message and touches no session state,
         * so a hire who looks something up and then asks the mentor to act stays in one
         * conversation rather than starting a second.
         */
        @Test
        fun `switching mode mid-conversation keeps one transcript`() = runTest {
            val session = BuddySession(userId = userId, title = "session")
            stageConversation(session)
            val saved = mutableListOf<BuddyMessage>()
            every { buddyMessageRepository.save(capture(saved)) } answers { firstArg() }
            coEvery { agentAiClient.buddyAgentTurnStream(any()) } returnsStream finalReply("Here.")
            every { buddyCitationRepository.saveAll(emptyList<BuddyCitation>()) } returns emptyList()

            service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "where are the deploy docs?",
                    BuddyTurnOptions(capabilitiesEnabled = false, filters = null),
                ).toList()
            service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "escalate that for me",
                    BuddyTurnOptions(capabilitiesEnabled = true, filters = null),
                ).toList()

            assertThat(saved).allMatch { it.session.id == session.id }
            verify(exactly = 0) { buddySessionRepository.save(any()) }
        }

        @Test
        fun `threads prior messages and the new question as the running conversation`() = runTest {
            val session = BuddySession(userId = userId, title = "session")
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByIdAndUserId(session.id, userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns listOf(
                BuddyMessage(session = session, role = BuddyMessageRole.USER, content = "Hi"),
                BuddyMessage(session = session, role = BuddyMessageRole.ASSISTANT, content = "Hello!"),
            )
            every { buddyMessageRepository.save(any()) } answers { firstArg() }
            every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()
            every { buddyCitationRepository.saveAll(emptyList<BuddyCitation>()) } returns emptyList()
            val requests = mutableListOf<BuddyAgentRequest>()
            coEvery {
                agentAiClient.buddyAgentTurnStream(capture(requests))
            } returnsStream finalReply("More detail.")

            service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "Can you say more?",
                    BuddyTurnOptions(capabilitiesEnabled = true, filters = null),
                ).toList()

            assertThat(requests.first().messages).containsExactly(
                BuddyAgentMessageDto(role = "user", content = "Hi"),
                BuddyAgentMessageDto(role = "assistant", content = "Hello!"),
                BuddyAgentMessageDto(role = "user", content = "Can you say more?"),
            )
        }

        @Test
        fun `sends the contribution vocabulary on every hop, not just the first`() = runTest {
            val session = BuddySession(userId = userId, title = "session")
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByIdAndUserId(session.id, userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns emptyList()
            every { buddyMessageRepository.save(any()) } answers { firstArg() }
            every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()
            every { buddyActionService.isAction(any()) } returns false
            every { buddyCitationRepository.saveAll(emptyList<BuddyCitation>()) } returns emptyList()
            val requests = mutableListOf<BuddyAgentRequest>()
            coEvery { agentAiClient.buddyAgentTurnStream(capture(requests)) } returnsStreams listOf(
                BuddyAgentResponse(
                    final = false,
                    text = "",
                    pendingToolCalls = listOf(BuddyToolCallDto(id = "c0", name = "get_my_metrics")),
                ),
                finalReply("Nice work on that retro."),
            )
            every { buddyToolExecutor.execute(any(), userId) } returns "no metrics"

            service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "how am I doing?",
                    BuddyTurnOptions(capabilitiesEnabled = true, filters = null),
                ).toList()

            // Every hop, unlike the summary: the persona is rebuilt whenever the running
            // conversation carries no system message, so a resumed turn that omitted the
            // vocabulary would leave the persona with no words for the hire's work mid-conversation.
            assertThat(requests).hasSize(2)
            assertThat(requests.map { it.vocabulary.contributionNounPlural })
                .containsExactly("changes", "changes")
            assertThat(requests.first().vocabulary.contributionVerbPast).isEqualTo("merged")
        }

        @Test
        fun `messages with the same session stay in that session`() = runTest {
            val session = BuddySession(userId = userId, title = "session")
            stageConversation(session)

            coEvery {
                agentAiClient.buddyAgentTurnStream(any())
            } returnsStream finalReply("ok")
            every { buddyCitationRepository.saveAll(emptyList<BuddyCitation>()) } returns emptyList()

            service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "First",
                    BuddyTurnOptions(capabilitiesEnabled = true, filters = null),
                ).toList()
            service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "Second",
                    BuddyTurnOptions(capabilitiesEnabled = true, filters = null),
                ).toList()

            verify(exactly = 0) {
                buddySessionRepository.save(any())
            }

            // Optional noch stärker:
            verify(exactly = 2) {
                buddySessionRepository.findByIdAndUserId(session.id, userId)
            }
        }

        @Test
        fun `persists the final answer once the agent loop completes`() = runTest {
            val session = BuddySession(userId = userId, title = "session")
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByIdAndUserId(session.id, userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns emptyList()
            every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()
            val saved = mutableListOf<BuddyMessage>()
            every { buddyMessageRepository.save(capture(saved)) } answers { firstArg() }
            coEvery {
                agentAiClient.buddyAgentTurnStream(any())
            } returnsStream finalReply("No question is too basic.")
            every { buddyCitationRepository.saveAll(emptyList<BuddyCitation>()) } returns emptyList()

            service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "Hi",
                    BuddyTurnOptions(capabilitiesEnabled = true, filters = null),
                ).toList()

            val assistantMessage = saved.first { it.role == BuddyMessageRole.ASSISTANT }
            assertThat(assistantMessage.content).isEqualTo("No question is too basic.")
        }

        @Test
        fun `runs a backend tool the AI asks for and feeds the result back`() = runTest {
            val session = BuddySession(userId = userId, title = "session")
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByIdAndUserId(session.id, userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns emptyList()
            every { buddyMessageRepository.save(any()) } answers { firstArg() }
            every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()
            every { buddyCitationRepository.saveAll(emptyList<BuddyCitation>()) } returns emptyList()

            val toolCall = BuddyToolCallDto(id = "call_0", name = "get_my_metrics")
            val paused = BuddyAgentResponse(
                final = false,
                messages = listOf(
                    BuddyAgentMessageDto(role = "assistant", content = "", toolCalls = listOf(toolCall)),
                ),
                pendingToolCalls = listOf(toolCall),
            )
            val requests = mutableListOf<BuddyAgentRequest>()
            coEvery { agentAiClient.buddyAgentTurnStream(capture(requests)) } returnsStreams listOf(
                paused,
                finalReply("Your PR has waited 52 hours — that's on the reviewer."),
            )
            every { buddyToolExecutor.execute(toolCall, userId) } returns "openContributionCount=1"

            val events = service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "is my PR stuck?",
                    BuddyTurnOptions(capabilitiesEnabled = true, filters = null),
                ).toList()

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
            val session = BuddySession(userId = userId, title = "session")
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByIdAndUserId(session.id, userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns emptyList()
            every { buddyMessageRepository.save(any()) } answers { firstArg() }
            every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()
            every { buddyCitationRepository.saveAll(emptyList<BuddyCitation>()) } returns emptyList()

            val actionCall = BuddyToolCallDto(id = "call_0", name = "open_orientation")
            val paused = BuddyAgentResponse(
                final = false,
                messages = listOf(
                    BuddyAgentMessageDto(role = "assistant", content = "", toolCalls = listOf(actionCall)),
                ),
                pendingToolCalls = listOf(actionCall),
            )
            val requests = mutableListOf<BuddyAgentRequest>()
            coEvery { agentAiClient.buddyAgentTurnStream(capture(requests)) } returnsStreams listOf(
                paused,
                finalReply("I can open the task packet for you — confirm below."),
            )
            every { buddyActionService.isAction("open_orientation") } returns true
            every { buddyActionService.propose(actionCall, userId) } returns
                HireActionOutcome(
                    toolResult = "Proposed to the hire; awaiting confirmation.",
                    proposal = HireActionProposal(
                        action = "open_orientation",
                        label = "Open the task packet",
                        question = null,
                    ),
                )

            val events = service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "help me start my first task",
                    BuddyTurnOptions(capabilitiesEnabled = true, filters = null),
                ).toList()

            // The proposal is emitted as its own gate-able event, carrying the action + button label...
            val proposal = events.first { it.type == "action_proposal" }
            assertThat(proposal.action).isEqualTo("open_orientation")
            assertThat(proposal.label).isEqualTo("Open the task packet")
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
         * Every confirm payload a proposal carries has to reach the stream. Drop `title` or
         * `attesterId` and `request_attestation` reaches the confirm endpoint with nothing to act
         * on, coming back as "I need to know what work to confirm and who to ask" every time —
         * the action cannot succeed at all.
         *
         * It hid because that message reads like a precondition the hire failed rather than a wire
         * that drops fields, and because the payload was declared on the event all along.
         */
        @Test
        fun `an attestation proposal carries what to confirm and who to ask`() =
            runTest {
                val session = BuddySession(userId = userId, title = "session")
                every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
                every { buddySessionRepository.findByIdAndUserId(session.id, userId) } returns session
                every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns emptyList()
                every { buddyMessageRepository.save(any()) } answers { firstArg() }
                every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()
                every { buddyCitationRepository.saveAll(emptyList<BuddyCitation>()) } returns emptyList()

                val attesterId = UUID.randomUUID()
                val actionCall = BuddyToolCallDto(id = "call_0", name = "request_attestation")
                val paused = BuddyAgentResponse(
                    final = false,
                    messages = listOf(
                        BuddyAgentMessageDto(role = "assistant", content = "", toolCalls = listOf(actionCall)),
                    ),
                    pendingToolCalls = listOf(actionCall),
                )
                coEvery { agentAiClient.buddyAgentTurnStream(any()) } returnsStreams listOf(
                    paused,
                    finalReply("I can ask them to confirm it — confirm below."),
                )
                every { buddyActionService.isAction("request_attestation") } returns true
                every { buddyActionService.propose(actionCall, userId) } returns
                    HireActionOutcome(
                        toolResult = "Proposed to the hire; awaiting confirmation.",
                        proposal = HireActionProposal(
                            action = "request_attestation",
                            label = "Ask them to confirm this",
                            question = null,
                            title = "Facilitated the sprint retro",
                            attesterId = attesterId.toString(),
                        ),
                    )

                val events = service
                    .sendMessageForMe(
                        authId,
                        session.id,
                        "can Ana confirm my retro?",
                        BuddyTurnOptions(capabilitiesEnabled = true, filters = null),
                    ).toList()

                val proposal = events.first { it.type == "action_proposal" }
                assertThat(proposal.title).isEqualTo("Facilitated the sprint retro")
                assertThat(proposal.attesterId).isEqualTo(attesterId.toString())
            }

        @Test
        fun `does not persist an assistant message when the agent turn fails`() = runTest {
            val session = BuddySession(userId = userId, title = "session")
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByIdAndUserId(session.id, userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns emptyList()
            every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()
            val saved = mutableListOf<BuddyMessage>()
            every { buddyMessageRepository.save(capture(saved)) } answers { firstArg() }
            coEvery { agentAiClient.buddyAgentTurnStream(any()) } throws
                AiServiceException(502, "", "AI buddy responded with error: boom")

            val events = service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "Hi",
                    BuddyTurnOptions(capabilitiesEnabled = true, filters = null),
                ).toList()

            assertThat(events.single().type).isEqualTo(BuddyStreamEventType.ERROR)
            assertThat(saved.map { it.role }).containsExactly(BuddyMessageRole.USER)
        }

        @Test
        fun `emits a BuddyStreamEvent done terminator`() = runTest {
            val session = BuddySession(userId = userId, title = "session")
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByIdAndUserId(session.id, userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns emptyList()
            every { buddyMessageRepository.save(any()) } answers { firstArg() }
            every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()
            coEvery { agentAiClient.buddyAgentTurnStream(any()) } returnsStream finalReply("done")
            every { buddyCitationRepository.saveAll(emptyList<BuddyCitation>()) } returns emptyList()

            val events: List<BuddyStreamEvent> = service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "Hi",
                    BuddyTurnOptions(capabilitiesEnabled = true, filters = null),
                ).toList()

            assertThat(events.last().type).isEqualTo("done")
        }

        @Test
        fun `sends only the window after the summary cursor, with the prior summary standing in`() = runTest {
            val session = BuddySession(userId = userId, title = "session").apply {
                summary = "Earlier we got the repo building."
                summarizedCount = 2
            }
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByIdAndUserId(session.id, userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns listOf(
                BuddyMessage(session = session, role = BuddyMessageRole.USER, content = "summarized 1"),
                BuddyMessage(session = session, role = BuddyMessageRole.ASSISTANT, content = "summarized 2"),
                BuddyMessage(session = session, role = BuddyMessageRole.USER, content = "recent question"),
                BuddyMessage(session = session, role = BuddyMessageRole.ASSISTANT, content = "recent answer"),
            )
            every { buddyMessageRepository.save(any()) } answers { firstArg() }
            every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()
            val requests = mutableListOf<BuddyAgentRequest>()
            coEvery {
                agentAiClient.buddyAgentTurnStream(capture(requests))
            } returnsStream finalReply("More detail.")
            every { buddyCitationRepository.saveAll(emptyList<BuddyCitation>()) } returns emptyList()

            service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "Can you say more?",
                    BuddyTurnOptions(capabilitiesEnabled = true, filters = null),
                ).toList()

            // The summarized prefix stays out of the prompt; the summary stands in for it...
            assertThat(requests.first().messages.map { it.content }).containsExactly(
                "recent question",
                "recent answer",
                "Can you say more?",
            )
            assertThat(requests.first().priorSummary).isEqualTo("Earlier we got the repo building.")
        }

        /**
         * A turn folds nothing, however far over the window it is.
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
            val session = BuddySession(userId = userId, title = "session")
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByIdAndUserId(session.id, userId) } returns session
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
            every { buddyCitationRepository.saveAll(emptyList<BuddyCitation>()) } returns emptyList()
            val requests = mutableListOf<BuddyAgentRequest>()
            coEvery { agentAiClient.buddyAgentTurnStream(capture(requests)) } returnsStream
                finalReply("Picking up where we were.")

            service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "m26",
                    BuddyTurnOptions(capabilitiesEnabled = true, filters = null),
                ).toList()

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
            val session = BuddySession(userId = userId, title = "session")
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByIdAndUserId(session.id, userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns emptyList()
            every { buddyMessageRepository.save(any()) } answers { firstArg() }
            every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()
            every { buddyCitationRepository.saveAll(emptyList<BuddyCitation>()) } returns emptyList()
            coEvery { agentAiClient.buddyAgentTurnStream(any()) } returnsStream finalReply("Here you go.")

            service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "Hi",
                    BuddyTurnOptions(capabilitiesEnabled = true, filters = null),
                ).toList()

            coVerify { buddyCompactionService.compactIfNeeded(userId, session.id) }
        }

        /**
         * A stream that dies part-way must not fold: the reply was never persisted, so folding
         * would advance the note past a turn the transcript does not contain.
         */
        @Test
        fun `asks for no fold when the agent turn fails`() = runTest {
            val session = BuddySession(userId = userId, title = "session")
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByIdAndUserId(session.id, userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns emptyList()
            every { buddyMessageRepository.save(any()) } answers { firstArg() }
            every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()
            coEvery { agentAiClient.buddyAgentTurnStream(any()) } throws
                AiServiceException(500, "boom", "AI down")

            service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "Hi",
                    BuddyTurnOptions(capabilitiesEnabled = true, filters = null),
                ).toList()

            coVerify(exactly = 0) { buddyCompactionService.compactIfNeeded(any(), session.id) }
        }

        @Test
        fun `sends the prior summary on the first hop only, never re-sent on a resume`() = runTest {
            val session = BuddySession(userId = userId, title = "session").apply { summary = "Earlier notes." }
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { buddySessionRepository.findByIdAndUserId(session.id, userId) } returns session
            every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns emptyList()
            every { buddyMessageRepository.save(any()) } answers { firstArg() }
            every { buddyToolExecutor.toolSpecs(any()) } returns emptyList()
            every { buddyCitationRepository.saveAll(emptyList<BuddyCitation>()) } returns emptyList()
            val toolCall = BuddyToolCallDto(id = "call_0", name = "get_my_metrics")
            val requests = mutableListOf<BuddyAgentRequest>()
            coEvery { agentAiClient.buddyAgentTurnStream(capture(requests)) } returnsStreams listOf(
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

            service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "m26",
                    BuddyTurnOptions(capabilitiesEnabled = true, filters = null),
                ).toList()

            assertThat(requests[0].priorSummary).isEqualTo("Earlier notes.")
            // The resume carries none: the summary is already folded into the running conversation
            // the AI returned, and re-sending would double-fold it.
            assertThat(requests[1].priorSummary).isNull()
        }

        @Test
        fun `strips quoted selection before publishing question event`() = runTest {
            val projectId = defaultProjectId
            val session = BuddySession(
                userId = userId,
                projectId = projectId,
                title = "session",
            )
            stageConversation(session)

            every { questionLog.record(any()) } just runs
            every { buddyCitationRepository.saveAll(emptyList<BuddyCitation>()) } returns emptyList()

            coEvery {
                agentAiClient.buddyAgentTurnStream(any())
            } returnsStream finalReply("Here.")

            service
                .sendMessageForMe(
                    authId,
                    session.id,
                    """
                    > This is the selected text.
                    > Please ignore this line.

                    How do we deploy this?
                    """.trimIndent(),
                    BuddyTurnOptions(capabilitiesEnabled = true, filters = null),
                ).toList()

            verify {
                questionLog.record(
                    match {
                        it.text == "How do we deploy this?"
                    },
                )
            }
        }

        @Test
        fun `persists resolved citations with assistant reply`() = runTest {
            val artifactId = UUID.randomUUID()
            val session = BuddySession(userId = userId, projectId = UUID.randomUUID())

            every { buddySessionRepository.findByIdAndUserId(session.id, userId) } returns session
            every { buddySessionRepository.nameIfUntitled(session.id, any()) } returns 1
            every {
                buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id)
            } returns emptyList()

            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)

            every { buddyToolExecutor.toolSpecs(userId) } returns emptyList()
            every { buddyActionService.actionSpecs(userId) } returns emptyList()

            coEvery {
                buddyAiClient.getSessionTitle(any())
            } returns AiGenerateSessionTitleResponse("A new beginning")

            every {
                questionLog.record(any())
            } just runs

            coEvery {
                agentAiClient.buddyAgentTurnStream(any())
            } returnsStream BuddyAgentResponse(
                reasoning = emptyList(),
                citations = listOf(
                    BuddyCitationDto(
                        artifactId = artifactId.toString(),
                        startLine = 42,
                        startPage = 3,
                    ),
                ),
                final = true,
                text = "Here is the answer.",
                messages = emptyList(),
                pendingToolCalls = emptyList(),
            )

            every {
                artifactLookupService.resolve(artifactId)
            } returns ResolvedArtifact(
                filename = "architecture.md",
                sourceUrl = "https://example.com/architecture.md",
            )

            val savedMessage = BuddyMessage(
                id = UUID.randomUUID(),
                session = session,
                role = BuddyMessageRole.ASSISTANT,
                content = "Here is the answer.",
            )

            every {
                buddyMessageRepository.save(any<BuddyMessage>())
            } returns savedMessage

            every {
                buddyCitationRepository.saveAll(any<Iterable<BuddyCitation>>())
            } answers { firstArg() }

            every { buddySessionRepository.save(any()) } answers { firstArg() }

            coEvery {
                buddyCompactionService.compactIfNeeded(userId, session.id)
            } just runs

            val events = service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "Where is the architecture documented?",
                    BuddyTurnOptions(filters = null),
                ).toList()

            coVerify {
                agentAiClient.buddyAgentTurnStream(any())
            }

            verify {
                artifactLookupService.resolve(artifactId)
            }

            verify {
                buddyCitationRepository.saveAll(
                    match<Iterable<BuddyCitation>> {
                        val citations = it.toList()
                        citations.size == 1 &&
                            citations[0].artifactId == artifactId &&
                            citations[0].filename == "architecture.md" &&
                            citations[0].sourceUrl == "https://example.com/architecture.md" &&
                            citations[0].startLine == 42 &&
                            citations[0].startPage == 3 &&
                            citations[0].message == savedMessage
                    },
                )
            }
        }

        @Test
        fun `saves incomplete assistant reply when stream is cancelled after partial reply`() = runTest {
            val sessionId = UUID.randomUUID()
            val projectId = defaultProjectId

            val session = BuddySession(
                id = sessionId,
                userId = userId,
                title = "Test",
                projectId = projectId,
            )

            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every {
                buddySessionRepository.findByIdAndUserId(sessionId, userId)
            } returns session
            every {
                buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId)
            } returns emptyList()
            every {
                buddyMessageRepository.save(any<BuddyMessage>())
            } answers { firstArg() }

            every { questionLog.record(any()) } just runs
            every { buddyToolExecutor.toolSpecs(userId) } returns emptyList()
            every { buddyActionService.actionSpecs(userId) } returns emptyList()

            coEvery {
                agentAiClient.buddyAgentTurnStream(any())
            } returnsStream BuddyAgentResponse(
                reasoning = emptyList(),
                citations = emptyList(),
                final = true,
                text = "First chunk. Second chunk.",
                messages = emptyList(),
                pendingToolCalls = emptyList(),
            )

            every {
                buddyCitationRepository.saveAll(any<Iterable<BuddyCitation>>())
            } returns emptyList()

            val job = launch {
                service
                    .sendMessageForMe(
                        authId = authId,
                        sessionId = sessionId,
                        content = "Where is the architecture documented?",
                        BuddyTurnOptions(filters = null),
                    ).collect { event ->
                        if (event.type == BuddyStreamEventType.TOKEN) {
                            cancel()
                        }
                    }
            }

            job.join()

            verify {
                buddyMessageRepository.save(
                    match<BuddyMessage> {
                        it.role == BuddyMessageRole.ASSISTANT &&
                            it.content.isNotBlank() &&
                            it.isIncomplete
                    },
                )
            }
        }

        @Test
        fun `propagates exception when persisting citations fails`() = runTest {
            val sessionId = UUID.randomUUID()
            val projectId = defaultProjectId

            val session = BuddySession(
                id = sessionId,
                userId = userId,
                title = "Test",
                projectId = projectId,
            )

            every {
                userApi.getUserIdByAuthId(any())
            } returns Optional.of(userId)

            every {
                buddySessionRepository.findByIdAndUserId(sessionId, userId)
            } returns session

            every {
                buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId)
            } returns emptyList()

            every {
                buddyMessageRepository.save(any<BuddyMessage>())
            } answers {
                firstArg()
            }

            every {
                questionLog.record(any())
            } just runs

            every {
                buddyToolExecutor.toolSpecs(userId)
            } returns emptyList()

            every {
                buddyActionService.actionSpecs(userId)
            } returns emptyList()

            coEvery {
                agentAiClient.buddyAgentTurnStream(any())
            } returnsStream BuddyAgentResponse(
                reasoning = emptyList(),
                citations = emptyList(),
                final = true,
                text = "This is the answer.",
                messages = emptyList(),
                pendingToolCalls = emptyList(),
            )

            every {
                buddyCitationRepository.saveAll(any<Iterable<BuddyCitation>>())
            } throws RuntimeException("database error")

            val exception = assertThrows<RuntimeException> {
                service
                    .sendMessageForMe(
                        authId = authId,
                        sessionId = sessionId,
                        content = "Where is the architecture documented?",
                        BuddyTurnOptions(filters = null),
                    ).toList()
            }

            assertThat(exception).hasMessage("database error")

            verify {
                buddyMessageRepository.save(
                    match<BuddyMessage> {
                        it.role == BuddyMessageRole.ASSISTANT &&
                            it.content == "This is the answer." &&
                            !it.isIncomplete
                    },
                )
            }

            verify(exactly = 1) {
                buddyCitationRepository.saveAll(any<Iterable<BuddyCitation>>())
            }
        }

        /** One turn in which the AI calls [actionCall] and the action service answers with [outcome]. */
        private suspend fun eventsForAction(
            actionCall: BuddyToolCallDto,
            outcome: HireActionOutcome,
        ): List<BuddyStreamEvent> {
            val session = BuddySession(userId = userId, title = "session")
            stageConversation(session)
            every { buddyCitationRepository.saveAll(emptyList<BuddyCitation>()) } returns emptyList()
            val paused = BuddyAgentResponse(
                final = false,
                messages = listOf(
                    BuddyAgentMessageDto(role = "assistant", content = "", toolCalls = listOf(actionCall)),
                ),
                pendingToolCalls = listOf(actionCall),
            )
            coEvery { agentAiClient.buddyAgentTurnStream(any()) } returnsStreams
                listOf(paused, finalReply("Done."))
            every { buddyActionService.isAction(actionCall.name) } returns true
            every { buddyActionService.propose(actionCall, userId) } returns outcome
            return service
                .sendMessageForMe(
                    authId,
                    session.id,
                    "please",
                    BuddyTurnOptions(capabilitiesEnabled = true),
                ).toList()
        }

        @Test
        fun `a proposal's id lists reach the client as strings`() =
            runTest {
                val call = BuddyToolCallDto(id = "call_0", name = "add_path_step")
                val ids = List(3) { UUID.randomUUID() }
                val proposal = HireActionProposal(
                    action = "add_path_step",
                    label = "Add this step to your path",
                    question = null,
                    optionIds = listOf(ids[0]),
                    waitsOnIds = listOf(ids[1]),
                    unlocksIds = listOf(ids[2]),
                )

                val event = eventsForAction(call, HireActionOutcome("Proposed.", proposal))
                    .single { it.type == "action_proposal" }

                assertThat(event.optionIds).containsExactly(ids[0].toString())
                assertThat(event.waitsOnIds).containsExactly(ids[1].toString())
                assertThat(event.unlocksIds).containsExactly(ids[2].toString())
            }

        @Test
        fun `a proposal leaves out the id lists it does not use`() =
            runTest {
                val call = BuddyToolCallDto(id = "call_0", name = "flag_to_pm")
                val proposal = HireActionProposal(
                    action = "flag_to_pm",
                    label = "Flag this to your PM",
                    question = "When is the freeze?",
                )

                val event = eventsForAction(call, HireActionOutcome("Proposed.", proposal))
                    .single { it.type == "action_proposal" }

                assertThat(event.optionIds).isNull()
                assertThat(event.waitsOnIds).isNull()
                assertThat(event.unlocksIds).isNull()
            }

        @Test
        fun `an action that cannot be proposed shows no proposal and the turn still answers`() =
            runTest {
                val call = BuddyToolCallDto(id = "call_0", name = "flag_to_pm")
                val refused = HireActionOutcome("No question was provided to flag.", null)

                val events = eventsForAction(call, refused)

                assertThat(events.map { it.type }).doesNotContain("action_proposal")
                assertThat(
                    events.filter { it.type == "token" }.joinToString("") { it.content.orEmpty() },
                ).isEqualTo("Done.")
            }
    }

    @Nested
    inner class DeleteMessage {
        @Test
        fun `deletes own message`() {
            val sessionUserId = UUID.fromString(userId.toString())
            val resolvedUserId = UUID.fromString(userId.toString())

            val session = BuddySession(userId = sessionUserId)
            val message = BuddyMessage(
                id = UUID.randomUUID(),
                session = session,
                role = BuddyMessageRole.USER,
                content = "Question",
            )

            every { buddyMessageRepository.findById(message.id) } returns Optional.of(message)
            every {
                buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id)
            } returns listOf(message)
            every {
                userApi.getUserIdByAuthId(any())
            } returns Optional.of(resolvedUserId)
            every {
                buddyMessageRepository.delete(any())
            } just runs

            service.deleteMessage(authId, message.id)

            verify {
                buddyMessageRepository.delete(message)
            }
            // A deleted question leaves the FAQ's material too.
            verify { questionLog.forget(QuestionId(message.id)) }
        }

        @Test
        fun `delete message returns not found when message does not exist`() {
            val messageId = UUID.randomUUID()

            every {
                buddyMessageRepository.findById(messageId)
            } returns Optional.empty()

            every {
                userApi.getUserIdByAuthId(any())
            } returns Optional.of(userId)

            assertThrows<ResponseStatusException> {
                service.deleteMessage(authId, messageId)
            }.also {
                assertThat(it.statusCode.value()).isEqualTo(404)
            }

            verify(exactly = 0) {
                buddyMessageRepository.delete(any())
            }
        }

        @Test
        fun `delete message returns not found for another users message`() {
            val session = BuddySession(userId = UUID.randomUUID())
            val message = BuddyMessage(
                id = UUID.randomUUID(),
                session = session,
                role = BuddyMessageRole.USER,
                content = "Private question",
            )

            every {
                buddyMessageRepository.findById(message.id)
            } returns Optional.of(message)

            every {
                userApi.getUserIdByAuthId(any())
            } returns Optional.of(userId)

            assertThrows<ResponseStatusException> {
                service.deleteMessage(authId, message.id)
            }.also {
                assertThat(it.statusCode.value()).isEqualTo(404)
            }

            verify(exactly = 0) {
                buddyMessageRepository.delete(any())
            }
        }

        @Test
        fun `cannot delete summarized message`() {
            val session = BuddySession(userId = userId)
            val message1 = BuddyMessage(
                id = UUID.randomUUID(),
                session = session,
                role = BuddyMessageRole.USER,
                content = "old",
            )
            val message2 = BuddyMessage(
                id = UUID.randomUUID(),
                session = session,
                role = BuddyMessageRole.ASSISTANT,
                content = "old answer",
            )
            val message3 = BuddyMessage(
                id = UUID.randomUUID(),
                session = session,
                role = BuddyMessageRole.USER,
                content = "recent",
            )

            session.summarizedCount = 2

            every {
                buddyMessageRepository.findById(message1.id)
            } returns Optional.of(message1)

            every {
                buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id)
            } returns listOf(message1, message2, message3)

            every {
                userApi.getUserIdByAuthId(any())
            } returns Optional.of(userId)

            assertThrows<ResponseStatusException> {
                service.deleteMessage(authId, message1.id)
            }.also {
                assertThat(it.statusCode.value()).isEqualTo(409)
            }

            verify(exactly = 0) {
                buddyMessageRepository.delete(any())
            }
        }

        @Test
        fun `can delete message at compaction cursor`() {
            val session = BuddySession(userId = userId)
            val message1 = BuddyMessage(
                id = UUID.randomUUID(),
                session = session,
                role = BuddyMessageRole.USER,
                content = "summarized",
            )
            val message2 = BuddyMessage(
                id = UUID.randomUUID(),
                session = session,
                role = BuddyMessageRole.ASSISTANT,
                content = "summarized",
            )
            val message3 = BuddyMessage(
                id = UUID.randomUUID(),
                session = session,
                role = BuddyMessageRole.USER,
                content = "recent",
            )

            session.summarizedCount = 2

            every {
                buddyMessageRepository.findById(message3.id)
            } returns Optional.of(message3)

            every {
                buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id)
            } returns listOf(message1, message2, message3)

            every {
                userApi.getUserIdByAuthId(any())
            } returns Optional.of(userId)

            every {
                buddyMessageRepository.delete(any())
            } just runs

            service.deleteMessage(authId, message3.id)

            verify {
                buddyMessageRepository.delete(message3)
            }
        }
    }

    @Nested
    inner class BinSession {
        @Test
        fun `bins session`() {
            val now = Instant.parse("2026-09-30T12:00:00Z")
            val session = BuddySession(userId = userId)

            every {
                buddySessionRepository.findByIdAndUserId(session.id, userId)
            } returns session

            every {
                clock.instant()
            } returns now

            every {
                buddySessionRepository.save(session)
            } returns session

            every {
                userApi.getUserIdByAuthId(any())
            } returns Optional.of(userId)

            service.binSession(authId, session.id)

            assertThat(session.status).isEqualTo(BuddySessionStatus.BINNED)
            assertThat(session.binnedAt).isEqualTo(now)

            verify {
                buddySessionRepository.save(session)
            }
        }

        @Test
        fun `bin session returns not found for another users session`() {
            val session = BuddySession(userId = userId)

            every {
                buddySessionRepository.findByIdAndUserId(session.id, userId)
            } returns null

            every {
                userApi.getUserIdByAuthId(any())
            } returns Optional.of(userId)

            assertThrows<ResponseStatusException> {
                service.binSession(authId, session.id)
            }.also {
                assertThat(it.statusCode.value()).isEqualTo(404)
            }

            verify(exactly = 0) {
                buddySessionRepository.save(any())
            }
        }
    }
}
