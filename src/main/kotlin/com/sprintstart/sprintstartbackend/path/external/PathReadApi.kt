package com.sprintstart.sprintstartbackend.path.external

import com.sprintstart.sprintstartbackend.path.external.model.OnboardingPosition
import com.sprintstart.sprintstartbackend.path.external.model.ResolvedPathStep
import com.sprintstart.sprintstartbackend.shared.ids.OnboardingTaskId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import java.util.UUID

/**
 * What the views over a hire's path read from it: the board's step cards and the PM-facing lists that say
 * where somebody stands.
 *
 * Steps are the ones the hire can see on the path page; a phase hidden from them is hidden here too. A user
 * without a path has no steps and no position, never an error.
 */
interface PathReadApi {
    /** Every visible step of [userId]'s path, keyed by step id. */
    fun stepsById(userId: UserId): Map<UUID, ResolvedPathStep>

    /** The step [reference] names on [userId]'s path, by id or by an unambiguous title; null otherwise. */
    fun resolveStep(userId: UserId, reference: String): ResolvedPathStep?

    /**
     * Ticks [taskId] on [userId]'s path, or unticks it. The step's own status is left alone.
     *
     * @throws org.springframework.web.server.ResponseStatusException 404 when [taskId] is not on that path.
     */
    fun setTaskFinished(userId: UserId, taskId: OnboardingTaskId, finished: Boolean)

    /** Where each of [userIds] stands; a user without a path is missing from the map. */
    fun positionsOf(userIds: Collection<UserId>): Map<UserId, OnboardingPosition>
}
