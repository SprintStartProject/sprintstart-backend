package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ProficiencyLevel
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.AuthoredCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.ChecklistCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.LinkCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.NoteCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.buddy.BuddyActionRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.goal.GoalView
import com.sprintstart.sprintstartbackend.onboarding.model.response.orientation.MyOrientationResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.starterwork.MyTaskZeroResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.starterwork.StarterWorkTaskProposalResponse
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.dto.ProjectDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserDto
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.server.ResponseStatusException

class BuddyActionServiceTest {
    private val taskZeroService: TaskZeroService = mockk()
    private val taskOrientationService: TaskOrientationService = mockk()
    private val knowledgeBaseService: KnowledgeBaseService = mockk(relaxed = true)
    private val userGoalService: UserGoalService = mockk()
    private val userApi: UserApi = mockk()
    private val attestationService: AttestationService = mockk()

    // Relaxed: claiming a goal also pins the task to the board, which is a side effect these tests
    // are not about -- the case that asserts it says so explicitly.
    private val boardService: BoardService = mockk(relaxed = true)
    private val competencyPlacementService: CompetencyPlacementService = mockk()
    private val service = BuddyActionService(
        taskZeroService,
        taskOrientationService,
        knowledgeBaseService,
        userGoalService,
        userApi,
        attestationService,
        boardService,
        competencyPlacementService,
    )

    private val userId = UUID.randomUUID()
    private val projectId = UUID.randomUUID()
    private val authId = "auth|hire"

    private fun userWith(vararg projects: ProjectDto) = UserDto(
        id = userId,
        username = "hire",
        firstname = "Sam",
        lastname = "Hire",
        avatarUrl = null,
        profileIcon = null,
        projects = projects.toSet(),
        projectRoles = emptyList(),
    )

    private val oneProject = ProjectDto(projectId, "Checkout", null)

    private fun onOneProject() {
        every { userApi.getUsersByIds(listOf(userId)) } returns listOf(userWith(oneProject))
    }

    private fun asHire() {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
    }

    private val jwt: Jwt = mockk<Jwt>().also { every { it.subject } returns authId }

    private fun call(name: String, args: Map<String, String> = emptyMap()) = BuddyToolCallDto(
        id = "c0",
        name = name,
        arguments = buildJsonObject { args.forEach { (k, v) -> put(k, v) } },
    )

    private fun taskZero(title: String?) = MyTaskZeroResponse(
        task = title?.let {
            StarterWorkTaskProposalResponse(
                id = UUID.randomUUID(),
                sourceId = "src",
                title = it,
                summary = null,
                rationale = null,
                sourceUrl = null,
                competencyKeys = emptyList(),
                status = ProposalStatus.LIVE,
                taskZeroEligible = true,
            )
        },
        assignedAt = title?.let { Instant.EPOCH },
        noneAvailable = title == null,
        loopProven = false,
    )

    // -- specs / dispatch -------------------------------------------------------------------------

    @Test
    fun `exposes exactly the eleven action tools`() {
        assertThat(service.actionSpecs().map { it.name }).containsExactlyInAnyOrder(
            "flag_to_pm",
            "claim_task_zero",
            "open_orientation",
            "claim_goal",
            "request_attestation",
            "set_github_login",
            "record_assessment",
            "place_checklist",
            "amend_checklist",
            "place_link",
            "place_note",
        )
    }

    @Test
    fun `recognises action tools and rejects read tools`() {
        assertThat(service.isAction("claim_task_zero")).isTrue()
        assertThat(service.isAction("get_my_metrics")).isFalse()
    }

    // -- propose (must never mutate) --------------------------------------------------------------

    @Test
    fun `proposes claim Task 0 with its confirm label and no mutation`() {
        onOneProject()

        val outcome = service.propose(call("claim_task_zero"), userId)

        assertThat(outcome.proposal?.action).isEqualTo("claim_task_zero")
        assertThat(outcome.proposal?.label).isEqualTo("Start Task 0")
        assertThat(outcome.toolResult).contains("confirm")
        // Proposing must not touch the assignment.
        verify(exactly = 0) { taskZeroService.getForHire(any(), any()) }
    }

    @Test
    fun `carries the composed question through a flag-to-PM proposal`() {
        onOneProject()

        val outcome = service.propose(
            call("flag_to_pm", mapOf("question" to "How do we deploy?")),
            userId,
        )

        assertThat(outcome.proposal?.action).isEqualTo("flag_to_pm")
        assertThat(outcome.proposal?.question).isEqualTo("How do we deploy?")
    }

