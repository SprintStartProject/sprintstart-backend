package com.sprintstart.sprintstartbackend.buddy.service

import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.user.external.UserApi
import java.util.UUID

/** Which projects a hire's buddy conversations draw on. */
internal class BuddyProjectScope(
    private val userApi: UserApi,
) {
    /**
     * The projects whose material this hire may be shown, as ids.
     *
     * Every project they are on rather than one of them: the buddy is not a per-project surface,
     * and a hire onboarding on two projects asking "how do we deploy" means either.
     *
     * An empty list is not "search everything": the AI service fails closed on an empty scope and
     * admits nothing. That is the intended reading, not a limitation. The same empty list comes
     * back from a user record that has not synced, a membership lookup that returned nothing, and
     * an account mid-provisioning, so it is not evidence the hire may see everything — and reading
     * it as intent would turn a missing value into an authorization decision. The failure modes are
     * not symmetric: fail-open shows one project's material to somebody on another, while
     * fail-closed returns an empty answer. [BuddyService.sendMessageForMe] refuses the turn and tells
     * the hire rather than letting an empty scope pass silently.
     */
    fun projectIdsFor(user: UserId): List<String> =
        userApi
            .getUsersByIds(listOf(user.value))
            .firstOrNull()
            ?.projects
            .orEmpty()
            .map { it.projectId.toString() }

    /** The project to bind a session to: the only one the user is on, or null when there is no single one. */
    fun singleProjectOf(projectIds: List<String>): UUID? =
        projectIds.singleOrNull()?.let { UUID.fromString(it) }
}
