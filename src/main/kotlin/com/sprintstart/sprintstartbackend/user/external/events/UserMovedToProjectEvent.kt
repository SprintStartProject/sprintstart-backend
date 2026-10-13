package com.sprintstart.sprintstartbackend.user.external.events

import java.util.UUID

/**
 * Published when a user is moved into a project and leaves at least one other project in the same step.
 *
 * A regular user belongs to exactly one project, so assigning them to a new one ends their earlier
 * memberships, and the project roles held on those go with them. Some of what other modules keep
 * about that person was derived from the project they just left, for example an onboarding path
 * built from its blueprint. Those modules reset it in reaction to this event rather than the user
 * module reaching into them.
 *
 * Not published when the user was already a member of the target project or had no other
 * membership to leave.
 *
 * @property userId The user that was moved.
 * @property projectId The project the user was moved into.
 * @property previousProjectIds The projects whose membership was ended by the move.
 */
data class UserMovedToProjectEvent(
    val userId: UUID,
    val projectId: UUID,
    val previousProjectIds: List<UUID>,
)