    @Test
    fun `does not propose flag-to-PM without a question`() {
        onOneProject()

        val outcome = service.propose(call("flag_to_pm"), userId)

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("No question")
    }

    @Test
    fun `does not propose an action when the hire is on no project`() {
        every { userApi.getUsersByIds(listOf(userId)) } returns listOf(userWith())

        val outcome = service.propose(call("claim_task_zero"), userId)

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("not on a project")
    }

    @Test
    fun `does not propose an action when the hire is on multiple projects`() {
        every { userApi.getUsersByIds(listOf(userId)) } returns listOf(
            userWith(oneProject, ProjectDto(UUID.randomUUID(), "Billing", null)),
        )

        val outcome = service.propose(call("open_orientation"), userId)

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("more than one project")
    }

    /**
     * The exemption that makes this action work at all. A GitHub login is a fact about a
     * *person*, not a project — and a hire on day one, not yet added to anything, is exactly who is
     * most likely to be setting one. Gating it would refuse them.
     */
    @Test
    fun `offers to save a username even when the hire is on no project`() {
        every { userApi.getUsersByIds(listOf(userId)) } returns listOf(userWith())

        val outcome = service.propose(call("set_github_login", mapOf("login" to "octocat")), userId)

        assertThat(outcome.proposal).isNotNull
        assertThat(outcome.proposal?.githubLogin).isEqualTo("octocat")
    }

    @Test
    fun `proposing a username changes nothing until it is confirmed`() {
        every { userApi.getUsersByIds(listOf(userId)) } returns listOf(userWith())

        service.propose(call("set_github_login", mapOf("login" to "octocat")), userId)

        verify(exactly = 0) { userApi.setGithubLogin(any(), any()) }
    }

    // -- record_assessment ------------------------------------------------------------------------

    /**
     * The same exemption as the username, on the same grounds: the competency ledger is global, so
     * a placement is a fact about the person. A hire on day one is exactly who the assessment is
     * for, and they are on nothing yet.
     */
    @Test
    fun `offers to record a placement even when the hire is on no project`() {
        every { userApi.getUsersByIds(listOf(userId)) } returns listOf(userWith())
        every { competencyPlacementService.labelFor("kotlin") } returns "Kotlin"

        val outcome = service.propose(
            call("record_assessment", mapOf("competency_key" to "kotlin", "level" to "intermediate")),
            userId,
        )

        assertThat(outcome.proposal?.competencyKey).isEqualTo("kotlin")
        assertThat(outcome.proposal?.level).isEqualTo("intermediate")
    }

    /**
     * The button says which skill and which level, because a hire confirming a judgement about
     * their own competence should not have to read back up the thread to find out what they are
     * agreeing to.
     */
    @Test
    fun `the confirm button names the competency and the level`() {
        every { userApi.getUsersByIds(listOf(userId)) } returns listOf(userWith())
        every { competencyPlacementService.labelFor("kotlin") } returns "Kotlin"

        val outcome = service.propose(
            call("record_assessment", mapOf("competency_key" to "kotlin", "level" to "advanced")),
            userId,
        )

        assertThat(outcome.proposal?.label).isEqualTo("Save: Kotlin — advanced")
    }

    @Test
    fun `proposing a placement writes nothing until it is confirmed`() {
        every { userApi.getUsersByIds(listOf(userId)) } returns listOf(userWith())
        every { competencyPlacementService.labelFor("kotlin") } returns "Kotlin"

        service.propose(
            call("record_assessment", mapOf("competency_key" to "kotlin", "level" to "beginner")),
            userId,
        )

        verify(exactly = 0) { competencyPlacementService.record(any(), any(), any()) }
    }

    /**
     * Caught while the mentor can still fix it. Discovered at confirm time this would be a hire
     * clicking a button and being told no, for a mistake they did not make.
     */
    @Test
    fun `a competency key nothing matches is corrected before the hire sees a button`() {
        every { competencyPlacementService.labelFor("astrology") } returns null

        val outcome = service.propose(
            call("record_assessment", mapOf("competency_key" to "astrology", "level" to "expert")),
            userId,
        )

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("get_competencies_to_assess")
    }

    @Test
    fun `a level off the scale is refused with the scale`() {
        every { competencyPlacementService.labelFor("kotlin") } returns "Kotlin"

        val outcome = service.propose(
            call("record_assessment", mapOf("competency_key" to "kotlin", "level" to "quite good")),
            userId,
        )

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("beginner", "expert")
    }

