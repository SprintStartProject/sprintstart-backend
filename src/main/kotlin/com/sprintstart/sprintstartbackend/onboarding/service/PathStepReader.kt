package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPathRepository
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Which step a mentor meant, and what the hire's path actually holds.
 *
 * Extracted for the same reason [OnboardingPositionReader] was: a step the buddy names from a
 * conversation and a step [BoardService] hydrates a card from must be the same lookup, or a
 * `PATH_STEP` card and the tool that placed it can end up disagreeing about which step it is.
 *
 * Read-only. Resolving a step must never be what moves a hire along it.
 */
@Component
class PathStepReader(
    private val onboardingPathRepository: OnboardingPathRepository,
) {
    /**
     * Every step of [userId]'s single path, phase-then-step `position` order, each carrying the
     * phase it belongs to.
     *
     * Phases whose `generationStatus` is hidden from the user are left out, mirroring
     * [com.sprintstart.sprintstartbackend.onboarding.model.mapper.toGetForUserResponse] — a step the
     * hire cannot see on the path page must not be resolvable from the board either.
     *
     * Empty when the user has no path at all, never an error: not everybody onboarding has one.
     */
    fun stepsFor(userId: UUID): List<ResolvedPathStep> {
        val path = onboardingPathRepository.findOnboardingPathByUserId(userId).orElse(null) ?: return emptyList()
        return path.phases
            .sortedBy { it.position }
            .filterNot { it.generationStatus.isHiddenFromUser() }
            .flatMap { phase -> phase.steps.sortedBy { it.position }.map { step -> ResolvedPathStep(phase, step) } }
    }

    /** [stepsFor], keyed by step id — for hydrating a card that already stores which step it means. */
    fun stepsById(userId: UUID): Map<UUID, ResolvedPathStep> = stepsFor(userId).associateBy { it.step.id }

    /**
     * The step [reference] names, or null when nothing matches.
     *
     * [reference] is a UUID when the caller already knows the step id; otherwise it is matched
     * against titles, trimmed and whitespace-collapsed, case-insensitive: an exact match first, then
     * a `contains` match if it is the only step that has one. A title that matches more than one step
     * is not a resolution the mentor should get to guess through, so it comes back null the same as
     * no match at all.
     */
    fun resolve(userId: UUID, reference: String): ResolvedPathStep? {
        val steps = stepsFor(userId)
        if (steps.isEmpty()) return null

        asUuidOrNull(reference)?.let { id -> return steps.firstOrNull { it.step.id == id } }

        val needle = normalise(reference)
        if (needle.isBlank()) return null

        steps.firstOrNull { normalise(it.step.title).equals(needle, ignoreCase = true) }?.let { return it }

        return steps.filter { normalise(it.step.title).contains(needle, ignoreCase = true) }.singleOrNull()
    }

    /** The hire's actual step titles, for a refusal sentence that lets the model self-correct. */
    fun titlesFor(userId: UUID): List<String> = stepsFor(userId).map { it.step.title }

    private fun asUuidOrNull(value: String): UUID? =
        try {
            UUID.fromString(value.trim())
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun normalise(value: String): String = value.trim().replace(WHITESPACE, " ")

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}

/** One step of a path, with the phase it belongs to. */
data class ResolvedPathStep(
    val phase: OnboardingPhase,
    val step: OnboardingStep,
)
