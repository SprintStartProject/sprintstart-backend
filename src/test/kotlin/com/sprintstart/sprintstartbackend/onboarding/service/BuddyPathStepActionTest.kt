package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.onboarding.external.enums.QuestionStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.SkipStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepOrigin
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.model.request.buddy.BuddyActionRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.skip.CreateOnboardingSkipRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.step.CreateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhaseForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.GetOnboardingQuestionForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.skip.CreateOnboardingSkipResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.skip.GetOnboardingStepSkipResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.CreateOnboardingStepResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.GetOnboardingStepsResponse
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant
import java.util.Optional
import java.util.UUID

/**
 * The two path actions that change what is *on* the path rather than ticking something off it:
 * asking the PM to skip a step, and adding one. Split from [BuddyPathActionTest] for size; the same
 * rules hold -- nothing written before a confirm, every precondition checked before the button, and
 * every refusal addressed to the mentor.
 */
class BuddyPathStepActionTest {
    private val buddyPathTools: BuddyPathTools = mockk()
    private val onboardingStepService: OnboardingStepService = mockk()
    private val onboardingStepPlacementService: OnboardingStepPlacementService = mockk()
    private val onboardingTaskService: OnboardingTaskService = mockk()
    private val questionAttemptService: QuestionAttemptService = mockk()
    private val onboardingSkipService: OnboardingSkipService = mockk()
    private val userApi: UserApi = mockk()

    // The real path component behind a real action service: these cases are about the path actions
    // *and* about BuddyActionService routing them around the project gate, and mocking the component
    // would test the routing against nothing.
    private val pathActions = BuddyPathActions(
        buddyPathTools = buddyPathTools,
        onboardingStepService = onboardingStepService,
        onboardingStepPlacementService = onboardingStepPlacementService,
        onboardingTaskService = onboardingTaskService,
        questionAttemptService = questionAttemptService,
        onboardingSkipService = onboardingSkipService,
        userApi = userApi,
    )

    private val service = BuddyActionService(
        taskZeroService = mockk(relaxed = true),
        taskOrientationService = mockk(relaxed = true),
        knowledgeBaseService = mockk(relaxed = true),
        userGoalService = mockk(relaxed = true),
        userApi = userApi,
        attestationService = mockk(relaxed = true),
        boardService = mockk(relaxed = true),
        competencyPlacementService = mockk(relaxed = true),
        buddyPathActions = pathActions,
    )

    private val userId = UUID.randomUUID()
    private val authId = "auth|hire"
    private val jwt: Jwt = mockk<Jwt>().also { every { it.subject } returns authId }

    // -- request_skip -----------------------------------------------------------------------------

    @Test
    fun `a skip request without a reason sends the mentor back to ask why`() {
        // The PM decides on the reason; a request with none is one that sits.
        val step = step("Set up the VPN", StepStatus.WAITING)
        every { buddyPathTools.findStep(userId, step.id) } returns step

        val outcome = service.propose(call("request_skip", "step_id" to step.id.toString()), userId)

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("ask why they want to skip it")
    }

    @Test
    fun `a step already waiting on a skip decision cannot get a second request`() {
        val step = step("Set up the VPN", StepStatus.WAITING).copy(skip = skip(accepted = null))
        every { buddyPathTools.findStep(userId, step.id) } returns step

        val outcome = service.propose(
            call("request_skip", "step_id" to step.id.toString(), "reason" to "I already have access."),
            userId,
        )

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("has not decided yet")
        // Where they can change it instead, since a second request is not possible.
        assertThat(outcome.toolResult).contains("/onboarding/${step.id}")
    }

    @Test
    fun `the skip proposal carries the reason, and proposing sends nothing`() {
        val step = step("Set up the VPN", StepStatus.WAITING)
        every { buddyPathTools.findStep(userId, step.id) } returns step

        val outcome = service.propose(
            call("request_skip", "step_id" to step.id.toString(), "reason" to "I already have VPN access."),
            userId,
        )

        assertThat(outcome.proposal?.label).isEqualTo("Ask your PM to skip “Set up the VPN”")
        assertThat(outcome.proposal?.reason).isEqualTo("I already have VPN access.")
        assertThat(outcome.toolResult).contains("Do not promise it will be accepted")
        verify(exactly = 0) { onboardingSkipService.createOnboardingSkipForMe(any(), any(), any()) }
    }