    @Test
    fun `confirming a placement records it and relays what the ledger said`() = runTest {
        asHire()
        every { competencyPlacementService.record(userId, "kotlin", ProficiencyLevel.INTERMEDIATE) } returns
            CompetencyPlacementService.PlacementOutcome(recorded = true, message = "Noted — “Kotlin”.")

        val result = service.perform(
            BuddyActionRequest(action = "record_assessment", competencyKey = "kotlin", level = "intermediate"),
            jwt,
        )

        assertThat(result.ok).isTrue()
        assertThat(result.message).contains("Kotlin")
    }

    @Test
    fun `confirming a placement needs no project`() = runTest {
        asHire()
        every { competencyPlacementService.record(any(), any(), any()) } returns
            CompetencyPlacementService.PlacementOutcome(recorded = true, message = "Noted.")

        val result = service.perform(
            BuddyActionRequest(action = "record_assessment", competencyKey = "kotlin", level = "beginner"),
            jwt,
        )

        assertThat(result.ok).isTrue()
        // The project is never resolved, so a hire on nothing is never turned away.
        verify(exactly = 0) { userApi.getUsersByIds(any()) }
    }

    /**
     * The scale is re-read from the word server-side, so a client cannot confirm a level the scale
     * does not have by editing the payload the proposal carried.
     */
    @Test
    fun `a confirmed level the scale does not have never reaches the ledger`() = runTest {
        val result = service.perform(
            BuddyActionRequest(action = "record_assessment", competencyKey = "kotlin", level = "godlike"),
            jwt,
        )

        assertThat(result.ok).isFalse()
        verify(exactly = 0) { competencyPlacementService.record(any(), any(), any()) }
    }

    @Test
    fun `a placement the ledger declines comes back as its own reason`() = runTest {
        asHire()
        every { competencyPlacementService.record(userId, "kotlin", ProficiencyLevel.EXPERT) } returns
            CompetencyPlacementService.PlacementOutcome(
                recorded = false,
                message = "Your own accepted work already proves “Kotlin”.",
            )

        val result = service.perform(
            BuddyActionRequest(action = "record_assessment", competencyKey = "kotlin", level = "expert"),
            jwt,
        )

        assertThat(result.ok).isFalse()
        assertThat(result.message).contains("already proves")
    }

    @Test
    fun `does not propose a username when none was given`() {
        onOneProject()

        val outcome = service.propose(call("set_github_login", mapOf("login" to "  ")), userId)

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("Ask the hire for their GitHub username")
    }

    @Test
    fun `confirming saves the username through the one writer and reports it back`() = runTest {
        asHire()
        every { userApi.setGithubLogin(userId, "OctoCat") } returns "octocat"

        val result = service.perform(BuddyActionRequest(action = "set_github_login", githubLogin = "OctoCat"), jwt)

        assertThat(result.ok).isTrue()
        // Reported as stored, not as typed: GithubLoginService lower-cases it, and telling the hire
        // something different from what was written is how the two drift in somebody's head.
        assertThat(result.message).contains("@octocat")
    }

    /**
     * A hire on no project must be able to confirm as well as be offered — the gate is skipped at
     * both ends or the button is one that always fails.
     */
    @Test
    fun `confirming a username needs no project`() = runTest {
        asHire()
        every { userApi.getUsersByIds(listOf(userId)) } returns listOf(userWith())
        every { userApi.setGithubLogin(userId, "octocat") } returns "octocat"

        val result = service.perform(BuddyActionRequest(action = "set_github_login", githubLogin = "octocat"), jwt)

        assertThat(result.ok).isTrue()
    }

    /**
     * The rules stay in GithubLoginService, and its sentence is what the hire reads — a 409 is
     * something they can act on, not a failed confirm with no explanation.
     */
    @Test
    fun `a username another hire already claims comes back as its own reason`() = runTest {
        asHire()
        every { userApi.setGithubLogin(userId, "octocat") } throws
            ResponseStatusException(HttpStatus.CONFLICT, "GitHub account 'octocat' is already linked to another user")

        val result = service.perform(BuddyActionRequest(action = "set_github_login", githubLogin = "octocat"), jwt)

        assertThat(result.ok).isFalse()
        assertThat(result.message).contains("already linked to another user")
    }

