package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.QuestionStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhaseForUserResponse
import java.util.UUID

/**
 * Where in a phase's dependency graph a step the buddy adds would go, checked against the hire's own
 * path before anybody sees a button.
 *
 * [waitsOn] are the items the new step opens after; [unlocks] are the items that will wait on the new
 * step instead of on those. "Put it in as the next thing" is `waitsOn = [what they are on]`,
 * `unlocks = [what currently waits on that]`.
 *
 * Checked here on the hire-facing shape, so a bad placement comes back to the mentor as a sentence;
 * [OnboardingStepPlacementService] enforces the same rules on the entities at confirm time.
 */
internal class PathStepPlacement(
    private val phase: GetOnboardingPhaseForUserResponse,
    val waitsOn: Set<UUID>,
    val unlocks: Set<UUID>,
    /** Whether [waitsOn] was worked out here rather than passed by the mentor. See [inferred]. */
    val entryInferred: Boolean = false,
) {
    private val stepsById = phase.steps.associateBy { it.id }
    private val questionsById = phase.questions.associateBy { it.id }

    /** Whether the step goes anywhere but the end of an unconnected list. */
    val isConnected: Boolean get() = waitsOn.isNotEmpty() || unlocks.isNotEmpty()

    /** A sentence for the mentor saying what is wrong with this placement, or null when it is sound. */
    fun problem(): String? {
        val unknown = (waitsOn + unlocks).filterNot { it in stepsById || it in questionsById }
        return when {
            unknown.isNotEmpty() ->
                "Some ids in waits_on or unlocks are not steps or questions of “${phase.title}”. " +
                    "Use the step_id and question_id values of that phase from get_my_onboarding_path."
            waitsOn.any { it in unlocks } ->
                "The same item is in both waits_on and unlocks. The new step goes after one set and " +
                    "before the other."
            unlocks.any { isDone(it) } ->
                "Something in unlocks is already done, so there is nothing left to put the new step " +
                    "in front of. Only put it before items that are still open."
            unlocks.any { candidate -> waitsOn.any { waitsTransitivelyOn(it, candidate) } } ->
                "That placement would make a loop: something in waits_on already waits on something " +
                    "in unlocks. Pick items further along for unlocks."
            else -> null
        }
    }

    /**
     * The list position the step gets: straight after the last step it waits on, else straight
     * before the first step it unlocks, else the end. Keeps the list view's order close to the graph.
     */
    fun position(): Int {
        val after = waitsOn.mapNotNull { stepsById[it]?.position }.maxOrNull()
        val before = unlocks.mapNotNull { stepsById[it]?.position }.minOrNull()
        return (after?.plus(1) ?: before ?: phase.steps.size).coerceIn(0, phase.steps.size)
    }

    /** What the button says about where it goes, by title. Empty when it goes nowhere in particular. */
    fun describe(): String {
        val after = waitsOn.map { titleOf(it) }
        val before = unlocks.map { titleOf(it) }
        return listOfNotNull(
            after.takeIf { it.isNotEmpty() }?.let { "after ${it.joinToString(", ")}" },
            before.takeIf { it.isNotEmpty() }?.let { "before ${it.joinToString(", ")}" },
        ).joinToString(", ")
    }

    /** Whether any item in [unlocks] is a step that is already started, which placing this would re-lock. */
    fun relocksStarted(): List<String> =
        unlocks.mapNotNull { id -> stepsById[id]?.takeIf { it.status == StepStatus.IN_PROGRESS }?.let { titleOf(id) } }

    private fun isDone(id: UUID): Boolean =
        stepsById[id]?.let { it.status == StepStatus.FINISHED || it.status == StepStatus.SKIPPED }
            ?: (questionsById[id]?.status == QuestionStatus.PASSED)

    private fun blockersOf(id: UUID): Set<UUID> = stepsById[id]?.blockerIds ?: questionsById[id]?.blockerIds.orEmpty()

    private fun waitsTransitivelyOn(node: UUID, candidate: UUID): Boolean {
        val seen = mutableSetOf<UUID>()
        val stack = ArrayDeque(listOf(node))
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            if (current == candidate) return true
            if (seen.add(current)) stack.addAll(blockersOf(current))
        }
        return false
    }

    private fun titleOf(id: UUID): String =
        "“" + (stepsById[id]?.title ?: questionsById[id]?.question ?: id.toString()) + "”"

    companion object {
        /**
         * A placement with its entry filled in when the mentor left it out.
         *
         * Testing had the mentor pass only `unlocks` for a step the hire asked to do next: the
         * question after it was locked behind the new step, but nothing led *into* the new step, so
         * it sat open from the start with no edge in -- a graph that no longer reads as one. The
         * entry is not something to leave to a model that reliably fills in half of a pair, so when
         * [waitsOn] is empty it is worked out, in this order:
         *
         * 1. **What the unlocked items waited on until now.** Putting a step in front of B means
         *    taking over B's incoming edges: A → B becomes A → new → B.
         * 2. **Where the hire is**: the step they have started, or else the one they finished most
         *    recently. An item with no edges in (the first of a phase) has nothing to take over, and
         *    "as the next thing" means after what they are doing.
         *
         * Nothing at all only when neither exists, or when using it would make a loop -- a phase the
         * hire has not touched, where a step really can stand at the start. The button names what it
         * comes after either way, so an inference the hire did not mean is visible before the click.
         */
        fun inferred(
            phase: GetOnboardingPhaseForUserResponse,
            waitsOn: Set<UUID>,
            unlocks: Set<UUID>,
        ): PathStepPlacement {
            if (waitsOn.isNotEmpty()) return PathStepPlacement(phase, waitsOn, unlocks)

            val items = phase.steps.map { it.id to it.blockerIds } + phase.questions.map { it.id to it.blockerIds }
            val inPhase = items.map { it.first }.toSet()
            val inherited = items
                .filter { it.first in unlocks }
                .flatMap { it.second }
                .filter { it in inPhase && it !in unlocks }
                .toSet()
            if (inherited.isNotEmpty()) return PathStepPlacement(phase, inherited, unlocks, entryInferred = true)

            val anchor = anchorOf(phase)?.takeIf { it !in unlocks }
                ?: return PathStepPlacement(phase, emptySet(), unlocks)
            val anchored = PathStepPlacement(phase, setOf(anchor), unlocks, entryInferred = true)
            return if (anchored.problem() == null) anchored else PathStepPlacement(phase, emptySet(), unlocks)
        }

        /** The step the hire is on: the one started, else the one finished most recently. */
        fun anchorOf(phase: GetOnboardingPhaseForUserResponse): UUID? =
            phase.steps.firstOrNull { it.status == StepStatus.IN_PROGRESS }?.id
                ?: phase.steps
                    .filter { it.status == StepStatus.FINISHED && it.completedAt != null }
                    .maxByOrNull { it.completedAt!! }
                    ?.id
    }
}
