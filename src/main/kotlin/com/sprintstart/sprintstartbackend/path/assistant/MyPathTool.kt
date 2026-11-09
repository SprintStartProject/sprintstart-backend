package com.sprintstart.sprintstartbackend.path.assistant

import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.HireSnapshotOrder
import com.sprintstart.sprintstartbackend.assistant.external.HireTool
import com.sprintstart.sprintstartbackend.assistant.external.HireToolContext
import com.sprintstart.sprintstartbackend.assistant.external.HireToolOrder
import com.sprintstart.sprintstartbackend.assistant.external.HireToolSuggestion
import com.sprintstart.sprintstartbackend.path.assistant.MyPathCopy.NO_PATH
import com.sprintstart.sprintstartbackend.path.assistant.MyPathCopy.READ_MY_PATH_SPEC
import com.sprintstart.sprintstartbackend.path.external.HirePathApi
import com.sprintstart.sprintstartbackend.path.external.enums.QuestionStatus
import com.sprintstart.sprintstartbackend.path.model.response.phase.GetOnboardingPhaseForUserResponse
import com.sprintstart.sprintstartbackend.path.model.response.question.GetOnboardingQuestionForUserResponse
import com.sprintstart.sprintstartbackend.path.model.response.step.GetOnboardingStepsResponse
import com.sprintstart.sprintstartbackend.path.model.response.task.GetOnboardingTaskResponse
import com.sprintstart.sprintstartbackend.shared.ids.CheckQuestionId
import com.sprintstart.sprintstartbackend.shared.ids.PhaseId
import com.sprintstart.sprintstartbackend.shared.ids.StepId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The buddy's path tool: the onboarding curriculum the hire is actually walking.
 *
 * Until this existed the mentor could not see the plan it was supposed to be mentoring. It knew
 * about arrival steps, pull requests, competencies and the board -- everything *around* the
 * onboarding path -- and nothing about the phases, steps and knowledge questions the hire's PM
 * prescribed. So "what should I do next" was answered out of the work pool while the hire sat on a
 * page telling them something else: two plans, and nothing saying which one was the plan.
 *
 * The division of labour this establishes, and which the tool descriptions restate where the model
 * will read them:
 *
 * - The **blueprint** is the PM's. The buddy never touches it; a mentor that could rewrite the
 *   curriculum is a mentor whose team stops trusting the curriculum.
 * - The **hire's copy** is the hire's, and the buddy may propose changes to it -- see
 *   the Buddy's path actions, where every one of them waits for a button.
 * - **Reading really is free of consequence here**, unlike the Buddy's board read,
 *   which brings a board's baseline cards up to date by looking. Nothing is created by asking.
 *
 * ### What this deliberately does not do
 *
 * It does not read the whole path out. Full detail for the phase the hire is standing in, titles
 * only for what is ahead -- a mentor that recites sixteen phases has produced a table of contents,
 * and the hire already has one: it is the page they are looking at. That also bounds the prompt,
 * which a sixteen-phase path with steps in every phase otherwise would not.
 *
 * It does not say which answer to a knowledge question is correct, because it is not told: the
 * hire-facing response never carries the correct option. The tutoring this enables is explaining the
 * material, never handing over the answer, and the surest way to keep it that way is that the
 * mentor cannot know it either.
 */