    @Test
    fun `carries the picked task through a claim-goal proposal`() {
        onOneProject()
        val taskId = UUID.randomUUID()

        val outcome = service.propose(call("claim_goal", mapOf("task_id" to taskId.toString())), userId)

        assertThat(outcome.proposal?.action).isEqualTo("claim_goal")
        assertThat(outcome.proposal?.taskId).isEqualTo(taskId)
        assertThat(outcome.toolResult).contains("confirm")
        // Proposing must not claim anything.
        verify(exactly = 0) { userGoalService.claimForMe(any(), any(), any()) }
    }

    @Test
    fun `does not propose claim-goal without a parseable task id`() {
        onOneProject()

        val outcome = service.propose(call("claim_goal", mapOf("task_id" to "not-a-uuid")), userId)

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("task_id")
    }

    // -- perform (the confirm round-trip) ---------------------------------------------------------

    @Test
    fun `claiming Task 0 assigns it and reports the title`() = runTest {
        asHire()
        onOneProject()
        every { taskZeroService.getForHire(userId, projectId) } returns taskZero("Fix the login redirect")

        val result = service.perform(BuddyActionRequest(action = "claim_task_zero"), jwt)

        assertThat(result.ok).isTrue()
        assertThat(result.message).contains("Fix the login redirect")
    }

    @Test
    fun `claiming Task 0 legibly reports when none is eligible`() = runTest {
        asHire()
        onOneProject()
        every { taskZeroService.getForHire(userId, projectId) } returns taskZero(null)

        val result = service.perform(BuddyActionRequest(action = "claim_task_zero"), jwt)

        assertThat(result.ok).isFalse()
        assertThat(result.message).contains("no eligible Task 0")
    }

    @Test
    fun `flagging to the PM escalates the question`() = runTest {
        asHire()
        onOneProject()

        val result = service.perform(
            BuddyActionRequest(action = "flag_to_pm", question = "How do we deploy?"),
            jwt,
        )

        assertThat(result.ok).isTrue()
        verify(exactly = 1) { knowledgeBaseService.escalate(authId, projectId, "How do we deploy?") }
    }

    @Test
    fun `flagging to the PM with no question never escalates`() = runTest {
        asHire()
        onOneProject()

        val result = service.perform(BuddyActionRequest(action = "flag_to_pm", question = "  "), jwt)

        assertThat(result.ok).isFalse()
        verify(exactly = 0) { knowledgeBaseService.escalate(any(), any(), any()) }
    }

    @Test
    fun `opening orientation reports the packet is ready`() = runTest {
        asHire()
        onOneProject()
        coEvery { taskOrientationService.getForHire(userId, projectId) } returns MyOrientationResponse(
            taskId = UUID.randomUUID(),
            taskTitle = "Fix the login redirect",
            taskUrl = null,
            packet = mockk(),
            reason = null,
        )

        val result = service.perform(BuddyActionRequest(action = "open_orientation"), jwt)

        assertThat(result.ok).isTrue()
        assertThat(result.message).contains("Fix the login redirect")
    }

    @Test
    fun `opening orientation relays the reason when there is no packet`() = runTest {
        asHire()
        onOneProject()
        coEvery { taskOrientationService.getForHire(userId, projectId) } returns MyOrientationResponse(
            taskId = null,
            taskTitle = null,
            taskUrl = null,
            packet = null,
            reason = "You have no current task yet",
        )

        val result = service.perform(BuddyActionRequest(action = "open_orientation"), jwt)

        assertThat(result.ok).isFalse()
        assertThat(result.message).contains("no current task")
    }

    @Test
    fun `an unrecognised action is a handled failure, not an error`() = runTest {
        asHire()

        val result = service.perform(BuddyActionRequest(action = "launch_rockets"), jwt)

        assertThat(result.ok).isFalse()
        assertThat(result.message).contains("isn't recognised")
    }

    @Test
    fun `claiming a goal reports what the hire now works toward`() = runTest {
        asHire()
        onOneProject()
        val taskId = UUID.randomUUID()
        every { userGoalService.claimForMe(authId, projectId, taskId) } returns GoalView(
            proposalId = taskId,
            title = "Fix the login redirect",
            summary = null,
            sourceUrl = null,
        )

        val result = service.perform(BuddyActionRequest(action = "claim_goal", taskId = taskId), jwt)

        assertThat(result.ok).isTrue()
        assertThat(result.message).contains("Fix the login redirect")
    }

