package com.sprintstart.sprintstartbackend.user.external

import java.util.UUID

/** Lets other modules record a user's activity and learn whether they are returning after a long absence. */
interface SessionActivityApi {
    /**
     * Stamps the user as active now and reports whether their previous activity lies further back
     * than the idle threshold.
     *
     * @param userId The user who is active.
     * @return `true` when the user was away longer than the idle threshold, `false` otherwise or on
     *   their first recorded activity.
     */
    fun recordVisit(userId: UUID): Boolean
}
