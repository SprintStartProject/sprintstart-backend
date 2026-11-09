package com.sprintstart.sprintstartbackend.assistant.external

/**
 * What the sender chose for one buddy turn, apart from the message itself.
 *
 * With [capabilitiesEnabled] false the mentor answers from the project's material and does nothing
 * else: no tools are mounted. The [filters] are sent on every hop, because the AI service applies
 * them on whichever hop the model searches. [currentPage] is the app path the sender was on.
 */
data class BuddyTurnOptions(
    val capabilitiesEnabled: Boolean = true,
    val filters: BuddySessionFilters? = null,
    val currentPage: String? = null,
)