    @Test
    fun `claiming a goal pins it to the board, so the next visit still knows about it`() = runTest {
        asHire()
        onOneProject()
        val taskId = UUID.randomUUID()
        every { userGoalService.claimForMe(authId, projectId, taskId) } returns GoalView(
            proposalId = taskId,
            title = "Fix the login redirect",
            summary = null,
            sourceUrl = null,
        )

        val result = service.perform(BuddyActionRequest(action = "claim_goal", taskId = taskId), jwt)

        // This conversation is gone by the next visit; the one instant we know for certain the
        // task is theirs is now, so the card is placed then rather than when the mentor thinks of it.
        verify { boardService.place(userId, projectId, BoardCardKind.CURRENT_TASK) }
        assertThat(result.message).contains("board")
    }

    @Test
    fun `claiming a goal relays why a rejected task cannot be claimed`() = runTest {
        asHire()
        onOneProject()
        val taskId = UUID.randomUUID()
        every { userGoalService.claimForMe(authId, projectId, taskId) } throws
            ResponseStatusException(HttpStatus.CONFLICT, "only a live task can be claimed as a goal")

        val result = service.perform(BuddyActionRequest(action = "claim_goal", taskId = taskId), jwt)

        assertThat(result.ok).isFalse()
        assertThat(result.message).contains("live")
    }

    @Test
    fun `a precondition failure downstream comes back as a legible reason`() = runTest {
        asHire()
        onOneProject()
        every { taskZeroService.getForHire(userId, projectId) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND, "You are not a member of that project.")

        val result = service.perform(BuddyActionRequest(action = "claim_task_zero"), jwt)

        assertThat(result.ok).isFalse()
        assertThat(result.message).contains("not a member")
    }

    // -- place_checklist ---------------------------------------------------------------------------

    private fun checklistCall(title: String, vararg items: String) = BuddyToolCallDto(
        id = "c0",
        name = "place_checklist",
        arguments = buildJsonObject {
            put("title", title)
            putJsonArray("items") { items.forEach { add(it) } }
        },
    )

    /**
     * The only action whose payload is content the model wrote, so the proposal has to carry the
     * lines themselves — a confirm that re-derived them could keep words the hire never read.
     */
    @Test
    fun `proposes a checklist carrying the lines it offered to keep`() {
        onOneProject()

        val outcome = service.propose(
            checklistCall("Getting started", "Find the component", "Run it locally"),
            userId,
        )

        assertThat(outcome.proposal?.action).isEqualTo("place_checklist")
        assertThat(outcome.proposal?.checklistTitle).isEqualTo("Getting started")
        assertThat(outcome.proposal?.checklistItems)
            .containsExactly("Find the component", "Run it locally")
        verify(exactly = 0) { boardService.addAuthoredCard(any(), any(), any()) }
    }

    /** One bullet is how a model emphasises a sentence; a card of it repeats the reply above it. */
    @Test
    fun `refuses to call a single line a checklist`() {
        onOneProject()

        val outcome = service.propose(checklistCall("Getting started", "Find the component"), userId)

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("at least 2")
    }

    @Test
    fun `confirming keeps the proposed lines as a card the hire owns`() = runTest {
        asHire()
        onOneProject()

        val result = service.perform(
            BuddyActionRequest(
                action = "place_checklist",
                checklistTitle = "Getting started",
                checklistItems = listOf("Find the component", "Run it locally"),
            ),
            jwt,
        )

        assertThat(result.ok).isTrue()
        val request = slot<AuthoredCardRequest>()
        verify { boardService.addAuthoredCard(userId, projectId, capture(request)) }
        val checklist = request.captured as ChecklistCardRequest
        assertThat(checklist.title).isEqualTo("Getting started")
        assertThat(checklist.items.map { it.text })
            .containsExactly("Find the component", "Run it locally")
        assertThat(checklist.items).allMatch { !it.done }
    }

    /** Free text from the client, so the caps are re-applied at confirm rather than trusted. */
    @Test
    fun `a confirm stripped of its list writes nothing`() = runTest {
        asHire()
        onOneProject()

        val result = service.perform(
            BuddyActionRequest(
                action = "place_checklist",
                checklistTitle = "Getting started",
                checklistItems = listOf("   ", ""),
            ),
            jwt,
        )

        assertThat(result.ok).isFalse()
        verify(exactly = 0) { boardService.addAuthoredCard(any(), any(), any()) }
    }

    // -- amend_checklist / place_link / place_note -------------------------------------------------