@Component
@Order(HireToolOrder.PATH)
@Suppress("TooManyFunctions")
class MyPathTool(
    private val hirePathApi: HirePathApi,
) : HireTool {
    override val spec = READ_MY_PATH_SPEC

    override val snapshotPosition = HireSnapshotOrder.PATH

    /** Asks where they are rather than for the plan: the plan is a page they already have. */
    override val suggestion = HireToolSuggestion(
        label = "Where am I on my path?",
        question = "Where am I in my onboarding path, and what should I do next?",
    )

    /**
     * Mounted only for a hire who has a path.
     *
     * "Absent, never empty", the same rule the arrival and pull-request tools follow: a tool whose
     * only possible answer is "there is no path" is one the mentor will raise the path with anyway.
     * Asked as a row count rather than as a read -- see [OnboardingPathService.hasPath].
     */
    override fun isMountedFor(hire: UserId): Boolean = hirePathApi.hasPath(hire)

    override fun execute(call: BuddyToolCallDto, context: HireToolContext): String = execute(context.hire.value)

    override fun snapshotFor(hire: UserId): String? = snapshotFor(hire.value)

    /**
     * Whether this hire has a path at all.
     *
     * Exposed so that the Buddy's path actions are gated on the same question this gates
     * its read tool on, answered by the same call. Two components deciding separately whether a path
     * exists is how a mentor ends up holding an action for a plan its read tool says is not there.
     */
    fun hasPath(userId: UUID): Boolean = hirePathApi.hasPath(UserId(userId))

    /**
     * What the hire's path says right now, as plain text for the model.
     *
     * Every branch returns a sentence, including the ones where there is nothing to report. A tool
     * that answers with silence is a tool whose result the model fills in for itself.
     */
    fun execute(userId: UUID): String {
        val path = hirePathApi.pathOf(UserId(userId)) ?: return NO_PATH
        val phases = path.phases.sortedBy { it.position }
        if (phases.isEmpty()) return noVisiblePhasesReading(path)

        return when (val where = PathStanding.of(phases)) {
            PathStanding.Finished -> finishedReading(path, phases)
            is PathStanding.Choosing -> choosingReading(path, phases, where.phases)
            is PathStanding.In -> inPhaseReading(
                path,
                where.phase,
                checklistsOf(where.phase),
                lastWrongAnswers(userId, where.phase),
            )
        }
    }

    /**
     * The checklist of every step in [phase] the hire could still finish, keyed by step id.
     *
     * Read once per call and shared, because two sections need it: the checklist of the step they
     * are on, and the steps whose checklist is already done. Locked and finished steps are left out
     * -- neither can be finished now, so neither checklist is anything to talk about -- and so is a
     * locked phase, where nothing can.
     */
    private fun checklistsOf(phase: GetOnboardingPhaseForUserResponse): Map<UUID, List<GetOnboardingTaskResponse>> {
        if (phase.locked) return emptyMap()
        return phase.steps
            .filter { it.isFinishable() }
            .associate { step ->
                step.id to hirePathApi.tasksOf(StepId(step.id)).sortedBy { it.position }
            }
    }

    /**
     * The path in one or two sentences, for the opening greeting to ground itself in, or null when
     * the hire has no path.
     *
     * Written for the greeting rather than reusing [execute]'s text, on the same reasoning the
     * competency greeting line follows: [execute] is addressed to a reasoner holding tools, and
     * telling the opener to "offer add_path_step" would put a tool name in front of the hire.
     * Absent entirely when there is no path, so a greeting can never open by discussing one.
     */
    fun snapshotFor(userId: UUID): String? {
        val path = hirePathApi.pathOf(UserId(userId)) ?: return null
        val phases = path.phases.sortedBy { it.position }
        if (phases.isEmpty()) return noVisiblePhasesSnapshot(path)

        return buildString {
            appendLine("Onboarding path:")
            when (val where = PathStanding.of(phases)) {
                PathStanding.Finished -> appendLine(finishedSnapshotLine(path))
                is PathStanding.Choosing -> appendLine(choosingSnapshotLine(where.phases))
                is PathStanding.In -> appendInPhaseSnapshot(phases, where.phase, checklistsOf(where.phase))
            }
            val empty = path.generationIssues
            if (empty.isNotEmpty()) {
                append(
                    "${empty.size} of their phases came back with nothing in them, so there is " +
                        "nothing in those to do.",
                )
            }
        }.trim()
    }

    /**
     * One phase of the hire's own path by id, or null when there is no such phase of theirs.
     *
     * Resolved *through the hire's own path*, which is what makes it the authorization check as
     * well as the lookup: an id the mentor picked up from anywhere else is simply not found here, so
     * a proposal can never be aimed at somebody else's onboarding. [findStep] and [findQuestion]
     * are the same idea for the other two kinds of node.
     *
     * Used by the Buddy's path actions to check a proposal *before* the hire sees a button, and to put
     * the real title on it. A button that names the thing it will change is the last chance anybody
     * has to notice the mentor meant a different step.
     *
     * Includes the phases generation left empty, which the hire's own read hides: repairing one of
     * those with `add_path_step` is the case the empty-phase section of the read asks for, and a
     * lookup that could not see them refused exactly that.
     */
    fun findPhase(userId: UUID, phaseId: UUID): GetOnboardingPhaseForUserResponse? =
        hirePathApi.pathOf(UserId(userId))?.phases?.firstOrNull { it.id == phaseId }
            ?: hirePathApi.hiddenPhaseOf(UserId(userId), PhaseId(phaseId))

    /** Every phase of the hire's own path, or empty when they have none. */
    fun phasesOf(userId: UUID): List<GetOnboardingPhaseForUserResponse> =
        hirePathApi.pathOf(UserId(userId))?.phases.orEmpty()

    /** One step of the hire's own path by id, or null. See [findPhase] for why it resolves this way. */
    fun findStep(userId: UUID, stepId: UUID): GetOnboardingStepsResponse? =
        hirePathApi
            .pathOf(UserId(userId))
            ?.phases
            ?.flatMap { it.steps }
            ?.firstOrNull { it.id == stepId }

    /** One question of the hire's own path by id, or null. See [findPhase]. */
    fun findQuestion(userId: UUID, questionId: UUID): GetOnboardingQuestionForUserResponse? =
        hirePathApi
            .pathOf(UserId(userId))
            ?.phases
            ?.flatMap { it.questions }
            ?.firstOrNull { it.id == questionId }

    /**
     * The hire's own last answer to every question of [phase] they got wrong and have not passed since.
     *
     * The mentor used to know only *that* an answer was wrong, which left it "let's go through the
     * material again" and nothing sharper. The answer itself is the diagnosis: it is the one thing
     * that says which idea did not land. Never the correct answer -- that is not on the hire-facing
     * shape and stays out of the mentor's reach. A choice is given as the option labels they picked,
     * which they know are wrong already.
     */
    private fun lastWrongAnswers(userId: UUID, phase: GetOnboardingPhaseForUserResponse): Map<UUID, String> =
        phase.questions
            .filter { it.status == QuestionStatus.RETRY }
            .mapNotNull { question ->
                val last = hirePathApi
                    .lastAttempt(CheckQuestionId(question.id), UserId(userId))
                    ?.takeUnless { it.correct }
                    ?: return@mapNotNull null
                val labels = question.options.filter { it.id in last.selectedOptionIds }.map { it.label }
                val answer = last.textAnswer?.takeIf { it.isNotBlank() } ?: labels.joinToString("; ")
                answer.takeIf { it.isNotBlank() }?.let { question.id to it.take(ANSWER_SHOWN) }
            }.toMap()

    companion object {
        const val READ_MY_PATH = "get_my_onboarding_path"

        /** How many steps or questions of the current phase are named. */
        const val ITEMS_SHOWN = 12

        /** How much of a hire's own last answer is quoted -- enough to see the idea, not an essay. */
        const val ANSWER_SHOWN = 300

        /** Written out, so that no editing step has to survive an escape sequence intact. */
        const val NEWLINE = "\n"

        /**
         * Where each kind of path node lives in the app, for the links the mentor puts in its replies.
         *
         * Paths into the client rather than absolute URLs, because the backend does not know what
         * host the hire is on and guessing wrong produces a link that leaves the app. The frontend
         * renders an app-relative link as an in-app navigation, so "want to take the check?" arrives
         * as something clickable rather than as directions.
         *
         * All three land on the onboarding page rather than on a step's own page: the page opens the
         * item's phase, scrolls to the card and lights it up, and starting it stays the hire's click.
         * A link in a conversation is for finding something, not for doing it.
         *
         * They are a contract with the router, which is why they are named here and asserted in
         * `MyPathToolTest`: a route rename that forgets this file produces links that 404, and a
         * mentor has no way to notice.
         */
        const val STEP_LINK = "/onboarding?step="

        /** A step's own page, where its checklist, its skip request and its reason live. */
        const val STEP_PAGE_LINK = "/onboarding/"
        const val QUESTION_LINK = "/onboarding?question="
        const val PHASE_LINK = "/onboarding?phase="

        /** Titles are somebody else's text, so they are quoted rather than run into the sentence. */
        internal fun quoted(text: String): String = "“" + text + "”"
    }
}
