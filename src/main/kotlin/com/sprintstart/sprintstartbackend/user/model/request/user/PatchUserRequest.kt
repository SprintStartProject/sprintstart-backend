package com.sprintstart.sprintstartbackend.user.model.request.user

import com.sprintstart.sprintstartbackend.user.external.enums.Role
import java.util.UUID

data class PatchUserRequest(
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val permissionGroup: Role? = null,
    // Set by a PM/HR, so it is recorded as PM_CONFIRMED rather than the user's own claim.
    val githubLogin: String? = null,
    // No source enum alongside it, unlike githubLogin: nothing reads how a Jira name was
    // established, and a column recording it would be one more thing to keep true for no reader.
    val jiraDisplayName: String? = null,
    val projectsId: Set<UUID> = emptySet(),
)
