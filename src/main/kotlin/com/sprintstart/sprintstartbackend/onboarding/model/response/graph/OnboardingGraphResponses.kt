package com.sprintstart.sprintstartbackend.onboarding.model.response.graph

import java.time.Instant
import java.util.UUID

/** The blockers a node or phase waits on after they were replaced. */
data class OnboardingBlockersResponse(
    val id: UUID,
    val blockerIds: Set<UUID>,
)

/**
 * What the onboarding page needs to know before it offers to build a path.
 *
 * @property running Whether a generation for the caller is in flight right now -- started from
 * another tab, or from this one before a reload.
 * @property runningProjectId The project the running generation builds from, when [running].
 * @property startedAt When the running generation started, when [running].
 * @property hasActiveBlueprint Whether the requested project has exactly one active blueprint, the
 * one thing a path cannot be built without.
 * @property activeBlueprintCount How many active blueprints the project has, so "none yet" and
 * "several, which is ambiguous" can be told apart when [hasActiveBlueprint] is false.
 */
data class OnboardingGenerationStatusResponse(
    val running: Boolean,
    val runningProjectId: UUID? = null,
    val startedAt: Instant? = null,
    val hasActiveBlueprint: Boolean,
    val activeBlueprintCount: Long = if (hasActiveBlueprint) 1 else 0,
)
