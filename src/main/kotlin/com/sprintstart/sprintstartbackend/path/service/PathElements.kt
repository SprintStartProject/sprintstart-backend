package com.sprintstart.sprintstartbackend.path.service

import com.sprintstart.sprintstartbackend.path.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.path.external.model.PathElement
import com.sprintstart.sprintstartbackend.path.external.model.PathElementKind
import com.sprintstart.sprintstartbackend.path.external.model.PathSummary
import com.sprintstart.sprintstartbackend.path.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.path.repository.OnboardingFeedbackRepository
import com.sprintstart.sprintstartbackend.path.repository.OnboardingPathRepository
import com.sprintstart.sprintstartbackend.path.repository.OnboardingPhaseRepository
import com.sprintstart.sprintstartbackend.path.repository.OnboardingResourceRepository
import com.sprintstart.sprintstartbackend.path.repository.OnboardingSkipRepository
import com.sprintstart.sprintstartbackend.path.repository.OnboardingStepRepository
import com.sprintstart.sprintstartbackend.path.repository.OnboardingTaskRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

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
                siblings = phase.path.phases.size,
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
                siblings = step.phase.steps.size,
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
                siblings = task.step.tasks.size,
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
        val parts = listOfNotNull(
            counted(steps.size, "step").takeIf { includeSteps && steps.isNotEmpty() },
            countedIfAny(steps.sumOf { it.tasks.size }, "task"),
            countedIfAny(steps.sumOf { it.resources.size }, "resource"),
            countedIfAny(steps.sumOf { it.skips.size }, "skip request"),
            countedIfAny(steps.sumOf { it.feedback.size }, "piece of feedback", "pieces of feedback"),
            countedIfAny(checks, "knowledge-check question"),
        )
        return when (parts.size) {
            0 -> ""
            1 -> parts.single()
            else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
        }
    }

    private fun countedIfAny(count: Int, singular: String, plural: String = "${singular}s"): String? =
        counted(count, singular, plural).takeIf { count > 0 }

    private fun counted(count: Int, singular: String, plural: String = "${singular}s"): String =
        "$count ${if (count == 1) singular else plural}"
}

/** A step counts as done once it is finished or skipped; that is what the hire's progress is made of. */
internal fun StepStatus.isDone(): Boolean = this == StepStatus.FINISHED || this == StepStatus.SKIPPED
