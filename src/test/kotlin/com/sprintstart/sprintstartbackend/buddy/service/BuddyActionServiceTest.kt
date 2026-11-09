package com.sprintstart.sprintstartbackend.buddy.service

import com.sprintstart.sprintstartbackend.arrival.assistant.RequestAttestationAction
import com.sprintstart.sprintstartbackend.arrival.service.AttestationService
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.HireActionRequest
import com.sprintstart.sprintstartbackend.board.assistant.boardActionsOver
import com.sprintstart.sprintstartbackend.board.service.BoardService
import com.sprintstart.sprintstartbackend.knowledge.assistant.FlagToPmAction
import com.sprintstart.sprintstartbackend.knowledge.service.HireKnowledgeService
import com.sprintstart.sprintstartbackend.knowledge.service.KnowledgeBaseService
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.starterwork.assistant.ClaimGoalAction
import com.sprintstart.sprintstartbackend.starterwork.assistant.OpenOrientationAction
import com.sprintstart.sprintstartbackend.starterwork.assistant.RecordAssessmentAction
import com.sprintstart.sprintstartbackend.starterwork.external.enums.GoalClaimant
import com.sprintstart.sprintstartbackend.starterwork.external.enums.ProficiencyLevel
import com.sprintstart.sprintstartbackend.starterwork.external.model.PlacementResult
import com.sprintstart.sprintstartbackend.starterwork.model.response.goal.GoalView
import com.sprintstart.sprintstartbackend.starterwork.model.response.orientation.MyOrientationResponse
import com.sprintstart.sprintstartbackend.starterwork.service.CompetencyPlacementService
import com.sprintstart.sprintstartbackend.starterwork.service.TaskOrientationService
import com.sprintstart.sprintstartbackend.starterwork.service.UserGoalService
import com.sprintstart.sprintstartbackend.starterwork.service.hireStarterWorkActionsOver
import com.sprintstart.sprintstartbackend.starterwork.service.hireStarterWorkOver
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.dto.ProjectDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID

class BuddyActionServiceTest {
    private val taskOrientationService: TaskOrientationService = mockk()
    private val knowledgeBaseService: KnowledgeBaseService = mockk(relaxed = true)
    private val userGoalService: UserGoalService = mockk()
    private val userApi: UserApi = mockk()
    private val attestationService: AttestationService = mockk()

    // Relaxed: claiming a goal also pins the task to the board, which is a side effect these tests
    // are not about -- the case that asserts it says so explicitly.
    private val boardService: BoardService = mockk(relaxed = true)
    private val competencyPlacementService: CompetencyPlacementService = mockk()

    private val hireStarterWork = hireStarterWorkOver(
        taskOrientationService = taskOrientationService,
        competencyPlacementService = competencyPlacementService,
    )
    private val hireStarterWorkActions = hireStarterWorkActionsOver(userGoalService, competencyPlacementService)

