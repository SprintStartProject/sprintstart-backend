package com.sprintstart.sprintstartbackend.onboarding.model.request.blueprint

import jakarta.validation.constraints.NotNull
import java.util.UUID

/**
 * @property projectId The project to generate blueprints for. Required: blueprints are only ever
 * selected for a path when their scope carries the requesting project.
 * @property scopes Bare scope names (`global`, `area:backend`), or `null` to refresh all of the
 * project's known scopes.
 */
data class GenerateBlueprintsRequest(
    @NotNull val projectId: UUID,
    val scopes: List<String>? = null,
)
