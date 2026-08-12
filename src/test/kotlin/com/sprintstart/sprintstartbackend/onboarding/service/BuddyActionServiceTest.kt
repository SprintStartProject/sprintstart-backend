package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.model.request.buddy.BuddyActionRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.goal.GoalView
import com.sprintstart.sprintstartbackend.onboarding.model.response.orientation.MyOrientationResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.starterwork.MyTaskZeroResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.starterwork.StarterWorkTaskProposalResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.verification.SubmitVerificationAttemptResponse
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
import java.time.Instant
import java.util.Optional
import java.util.UUID

class BuddyActionServiceTest {
    private val taskZeroService: TaskZeroService = mockk()
    private val taskOrientationService: TaskOrientationService = mockk()
    private val knowledgeBaseService: KnowledgeBaseService = mockk(relaxed = true)
    private val userGoalService: UserGoalService = mockk()
    private val verificationService: VerificationService = mockk()
    private val userApi: UserApi = mockk()
    private val attestationService: AttestationService = mockk()

    // Relaxed: claiming a goal also pins the task to the board, which is a side effect these tests
    // are not about -- the case that asserts it says so explicitly.
    private val boardService: BoardService = mockk(relaxed = true)
    private val service = BuddyActionService(
        taskZeroService,
        taskOrientationService,
        knowledgeBaseService,
        userGoalService,
        verificationService,
        userApi,
        attestationService,
        boardService,
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

    private fun attempt(moduleId: UUID, passed: Boolean, feedback: String, hint: String? = null) =
        SubmitVerificationAttemptResponse(
            attemptId = UUID.randomUUID(),
            moduleId = moduleId,
            passed = passed,
            score = if (passed) 1.0 else 0.0,
            feedback = feedback,
            hint = hint,
            attemptNo = 1,
        )

    // -- specs / dispatch -------------------------------------------------------------------------

    @Test
    fun `exposes exactly the seven action tools`() {
        assertThat(service.actionSpecs().map { it.name }).containsExactlyInAnyOrder(
            "flag_to_pm",
            "claim_task_zero",
            "open_orientation",
            "claim_goal",
            "submit_verification",
            "request_attestation",
            "set_github_login",
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
     * ⚠️ The exemption that makes this action work at all. A GitHub login is a fact about a
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

    @Test
    fun `carries module and answer through a submit-verification proposal`() {
        onOneProject()
        val moduleId = UUID.randomUUID()

        val outcome = service.propose(
            call("submit_verification", mapOf("module_id" to moduleId.toString(), "answer" to "42")),
            userId,
        )

        assertThat(outcome.proposal?.action).isEqualTo("submit_verification")
        assertThat(outcome.proposal?.moduleId).isEqualTo(moduleId)
        assertThat(outcome.proposal?.answer).isEqualTo("42")
        coVerify(exactly = 0) { verificationService.submitModuleAttemptForMe(any(), any(), any()) }
    }

    @Test
    fun `does not propose submit-verification without an answer`() {
        onOneProject()

        val outcome = service.propose(
            call("submit_verification", mapOf("module_id" to UUID.randomUUID().toString())),
            userId,
        )

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("No answer")
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
    fun `submitting a passing verification answer relays the pass`() = runTest {
        asHire()
        onOneProject()
        val moduleId = UUID.randomUUID()
        coEvery { verificationService.submitModuleAttemptForMe(authId, moduleId, any()) } returns
            attempt(moduleId, passed = true, feedback = "The PR satisfies the rubric.")

        val result = service.perform(
            BuddyActionRequest(action = "submit_verification", moduleId = moduleId, answer = "42"),
            jwt,
        )

        assertThat(result.ok).isTrue()
        assertThat(result.message).contains("Passed")
    }

    @Test
    fun `submitting a failing verification answer relays the feedback and hint`() = runTest {
        asHire()
        onOneProject()
        val moduleId = UUID.randomUUID()
        coEvery { verificationService.submitModuleAttemptForMe(authId, moduleId, any()) } returns
            attempt(moduleId, passed = false, feedback = "CI is failing.", hint = "Fix the red check first.")

        val result = service.perform(
            BuddyActionRequest(action = "submit_verification", moduleId = moduleId, answer = "42"),
            jwt,
        )

        assertThat(result.ok).isFalse()
        assertThat(result.message).contains("CI is failing")
        assertThat(result.message).contains("Fix the red check first")
    }

    @Test
    fun `submitting verification without its payload is a handled failure`() = runTest {
        asHire()
        onOneProject()

        val result = service.perform(BuddyActionRequest(action = "submit_verification"), jwt)

        assertThat(result.ok).isFalse()
        coVerify(exactly = 0) { verificationService.submitModuleAttemptForMe(any(), any(), any()) }
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
}