    // The starter-work actions are the real ones over the same mocks, so these cases still cover them.
    private val service = BuddyActionService(
        userApi,
        listOf(
            FlagToPmAction(HireKnowledgeService(knowledgeBaseService)),
            RequestAttestationAction(attestationService),
            SetGithubLoginAction(userApi),
            OpenOrientationAction(hireStarterWork),
            ClaimGoalAction(hireStarterWorkActions),
            RecordAssessmentAction(hireStarterWork, hireStarterWorkActions),
        ) + boardActionsOver(boardService),
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

    // -- specs / dispatch -------------------------------------------------------------------------

    @Test
    fun `exposes the project-scoped action tools`() {
        assertThat(service.actionSpecs(userId).map { it.name }).containsExactlyInAnyOrder(
            "flag_to_pm",
            "open_orientation",
            "claim_goal",
            "request_attestation",
            "set_github_login",
            "record_assessment",
            "place_checklist",
            "amend_checklist",
            "place_note",
            "tick_checklist_items",
            "reword_checklist_item",
            "place_link",
            "edit_note",
            "edit_link",
            "edit_checklist",
            "dismiss_cards",
            "reorder_cards",
        )
    }

    @Test
    fun `tells the mentor that a hire asking to flag something is reason enough`() {
        // "Last resort" alone had the mentor refuse an explicit "flag this to my PM".
        val flag = service.actionSpecs(userId).single { it.name == "flag_to_pm" }

        assertThat(flag.description).contains("the hire ASKS you to flag")
        assertThat(flag.description).contains("never decide for them that it is not a PM matter")
    }

    @Test
    fun `recognises action tools and rejects read tools`() {
        assertThat(service.isAction("open_orientation")).isTrue()
        assertThat(service.isAction("get_my_metrics")).isFalse()
    }

    // -- propose (must never mutate) --------------------------------------------------------------

    @Test
    fun `proposes opening the task packet with its confirm label and no work done`() {
        onOneProject()

        val outcome = service.propose(call("open_orientation"), userId)

        assertThat(outcome.proposal?.action).isEqualTo("open_orientation")
        assertThat(outcome.proposal?.label).isEqualTo("Open the task packet")
        assertThat(outcome.toolResult).contains("confirm")
        // Proposing must not assemble anything.
        coVerify(exactly = 0) { taskOrientationService.getForHire(any(), any()) }
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

        val outcome = service.propose(call("open_orientation"), userId)

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("not on a project")
    }

    /**
     * The board writes' reason lines come from a table rather than an exhaustive `when`, so the
     * compiler no longer points at a new one that was left out. This does instead: every board write
     * has to be able to say what it could not do for a hire with no project.
     */
    @Test
    fun `every board write can say what it cannot do for a hire on no project`() {
        every { userApi.getUsersByIds(listOf(userId)) } returns listOf(userWith())
        val boardWrites = service.actionSpecs(userId).map { it.name }.filter { name ->
            name.contains("checklist") ||
                name.contains("card") ||
                name.startsWith("place_") ||
                name.startsWith("edit_")
        }

        assertThat(boardWrites).hasSize(11)
        boardWrites.forEach { name ->
            val outcome = service.propose(call(name), userId)
            assertThat(outcome.proposal).isNull()
            assertThat(outcome.toolResult).contains("not on a project").contains("board")
        }
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
            PlacementResult(recorded = true, message = "Noted — “Kotlin”.")

        val result = service.perform(
            HireActionRequest(action = "record_assessment", competencyKey = "kotlin", level = "intermediate"),
            jwt,
        )

        assertThat(result.ok).isTrue()
        assertThat(result.message).contains("Kotlin")
    }

    @Test
    fun `confirming a placement needs no project`() = runTest {
        asHire()
        every { competencyPlacementService.record(any(), any(), any()) } returns
            PlacementResult(recorded = true, message = "Noted.")

        val result = service.perform(
            HireActionRequest(action = "record_assessment", competencyKey = "kotlin", level = "beginner"),
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
        asHire()
        val result = service.perform(
            HireActionRequest(action = "record_assessment", competencyKey = "kotlin", level = "godlike"),
            jwt,
        )

        assertThat(result.ok).isFalse()
        verify(exactly = 0) { competencyPlacementService.record(any(), any(), any()) }
    }

    @Test
    fun `a placement the ledger declines comes back as its own reason`() = runTest {
        asHire()
        every { competencyPlacementService.record(userId, "kotlin", ProficiencyLevel.EXPERT) } returns
            PlacementResult(
                recorded = false,
                message = "Your own accepted work already proves “Kotlin”.",
            )

        val result = service.perform(
            HireActionRequest(action = "record_assessment", competencyKey = "kotlin", level = "expert"),
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

        val result = service.perform(HireActionRequest(action = "set_github_login", githubLogin = "OctoCat"), jwt)

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

        val result = service.perform(HireActionRequest(action = "set_github_login", githubLogin = "octocat"), jwt)

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

        val result = service.perform(HireActionRequest(action = "set_github_login", githubLogin = "octocat"), jwt)

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
        verify(exactly = 0) { userGoalService.claimForMe(any(), any(), any(), any()) }
    }

    @Test
    fun `does not propose claim-goal without a parseable task id`() {
        onOneProject()

        val outcome = service.propose(call("claim_goal", mapOf("task_id" to "not-a-uuid")), userId)

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("task_id")
    }

    @Test
    fun `does not propose an attestation without a title`() {
        onOneProject()

        val outcome = service.propose(
            call("request_attestation", mapOf("title" to "  ", "attester_id" to UUID.randomUUID().toString())),
            userId,
        )

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("No title was provided")
    }

    @Test
    fun `does not propose an attestation without an attester`() {
        onOneProject()

        val outcome = service.propose(call("request_attestation", mapOf("title" to "Ran the retro")), userId)

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("get_teammates")
    }

    @Test
    fun `carries the work and the attester through an attestation proposal`() {
        onOneProject()
        val attesterId = UUID.randomUUID()

        val outcome = service.propose(
            call("request_attestation", mapOf("title" to " Ran the retro ", "attester_id" to attesterId.toString())),
            userId,
        )

        assertThat(outcome.proposal?.action).isEqualTo("request_attestation")
        assertThat(outcome.proposal?.title).isEqualTo("Ran the retro")
        assertThat(outcome.proposal?.attesterId).isEqualTo(attesterId.toString())
    }

    // -- perform (the confirm round-trip) ---------------------------------------------------------

    @Test
    fun `flagging to the PM escalates the question`() = runTest {
        asHire()
        onOneProject()

        val result = service.perform(
            HireActionRequest(action = "flag_to_pm", question = "How do we deploy?"),
            jwt,
        )

        assertThat(result.ok).isTrue()
        verify(exactly = 1) { knowledgeBaseService.escalate(authId, projectId, "How do we deploy?") }
    }

    @Test
    fun `confirming an attestation files it for the hire on their project`() = runTest {
        asHire()
        onOneProject()
        val attesterId = UUID.randomUUID()
        every {
            attestationService.requestAttestation(
                UserId(userId),
                ProjectId(projectId),
                "Ran the retro",
                UserId(attesterId),
            )
        } returns "Ran the retro"

        val result = service.perform(
            HireActionRequest(action = "request_attestation", title = "Ran the retro", attesterId = attesterId),
            jwt,
        )

        assertThat(result.ok).isTrue()
        assertThat(result.message).contains("“Ran the retro”")
    }

    @Test
    fun `an attestation the arrival rules refuse comes back as their sentence`() = runTest {
        asHire()
        onOneProject()
        every { attestationService.requestAttestation(any(), any(), any(), any()) } throws
            ResponseStatusException(HttpStatus.BAD_REQUEST, "You can't confirm your own work.")

        val result = service.perform(
            HireActionRequest(action = "request_attestation", title = "Ran the retro", attesterId = userId),
            jwt,
        )

        assertThat(result.ok).isFalse()
        assertThat(result.message).isEqualTo("You can't confirm your own work.")
    }

    @Test
    fun `an attestation confirm missing its attester files nothing`() = runTest {
        asHire()
        onOneProject()

        val result = service.perform(HireActionRequest(action = "request_attestation", title = "Ran the retro"), jwt)

        assertThat(result.ok).isFalse()
        verify(exactly = 0) { attestationService.requestAttestation(any(), any(), any(), any()) }
    }

    @Test
    fun `flagging to the PM with no question never escalates`() = runTest {
        asHire()
        onOneProject()

        val result = service.perform(HireActionRequest(action = "flag_to_pm", question = "  "), jwt)

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

        val result = service.perform(HireActionRequest(action = "open_orientation"), jwt)

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

        val result = service.perform(HireActionRequest(action = "open_orientation"), jwt)

        assertThat(result.ok).isFalse()
        assertThat(result.message).contains("no current task")
    }

    @Test
    fun `an unrecognised action is a handled failure, not an error`() = runTest {
        asHire()

        val result = service.perform(HireActionRequest(action = "launch_rockets"), jwt)

        assertThat(result.ok).isFalse()
        assertThat(result.message).contains("isn't recognised")
    }

    @Test
    fun `claiming a goal reports what the hire now works toward`() = runTest {
        asHire()
        onOneProject()
        val taskId = UUID.randomUUID()
        every { userGoalService.claimForMe(authId, projectId, taskId, GoalClaimant.BUDDY) } returns GoalView(
            proposalId = taskId,
            title = "Fix the login redirect",
            summary = null,
            sourceUrl = null,
        )

        val result = service.perform(HireActionRequest(action = "claim_goal", taskId = taskId), jwt)

        assertThat(result.ok).isTrue()
        assertThat(result.message).contains("Fix the login redirect")
    }

    @Test
    fun `claiming a goal tells the hire it is on their board`() = runTest {
        asHire()
        onOneProject()
        val taskId = UUID.randomUUID()
        every { userGoalService.claimForMe(authId, projectId, taskId, GoalClaimant.BUDDY) } returns GoalView(
            proposalId = taskId,
            title = "Fix the login redirect",
            summary = null,
            sourceUrl = null,
        )

        val result = service.perform(HireActionRequest(action = "claim_goal", taskId = taskId), jwt)

        // The pinning itself happens in `UserGoalService.claimForMe` (tested there); the buddy
        // only has to tell the hire where to find it.
        assertThat(result.message).contains("board")
    }

    @Test
    fun `claiming a goal relays why a rejected task cannot be claimed`() = runTest {
        asHire()
        onOneProject()
        val taskId = UUID.randomUUID()
        every { userGoalService.claimForMe(authId, projectId, taskId, GoalClaimant.BUDDY) } throws
            ResponseStatusException(HttpStatus.CONFLICT, "only a live task can be claimed as a goal")

        val result = service.perform(HireActionRequest(action = "claim_goal", taskId = taskId), jwt)

        assertThat(result.ok).isFalse()
        assertThat(result.message).contains("live")
    }

    @Test
    fun `a precondition failure downstream comes back as a legible reason`() = runTest {
        asHire()
        onOneProject()
        coEvery { taskOrientationService.getForHire(userId, projectId) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND, "You are not a member of that project.")

        val result = service.perform(HireActionRequest(action = "open_orientation"), jwt)

        assertThat(result.ok).isFalse()
        assertThat(result.message).contains("not a member")
    }
}
