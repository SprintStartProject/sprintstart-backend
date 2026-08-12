package com.sprintstart.sprintstartbackend.user.model.response.user

import com.sprintstart.sprintstartbackend.user.external.enums.GithubLoginSource
import com.sprintstart.sprintstartbackend.user.external.enums.GithubLoginVerification
import com.sprintstart.sprintstartbackend.user.external.enums.Role
import java.time.Instant
import java.util.UUID

data class GetUserResponse(
    val id: UUID,
    val authId: String,
    val username: String,
    val email: String? = null,
    val firstName: String,
    val lastName: String,
    val projectIds: Set<UUID>,
    val roles: Set<Role>,
    val projectRoles: List<ProjectRoleSummary>,
    val permissionGroup: Role,
    val enabled: Boolean,
    val profileIcon: String? = null,
    val githubLogin: String? = null,
    val githubLoginSource: GithubLoginSource? = null,
    /**
     * What GitHub said about whether [githubLogin] exists, or **null when nobody has an answer**.
     *
     * Null is three cases the reader does not need to tell apart, because the action for all of
     * them is the same — try again later: never checked, checked and GitHub would not say, and no
     * login declared at all. It is emphatically **not** [GithubLoginVerification.NOT_FOUND]; only a
     * definitive answer from GitHub is ever recorded, and telling somebody their perfectly good
     * username does not exist is worse than telling them nothing.
     *
     * Cleared whenever the login changes, so a verdict never outlives the value it was about.
     */
    val githubLoginVerification: GithubLoginVerification? = null,
    /** When that verdict was reached. Null exactly when [githubLoginVerification] is. */
    val githubLoginVerifiedAt: Instant? = null,
    /**
     * The name this user appears under in Jira, as stored.
     *
     * No verification field beside it, deliberately: there is nothing to ask. GitHub can be asked
     * whether an account exists; Jira renders whatever name a person set, so "does this name exist"
     * has no answer worth showing. What it does have is a namesake risk no check would catch.
     */
    val jiraDisplayName: String? = null,
)

data class ProjectRoleSummary(
    val id: UUID,
    val name: String,
)
