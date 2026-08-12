package com.sprintstart.sprintstartbackend.onboarding.model.response.ramp

import com.sprintstart.sprintstartbackend.onboarding.external.enums.RampStage
import com.sprintstart.sprintstartbackend.onboarding.model.response.starterwork.StarterWorkTaskProposalResponse
import java.time.Instant
import java.util.UUID

/**
 * Whether a hire has been shown to work unsupervised here, and if not, what is missing.
 *
 * [blockers] is the honest half. "Not autonomous yet" without a reason is a grade; with one it is a
 * next step. Empty when [reachedAt] is set.
 */
data class AutonomyResponse(
    val reached: Boolean,
    /** When it happened — the qualifying merge, not when the system noticed. */
    val reachedAt: Instant?,
    val provenByArtifactId: UUID?,
    val blockers: List<String>,
)

/**
 * A hire's position on the ramp of real tasks, on one project.
 *
 * There is deliberately **no completion percentage**: the ramp is real work, and a percentage of
 * real work is a number nobody can act on. [stage] and [currentTask] answer what somebody is
 * actually doing; [unlockedBy] answers why they are there.
 */
data class MyRampResponse(
    val stage: RampStage,
    val currentTask: StarterWorkTaskProposalResponse?,
    /** One line saying what moved the hire to this stage — never a score. */
    val unlockedBy: String,
    /** Pull requests this hire has merged on the project. The ramp's only real counter. */
    val mergedCount: Int,
    /** Competency keys credited by merged work, not by chat placement. */
    val creditedCompetencyKeys: List<String>,
    val autonomy: AutonomyResponse,
)
