package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.QuestionStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhaseForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.GetOnboardingQuestionForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.GetOnboardingStepsResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.task.GetOnboardingTaskResponse
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
    private val onboardingTaskService: OnboardingTaskService,
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

        val current = phases[currentIndex]
        val checklists = checklistsOf(current)

        return buildString {
            appendLine(standing(phases, currentIndex))
            appendLine()
            appendCurrentPhase(current, currentIndex, phases)
            appendReadyToClose(current, phases, checklists)
            appendCurrentTasks(stepTheyAreOn(current), checklists)
            appendNextItem(phases, readyToClose(current, checklists).firstOrNull())
            appendAhead(phases, currentIndex)
            appendEmptyPhases(path)
            append(NEWLINE + CLOSING)
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
                step.id to onboardingTaskService.getOnboardingTasksByStepId(step.id).sortedBy { it.position }
            }
    }

    /**
     * The steps whose every checklist line is ticked while the step itself is still open.
     *
     * The most common way a hire gets stuck without knowing it: they did the work, ticked every line,
     * and never pressed the step's own button -- so whatever waits on the step stays locked, and the
     * page gives no reason. A checklist that is done is not a finished step (the product keeps the
     * two apart on purpose), which is exactly why this is something to *ask* about rather than
     * something to assume. A step with no checklist is never on this list: there is nothing that
     * says it is done.
     */
    private fun readyToClose(
        phase: GetOnboardingPhaseForUserResponse,
        checklists: Map<UUID, List<GetOnboardingTaskResponse>>,
    ): List<GetOnboardingStepsResponse> =
        phase.steps
            .sortedBy { it.position }
            // Not a step they asked to skip: pushing them to finish it would withdraw the request.
            .filterNot { it.hasPendingSkip() }
            .filter { step -> checklists[step.id]?.let { it.isNotEmpty() && it.all { task -> task.finished } } == true }

    /**
     * What finishing [step] would open up, named: the items in its phase that wait on it, and --
     * when it is the last open thing in the phase -- the phases that wait on the phase.
     *
     * Named because "it unlocks the next thing" is a reason nobody can check, and "it is what #3
     * waits on" is one the hire can see on their page.
     */
    private fun unlockedBy(
        step: GetOnboardingStepsResponse,
        phase: GetOnboardingPhaseForUserResponse,
        phases: List<GetOnboardingPhaseForUserResponse>,
        numbers: Map<UUID, Int>,
    ): List<String> {
        val inPhase = phase.steps
            .filter { step.id in it.blockerIds && it.id != step.id }
            .map { "#${numbers[it.id]} ${quoted(it.title)}" } +
            phase.questions
                .filter { step.id in it.blockerIds }
                .map { "#${numbers[it.id]} ${quoted(it.question)}" }

        // Locked steps count as open here: one waiting on this step is still between it and the end.
        val lastOpen = phase.steps.none {
            it.id != step.id && it.status != StepStatus.FINISHED && it.status != StepStatus.SKIPPED
        } &&
            phase.questions.all { it.status == QuestionStatus.PASSED }
        val laterPhases = if (lastOpen) {
            phases.filter { phase.id in it.blockerIds }.map { "the phase ${quoted(it.title)}" }
        } else {
            emptyList()
        }
        return inPhase + laterPhases
    }

    /**
     * Steps they have done the work of but not closed, with what each is holding up.
     *
     * Addressed to the mentor with an instruction, because this is the one case where it should bring
     * a completion up *unprompted*: a hire who does not know why their next step is locked is not
     * going to ask about the step that is locking it.
     */
    private fun StringBuilder.appendReadyToClose(
        phase: GetOnboardingPhaseForUserResponse,
        phases: List<GetOnboardingPhaseForUserResponse>,
        checklists: Map<UUID, List<GetOnboardingTaskResponse>>,
    ) {
        val ready = readyToClose(phase, checklists)
        if (ready.isEmpty()) return

        val numbers = numbering(phase.steps.sortedBy { it.position }, phase.questions.sortedBy { it.position })
        append(NEWLINE)
        appendLine(
            "READY TO CLOSE -- every line of these checklists is ticked, but the step itself is still " +
                "open, so nothing that waits on it has unlocked:",
        )
        ready.take(ITEMS_SHOWN).forEach { step ->
            val waiting = unlockedBy(step, phase, phases, numbers)
            val holding = if (waiting.isEmpty()) "" else " -- it is what ${waiting.joinToString(", ")} waits on"
            appendLine(
                "- #${numbers[step.id]} ${quoted(step.title)} [step_id: ${step.id}] " +
                    "[link: $STEP_LINK${step.id}]$holding",
            )
        }
        appendLine(
            "This step is where they actually are, and it comes before anything else that is open. " +
                "When they ask what is next, where they are or why something is locked, answer in this " +
                "order: (1) they are on this step, and its checklist is all ticked; (2) what finishing " +
                "it opens, by number; (3) ask whether they are done with it -- and call complete_step " +
                "for it in that same reply so the button is there. Keep it light: do not lead with the " +
                "button, and do not lecture them about being sure. Otherwise mention it in a sentence at " +
                "the end of your answer. Once is enough: if they say not yet, leave it.",
        )
    }

    /**
     * The step whose checklist is worth putting in front of the mentor: the one they have started, or
     * else the first one they could start.
     *
     * Started wins over next, because a hire with something open is talking about that and not about
     * what comes after it. Null when the phase has neither, which is when a checklist would be a
     * heading over nothing.
     */
    private fun stepTheyAreOn(phase: GetOnboardingPhaseForUserResponse): GetOnboardingStepsResponse? {
        if (phase.locked) return null
        val ordered = phase.steps.sortedBy { it.position }.filterNot { it.locked }
        return ordered.firstOrNull { it.status == StepStatus.IN_PROGRESS }
            ?: ordered.firstOrNull { it.status == StepStatus.WAITING }
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
                // First, when there is one: a step whose checklist is done but that was never closed
                // is what a greeting can usefully open on, because it is what is quietly holding
                // everything after it.
                readyToClose(current, checklistsOf(current)).firstOrNull()?.let {
                    appendLine(
                        "Every line of the checklist of ${quoted(it.title)} is ticked, but the step " +
                            "itself is still open, so what comes after it has not unlocked. It is " +
                            "worth asking whether they are done with it.",
                    )
                }
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

    /** Every phase of the hire's own path, or empty when they have none. */
    fun phasesOf(userId: UUID): List<GetOnboardingPhaseForUserResponse> =
        onboardingPathService.findPathForUserId(userId)?.phases.orEmpty()

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

    /**
     * The phase they are in, in full: what it is for, its steps, and its questions.
     *
     * Every item carries three things beyond its own text, each for a reason a testing session made
     * obvious:
     *
     * - **A number**, the same number the hire's page prints on the card. It is what lets them say
     *   "let's do 3" instead of retyping a title, and it only works because both sides derive it the
     *   same way: steps in position order, then questions in position order (see [numbering]).
     * - **A link**, so "want to take the check?" can arrive as something clickable rather than as an
     *   instruction to go and find it.
     * - **What a locked item is waiting on, by name.** "Locked" on its own left the mentor telling a
     *   hire they could go ahead and do a step the page would not let them open.
     */
    private fun StringBuilder.appendCurrentPhase(
        phase: GetOnboardingPhaseForUserResponse,
        index: Int,
        phases: List<GetOnboardingPhaseForUserResponse>,
    ) {
        appendLine(
            "Phase ${index + 1} of ${phases.size}: ${quoted(phase.title)} " +
                "[phase_id: ${phase.id}] [link: $PHASE_LINK${phase.id}]",
        )
        phase.description.takeIf { it.isNotBlank() }?.let { appendLine("What it is for: $it") }
        if (phase.locked) {
            val waiting = phase.blockerIds.mapNotNull { id -> phases.firstOrNull { it.id == id } }
            appendLine(
                "This whole phase is locked, so nothing in it can be started yet. It waits on: " +
                    waiting.joinToString(", ") { quoted(it.title) }.ifBlank { "an earlier phase" },
            )
        }

        val steps = phase.steps.sortedBy { it.position }
        val questions = phase.questions.sortedBy { it.position }

        if (steps.isEmpty() && questions.isEmpty()) {
            appendLine(
                "This phase has nothing in it -- no steps, no questions. Nothing was generated for " +
                    "it, and waiting will not change that.",
            )
            return
        }

        val numbers = numbering(steps, questions)
        val titles = titlesIn(phase)
        val graph = PhaseGraph(numbers, titles, opensIn(phase))

        appendGraphIntro(phase, steps, questions, numbers)

        if (steps.isNotEmpty()) {
            appendLine("Steps, numbered as their page shows them:")
            steps.take(ITEMS_SHOWN).forEach { appendStep(it, graph) }
            if (steps.size > ITEMS_SHOWN) appendLine("- and ${steps.size - ITEMS_SHOWN} more")
        }

        if (questions.isNotEmpty()) {
            appendLine(
                "Knowledge questions. They count like steps, so a phase whose steps are done and " +
                    "whose questions are unanswered is still the phase they are standing in:",
            )
            questions.take(ITEMS_SHOWN).forEach { appendQuestion(it, phase, graph) }
            if (questions.size > ITEMS_SHOWN) appendLine("- and ${questions.size - ITEMS_SHOWN} more")
        }
    }

    /**
     * The number each item of a phase carries, keyed by id.
     *
     * Steps first in position order, then questions in position order — which is the order the hire's
     * own page lists them in, and that is the whole point: a number only helps if the thing they say
     * and the thing the mentor hears are the same item. Position alone would not do it, because
     * steps and questions are numbered from the same sequence on screen but carry their own
     * positions underneath.
     */
    private fun numbering(
        steps: List<GetOnboardingStepsResponse>,
        questions: List<GetOnboardingQuestionForUserResponse>,
    ): Map<UUID, Int> =
        (steps.map { it.id } + questions.map { it.id })
            .withIndex()
            .associate { (index, id) -> id to index + 1 }

    /** How to read the phase's items as a graph, and which of them are open right now. */
    private fun StringBuilder.appendGraphIntro(
        phase: GetOnboardingPhaseForUserResponse,
        steps: List<GetOnboardingStepsResponse>,
        questions: List<GetOnboardingQuestionForUserResponse>,
        numbers: Map<UUID, Int>,
    ) {
        appendLine(
            "The items of a phase form a dependency graph, not a list: an item opens once everything " +
                "it comes after is done, several can be open at the same time, and finishing one can " +
                "open several at once. Each item below says what it comes after and what it opens. " +
                "Talk about it that way -- never \"after #6 comes #7\" unless #7 really comes after #6.",
        )
        val openNow = steps.filter { it.isFinishable() && !it.hasPendingSkip() }.map { it.id } +
            questions.filter { it.status == QuestionStatus.OPEN || it.status == QuestionStatus.RETRY }.map { it.id }
        if (!phase.locked && openNow.isNotEmpty()) {
            appendLine("Open right now: " + openNow.joinToString(", ") { "#${numbers[it]}" })
        }
        // The two halves of "add a step as the next thing", worked out rather than described: told
        // how to place a step, the mentor passed what it unlocks and left out what it comes after.
        PathStepPlacement.anchorOf(phase)?.let { anchor ->
            val done = steps
                .filter { it.status == StepStatus.FINISHED || it.status == StepStatus.SKIPPED }
                .map { it.id }
                .toSet() + questions.filter { it.status == QuestionStatus.PASSED }.map { it.id }
            val after = opensIn(phase)[anchor].orEmpty().filterNot { it in done }
            appendLine(
                "To add a step as the next thing after #${numbers[anchor]}, where they are: pass BOTH " +
                    "waits_on = [$anchor] and unlocks = [${after.joinToString(", ")}].",
            )
        }
    }

    /**
     * For every item of [phase], the items that come directly after it -- the edges the graph view
     * draws, read the other way round, because "finishing this opens #3 and #4" is the sentence a
     * hire can act on and the data only stores "#3 comes after this".
     */
    private fun opensIn(phase: GetOnboardingPhaseForUserResponse): Map<UUID, List<UUID>> {
        val edges = phase.steps.map { it.id to it.blockerIds } + phase.questions.map { it.id to it.blockerIds }
        return edges
            .flatMap { (item, blockers) -> blockers.map { it to item } }
            .groupBy({ it.first }, { it.second })
    }

    /** What every line of the current phase needs to name its neighbours. */
    private data class PhaseGraph(
        val numbers: Map<UUID, Int>,
        val titles: Map<UUID, String>,
        val opens: Map<UUID, List<UUID>>,
    )

    /** Every item of a phase by id, so a blocker can be named rather than counted. */
    private fun titlesIn(phase: GetOnboardingPhaseForUserResponse): Map<UUID, String> =
        phase.steps.associate { it.id to it.title } + phase.questions.associate { it.id to it.question }

    /** One step: what it is, where it stands, and the ids and link an action or a reply needs. */
    private fun StringBuilder.appendStep(
        step: GetOnboardingStepsResponse,
        graph: PhaseGraph,
    ) {
        val numbers = graph.numbers
        val state = when {
            step.status == StepStatus.FINISHED -> "done"
            step.status == StepStatus.SKIPPED -> "skipped"
            step.hasPendingSkip() -> "SKIP REQUESTED, waiting on their PM"
            step.locked -> "LOCKED, cannot be started yet"
            step.status == StepStatus.IN_PROGRESS -> "started"
            else -> "open"
        }
        appendLine(
            "- #${numbers[step.id]} [$state] ${quoted(step.title)} (${step.estimatedMinutes} min) " +
                "[step_id: ${step.id}] [link: $STEP_LINK${step.id}]",
        )
        step.description.takeIf { it.isNotBlank() }?.let { appendLine("    · $it") }
        step.expectedOutcomes.take(OUTCOMES_SHOWN).forEach {
            appendLine("    · should leave them able to: $it")
        }
        appendEdges(step.id, step.locked, step.blockerIds, graph)
        appendSkip(step)
    }

    /**
     * Where a skip request for [step] stands, and what that means for the mentor.
     *
     * A skip already asked for is the one thing about a step whose state is nowhere else in this
     * text, and a mentor that cannot see it will offer to request a second one -- or push the hire
     * to finish a step whose skip is waiting on their PM, which withdraws the request.
     */
    private fun StringBuilder.appendSkip(step: GetOnboardingStepsResponse) {
        step.skip?.let { skip ->
            val verdict = when (skip.accepted) {
                true -> "their PM accepted it"
                false -> "their PM declined it"
                null -> "nobody has decided yet"
            }
            appendLine("    · they asked to skip this ($verdict): ${quoted(skip.reason)}")
            // The PM's own words are the most useful thing about a decision, and a declined request
            // is exactly when the hire will want to talk about why.
            skip.reviewComment?.takeIf { it.isNotBlank() }?.let {
                appendLine("    · their PM's comment on it: ${quoted(it)}")
            }
            if (skip.accepted == null) {
                appendLine(
                    "    · while it is pending, do not push them to do this step, and do not offer " +
                        "complete_step for it unless they say they did it anyway: finishing the step " +
                        "withdraws the request. They can change or withdraw the reason on the step's " +
                        "own page [page: $STEP_PAGE_LINK${step.id}].",
                )
            }
        }
    }

    /**
     * One question, as the hire sees it -- and no further.
     *
     * The options are listed because the hire is looking at them, and a mentor that cannot name them
     * has to ask the hire to read their own screen out. Which one is right is not here, and the
     * absence is the feature: see the class comment.
     */
    private fun StringBuilder.appendQuestion(
        question: GetOnboardingQuestionForUserResponse,
        phase: GetOnboardingPhaseForUserResponse,
        graph: PhaseGraph,
    ) {
        val numbers = graph.numbers
        val state = when (question.status) {
            QuestionStatus.PASSED -> "passed"
            QuestionStatus.RETRY -> "answered wrong before, still open"
            QuestionStatus.LOCKED -> "LOCKED, cannot be answered yet"
            QuestionStatus.OPEN -> "open"
        }
        appendLine(
            "- #${numbers[question.id]} [$state] ${quoted(question.question)} (${question.type}) " +
                "[question_id: ${question.id}] [link: $QUESTION_LINK${question.id}]",
        )
        val options = question.options.sortedBy { it.position }
        if (options.isNotEmpty()) {
            appendLine("    · the options they see: " + options.joinToString("; ") { it.label })
            // Hinting is answering. Testing had the mentor say one option "matches the title of #1
            // word for word" -- no answer stated, and the question given away all the same.
            appendLine(
                "    · never narrow these down for them: not by pointing at an option that matches a " +
                    "title or wording elsewhere, not by ruling any out, and not by saying how close a " +
                    "wrong answer was -- you do not know.",
            )
        }
        appendEdges(question.id, question.status == QuestionStatus.LOCKED, question.blockerIds, graph)
        // A wrong answer is the clearest signal on the whole path that a step did not land. Teaching
        // the material in the conversation comes first; a refresher step is for when what they
        // missed is more than one explanation, so it is still there tomorrow.
        if (question.status == QuestionStatus.RETRY) {
            // Placed before the question, so the refresher is what opens it, and after what the
            // question waits on now -- or, when that is nothing, after where the hire is. The same
            // inference the action applies, spelled out so the mentor passes both halves.
            val waitsOn = PathStepPlacement.inferred(phase, emptySet(), setOf(question.id)).waitsOn.joinToString(", ")
            appendLine(
                "    · they got this wrong before, so the material behind it did not land. Go through it " +
                    "with them first. If what they missed is bigger than one explanation, offer " +
                    "add_path_step for one short refresher step in this phase [phase_id: ${phase.id}] " +
                    "that says what to revisit and where -- never the answer. Put it in front of this " +
                    "question: unlocks = [${question.id}], waits_on = [$waitsOn].",
            )
        }
    }

    /**
     * Where an item sits in its phase's graph: what it comes after, whether that has locked it, and
     * what finishing it opens.
     *
     * Every item, not only locked ones. Told only about locks, the mentor read the numbers as a
     * sequence and told a hire "after #6 comes #7" about items that do not depend on each other at all
     * -- and could not place a new step anywhere but the end, because it had never seen an edge.
     * A lock that comes from the phase itself is stated once above and said so here, rather than
     * repeating the phase's own blockers on every line.
     */
    private fun StringBuilder.appendEdges(
        id: UUID,
        locked: Boolean,
        blockerIds: Set<UUID>,
        graph: PhaseGraph,
    ) {
        val named = blockerIds.mapNotNull { blocker ->
            graph.titles[blocker]?.let { title -> "#${graph.numbers[blocker]} " + quoted(title) }
        }
        when {
            named.isNotEmpty() && locked ->
                appendLine(
                    "    · comes after ${named.joinToString(", ")}, which is not finished yet -- so it is " +
                        "locked. Do not offer to start it before then.",
                )
            named.isNotEmpty() -> appendLine("    · comes after ${named.joinToString(", ")}")
            locked -> appendLine("    · locked by this phase, not by anything inside it. Do not offer to start it.")
        }
        val opens = graph.opens[id].orEmpty().mapNotNull { next -> graph.numbers[next]?.let { "#$it" } }
        if (opens.isNotEmpty()) appendLine("    · opens: ${opens.joinToString(", ")}")
    }

    /**
     * The checklist of the step the hire is actually on, with the id of each line.
     *
     * Only for the one step, because this is the level where a conversation happens -- "I have done
     * the first two, the third one is where I am stuck" -- and putting every step's checklist in the
     * prompt would bury the path it is meant to describe.
     *
     * Read by step id rather than through an authorizing read, because the step came out of the
     * hire's own path a moment ago: the resolution in [findStep] is what proves it is theirs, and
     * doing it twice would not make it truer.
     */
    private fun StringBuilder.appendCurrentTasks(
        step: GetOnboardingStepsResponse?,
        checklists: Map<UUID, List<GetOnboardingTaskResponse>>,
    ) {
        if (step == null) return
        val tasks = checklists[step.id].orEmpty()
        if (tasks.isEmpty()) return

        append(NEWLINE)
        appendLine("The checklist of ${quoted(step.title)}, the step they are on:")
        tasks.take(ITEMS_SHOWN).forEach { task ->
            val mark = if (task.finished) "done" else "open"
            appendLine("- [$mark] ${quoted(task.title)} [task_id: ${task.id}]")
        }
        if (tasks.size > ITEMS_SHOWN) appendLine("- and ${tasks.size - ITEMS_SHOWN} more")
        // Said here because it is where the mentor will be tempted otherwise: the step's own
        // completion is not the sum of its checklist, and the product allows both.
        appendLine(
            "Ticking these off is complete_task. A step can be finished with lines still open, so " +
                "never tell them the checklist has to be empty first -- and never tick a line off " +
                "because the conversation covered it.",
        )
    }

    /**
     * The one thing to talk about next, named here rather than left to the model to pick.
     *
     * A step whose checklist is done but that was never closed comes first: it is where they
     * actually are, and whatever the page calls next is often locked behind it.
     */
    private fun StringBuilder.appendNextItem(
        phases: List<GetOnboardingPhaseForUserResponse>,
        ready: GetOnboardingStepsResponse?,
    ) {
        append(NEWLINE)
        if (ready != null) {
            appendLine(
                "The next thing waiting for them: finishing the step ${quoted(ready.title)} " +
                    "[step_id: ${ready.id}] [link: $STEP_LINK${ready.id}], whose checklist is already done.",
            )
            return
        }
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

    /** Whether the hire asked to skip this step and their PM has not decided yet. */
    private fun GetOnboardingStepsResponse.hasPendingSkip(): Boolean = skip != null && skip.accepted == null

    /** Whether a step can still be finished: not locked, and neither finished nor skipped. */
    private fun GetOnboardingStepsResponse.isFinishable(): Boolean =
        !locked && (status == StepStatus.WAITING || status == StepStatus.IN_PROGRESS)

    /** Whether a phase still has anything open: an unfinished step, or an unpassed question. */
    private fun GetOnboardingPhaseForUserResponse.isOpen(): Boolean {
        val openStep = steps.any { it.status != StepStatus.FINISHED && it.status != StepStatus.SKIPPED }
        return openStep || questions.any { it.status != QuestionStatus.PASSED }
    }

    /**
     * The first open, unlocked item on the path, by the rule the hire's own page uses.
     *
     * The page's "next" button (`resolveNextAction`): phases in order, locked phases skipped
     * entirely, then the first open unlocked *step* by position, and only when there is none, the
     * first open *question*. Steps and questions carry separate positions, so mixing them by position
     * -- which this used to do -- named a question as next while the page pointed at a step.
     *
     * Written here against the hire-facing shape rather than reusing
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
                    !it.locked &&
                        it.status != StepStatus.FINISHED &&
                        it.status != StepStatus.SKIPPED &&
                        // Asked to skip and waiting on the PM: not what to tell them to do next.
                        !it.hasPendingSkip()
                }
            val question = phase.questions
                .sortedBy { it.position }
                .firstOrNull { it.status == QuestionStatus.OPEN || it.status == QuestionStatus.RETRY }

            if (step != null) {
                return NextItem(
                    plain = "the step ${quoted(step.title)}",
                    withIds = "the step ${quoted(step.title)} [step_id: ${step.id}] " +
                        "[link: $STEP_LINK${step.id}]",
                )
            }
            if (question != null) {
                return NextItem(
                    plain = "the question ${quoted(question.question)}",
                    withIds = "the question ${quoted(question.question)} " +
                        "[question_id: ${question.id}] [link: $QUESTION_LINK${question.id}]",
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
         * `BuddyPathToolsTest`: a route rename that forgets this file produces links that 404, and a
         * mentor has no way to notice.
         */
        const val STEP_LINK = "/onboarding?step="

        /** A step's own page, where its checklist, its skip request and its reason live. */
        const val STEP_PAGE_LINK = "/onboarding/"
        const val QUESTION_LINK = "/onboarding?question="
        const val PHASE_LINK = "/onboarding?phase="

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
                "change to their path goes through a proposal they confirm. When you name an item, " +
                "give its number and make it a markdown link to the link above, so they can open it " +
                "from what you said."

        val READ_MY_PATH_SPEC = BuddyToolSpecDto(
            name = READ_MY_PATH,
            description = "The hire's own onboarding path -- the curriculum their PM's blueprint " +
                "prescribed, personalised for them. It gives you the phase they are standing in " +
                "with its steps and knowledge questions in full, the checklist of the step they " +
                "are on, one named next thing, the titles of what is ahead, and any phase that " +
                "came back empty. Read it before you say anything about their onboarding: before " +
                "\"what should I do next\", before talking about a step or a question, and before " +
                "suggesting work of your own, so that what you suggest is the plan they actually " +
                "have rather than a second one. Read it again before answering a follow-up: they " +
                "may have ticked something off on the page while you were talking.\n" +
                "Each item comes with three things to use. A NUMBER (#1, #2, ...) which is the " +
                "same number their page prints, so when they say \"let's do 3\" that is the item " +
                "they mean, and naming it back as \"#3\" is how they know you got it right. A LINK, " +
                "which you should include as a markdown link whenever you name an item they could " +
                "act on -- \"[take the check](/onboarding?question=...)\" -- so they can get there " +
                "in one click instead of hunting for it. And the ids the path actions need, so read " +
                "this before offering one.\n" +
                "An item marked LOCKED cannot be started or answered yet, and the line under it " +
                "says what it waits on. Never tell the hire they can go ahead with a locked item, " +
                "even if they ask directly: say what has to be finished first and offer that " +
                "instead.\n" +
                "A step marked SKIP REQUESTED is waiting on their PM: do not push them to do it. " +
                "When they want to skip a step, that is request_skip -- their PM decides.\n" +
                "It does not tell you which answer to a question is correct -- that is deliberate, " +
                "and you must not guess one aloud: explain the material and let the hire answer. " +
                "Reading it changes nothing. Takes no arguments -- it always reads the caller.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {}
            },
        )
    }
}
