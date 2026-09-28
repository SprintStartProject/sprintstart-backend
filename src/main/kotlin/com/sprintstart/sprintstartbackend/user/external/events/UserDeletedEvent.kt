package com.sprintstart.sprintstartbackend.user.external.events

import java.util.UUID

/**
 * Published when a user is deleted, so every module holding data about them can erase it.
 *
 * The user module deletes the account and its own projection; what other modules recorded about
 * the person is theirs to remove, and only they know what they hold. Carries the id alone —
 * anything a listener needs beyond that is already gone by the time it runs.
 *
 * @property userId The identifier of the deleted user.
 */
data class UserDeletedEvent(
    val userId: UUID,
)
