package com.sprintstart.sprintstartbackend.blueprint.external

import com.sprintstart.sprintstartbackend.blueprint.external.enums.BlueprintPhaseType
import com.sprintstart.sprintstartbackend.blueprint.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.blueprint.external.enums.RequirementType
import com.sprintstart.sprintstartbackend.blueprint.external.enums.StepType
import java.util.UUID

/**
 * A detached, read-only copy of a blueprint path with everything needed to build an onboarding path
 * from it.
 *
 * Dependency edges are carried as the ids of the blocking phase or node in the same snapshot, so the
 * graph can be rebuilt without touching the blueprint's persistence.
 */
data class BlueprintSnapshot(
    val id: UUID,
    val projectId: UUID?,
    val phases: List<BlueprintPhaseSnapshot>,
)

/** One phase of a [BlueprintSnapshot]. */
data class BlueprintPhaseSnapshot(
    val id: UUID,
    val position: Int,
    val title: String,
    val description: String?,
    val aiPrompt: String?,
    val type: BlueprintPhaseType,
    val graphX: Double?,
    val graphY: Double?,
    val blockedBy: Set<UUID>,
    val requirements: List<BlueprintRequirementSnapshot>,
    val steps: List<BlueprintStepSnapshot>,
    val checkQuestions: List<BlueprintCheckQuestionSnapshot>,
) {
    /** Whether the phase's role/skill requirements are satisfied by the user profile. */
    fun isIncludedFor(projectRoleIds: Set<UUID>, skillIds: Set<UUID>): Boolean =
        requirements.all { requirement ->
            when (requirement.type) {
                RequirementType.PROJECT_ROLE -> requirement.referenceId in projectRoleIds
                RequirementType.SKILL -> requirement.referenceId in skillIds
            }
        }
}

/** A role or skill a user must hold for a phase to be included in their path. */
data class BlueprintRequirementSnapshot(
    val type: RequirementType,
    val referenceId: UUID,
)

/** One step of a phase. [blockedBy] holds ids of steps and check questions in the same snapshot. */
data class BlueprintStepSnapshot(
    val id: UUID,
    val position: Int,
    val title: String,
    val description: String,
    val type: StepType,
    val aiAssisted: Boolean,
    val estimatedMinutes: Int,
    val expectedOutcome: String,
    val graphX: Double?,
    val graphY: Double?,
    val blockedBy: Set<UUID>,
    val tasks: List<BlueprintTaskSnapshot>,
    val resources: List<BlueprintResourceSnapshot>,
)

/** A task inside a step. */
data class BlueprintTaskSnapshot(
    val position: Int,
    val title: String,
    val description: String,
)

/** A resource linked from a step. */
data class BlueprintResourceSnapshot(
    val title: String,
    val description: String,
    val url: String,
)

/** One knowledge-check question of a phase. [blockedBy] works as for steps. */
data class BlueprintCheckQuestionSnapshot(
    val id: UUID,
    val title: String,
    val position: Int,
    val type: CheckQuestionType,
    val question: String,
    val explanation: String?,
    val correctAnswer: String?,
    val graphX: Double?,
    val graphY: Double?,
    val blockedBy: Set<UUID>,
    val options: List<BlueprintCheckOptionSnapshot>,
)

/** An answer option of a check question. */
data class BlueprintCheckOptionSnapshot(
    val position: Int,
    val label: String,
    val correct: Boolean,
)
