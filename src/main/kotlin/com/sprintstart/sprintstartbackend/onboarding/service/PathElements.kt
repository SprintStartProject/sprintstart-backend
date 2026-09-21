package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingFeedbackRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPathRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPhaseRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingResourceRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingSkipRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingStepRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingTaskRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** The kinds of thing on an onboarding path that the content area can point at. */
enum class PathElementKind(
    val noun: String,
) {
    PHASE("phase"),
    STEP("step"),
    TASK("task"),
    RESOURCE("resource"),
    SKIP("skip request"),
    FEEDBACK("feedback"),
}

/**
 * One thing on somebody's onboarding path, as far as a preview needs to know about it.
 *
 * @property ownerId The person whose path it is. Paths belong to people, not projects, so this is
 * what decides whether a project's manager may touch it.
 * @property position Its place among its siblings, or null when it has none.
 * @property children How many things sit directly under it, which is what a new child's position is
 * validated against.
 * @property contains What deleting it takes with it, in words; empty when it holds nothing.
 * @property finishedSteps How many steps under it the person already got through, finished or skipped.
 * @property stepStatus The status of the step it is or hangs off, or null for a phase.
 */
data class PathElement(
    val kind: PathElementKind,
    val id: UUID,
    val ownerId: UUID,
    val title: String,
    val position: Int?,
    val children: Int,
    val contains: String,
    val stepStatus: StepStatus?,
    val finishedSteps: Int = 0,
)

/**
 * Finds what an id points at on an onboarding path, and whose path it is.
 *
 * The by-id services behind the content actions load an element without asking whose it is. This
 * is the one place that walks an element up to its owner, so the check that decides whether a
 * manager may touch it is the same one for every kind.
 *
 * Read-only transactions, because every hop up is a lazy association.
 */
@Component
class PathElements(
    private val pathRepository: OnboardingPathRepository,
    private val phaseRepository: OnboardingPhaseRepository,
    private val stepRepository: OnboardingStepRepository,
    private val taskRepository: OnboardingTaskRepository,
    private val resourceRepository: OnboardingResourceRepository,
    private val skipRepository: OnboardingSkipRepository,
    private val feedbackRepository: OnboardingFeedbackRepository,
) {
    /** The element of [kind] that [id] names, or null when there is none. */
    @Transactional(readOnly = true)
    fun find(kind: PathElementKind, id: UUID): PathElement? =
        when (kind) {
            PathElementKind.PHASE -> phase(id)
            PathElementKind.STEP -> step(id)
            PathElementKind.TASK -> task(id)
            PathElementKind.RESOURCE -> resource(id)
            PathElementKind.SKIP -> skip(id)
            PathElementKind.FEEDBACK -> feedback(id)
        }

    /** A summary of somebody's whole path, for a reset; null when they have none. */
    @Transactional(readOnly = true)
    fun pathOf(userId: UUID): PathSummary? {
        val path = pathRepository.findByUserId(userId).orElse(null) ?: return null
        val steps = path.phases.flatMap { it.steps }
        return PathSummary(
            phases = path.phases.size,
            steps = steps.size,
            finishedSteps = steps.count { it.status.isDone() },
            checks = path.phases.sumOf { it.checkQuestions.size },
        )
    }

    private fun phase(id: UUID): PathElement? =
        phaseRepository.findById(id).orElse(null)?.let { phase ->
            PathElement(
                kind = PathElementKind.PHASE,
                id = phase.id,
                ownerId = phase.path.userId,
                title = phase.title,
                position = phase.position,
                children = phase.steps.size,
                contains = containedIn(phase.steps, phase.checkQuestions.size),
                stepStatus = null,
                finishedSteps = phase.steps.count { it.status.isDone() },
            )
        }

    private fun step(id: UUID): PathElement? =
        stepRepository.findById(id).orElse(null)?.let { step ->
            PathElement(
                kind = PathElementKind.STEP,
                id = step.id,
                ownerId = step.phase.path.userId,
                title = step.title,
                position = step.position,
                children = step.tasks.size,
                contains = containedIn(listOf(step), checks = 0, includeSteps = false),
                stepStatus = step.status,
                finishedSteps = if (step.status.isDone()) 1 else 0,
            )
        }

    private fun task(id: UUID): PathElement? =
        taskRepository.findById(id).orElse(null)?.let { task ->
            PathElement(
                kind = PathElementKind.TASK,
                id = task.id,
                ownerId = task.step.phase.path.userId,
                title = task.title,
                position = task.position,
                children = 0,
                contains = "",
                stepStatus = task.step.status,
            )
        }

    private fun resource(id: UUID): PathElement? =
        resourceRepository.findById(id).orElse(null)?.let { resource ->
            PathElement(
                kind = PathElementKind.RESOURCE,
                id = resource.id,
                ownerId = resource.step.phase.path.userId,
                title = resource.title,
                position = null,
                children = 0,
                contains = "",
                stepStatus = resource.step.status,
            )
        }

    private fun skip(id: UUID): PathElement? =
        skipRepository.findById(id).orElse(null)?.let { skip ->
            PathElement(
                kind = PathElementKind.SKIP,
                id = skip.id,
                ownerId = skip.step.phase.path.userId,
                title = skip.step.title,
                position = null,
                children = 0,
                contains = "",
                stepStatus = skip.step.status,
            )
        }

    private fun feedback(id: UUID): PathElement? =
        feedbackRepository.findById(id).orElse(null)?.let { feedback ->
            PathElement(
                kind = PathElementKind.FEEDBACK,
                id = feedback.id,
                ownerId = feedback.userId,
                title = feedback.step?.title ?: "the path as a whole",
                position = null,
                children = 0,
                contains = "",
                stepStatus = feedback.step?.status,
            )
        }

    /** What removing [steps] removes beneath them, in words, or empty when it is nothing. */
    private fun containedIn(steps: List<OnboardingStep>, checks: Int, includeSteps: Boolean = true): String {
        val parts = buildList {
            if (includeSteps && steps.isNotEmpty()) add(counted(steps.size, "step"))
            steps.sumOf { it.tasks.size }.takeIf { it > 0 }?.let { add(counted(it, "task")) }
            steps.sumOf { it.resources.size }.takeIf { it > 0 }?.let { add(counted(it, "resource")) }
            steps.sumOf { it.skips.size }.takeIf { it > 0 }?.let { add(counted(it, "skip request")) }
            steps.sumOf { it.feedback.size }.takeIf { it > 0 }?.let {
                add(counted(it, "piece of feedback", "pieces of feedback"))
            }
            if (checks > 0) add(counted(checks, "knowledge-check question"))
        }
        return when (parts.size) {
            0 -> ""
            1 -> parts.single()
            else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
        }
    }

    private fun counted(count: Int, singular: String, plural: String = "${singular}s"): String =
        "$count ${if (count == 1) singular else plural}"
}

/** A step counts as done once it is finished or skipped; that is what the hire's progress is made of. */
internal fun StepStatus.isDone(): Boolean = this == StepStatus.FINISHED || this == StepStatus.SKIPPED

/** How much a reset would remove, and how much of it the hire already got through. */
data class PathSummary(
    val phases: Int,
    val steps: Int,
    val finishedSteps: Int,
    val checks: Int,
)
