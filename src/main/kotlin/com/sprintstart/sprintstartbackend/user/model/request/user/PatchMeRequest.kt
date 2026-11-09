package com.sprintstart.sprintstartbackend.user.model.request.user

import java.util.UUID

data class PatchMeRequest(
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val profileIcon: String? = null,
    /**
     * The GitHub account the user contributes as, which attributes a submitted pull request to them. Blank
     * clears it.
     */
    val githubLogin: String? = null,
    /**
     * The name the user appears under in Jira, which attributes an assigned issue to them. Blank clears it
     * and opts out of counting their Jira work.
     */
    val jiraDisplayName: String? = null,
    val projectsId: Set<UUID>,
)
