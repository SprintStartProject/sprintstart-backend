package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyMessageRole
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyAgentMessageDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyAgentRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyAgentResponse
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyOpenRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyOpenStreamEvent
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyActionProposal
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyTeamMessage
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyTeamSession
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

class BuddyTeamServiceTest {
    private val buddyTeamSessionRepository: BuddyTeamSessionRepository = mockk()
    private val buddyTeamMessageRepository: BuddyTeamMessageRepository = mockk()
    private val onboardingAiClient: OnboardingAiClient = mockk()
    private val buddyTeamTools: BuddyTeamTools = mockk()
    private val buddyProposalService: BuddyProposalService = mockk()
    private val userApi: UserApi = mockk()
    private val buddyCompactionService: BuddyCompactionService = mockk(relaxed = true)

    private val service = BuddyTeamService(
        buddyTeamSessionRepository,
        buddyTeamMessageRepository,
        onboardingAiClient,
        buddyTeamTools,
        buddyProposalService,
        userApi,
        buddyCompactionService,
        CoroutineScope(Dispatchers.Unconfined),
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
        coEvery { onboardingAiClient.buddyAgentTurn(any()) } returns finalReply("Nobody is stuck.")

        service.sendMessageForMe(authId, projectId, "who is stuck?").toList()

        assertThat(saved).allMatch { it.session.id == session.id }
        assertThat(saved.map { it.role }).containsExactly(BuddyMessageRole.USER, BuddyMessageRole.ASSISTANT)
    }

    @Test
    fun `creates the team conversation on first use`() = runTest {
        every { buddyTeamSessionRepository.findByUserIdAndProjectId(userId, projectId) } returns null
        every { buddyTeamSessionRepository.save(any()) } answers { firstArg() }
        every { buddyTeamMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(any()) } returns emptyList()
        coEvery { onboardingAiClient.buddyAgentTurn(any()) } returns finalReply("Hello.")

        service.sendMessageForMe(authId, projectId, "hello").toList()

        verify { buddyTeamSessionRepository.save(match { it.userId == userId && it.projectId == projectId }) }
    }

    @Test
    fun `scopes retrieval to the one project and tells the AI it is team mode on every hop`() = runTest {
        val requests = mutableListOf<BuddyAgentRequest>()
        coEvery { onboardingAiClient.buddyAgentTurn(capture(requests)) } returnsMany listOf(
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
        coEvery { onboardingAiClient.buddyAgentTurn(any()) } returnsMany listOf(
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
        coEvery { onboardingAiClient.buddyAgentTurn(any()) } returns finalReply("Done.")

        service.sendMessageForMe(authId, projectId, "You can send it").toList()

        assertThat(mountedSets).containsExactly(setOf(TeamArea.KNOWLEDGE))
    }

    @Test
    fun `stores the areas a reply opened with the reply`() = runTest {
        val saved = savedReplies()
        val mountedSets = mountedOnEachHop()
        every { buddyTeamTools.openArea(any()) } returns
            BuddyTeamTools.OpenAreaOutcome(area = TeamArea.KNOWLEDGE, toolResult = "Opened knowledge.")
        coEvery { onboardingAiClient.buddyAgentTurn(any()) } returnsMany listOf(
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
    fun `an area that was only carried over is not carried again`() = runTest {
        asTranscript(reply(opened = "KNOWLEDGE"))
        val saved = savedReplies()
        mountedOnEachHop()
        coEvery { onboardingAiClient.buddyAgentTurn(any()) } returns finalReply("Sent.")

        service.sendMessageForMe(authId, projectId, "You can send it").toList()

        assertThat(saved.single { it.role == BuddyMessageRole.ASSISTANT }.openedAreas).isNull()
    }

    @Test
    fun `opening the area again keeps it open for one more message`() = runTest {
        asTranscript(reply(opened = "KNOWLEDGE"))
        val saved = savedReplies()
        mountedOnEachHop()
        every { buddyTeamTools.openArea(any()) } returns
            BuddyTeamTools.OpenAreaOutcome(area = TeamArea.KNOWLEDGE, toolResult = "Opened knowledge.")
        coEvery { onboardingAiClient.buddyAgentTurn(any()) } returnsMany listOf(
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
    fun `nothing is carried over from anything but the reply just before`() = runTest {
        val mountedSets = mountedOnEachHop()
        coEvery { onboardingAiClient.buddyAgentTurn(any()) } returns finalReply("Ok.")

        // An older reply opened an area, but a later one did not: the area has closed.
        asTranscript(
            reply(opened = "KNOWLEDGE"),
            BuddyTeamMessage(session = session, role = BuddyMessageRole.USER, content = "thanks"),
            reply(content = "You are welcome."),
        )
        service.sendMessageForMe(authId, projectId, "and now?").toList()

        // The last message is a greeting that opens a visit.
        asTranscript(reply(content = "Hi again.", opening = true))
        service.sendMessageForMe(authId, projectId, "hello").toList()

        // The last message is the manager's own, so there is no reply to inherit from.
        asTranscript(BuddyTeamMessage(session = session, role = BuddyMessageRole.USER, content = "hello?"))
        service.sendMessageForMe(authId, projectId, "anyone there?").toList()

        assertThat(mountedSets).containsExactly(emptySet(), emptySet(), emptySet())
    }

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
        coEvery { onboardingAiClient.buddyAgentTurn(any()) } returns finalReply("Done.")

        service.sendMessageForMe(authId, projectId, "You can send it").toList()

        assertThat(mountedSets).containsExactly(setOf(TeamArea.KNOWLEDGE))
    }

    @Test
    fun `capabilities off mounts no tools in team mode either`() = runTest {
        val requests = mutableListOf<BuddyAgentRequest>()
        coEvery { onboardingAiClient.buddyAgentTurn(capture(requests)) } returns finalReply("From the docs.")

        service.sendMessageForMe(authId, projectId, "how do we deploy?", capabilitiesEnabled = false).toList()

        assertThat(requests.single().backendTools).isEmpty()
        assertThat(requests.single().capabilitiesEnabled).isFalse()
        verify(exactly = 0) { buddyTeamTools.toolSpecs(any()) }
    }

    @Test
    fun `passes each tool call the tools mounted on the hop it came from`() = runTest {
        coEvery { onboardingAiClient.buddyAgentTurn(any()) } returnsMany listOf(
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
        coEvery { onboardingAiClient.buddyAgentTurn(any()) } returns finalReply("Nobody is stuck.")

        service.sendMessageForMe(authId, projectId, "who is stuck?").toList()

        coVerify { buddyCompactionService.compactTeamIfNeeded(userId, projectId) }
        coVerify(exactly = 0) { buddyCompactionService.compactIfNeeded(any()) }
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
        coEvery { onboardingAiClient.buddyAgentTurn(capture(requests)) } returnsMany listOf(
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