    @Test
    fun `asking again after a decline puts the PM's comment in front of the mentor`() {
        val step = step("Set up the VPN", StepStatus.WAITING)
            .copy(skip = skip(accepted = false, reviewComment = "Everyone needs the company VPN."))
        every { buddyPathTools.findStep(userId, step.id) } returns step

        val outcome = service.propose(
            call("request_skip", "step_id" to step.id.toString(), "reason" to "I work on-site only."),
            userId,
        )

        assertThat(outcome.proposal).isNotNull()
        assertThat(outcome.toolResult).contains("“Everyone needs the company VPN.”")
        assertThat(outcome.toolResult).contains("addresses that")
    }

    @Test
    fun `a confirmed skip request goes through the hire's own skip route`() = runTest {
        val step = step("Set up the VPN", StepStatus.WAITING)
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { buddyPathTools.findStep(userId, step.id) } returns step
        val sent = slot<CreateOnboardingSkipRequest>()
        every { onboardingSkipService.createOnboardingSkipForMe(authId, step.id, capture(sent)) } returns
            CreateOnboardingSkipResponse(
                id = UUID.randomUUID(),
                stepId = step.id,
                status = SkipStatus.PENDING,
                reason = "I already have VPN access.",
                createdAt = Instant.EPOCH,
            )

        val result = service.perform(
            BuddyActionRequest(action = "request_skip", stepId = step.id, reason = "I already have VPN access."),
            jwt,
        )

        assertThat(result.ok).isTrue()
        assertThat(sent.captured.reason).isEqualTo("I already have VPN access.")
        assertThat(result.message).contains("your PM will decide")
    }

    @Test
    fun `completing a step with a pending skip says on the button that it withdraws the request`() {
        // The completion route drops a pending skip. Allowed, but never silently.
        val step = step("Set up the VPN", StepStatus.IN_PROGRESS).copy(skip = skip(accepted = null))
        every { buddyPathTools.findStep(userId, step.id) } returns step

        val outcome = service.propose(call("complete_step", "step_id" to step.id.toString()), userId)

        assertThat(outcome.proposal?.label).contains("withdraws your skip request")
        assertThat(outcome.toolResult).contains("finishing it withdraws that request")
    }

    // -- add_path_step ----------------------------------------------------------------------------

    @Test
    fun `a step with no description is refused, because a title alone is a guess`() {
        val phase = phase("Deployment")
        every { buddyPathTools.findPhase(userId, phase.id) } returns phase

        val outcome = service.propose(
            call("add_path_step", "phase_id" to phase.id.toString(), "title" to "Learn the deploy"),
            userId,
        )

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("has to guess at")
    }

    @Test
    fun `a step already on the path is not added a second time`() {
        val phase = phase("Setup", steps = listOf(step("Clone the repository", StepStatus.WAITING)))
        every { buddyPathTools.findPhase(userId, phase.id) } returns phase

        val outcome = service.propose(
            call(
                "add_path_step",
                "phase_id" to phase.id.toString(),
                "title" to "clone the REPOSITORY",
                "description" to "Get the code onto your machine.",
            ),
            userId,
        )

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("already a step")
    }

    @Test
    fun `the proposal carries the phase, the title and the description`() {
        val phase = phase("Deployment")
        every { buddyPathTools.findPhase(userId, phase.id) } returns phase

        val outcome = service.propose(
            call(
                "add_path_step",
                "phase_id" to phase.id.toString(),
                "title" to "Walk through a release",
                "description" to "Sit with whoever cuts the next release.",
            ),
            userId,
        )

        assertThat(outcome.proposal?.label).isEqualTo("Add “Walk through a release” to your path")
        assertThat(outcome.proposal?.phaseId).isEqualTo(phase.id)
        assertThat(outcome.proposal?.description).isEqualTo("Sit with whoever cuts the next release.")
        // The line that makes this safe to offer at all, stated where the model reads it.
        assertThat(outcome.toolResult).contains("their PM's blueprint is untouched")
    }

    @Test
    fun `a confirmed step with no placement lands at the end of the phase, as a task`() = runTest {
        val phase = phase("Deployment", steps = listOf(step("Read the runbook", StepStatus.FINISHED)))
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { buddyPathTools.findPhase(userId, phase.id) } returns phase
        val created = slot<CreateOnboardingStepRequest>()
        every {
            onboardingStepPlacementService.createConnectedStepForMe(
                authId,
                phase.id,
                capture(created),
                // The origin, asserted by being the only stub that matches: a step the buddy added
                // used to arrive labelled "Custom step by PM" -- something the hire's team requires
                // -- when it was something they agreed to in a chat.
                StepOrigin.BUDDY,
                emptySet(),
                emptySet(),
            )
        } returns createdStep("Walk through a release")

        val result = service.perform(
            BuddyActionRequest(
                action = "add_path_step",
                phaseId = phase.id,
                title = "Walk through a release",
                description = "Sit with whoever cuts the next release.",
            ),
            jwt,
        )

        assertThat(result.ok).isTrue()
        assertThat(created.captured.position).isEqualTo(1)
        assertThat(created.captured.type).isEqualTo(StepType.TASK)
        // No expected outcome: that is a promise about what the step leaves somebody able to do,
        // and the mentor is not in a position to make one.
        assertThat(created.captured.expectedOutcome).isEmpty()
        assertThat(result.message).contains("it is yours")
    }

