package com.sprintstart.sprintstartbackend.user.model.request.user

import java.util.UUID

data class PatchMeRequest(
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val profileIcon: String? = null,
    // The GitHub account the user contributes as, used to attribute a submitted pull request to
    // them during artifact verification. Blank clears it.
    val githubLogin: String? = null,
    // The name the user appears under in Jira, used to attribute an assigned issue to them. Blank
    // clears it, which is also how somebody opts out of having their Jira work counted.
    val jiraDisplayName: String? = null,
    val projectsId: Set<UUID>,
)
