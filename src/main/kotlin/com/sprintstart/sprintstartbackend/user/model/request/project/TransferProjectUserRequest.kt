package com.sprintstart.sprintstartbackend.user.model.request.project

import jakarta.validation.constraints.NotNull
import java.util.UUID

/**
 * Moves a single user from one project into another in one request.
 *
 * The target project is taken from the path, so the body only carries the user and the project they
 * are moved out of. Modelling the move as one request — instead of a delete followed by an assign —
 * keeps it atomic: a project manager who loses the second call would otherwise have released a user
 * they can no longer see, and therefore no longer re-assign.
 */
data class TransferProjectUserRequest(
    @field:NotNull
    val userId: UUID,
    @field:NotNull
    val sourceProjectId: UUID,
)
