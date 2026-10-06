package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyMessageRole
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyAgentMessageDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyAgentRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyAgentResponse
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyAgentStreamEvent
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyOpenRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyOpenStreamEvent
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyStreamEvent
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyActionProposal
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddySessionFilters
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyTeamMessage
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyTeamSession
import com.sprintstart.sprintstartbackend.onboarding.model.exceptions.OnboardingAiException
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddyTeamMessageRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddyTeamSessionRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Optional
import java.util.UUID

@Suppress("LargeClass")
class BuddyTeamServiceTest {
    private val buddyTeamSessionRepository: BuddyTeamSessionRepository = mockk()
    private val buddyTeamMessageRepository: BuddyTeamMessageRepository = mockk()
    private val onboardingAiClient: OnboardingAiClient = mockk()
    private val buddyTeamTools: BuddyTeamTools = mockk()
    private val buddyProposalService: BuddyProposalService = mockk()
    private val userApi: UserApi = mockk()
    private val buddyCompactionService: BuddyCompactionService = mockk(relaxed = true)
    private val artifactLookupService: ArtifactLookupService = mockk()

    private val service = BuddyTeamService(
        buddyTeamSessionRepository,
        buddyTeamMessageRepository,
        onboardingAiClient,
        buddyTeamTools,
        buddyProposalService,
        userApi,
        buddyCompactionService,
        CoroutineScope(Dispatchers.Unconfined),
        artifactLookupService,
    )

    private val authId = "auth|pm"
    private val userId = UUID.randomUUID()
    private val projectId = UUID.randomUUID()
    private val session = BuddyTeamSession(userId = userId, projectId = projectId)

    private fun spec(name: String) =
        BuddyToolSpecDto(name = name, description = "", parameters = JsonObject(emptyMap()))

    private fun finalReply(text: String) = BuddyAgentResponse(final = true, text = text)

