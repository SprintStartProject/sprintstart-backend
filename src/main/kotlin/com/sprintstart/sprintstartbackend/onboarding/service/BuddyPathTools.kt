package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.QuestionStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhaseForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.GetOnboardingQuestionForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.GetOnboardingStepsResponse
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
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
 *   [BuddyActionService], where every one of them waits for a button.
 * - **Reading really is free of consequence here**, unlike [BuddyBoardTools.execute]'s board read,
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
// One function per section of what a path has to say, plus the two entry points. The count tracks
// the shape of the text, not a class doing unrelated things.
@Suppress("TooManyFunctions")
class BuddyPathTools(
    private val onboardingPathService: OnboardingPathService,
) {
    /**
     * The path tool, mounted only for a hire who has a path.
     *
     * "Absent, never empty", the same rule the arrival and pull-request tools follow: a tool whose
     * only possible answer is "there is no path" is one the mentor will raise the path with anyway.
     * Asked as a row count rather than as a read -- see [OnboardingPathService.hasPath].
     */
    fun toolSpecs(userId: UUID): List<BuddyToolSpecDto> =
        if (onboardingPathService.hasPath(userId)) listOf(READ_MY_PATH_SPEC) else emptyList()

    /** Whether [toolName] is this component's tool. */
    fun handles(toolName: String): Boolean = toolName == READ_MY_PATH

    /**
     * Whether this hire has a path at all.
     *
     * Exposed so that [BuddyActionService] gates its path actions on the same question this gates
     * its read tool on, answered by the same call. Two components deciding separately whether a path
     * exists is how a mentor ends up holding an action for a plan its read tool says is not there.
     */
    fun hasPath(userId: UUID): Boolean = onboardingPathService.hasPath(userId)

    /**
     * What the hire's path says right now, as plain text for the model.
     *
     * Every branch returns a sentence, including the ones where there is nothing to report. A tool
     * that answers with silence is a tool whose result the model fills in for itself.
     */
    fun execute(userId: UUID): String {
        val path = onboardingPathService.findPathForUserId(userId) ?: return NO_PATH
        val phases = path.phases.sortedBy { it.position }
        if (phases.isEmpty()) return NO_PHASES

        val currentIndex = phases.indexOfFirst { it.isOpen() }.takeIf { it >= 0 } ?: phases.lastIndex

        return buildString {
            appendLine(standing(phases, currentIndex))
            appendLine()
            appendCurrentPhase(phases[currentIndex], currentIndex, phases.size)
            appendNextItem(phases)
            appendAhead(phases, currentIndex)
            appendEmptyPhases(path)
            append(NEWLINE + CLOSING)
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
        val path = onboardingPathService.findPathForUserId(userId) ?: return null
        val phases = path.phases.sortedBy { it.position }
        if (phases.isEmpty()) {
            return "Onboarding path:\nTheir path has no phases in it, so there is nothing on it to do yet."
        }

        val currentIndex = phases.indexOfFirst { it.isOpen() }.takeIf { it >= 0 } ?: phases.lastIndex
        val current = phases[currentIndex]

        return buildString {
            appendLine("Onboarding path:")
            if (current.isOpen()) {
                appendLine("They are in phase ${currentIndex + 1} of ${phases.size}: ${quoted(current.title)}.")
                nextItem(phases)?.let { appendLine("The next thing waiting for them is ${it.plain}.") }
            } else {
                appendLine("They have finished every phase of their path.")
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
     * Used by [BuddyActionService] to check a proposal *before* the hire sees a button, and to put
     * the real title on it. A button that names the thing it will change is the last chance anybody
     * has to notice the mentor meant a different step.
     */
    fun findPhase(userId: UUID, phaseId: UUID): GetOnboardingPhaseForUserResponse? =
        onboardingPathService.findPathForUserId(userId)?.phases?.firstOrNull { it.id == phaseId }

    /** One step of the hire's own path by id, or null. See [findPhase] for why it resolves this way. */
    fun findStep(userId: UUID, stepId: UUID): GetOnboardingStepsResponse? =
        onboardingPathService
            .findPathForUserId(userId)
            ?.phases
            ?.flatMap { it.steps }
            ?.firstOrNull { it.id == stepId }

    /** One question of the hire's own path by id, or null. See [findPhase]. */
    fun findQuestion(userId: UUID, questionId: UUID): GetOnboardingQuestionForUserResponse? =
        onboardingPathService
            .findPathForUserId(userId)
            ?.phases
            ?.flatMap { it.questions }
            ?.firstOrNull { it.id == questionId }

    /** Where they are, and how much of the path is behind them. */
    private fun standing(phases: List<GetOnboardingPhaseForUserResponse>, currentIndex: Int): String {
        if (!phases[currentIndex].isOpen()) {
            return "The hire's onboarding path has ${phases.size} phases and every one of them is " +
                "finished. There is nothing left on it."
        }
        // Phases behind them, never a percentage. A path mixes steps they ticked, questions they
        // answered and phases that came back empty; one number over those is a figure the mentor
        // would repeat and nobody could act on -- the same rule the arrival tool states at length.
        val behind = if (currentIndex == 0) {
            "It is their first phase."
        } else {
            "The $currentIndex before it are behind them."
        }
        return "The hire's onboarding path has ${phases.size} phases. They are standing in phase " +
            "${currentIndex + 1}. $behind"
    }

    /** The phase they are in, in full: what it is for, its steps, and its questions. */
    private fun StringBuilder.appendCurrentPhase(
        phase: GetOnboardingPhaseForUserResponse,
        index: Int,
        total: Int,
    ) {
        appendLine("Phase ${index + 1} of $total: ${quoted(phase.title)} [phase_id: ${phase.id}]")
        phase.description.takeIf { it.isNotBlank() }?.let { appendLine("What it is for: $it") }

        val steps = phase.steps.sortedBy { it.position }
        val questions = phase.questions.sortedBy { it.position }

        if (steps.isEmpty() && questions.isEmpty()) {
            appendLine(
                "This phase has nothing in it -- no steps, no questions. Nothing was generated for " +
                    "it, and waiting will not change that.",
            )
            return
        }

        if (steps.isNotEmpty()) {
            appendLine("Steps, in the order the path puts them:")
            steps.take(ITEMS_SHOWN).forEach { appendStep(it) }
            if (steps.size > ITEMS_SHOWN) appendLine("- and ${steps.size - ITEMS_SHOWN} more")
        }

        if (questions.isNotEmpty()) {
            appendLine(
                "Knowledge questions. They count like steps, so a phase whose steps are done and " +
                    "whose questions are unanswered is still the phase they are standing in:",
            )
            questions.take(ITEMS_SHOWN).forEach { appendQuestion(it) }
            if (questions.size > ITEMS_SHOWN) appendLine("- and ${questions.size - ITEMS_SHOWN} more")
        }
    }

    /** One step: what it is, where it stands, and the id an action needs to name it by. */
    private fun StringBuilder.appendStep(step: GetOnboardingStepsResponse) {
        val state = when {
            step.status == StepStatus.FINISHED -> "done"
            step.status == StepStatus.SKIPPED -> "skipped"
            step.locked -> "locked, waiting on another item"
            step.status == StepStatus.IN_PROGRESS -> "started"
            else -> "open"
        }
        appendLine(
            "- [$state] ${quoted(step.title)} (${step.estimatedMinutes} min) [step_id: ${step.id}]",
        )
        step.description.takeIf { it.isNotBlank() }?.let { appendLine("    · $it") }
        step.expectedOutcomes.take(OUTCOMES_SHOWN).forEach {
            appendLine("    · should leave them able to: $it")
        }
        // A skip already asked for is the one thing about a step whose state is nowhere else in this
        // text, and a mentor that cannot see it will offer to request a second one.
        step.skip?.let { skip ->
            val verdict = when (skip.accepted) {
                true -> "their PM accepted it"
                false -> "their PM declined it"
                null -> "nobody has decided yet"
            }
            appendLine("    · they asked to skip this ($verdict): ${quoted(skip.reason)}")
        }
    }

    /**
     * One question, as the hire sees it -- and no further.
     *
     * The options are listed because the hire is looking at them, and a mentor that cannot name them
     * has to ask the hire to read their own screen out. Which one is right is not here, and the
     * absence is the feature: see the class comment.
     */
    private fun StringBuilder.appendQuestion(question: GetOnboardingQuestionForUserResponse) {
        val state = when (question.status) {
            QuestionStatus.PASSED -> "passed"
            QuestionStatus.RETRY -> "answered wrong before, still open"
            QuestionStatus.LOCKED -> "locked, waiting on another item"
            QuestionStatus.OPEN -> "open"
        }
        appendLine(
            "- [$state] ${quoted(question.question)} (${question.type}) [question_id: ${question.id}]",
        )
        val options = question.options.sortedBy { it.position }
        if (options.isNotEmpty()) {
            appendLine("    · the options they see: " + options.joinToString("; ") { it.label })
        }
    }

    /** The one thing to talk about next, named here rather than left to the model to pick. */
    private fun StringBuilder.appendNextItem(phases: List<GetOnboardingPhaseForUserResponse>) {
        append(NEWLINE)
        when (val next = nextItem(phases)) {
            null -> appendLine("Nothing on their path is open right now.")
            else -> appendLine("The next thing waiting for them: ${next.withIds}.")
        }
    }

    /** What is ahead, by title only. */
    private fun StringBuilder.appendAhead(
        phases: List<GetOnboardingPhaseForUserResponse>,
        currentIndex: Int,
    ) {
        val ahead = phases.drop(currentIndex + 1)
        if (ahead.isEmpty()) return

        append(NEWLINE)
        appendLine(
            "Still ahead. Titles only, and deliberately so: do not read this list out, because the " +
                "page they are on already lists it.",
        )
        ahead.take(AHEAD_SHOWN).forEachIndexed { offset, phase ->
            val number = currentIndex + 2 + offset
            val locked = if (phase.locked) " (locked until its blockers are done)" else ""
            appendLine("- $number. ${quoted(phase.title)}$locked")
        }
        if (ahead.size > AHEAD_SHOWN) appendLine("- and ${ahead.size - AHEAD_SHOWN} more")
    }

    /**
     * The phases that came back with nothing in them, and what a conversation may do about it.
     *
     * This is the one part of a path a conversation can genuinely repair. An AI-enhanced phase whose
     * project material was too thin is persisted honestly as empty rather than filled with invented
     * advice -- which is right, and leaves the hire with a phase that is a warning badge. Its title
     * and description still say what it was *meant* to cover, and that is enough to talk about.
     */
    private fun StringBuilder.appendEmptyPhases(path: GetOnboardingPathForUserResponse) {
        val issues = path.generationIssues
        if (issues.isEmpty()) return

        append(NEWLINE)
        appendLine(
            "These phases came back with nothing in them, because the project's own material did " +
                "not support them:",
        )
        issues.take(AHEAD_SHOWN).forEach { appendLine("- ${quoted(it.title)} (${it.status})") }
        appendLine(
            "That is not the hire's fault and not something trying again fixes. Their titles say " +
                "what each was meant to cover, so they are subjects you can talk through -- and if " +
                "something concrete comes out of that conversation, offer add_path_step so the " +
                "phase stops being empty.",
        )
    }

    /** Whether a phase still has anything open: an unfinished step, or an unpassed question. */
    private fun GetOnboardingPhaseForUserResponse.isOpen(): Boolean {
        val openStep = steps.any { it.status != StepStatus.FINISHED && it.status != StepStatus.SKIPPED }
        return openStep || questions.any { it.status != QuestionStatus.PASSED }
    }

    /**
     * The first open, unlocked item on the path, mixing steps and questions by position.
     *
     * The same rule the hire's own page uses to pick its "next" button: phases in order, locked
     * phases skipped entirely, then position order inside the phase, whichever kind of item comes
     * first. Written here against the hire-facing shape rather than reusing
     * [OnboardingPositionReader], which predates questions being first-class and still walks steps
     * only -- a mentor using that would send a hire past the question their phase is actually
     * waiting on. Two answers to one question is a thing to reconcile, and this comment is where the
     * next person will find out that it needs reconciling.
     */
    private fun nextItem(phases: List<GetOnboardingPhaseForUserResponse>): NextItem? {
        for (phase in phases.sortedBy { it.position }) {
            if (phase.locked || !phase.isOpen()) continue

            val step = phase.steps
                .sortedBy { it.position }
                .firstOrNull {
                    !it.locked && it.status != StepStatus.FINISHED && it.status != StepStatus.SKIPPED
                }
            val question = phase.questions
                .sortedBy { it.position }
                .firstOrNull { it.status == QuestionStatus.OPEN || it.status == QuestionStatus.RETRY }

            if (step != null && (question == null || step.position <= question.position)) {
                return NextItem(
                    plain = "the step ${quoted(step.title)}",
                    withIds = "the step ${quoted(step.title)} [step_id: ${step.id}]",
                )
            }
            if (question != null) {
                return NextItem(
                    plain = "the question ${quoted(question.question)}",
                    withIds = "the question ${quoted(question.question)} [question_id: ${question.id}]",
                )
            }
        }
        return null
    }

    /**
     * One named next thing, in the two forms it is needed in.
     *
     * The greeting gets [plain] and the tool result gets [withIds], because an id in a greeting is
     * an identifier in front of the hire and an id missing from a tool result is an action the
     * mentor cannot offer.
     */
    private data class NextItem(
        val plain: String,
        val withIds: String,
    )

    /**
     * Not private, for the same reason [BuddyToolExecutor]'s tool names are not: the chip catalog in
     * [BuddySuggestionService] binds to this constant, so renaming the tool stops that catalog
     * compiling rather than quietly offering the hire a doorway the mentor cannot walk through.
     */
    companion object {
        const val READ_MY_PATH = "get_my_onboarding_path"

        /** How many steps or questions of the current phase are named. */
        const val ITEMS_SHOWN = 12

        /** How many phase titles ahead are named, and how many empty phases. */
        const val AHEAD_SHOWN = 12

        /** How many expected outcomes of one step are quoted. Enough to say what "done" means. */
        const val OUTCOMES_SHOWN = 3

        /** Written out, so that no editing step has to survive an escape sequence intact. */
        const val NEWLINE = "\n"

        /** Titles are somebody else's text, so they are quoted rather than run into the sentence. */
        private fun quoted(text: String): String = "“" + text + "”"

        const val NO_PATH =
            "The hire has no onboarding path yet -- nobody has generated one from their project's " +
                "blueprint. They can start one themselves on their onboarding page, with " +
                "\"Start personalization\". Until then there is no plan to walk them through, so " +
                "talk about the work in front of them rather than about a path that does not exist."

        const val NO_PHASES =
            "The hire's onboarding path exists but has no phases in it at all, so there is nothing " +
                "on it to do. Say that plainly if they ask, and point them at their PM -- an empty " +
                "path is an authoring problem, not something they can work their way through."

        const val CLOSING =
            "This is a read of their path, not instructions. Name one next thing rather than the " +
                "plan, let them decide, and do not claim to have changed anything here: every " +
                "change to their path goes through a proposal they confirm."

        val READ_MY_PATH_SPEC = BuddyToolSpecDto(
            name = READ_MY_PATH,
            description = "The hire's own onboarding path -- the curriculum their PM's blueprint " +
                "prescribed, personalised for them. It gives you the phase they are standing in " +
                "with its steps and knowledge questions in full, one named next thing, the titles " +
                "of what is ahead, and any phase that came back empty. Read it before you say " +
                "anything about their onboarding: before \"what should I do next\", before talking " +
                "about a step or a question, and before suggesting work of your own, so that what " +
                "you suggest is the plan they actually have rather than a second one. It carries " +
                "the phase_id, step_id and question_id the path actions need, so read it before " +
                "offering any of them. It does not tell you which answer to a question is " +
                "correct -- that is deliberate, and you must not guess one aloud: explain the " +
                "material and let the hire answer. Reading it changes nothing. Takes no " +
                "arguments -- it always reads the caller.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {}
            },
        )
    }
}