    /**
     * Only the new lines cross the wire. The mentor is never handed the whole list to send back,
     * which is what makes it impossible for it to reword or drop one on the way — the guarantee
     * lives in `BoardService.appendChecklistItems`, and this is the half that keeps it honest.
     */
    @Test
    fun `proposes an amendment carrying the card and only the new lines`() {
        onOneProject()
        val cardId = UUID.randomUUID()

        val outcome = service.propose(
            BuddyToolCallDto(
                id = "c0",
                name = "amend_checklist",
                arguments = buildJsonObject {
                    put("card_id", cardId.toString())
                    putJsonArray("items") { add("Write the test"); add("Open a draft PR") }
                },
            ),
            userId,
        )

        assertThat(outcome.proposal?.cardId).isEqualTo(cardId)
        assertThat(outcome.proposal?.checklistItems).containsExactly("Write the test", "Open a draft PR")
        verify(exactly = 0) { boardService.appendChecklistItems(any(), any(), any()) }
    }

    @Test
    fun `does not propose an amendment without a card to amend`() {
        onOneProject()

        val outcome = service.propose(
            BuddyToolCallDto(
                id = "c0",
                name = "amend_checklist",
                arguments = buildJsonObject { putJsonArray("items") { add("Write the test") } },
            ),
            userId,
        )

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("read_board")
    }

    @Test
    fun `confirming an amendment appends and says nothing else changed`() = runTest {
        asHire()
        onOneProject()
        val cardId = UUID.randomUUID()

        val result = service.perform(
            BuddyActionRequest(
                action = "amend_checklist",
                cardId = cardId,
                checklistItems = listOf("Write the test"),
            ),
            jwt,
        )

        assertThat(result.ok).isTrue()
        verify { boardService.appendChecklistItems(userId, cardId, listOf("Write the test")) }
    }

    /** A card that is not theirs, or not a checklist: a sentence, not a failed confirm. */
    @Test
    fun `an amendment the board refuses comes back as its own reason`() = runTest {
        asHire()
        onOneProject()
        val cardId = UUID.randomUUID()
        every { boardService.appendChecklistItems(any(), any(), any()) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND, "No such card on your board")

        val result = service.perform(
            BuddyActionRequest(
                action = "amend_checklist",
                cardId = cardId,
                checklistItems = listOf("Write the test"),
            ),
            jwt,
        )

        assertThat(result.ok).isFalse()
        assertThat(result.message).contains("No such card")
    }

    /** A card is a promise that the address works, so only a real one may be offered. */
    @Test
    fun `refuses a link that is not an http address`() {
        onOneProject()

        val outcome = service.propose(
            BuddyToolCallDto(
                id = "c0",
                name = "place_link",
                arguments = buildJsonObject {
                    put("url", "javascript:alert(1)")
                    put("label", "The runbook")
                },
            ),
            userId,
        )

        assertThat(outcome.proposal).isNull()
    }

    @Test
    fun `confirming a link keeps it as a card the hire owns`() = runTest {
        asHire()
        onOneProject()

        val result = service.perform(
            BuddyActionRequest(
                action = "place_link",
                linkUrl = "https://example.test/runbook",
                linkLabel = "The deploy runbook",
            ),
            jwt,
        )

        assertThat(result.ok).isTrue()
        val request = slot<AuthoredCardRequest>()
        verify { boardService.addAuthoredCard(userId, projectId, capture(request)) }
        val link = request.captured as LinkCardRequest
        assertThat(link.url).isEqualTo("https://example.test/runbook")
        assertThat(link.label).isEqualTo("The deploy runbook")
    }

    /** Every reply already carries a button that keeps the whole answer. */
    @Test
    fun `refuses a note too short to be worth its own card`() {
        onOneProject()

        val outcome = service.propose(
            BuddyToolCallDto(
                id = "c0",
                name = "place_note",
                arguments = buildJsonObject { put("text", "Deploys happen on Thursdays.") },
            ),
            userId,
        )

        assertThat(outcome.proposal).isNull()
    }

    @Test
    fun `confirming a note keeps its words as the hire's card`() = runTest {
        asHire()
        onOneProject()
        val text = "Deploys run on Thursdays, behind a feature flag that the release lead flips " +
            "once the smoke tests are green. Nobody deploys on a Friday."

        val result = service.perform(
            BuddyActionRequest(action = "place_note", noteText = text),
            jwt,
        )

        assertThat(result.ok).isTrue()
        val request = slot<AuthoredCardRequest>()
        verify { boardService.addAuthoredCard(userId, projectId, capture(request)) }
        assertThat((request.captured as NoteCardRequest).text).isEqualTo(text)
    }
}