    @BeforeEach
    fun manager() {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { userApi.canManageProject(authId, projectId) } returns true
        every { buddyTeamSessionRepository.findByUserIdAndProjectId(userId, projectId) } returns session
        every { buddyTeamMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns emptyList()
        every { buddyTeamMessageRepository.save(any()) } answers { firstArg() }
        every { buddyTeamTools.toolSpecs(any()) } returns listOf(spec(BuddyTeamTools.GET_TEAM_ATTENTION))
        every { buddyProposalService.isAction(any()) } returns false
        every { buddyTeamTools.areaOf(any()) } returns null
    }

    /**
     * Authorisation happens before anything is read or written. A hire, or the PM of another project,
     * naming this project gets a 403 — never an empty conversation that looks like team mode worked.
     */
    @Test
    fun `refuses a caller who does not manage the project before touching the conversation`() = runTest {
        every { userApi.canManageProject(authId, projectId) } returns false

        assertThrows<ResponseStatusException> {
            service.sendMessageForMe(authId, projectId, "who is stuck?")
        }.also { assertThat(it.statusCode.value()).isEqualTo(403) }

        verify(exactly = 0) { buddyTeamSessionRepository.findByUserIdAndProjectId(any(), any()) }
        verify(exactly = 0) { buddyTeamMessageRepository.save(any()) }
    }

    @Test
    fun `refuses to show the transcript to a caller who does not manage the project`() {
        every { userApi.canManageProject(authId, projectId) } returns false

        assertThrows<ResponseStatusException> { service.getMessagesForMe(authId, projectId) }
            .also { assertThat(it.statusCode.value()).isEqualTo(403) }
    }

    @Test
    fun `refuses to open team mode for a caller who does not manage the project`() = runTest {
        every { userApi.canManageProject(authId, projectId) } returns false

        assertThrows<ResponseStatusException> { service.streamOpenForMe(authId, projectId) }
            .also { assertThat(it.statusCode.value()).isEqualTo(403) }
    }

    @Test
    fun `404s a caller who does not exist`() = runTest {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.empty()

        assertThrows<ResponseStatusException> { service.sendMessageForMe(authId, projectId, "hi") }
            .also { assertThat(it.statusCode.value()).isEqualTo(404) }
    }

    /** The team conversation for this project, never the manager's own onboarding and never another project's. */
    @Test
    fun `speaks into the conversation for this manager and this project`() = runTest {
        val saved = mutableListOf<BuddyTeamMessage>()
        every { buddyTeamMessageRepository.save(capture(saved)) } answers { firstArg() }
        coEvery { onboardingAiClient.buddyAgentTurnStream(any()) } returnsStream finalReply("Nobody is stuck.")

        service.sendMessageForMe(authId, projectId, "who is stuck?").toList()

        assertThat(saved).allMatch { it.session.id == session.id }
        assertThat(saved.map { it.role }).containsExactly(BuddyMessageRole.USER, BuddyMessageRole.ASSISTANT)
    }

    @Test
    fun `creates the team conversation on first use`() = runTest {
        every { buddyTeamSessionRepository.findByUserIdAndProjectId(userId, projectId) } returns null
        every { buddyTeamSessionRepository.save(any()) } answers { firstArg() }
        every { buddyTeamMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(any()) } returns emptyList()
        coEvery { onboardingAiClient.buddyAgentTurnStream(any()) } returnsStream finalReply("Hello.")

        service.sendMessageForMe(authId, projectId, "hello").toList()

        verify { buddyTeamSessionRepository.save(match { it.userId == userId && it.projectId == projectId }) }
    }

    @Test
    fun `scopes retrieval to the one project and tells the AI it is team mode on every hop`() = runTest {
        val requests = mutableListOf<BuddyAgentRequest>()
        coEvery { onboardingAiClient.buddyAgentTurnStream(capture(requests)) } returnsStreams listOf(
            BuddyAgentResponse(
                final = false,
                messages = listOf(BuddyAgentMessageDto(role = "assistant")),
                pendingToolCalls = listOf(BuddyToolCallDto(id = "c1", name = BuddyTeamTools.GET_TEAM_ATTENTION)),
            ),
            finalReply("Sam is waiting on a review."),
        )
        every { buddyTeamTools.execute(any(), any(), any()) } returns "Sam is waiting."

        service.sendMessageForMe(authId, projectId, "who is stuck?").toList()

        assertThat(requests).hasSize(2)
        assertThat(requests).allMatch { it.teamMode && it.projectIds == listOf(projectId.toString()) }
    }

    /** The AI narrows retrieval whichever mode it is in, so a hop that dropped the filters would silently widen it. */
    @Test
    fun `sends the retrieval filters to the AI on every hop`() = runTest {
        val filters = BuddySessionFilters(
            sourceSystems = listOf(SourceSystem.GITHUB),
            from = "2026-10-01T00:00:00Z",
            to = "2026-10-06T21:59:59.999Z",
        )
        val requests = mutableListOf<BuddyAgentRequest>()
        coEvery { onboardingAiClient.buddyAgentTurnStream(capture(requests)) } returnsStreams listOf(
            BuddyAgentResponse(
                final = false,
                messages = listOf(BuddyAgentMessageDto(role = "assistant")),
                pendingToolCalls = listOf(BuddyToolCallDto(id = "c1", name = BuddyTeamTools.GET_TEAM_ATTENTION)),
            ),
            finalReply("Sam is waiting on a review."),
        )
        every { buddyTeamTools.execute(any(), any(), any()) } returns "Sam is waiting."

        service.sendMessageForMe(authId, projectId, "who is stuck?", filters = filters).toList()

        assertThat(requests).hasSize(2)
        assertThat(requests).allMatch { it.filters === filters }
    }

    @Test
    fun `sends no filters when the manager set none`() = runTest {
        val requests = mutableListOf<BuddyAgentRequest>()
        coEvery {
            onboardingAiClient.buddyAgentTurnStream(capture(requests))
        } returnsStream finalReply("Nobody is stuck.")

        service.sendMessageForMe(authId, projectId, "who is stuck?").toList()

        assertThat(requests).hasSize(1)
        assertThat(requests.single().filters).isNull()
    }

    /**
     * An area opened on one hop is mounted on the next. Resolving tools once per turn — as the hire's
     * buddy does — would leave `open_area` returning "opened" and the tools never arriving.
     */
    @Test
    fun `rebuilds the tools on every hop so an opened area is mounted from the next one`() = runTest {
        val mountedSets = mutableListOf<Set<TeamArea>>()
        every { buddyTeamTools.toolSpecs(any()) } answers {
            mountedSets.add(firstArg<Set<TeamArea>>().toSet())
            listOf(spec(BuddyTeamTools.OPEN_AREA))
        }
        every { buddyTeamTools.openArea(any()) } returns
            BuddyTeamTools.OpenAreaOutcome(area = TeamArea.KNOWLEDGE, toolResult = "Opened knowledge.")
        coEvery { onboardingAiClient.buddyAgentTurnStream(any()) } returnsStreams listOf(
            BuddyAgentResponse(
                final = false,
                messages = listOf(BuddyAgentMessageDto(role = "assistant")),
                pendingToolCalls = listOf(
                    BuddyToolCallDto(
                        id = "c1",
                        name = BuddyTeamTools.OPEN_AREA,
                        arguments = JsonObject(mapOf("area" to JsonPrimitive("knowledge"))),
                    ),
                ),
            ),
            finalReply("Here are the escalations."),
        )

        service.sendMessageForMe(authId, projectId, "any open escalations?").toList()

        assertThat(mountedSets).containsExactly(emptySet(), setOf(TeamArea.KNOWLEDGE))
    }

    /**
     * The failure this guards against, seen live: the buddy drafts an answer (opening the knowledge area),
     * the manager says "yes, send it" in the next message, and the tool that makes the change is gone, so the
     * model invents a confirm button. The transcript is text only; what a reply opened is stored with it.
     */
    private fun asTranscript(vararg messages: BuddyTeamMessage) {
        every { buddyTeamMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns
            messages.toList()
    }

    private fun reply(content: String = "Here is a draft.", opened: String? = null, opening: Boolean = false) =
        BuddyTeamMessage(
            session = session,
            role = BuddyMessageRole.ASSISTANT,
            content = content,
            opening = opening,
            openedAreas = opened,
        )

    private fun mountedOnEachHop(): MutableList<Set<TeamArea>> {
        val mountedSets = mutableListOf<Set<TeamArea>>()
        every { buddyTeamTools.toolSpecs(any()) } answers {
            mountedSets.add(firstArg<Set<TeamArea>>().toSet())
            listOf(spec(BuddyTeamTools.OPEN_AREA))
        }
        return mountedSets
    }

    private fun savedReplies(): List<BuddyTeamMessage> {
        val saved = mutableListOf<BuddyTeamMessage>()
        every { buddyTeamMessageRepository.save(any()) } answers {
            firstArg<BuddyTeamMessage>().also { saved.add(it) }
        }
        return saved
    }

    @Test
    fun `an area the last reply opened is still mounted when the manager approves what it drafted`() = runTest {
        asTranscript(
            BuddyTeamMessage(session = session, role = BuddyMessageRole.USER, content = "can you answer Ada?"),
            reply(opened = "KNOWLEDGE"),
        )
        val mountedSets = mountedOnEachHop()
        coEvery { onboardingAiClient.buddyAgentTurnStream(any()) } returnsStream finalReply("Done.")

        service.sendMessageForMe(authId, projectId, "You can send it").toList()

        assertThat(mountedSets).containsExactly(setOf(TeamArea.KNOWLEDGE))
    }

    @Test
    fun `stores the areas a reply opened with the reply`() = runTest {
        val saved = savedReplies()
        val mountedSets = mountedOnEachHop()
        every { buddyTeamTools.openArea(any()) } returns
            BuddyTeamTools.OpenAreaOutcome(area = TeamArea.KNOWLEDGE, toolResult = "Opened knowledge.")
        coEvery { onboardingAiClient.buddyAgentTurnStream(any()) } returnsStreams listOf(
            BuddyAgentResponse(
                final = false,
                messages = listOf(BuddyAgentMessageDto(role = "assistant")),
                pendingToolCalls = listOf(
                    BuddyToolCallDto(
                        id = "c1",
                        name = BuddyTeamTools.OPEN_AREA,
                        arguments = JsonObject(mapOf("area" to JsonPrimitive("knowledge"))),
                    ),
                ),
            ),
            finalReply("Here is a draft."),
        )

        service.sendMessageForMe(authId, projectId, "can you answer Ada?").toList()

        assertThat(mountedSets).containsExactly(emptySet(), setOf(TeamArea.KNOWLEDGE))
        assertThat(saved.single { it.role == BuddyMessageRole.ASSISTANT }.openedAreas).isEqualTo("KNOWLEDGE")
    }

    @Test
    fun `an area that was only carried over, and not used, is not stored again`() = runTest {
        asTranscript(reply(opened = "KNOWLEDGE"))
        val saved = savedReplies()
        mountedOnEachHop()
        coEvery { onboardingAiClient.buddyAgentTurnStream(any()) } returnsStream finalReply("Sent.")

        service.sendMessageForMe(authId, projectId, "You can send it").toList()

        assertThat(saved.single { it.role == BuddyMessageRole.ASSISTANT }.openedAreas).isNull()
    }

    @Test
    fun `an area opened again is stored again with the reply that opened it`() = runTest {
        asTranscript(reply(opened = "KNOWLEDGE"))
        val saved = savedReplies()
        mountedOnEachHop()
        every { buddyTeamTools.openArea(any()) } returns
            BuddyTeamTools.OpenAreaOutcome(area = TeamArea.KNOWLEDGE, toolResult = "Opened knowledge.")
        coEvery { onboardingAiClient.buddyAgentTurnStream(any()) } returnsStreams listOf(
            BuddyAgentResponse(
                final = false,
                messages = listOf(BuddyAgentMessageDto(role = "assistant")),
                pendingToolCalls = listOf(
                    BuddyToolCallDto(
                        id = "c1",
                        name = BuddyTeamTools.OPEN_AREA,
                        arguments = JsonObject(mapOf("area" to JsonPrimitive("knowledge"))),
                    ),
                ),
            ),
            finalReply("Anything else in there?"),
        )

        service.sendMessageForMe(authId, projectId, "and the other one?").toList()

        assertThat(saved.single { it.role == BuddyMessageRole.ASSISTANT }.openedAreas).isEqualTo("KNOWLEDGE")
    }

    @Test
    fun `an area stays mounted through a discussion, however many messages come before the approval`() = runTest {
        asTranscript(
            BuddyTeamMessage(session = session, role = BuddyMessageRole.USER, content = "can you answer Ada?"),
            reply(opened = "KNOWLEDGE"),
            BuddyTeamMessage(session = session, role = BuddyMessageRole.USER, content = "make it shorter"),
            reply(content = "Shorter draft."),
            BuddyTeamMessage(session = session, role = BuddyMessageRole.USER, content = "friendlier please"),
            reply(content = "Friendlier draft."),
        )
        val mountedSets = mountedOnEachHop()
        coEvery { onboardingAiClient.buddyAgentTurnStream(any()) } returnsStream finalReply("Done.")

        service.sendMessageForMe(authId, projectId, "You can send it").toList()

        assertThat(mountedSets).containsExactly(setOf(TeamArea.KNOWLEDGE))
    }

    @Test
    fun `every area the latest replies opened stays mounted`() = runTest {
        asTranscript(reply(opened = "KNOWLEDGE"), reply(opened = "TEAM,ARRIVAL"))
        val mountedSets = mountedOnEachHop()
        coEvery { onboardingAiClient.buddyAgentTurnStream(any()) } returnsStream finalReply("Done.")

        service.sendMessageForMe(authId, projectId, "go ahead").toList()

        assertThat(mountedSets).containsExactly(setOf(TeamArea.KNOWLEDGE, TeamArea.TEAM, TeamArea.ARRIVAL))
    }

    /** The bound on the reviewer's concern: a conversation moving on must not keep every area's tools. */
    @Test
    fun `an area left alone for the whole window closes`() = runTest {
        asTranscript(
            reply(opened = "KNOWLEDGE"),
            *Array(AREA_OPEN_FOR_REPLIES) { reply(content = "Something else.") },
        )
        val mountedSets = mountedOnEachHop()
        coEvery { onboardingAiClient.buddyAgentTurnStream(any()) } returnsStream finalReply("Ok.")

        service.sendMessageForMe(authId, projectId, "and now?").toList()

        assertThat(mountedSets).containsExactly(emptySet())
    }

    private fun toolCall(name: String) =
        BuddyAgentResponse(
            final = false,
            messages = listOf(BuddyAgentMessageDto(role = "assistant")),
            pendingToolCalls = listOf(BuddyToolCallDto(id = "c1", name = name)),
        )

    @Test
    fun `a carried-over area whose tool runs is stored again, so it stays open while it is in use`() = runTest {
        asTranscript(reply(opened = "KNOWLEDGE"))
        val saved = savedReplies()
        every { buddyTeamTools.toolSpecs(any()) } returns
            listOf(spec(BuddyTeamTools.OPEN_AREA), spec("list_open_escalations"))
        every { buddyTeamTools.areaOf("list_open_escalations") } returns TeamArea.KNOWLEDGE
        every { buddyTeamTools.execute(any(), any(), any()) } returns "One open question."
        coEvery { onboardingAiClient.buddyAgentTurnStream(any()) } returnsStreams
            listOf(toolCall("list_open_escalations"), finalReply("There is one."))

        service.sendMessageForMe(authId, projectId, "anything else open?").toList()

        assertThat(saved.single { it.role == BuddyMessageRole.ASSISTANT }.openedAreas).isEqualTo("KNOWLEDGE")
    }

    /**
     * The reviewer's blocker: the draft was written without opening the knowledge area, so "send it" had
     * no action mounted. The backend knows which area the action is in and opens it itself; the action is
     * not run on the hop it was not mounted for, and the model is asked to call it again.
     */
    @Test
    fun `calling an action of an area that is not open opens the area instead of running it`() = runTest {
        val saved = savedReplies()
        val mountedSets = mountedOnEachHop()
        every { buddyTeamTools.areaOf("answer_escalation") } returns TeamArea.KNOWLEDGE
        val requests = mutableListOf<BuddyAgentRequest>()
        coEvery { onboardingAiClient.buddyAgentTurnStream(capture(requests)) } returnsStreams
            listOf(toolCall("answer_escalation"), finalReply("Confirm below."))

        val events = service.sendMessageForMe(authId, projectId, "You can send it").toList()

        assertThat(mountedSets).containsExactly(emptySet(), setOf(TeamArea.KNOWLEDGE))
        assertThat(
            requests
                .last()
                .messages
                .last()
                .content,
        ).contains("knowledge area")
            .contains("call answer_escalation again")
        assertThat(events.single { it.type == "tool_use" }.name).isEqualTo(BuddyTeamTools.OPEN_AREA)
        assertThat(saved.single { it.role == BuddyMessageRole.ASSISTANT }.openedAreas).isEqualTo("KNOWLEDGE")
        verify(exactly = 0) { buddyProposalService.propose(any(), any()) }
        verify(exactly = 0) { buddyTeamTools.execute(any(), any(), any()) }
    }

    /**
     * The whole flow the review asked for: "send it" after a draft written without opening the knowledge
     * area. The first call opens the area, the second, now mounted, becomes a proposal card.
     */
    @Test
    fun `after the area is opened for it, calling the action again puts a proposal in front of the manager`() =
        runTest {
            every { buddyTeamTools.toolSpecs(any()) } answers {
                val opened = firstArg<Set<TeamArea>>()
                listOf(spec(BuddyTeamTools.OPEN_AREA)) +
                    if (TeamArea.KNOWLEDGE in opened) listOf(spec("answer_escalation")) else emptyList()
            }
            every { buddyTeamTools.areaOf("answer_escalation") } returns TeamArea.KNOWLEDGE
            every { buddyProposalService.isAction("answer_escalation") } returns true
            val proposal = BuddyActionProposal(
                userId = userId,
                projectId = projectId,
                action = "answer_escalation",
                params = "{}",
                label = "Answer: how do we deploy?",
                preview = "The answer is published.",
                risk = BuddyProposalRisk.STANDARD,
                createdAt = Instant.now(),
                expiresAt = Instant.now().plusSeconds(60),
            )
            every { buddyProposalService.propose(any(), any()) } returns
                BuddyProposalService.ProposeOutcome(toolResult = "Offered to the manager.", proposal = proposal)
            coEvery { onboardingAiClient.buddyAgentTurnStream(any()) } returnsStreams listOf(
                toolCall("answer_escalation"),
                toolCall("answer_escalation"),
                finalReply("It is in front of you to confirm."),
            )

            val events = service.sendMessageForMe(authId, projectId, "You can send it").toList()

            assertThat(events.single { it.type == "action_proposal" }.proposalId).isEqualTo(proposal.id.toString())
            verify(exactly = 1) { buddyProposalService.propose(any(), any()) }
        }

    @Test
    fun `a tool call written out as text opens the area of the tool it names`() = runTest {
        val mountedSets = mountedOnEachHop()
        every { buddyTeamTools.areaOf("answer_escalation") } returns TeamArea.KNOWLEDGE
        val requests = mutableListOf<BuddyAgentRequest>()
        val written = """{"name":"answer_escalation","parameters":{"request_id":"r1"}}"""
        coEvery { onboardingAiClient.buddyAgentTurnStream(capture(requests)) } returnsStreams listOf(
            BuddyAgentResponse(
                final = true,
                text = written,
                messages = listOf(BuddyAgentMessageDto(role = "assistant", content = written)),
            ),
            finalReply("Confirm below."),
        )

        service.sendMessageForMe(authId, projectId, "You can send it").toList()

        assertThat(mountedSets).containsExactly(emptySet(), setOf(TeamArea.KNOWLEDGE))
        assertThat(
            requests
                .last()
                .messages
                .last()
                .content,
        ).contains("The area of answer_escalation is open now")
            .isNotEqualTo(TOOL_CALL_WRITTEN_OUT)
    }

    @Test
    fun `nothing is opened for a call when open_area itself is not mounted`() = runTest {
        val mountedSets = mutableListOf<Set<TeamArea>>()
        every { buddyTeamTools.toolSpecs(any()) } answers {
            mountedSets.add(firstArg<Set<TeamArea>>().toSet())
            listOf(spec(BuddyTeamTools.GET_TEAM_ATTENTION))
        }
        every { buddyTeamTools.areaOf("answer_escalation") } returns TeamArea.KNOWLEDGE
        every { buddyTeamTools.execute(any(), any(), any()) } returns "The tool answer_escalation is not available."
        coEvery { onboardingAiClient.buddyAgentTurnStream(any()) } returnsStreams
            listOf(toolCall("answer_escalation"), finalReply("I cannot do that here."))

        service.sendMessageForMe(authId, projectId, "You can send it").toList()

        assertThat(mountedSets).containsExactly(emptySet(), emptySet())
    }

    @Test
    fun `a new visit starts with nothing mounted`() = runTest {
        val mountedSets = mountedOnEachHop()
        coEvery { onboardingAiClient.buddyAgentTurnStream(any()) } returnsStream finalReply("Ok.")

        // What an earlier visit opened is closed by the greeting that begins the next one.
        asTranscript(reply(opened = "KNOWLEDGE"), reply(content = "Hi again.", opening = true))
        service.sendMessageForMe(authId, projectId, "hello").toList()

        // No reply yet in this visit, only the manager's own message.
        asTranscript(BuddyTeamMessage(session = session, role = BuddyMessageRole.USER, content = "hello?"))
        service.sendMessageForMe(authId, projectId, "anyone there?").toList()

        assertThat(mountedSets).containsExactly(emptySet(), emptySet())
    }

    @Test
    fun `an area opened in this visit survives a greeting that came before it`() = runTest {
        asTranscript(reply(opened = "TEAM"), reply(content = "Hi again.", opening = true), reply(opened = "KNOWLEDGE"))
        val mountedSets = mountedOnEachHop()
        coEvery { onboardingAiClient.buddyAgentTurnStream(any()) } returnsStream finalReply("Done.")

        service.sendMessageForMe(authId, projectId, "You can send it").toList()

        assertThat(mountedSets).containsExactly(setOf(TeamArea.KNOWLEDGE))
    }

    /**
     * Seen live: the model answered with `{"name":"find_member","parameters":{...}}` as its reply. Nothing
     * ran, and the manager was shown a broken request as if it were an answer.
     */
    @Test
    fun `a tool call written out as the reply is sent back rather than kept`() = runTest {
        val saved = savedReplies()
        val requests = mutableListOf<BuddyAgentRequest>()
        val written = """I'll look them up. {"name":"find_member","parameters":{"query":"Ada"}}"""
        coEvery { onboardingAiClient.buddyAgentTurnStream(capture(requests)) } returnsStreams listOf(
            BuddyAgentResponse(
                final = true,
                text = written,
                messages = listOf(BuddyAgentMessageDto(role = "assistant", content = written)),
            ),
            finalReply("Ada is on the project."),
        )

        val events = service.sendMessageForMe(authId, projectId, "how is Ada?").toList()

        assertThat(requests).hasSize(2)
        assertThat(requests[1].messages.map { it.role }.takeLast(2)).containsExactly("assistant", "user")
        assertThat(requests[1].messages.last().content).isEqualTo(TOOL_CALL_WRITTEN_OUT)
        assertThat(shownAfterLastReset(events)).isEqualTo("Ada is on the project.")
        assertThat(saved.single { it.role == BuddyMessageRole.ASSISTANT }.content)
            .isEqualTo("Ada is on the project.")
    }

    /**
     * The words leave while the model writes them, so the call it wrote out has already been shown by
     * the time it is known not to be an answer. The client is told to take it back, in between.
     */
    @Test
    fun `tells the client to drop the words already shown when the reply turns out to be a tool call`() = runTest {
        val written = """{"name":"find_member","parameters":{"query":"Ada"}}"""
        coEvery { onboardingAiClient.buddyAgentTurnStream(any()) } returnsMany listOf(
            streamOf(token(written), result(finalReply(written))),
            streamOf(token("Ada is "), token("on the project."), result(finalReply("Ada is on the project."))),
        )

        val events = service.sendMessageForMe(authId, projectId, "how is Ada?").toList()

        assertThat(events.map { it.type }).containsExactly("token", "reset", "token", "token", "done")
        assertThat(events.first().content).isEqualTo(written)
    }

    @Test
    fun `a model that keeps writing the call out ends in the fallback reply, never the raw call`() = runTest {
        val saved = savedReplies()
        val written = """{"name":"find_member","parameters":{"query":"Ada"}}"""
        coEvery { onboardingAiClient.buddyAgentTurnStream(any()) } returnsStream finalReply(written)

        val events = service.sendMessageForMe(authId, projectId, "how is Ada?").toList()

        assertThat(shownAfterLastReset(events)).isEqualTo(BuddyService.FALLBACK_REPLY)
        assertThat(events.count { it.type == BuddyService.RESET }).isEqualTo(BuddyService.MAX_AGENT_STEPS)
        assertThat(saved.single { it.role == BuddyMessageRole.ASSISTANT }.content)
            .isEqualTo(BuddyService.FALLBACK_REPLY)
        coVerify(exactly = BuddyService.MAX_AGENT_STEPS) { onboardingAiClient.buddyAgentTurnStream(any()) }
    }

    @Test
    fun `passes reasoning, words and searches on as the AI streams them`() = runTest {
        coEvery { onboardingAiClient.buddyAgentTurnStream(any()) } returns streamOf(
            reasoning("Checking "),
            reasoning("the team."),
            toolUse("search_docs"),
            token("Nobody "),
            token("is stuck."),
            result(finalReply("Nobody is stuck.")),
        )

        val events = service.sendMessageForMe(authId, projectId, "who is stuck?").toList()

        assertThat(events.map { it.type })
            .containsExactly("reasoning", "reasoning", "tool_use", "token", "token", "done")
        assertThat(events.filter { it.type == "reasoning" }.map { it.reasoning })
            .containsExactly("Checking ", "the team.")
        assertThat(events.first { it.type == "tool_use" }.name).isEqualTo("search_docs")
    }

    @Test
    fun `stores everything the manager was shown, and sets the hops apart`() = runTest {
        val saved = savedReplies()
        val call = BuddyToolCallDto(id = "c1", name = BuddyTeamTools.GET_TEAM_ATTENTION)
        coEvery { onboardingAiClient.buddyAgentTurnStream(any()) } returnsMany listOf(
            streamOf(
                token("Let me look. "),
                result(
                    BuddyAgentResponse(
                        final = false,
                        messages = listOf(BuddyAgentMessageDto(role = "assistant")),
                        pendingToolCalls = listOf(call),
                    ),
                ),
            ),
            streamOf(token("Sam is waiting."), result(finalReply("Sam is waiting."))),
        )
        every { buddyTeamTools.execute(any(), any(), any()) } returns "Sam is waiting."

        val events = service.sendMessageForMe(authId, projectId, "who is stuck?").toList()

        assertThat(events.filter { it.type == "token" }.map { it.content })
            .containsExactly("Let me look. ", "\n\n", "Sam is waiting.")
        assertThat(saved.single { it.role == BuddyMessageRole.ASSISTANT }.content)
            .isEqualTo("Let me look. \n\nSam is waiting.")
    }

    @Test
    fun `fails the turn when the AI reports an error and stores no reply`() = runTest {
        val saved = savedReplies()
        coEvery { onboardingAiClient.buddyAgentTurnStream(any()) } returns streamOf(
            token("Part of "),
            BuddyAgentStreamEvent(type = BuddyAgentStreamEvent.ERROR, message = "model unavailable"),
        )

        assertThrows<OnboardingAiException> {
            service.sendMessageForMe(authId, projectId, "who is stuck?").toList()
        }.also { assertThat(it.statusCode).isEqualTo(503) }

        assertThat(saved.map { it.role }).containsExactly(BuddyMessageRole.USER)
    }

    private fun shownAfterLastReset(events: List<BuddyStreamEvent>): String =
        events
            .takeLastWhile { it.type != BuddyService.RESET }
            .filter { it.type == BuddyService.TOKEN }
            .joinToString("") { it.content.orEmpty() }

    @Test
    fun `folding old messages into the memory note does not close what the last reply opened`() = runTest {
        session.summarizedCount = 2
        asTranscript(
            BuddyTeamMessage(session = session, role = BuddyMessageRole.USER, content = "old question"),
            reply(content = "old answer"),
            BuddyTeamMessage(session = session, role = BuddyMessageRole.USER, content = "can you answer Ada?"),
            reply(opened = "KNOWLEDGE"),
        )
        val mountedSets = mountedOnEachHop()
        coEvery { onboardingAiClient.buddyAgentTurnStream(any()) } returnsStream finalReply("Done.")

        service.sendMessageForMe(authId, projectId, "You can send it").toList()

        assertThat(mountedSets).containsExactly(setOf(TeamArea.KNOWLEDGE))
    }

    @Test
    fun `capabilities off mounts no tools in team mode either`() = runTest {
        val requests = mutableListOf<BuddyAgentRequest>()
        coEvery {
            onboardingAiClient.buddyAgentTurnStream(capture(requests))
        } returnsStream finalReply("From the docs.")

        service.sendMessageForMe(authId, projectId, "how do we deploy?", capabilitiesEnabled = false).toList()

        assertThat(requests.single().backendTools).isEmpty()
        assertThat(requests.single().capabilitiesEnabled).isFalse()
        verify(exactly = 0) { buddyTeamTools.toolSpecs(any()) }
    }

    @Test
    fun `passes each tool call the tools mounted on the hop it came from`() = runTest {
        coEvery { onboardingAiClient.buddyAgentTurnStream(any()) } returnsStreams listOf(
            BuddyAgentResponse(
                final = false,
                messages = listOf(BuddyAgentMessageDto(role = "assistant")),
                pendingToolCalls = listOf(BuddyToolCallDto(id = "c1", name = "list_open_escalations")),
            ),
            finalReply("Done."),
        )
        every { buddyTeamTools.execute(any(), any(), any()) } returns "not available"

        service.sendMessageForMe(authId, projectId, "list escalations").toList()

        verify {
            buddyTeamTools.execute(
                match { it.name == "list_open_escalations" },
                match { it.projectId == projectId && it.userId == userId },
                setOf(BuddyTeamTools.GET_TEAM_ATTENTION),
            )
        }
    }

    /** The team note is folded; the manager's own onboarding memory is never touched from here. */
    @Test
    fun `asks for a team fold once the reply is persisted, never the hire's`() = runTest {
        coEvery { onboardingAiClient.buddyAgentTurnStream(any()) } returnsStream finalReply("Nobody is stuck.")

        service.sendMessageForMe(authId, projectId, "who is stuck?").toList()

        coVerify { buddyCompactionService.compactTeamIfNeeded(userId, projectId) }
        coVerify(exactly = 0) { buddyCompactionService.compactIfNeeded(any(), UUID.randomUUID()) }
    }

    @Test
    fun `opens with the team snapshot, in team mode, and persists the greeting as the visit's opening`() = runTest {
        every { buddyTeamTools.teamSnapshot(projectId) } returns "Who needs attention:\nSam is waiting."
        val requests = mutableListOf<BuddyOpenRequest>()
        every { onboardingAiClient.streamBuddyOpen(capture(requests)) } returns flowOf(
            BuddyOpenStreamEvent(type = "token", content = "Sam has been waiting."),
            BuddyOpenStreamEvent(type = "done", greeting = "Sam has been waiting."),
        )

        val events = service.streamOpenForMe(authId, projectId).toList()

        assertThat(requests.single().teamMode).isTrue()
        assertThat(requests.single().state).contains("Sam is waiting.")
        assertThat(events.last().type).isEqualTo("done")
        verify { buddyTeamMessageRepository.save(match { it.opening && it.content == "Sam has been waiting." }) }
        coVerify { buddyCompactionService.compactTeamIfNeeded(userId, projectId) }
    }

    @Test
    fun `replays an existing team greeting without calling the model`() = runTest {
        every { buddyTeamMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns listOf(
            BuddyTeamMessage(
                session = session,
                role = BuddyMessageRole.ASSISTANT,
                content = "Hi again.",
                opening = true,
            ),
        )

        val events = service.streamOpenForMe(authId, projectId).toList()

        assertThat(events.first().content).isEqualTo("Hi again.")
        verify(exactly = 0) { onboardingAiClient.streamBuddyOpen(any()) }
    }

    @Test
    fun `shows the current visit of the team conversation`() {
        every { buddyTeamMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns listOf(
            BuddyTeamMessage(session = session, role = BuddyMessageRole.USER, content = "old visit"),
            BuddyTeamMessage(session = session, role = BuddyMessageRole.ASSISTANT, content = "Hello.", opening = true),
            BuddyTeamMessage(session = session, role = BuddyMessageRole.USER, content = "who is stuck?"),
        )

        val messages = service.getMessagesForMe(authId, projectId)

        assertThat(messages.map { it.content }).containsExactly("Hello.", "who is stuck?")
    }

    /**
     * The one rule that makes team actions safe: a call proposes, it never performs. The manager sees a
     * card for the stored proposal, the model is told it was offered, and no tool ran.
     */
    @Test
    fun `an action call becomes a stored proposal event and never runs as a tool`() = runTest {
        every { buddyTeamTools.toolSpecs(any()) } returns listOf(spec("dismiss_escalation"))
        every { buddyProposalService.isAction("dismiss_escalation") } returns true
        val proposal = BuddyActionProposal(
            userId = userId,
            projectId = projectId,
            action = "dismiss_escalation",
            params = "{}",
            label = "Dismiss: how do we deploy?",
            preview = "The question disappears from the inbox.",
            risk = BuddyProposalRisk.DESTRUCTIVE,
            createdAt = Instant.now(),
            expiresAt = Instant.now().plusSeconds(60),
        )
        every { buddyProposalService.propose(any(), any()) } returns
            BuddyProposalService.ProposeOutcome(toolResult = "Proposed to the manager.", proposal = proposal)
        val requests = mutableListOf<BuddyAgentRequest>()
        coEvery { onboardingAiClient.buddyAgentTurnStream(capture(requests)) } returnsStreams listOf(
            BuddyAgentResponse(
                final = false,
                messages = listOf(BuddyAgentMessageDto(role = "assistant")),
                pendingToolCalls = listOf(BuddyToolCallDto(id = "c1", name = "dismiss_escalation")),
            ),
            finalReply("I can dismiss it — confirm below."),
        )

        val events = service.sendMessageForMe(authId, projectId, "dismiss that question").toList()

        val card = events.single { it.type == "action_proposal" }
        assertThat(card.proposalId).isEqualTo(proposal.id.toString())
        assertThat(card.preview).isEqualTo("The question disappears from the inbox.")
        assertThat(card.risk).isEqualTo("DESTRUCTIVE")
        assertThat(events.none { it.type == "tool_use" }).isTrue()
        val toolResult = requests.last().messages.last()
        assertThat(toolResult.content).isEqualTo("Proposed to the manager.")
        verify(exactly = 0) { buddyTeamTools.execute(any(), any(), any()) }
    }
}