    @Test
    fun `an unconnected step is proposed with a warning that it is never what comes next`() {
        // Testing found a refresher the hire asked to do next floating unconnected in the graph.
        val phase = phase("Deployment")
        every { buddyPathTools.findPhase(userId, phase.id) } returns phase

        val outcome = service.propose(
            call(
                "add_path_step",
                "phase_id" to phase.id.toString(),
                "title" to "Refresher",
                "description" to "Reread the runbook.",
            ),
            userId,
        )

        assertThat(outcome.toolResult).contains("is never what comes next")
    }

    @Test
    fun `a step placed as the next thing goes between what it waits on and what it unlocks`() {
        val current = step("Read the runbook", StepStatus.IN_PROGRESS).copy(position = 0)
        val later = step("Deploy to staging", StepStatus.WAITING, locked = true)
            .copy(position = 1, blockerIds = setOf(current.id))
        val phase = phase("Deployment", steps = listOf(current, later))
        every { buddyPathTools.findPhase(userId, phase.id) } returns phase

        val outcome = service.propose(
            placedCall(phase.id, "Walk through a release", waitsOn = listOf(current.id), unlocks = listOf(later.id)),
            userId,
        )

        assertThat(outcome.proposal?.label)
            .isEqualTo("Add “Walk through a release” after “Read the runbook”, before “Deploy to staging”")
        assertThat(outcome.proposal?.waitsOnIds).containsExactly(current.id)
        assertThat(outcome.proposal?.unlocksIds).containsExactly(later.id)
        assertThat(outcome.toolResult).contains("what it unlocks now waits on it")
    }

    @Test
    fun `a step given only what it unlocks takes over that item's way in`() {
        // Testing: the hire finished #1 and asked for a step next; the mentor passed only unlocks, and
        // the new step was locked-in behind nothing -- open from the start, no edge into it.
        val current = step("Read the runbook", StepStatus.FINISHED)
        val question = question("What does the runbook cover?", QuestionStatus.OPEN)
            .copy(blockerIds = setOf(current.id))
        val phase = phase("Deployment", steps = listOf(current)).copy(questions = listOf(question))
        every { buddyPathTools.findPhase(userId, phase.id) } returns phase

        val outcome = service.propose(
            placedCall(phase.id, "Refresher", waitsOn = emptyList(), unlocks = listOf(question.id)),
            userId,
        )

        assertThat(outcome.proposal?.waitsOnIds).containsExactly(current.id)
        assertThat(outcome.proposal?.label).contains("after “Read the runbook”")
        assertThat(outcome.toolResult).contains("You passed no waits_on")
    }

    @Test
    fun `a step in front of an item nothing leads into opens after where the hire is`() {
        // The question had no edge in, so there is nothing to take over: "as the next thing" means
        // after the step they just finished.
        val finished = step("Read the runbook", StepStatus.FINISHED).copy(completedAt = Instant.EPOCH)
        val question = question("What does the runbook cover?", QuestionStatus.OPEN)
        val phase = phase("Deployment", steps = listOf(finished)).copy(questions = listOf(question))
        every { buddyPathTools.findPhase(userId, phase.id) } returns phase

        val outcome = service.propose(
            placedCall(phase.id, "Refresher", waitsOn = emptyList(), unlocks = listOf(question.id)),
            userId,
        )

        assertThat(outcome.proposal?.waitsOnIds).containsExactly(finished.id)
        assertThat(outcome.proposal?.unlocksIds).containsExactly(question.id)
    }

    @Test
    fun `a placement in front of something already done is refused`() {
        val done = step("Read the runbook", StepStatus.FINISHED)
        val phase = phase("Deployment", steps = listOf(done))
        every { buddyPathTools.findPhase(userId, phase.id) } returns phase

        val outcome = service.propose(
            placedCall(phase.id, "Refresher", waitsOn = emptyList(), unlocks = listOf(done.id)),
            userId,
        )

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("already done")
    }

    @Test
    fun `a placement that would make a loop is refused before the button`() {
        // later waits on current; a new step that waits on later while unlocking current would make
        // current wait on itself, and nobody could ever finish the phase.
        val current = step("Read the runbook", StepStatus.WAITING)
        val later = step("Deploy to staging", StepStatus.WAITING).copy(blockerIds = setOf(current.id))
        val phase = phase("Deployment", steps = listOf(current, later))
        every { buddyPathTools.findPhase(userId, phase.id) } returns phase

        val outcome = service.propose(
            placedCall(phase.id, "Refresher", waitsOn = listOf(later.id), unlocks = listOf(current.id)),
            userId,
        )

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("loop")
    }

    @Test
    fun `a confirmed placed step lands next to what it waits on and is connected`() = runTest {
        val current = step("Read the runbook", StepStatus.IN_PROGRESS).copy(position = 0)
        val later = step("Deploy to staging", StepStatus.WAITING).copy(position = 1, blockerIds = setOf(current.id))
        val phase = phase("Deployment", steps = listOf(current, later))
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { buddyPathTools.findPhase(userId, phase.id) } returns phase
        val created = slot<CreateOnboardingStepRequest>()
        every {
            onboardingStepPlacementService.createConnectedStepForMe(
                authId,
                phase.id,
                capture(created),
                StepOrigin.BUDDY,
                setOf(current.id),
                setOf(later.id),
            )
        } returns createdStep("Walk through a release")

        val result = service.perform(
            BuddyActionRequest(
                action = "add_path_step",
                phaseId = phase.id,
                title = "Walk through a release",
                description = "Sit with whoever cuts the next release.",
                waitsOnIds = listOf(current.id),
                unlocksIds = listOf(later.id),
            ),
            jwt,
        )

        assertThat(result.ok).isTrue()
        assertThat(created.captured.position).isEqualTo(1)
    }

    // -- fixtures ---------------------------------------------------------------------------------

    private fun placedCall(phaseId: UUID, title: String, waitsOn: List<UUID>, unlocks: List<UUID>) =
        BuddyToolCallDto(
            id = "c0",
            name = "add_path_step",
            arguments = buildJsonObject {
                put("phase_id", phaseId.toString())
                put("title", title)
                put("description", "What doing it involves.")
                putJsonArray("waits_on") { waitsOn.forEach { add(it.toString()) } }
                putJsonArray("unlocks") { unlocks.forEach { add(it.toString()) } }
            },
        )

    private fun call(name: String, vararg args: Pair<String, String>) = BuddyToolCallDto(
        id = "c0",
        name = name,
        arguments = buildJsonObject { args.forEach { (k, v) -> put(k, v) } },
    )

    private fun phase(title: String, steps: List<GetOnboardingStepsResponse> = emptyList()) =
        GetOnboardingPhaseForUserResponse(
            id = UUID.randomUUID(),
            pathId = UUID.randomUUID(),
            position = 0,
            title = title,
            description = "",
            locked = false,
            steps = steps,
        )

    private fun question(text: String, status: QuestionStatus) = GetOnboardingQuestionForUserResponse(
        id = UUID.randomUUID(),
        phaseId = UUID.randomUUID(),
        position = 0,
        type = CheckQuestionType.SHORT_TEXT,
        question = text,
        options = emptyList(),
        status = status,
    )

    private fun step(title: String, status: StepStatus, locked: Boolean = false) =
        GetOnboardingStepsResponse(
            id = UUID.randomUUID(),
            phaseId = UUID.randomUUID(),
            position = 0,
            title = title,
            description = "",
            type = StepType.TASK,
            estimatedMinutes = 20,
            isAiAssisted = false,
            status = status,
            completedAt = null,
            skip = null,
            locked = locked,
        )

    private fun skip(accepted: Boolean?, reviewComment: String? = null) = GetOnboardingStepSkipResponse(
        id = UUID.randomUUID(),
        stepId = UUID.randomUUID(),
        reason = "I already know this.",
        accepted = accepted,
        reviewComment = reviewComment,
        reviewedAt = null,
    )

    private fun createdStep(title: String) = CreateOnboardingStepResponse(
        id = UUID.randomUUID(),
        phaseId = UUID.randomUUID(),
        position = 1,
        title = title,
        description = "",
        type = StepType.TASK,
        estimatedMinutes = 15,
        isAiAssisted = false,
        expectedOutcome = "",
        status = StepStatus.WAITING,
    )
}
