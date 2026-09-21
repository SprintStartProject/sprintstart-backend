package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.stereotype.Component
import java.util.UUID

/** An element of somebody's path that belongs to a member of the turn's project. */
data class ScopedElement(
    val element: PathElement,
    val owner: ProjectMember,
)

/**
 * Which onboarding content a project's manager may touch: the paths of the project's members.
 *
 * Paths belong to people, not projects — a path has one owner and no project — so an element is in
 * scope exactly when its owner is a member of the turn's project. Every content tool asks this, the
 * reads and the actions alike, and asks it again when a stored proposal is confirmed, so what a
 * manager is shown and what they may change cannot disagree.
 *
 * The other half of the same fact is that a member of several projects has *one* path. Changing it
 * changes it for the other projects' managers too; [alsoOn] is what lets a preview say so.
 */
@Component
class ContentScope(
    private val pathElements: PathElements,
    private val projectMembershipApi: ProjectMembershipApi,
    private val userApi: UserApi,
) {
    /**
     * The [kind] element [id] names, if it sits on the path of a member of [projectId].
     *
     * Null for everything else — no such element, or one on somebody else's path — and deliberately
     * not distinguishable, so the model cannot use a refusal to probe for ids elsewhere.
     */
    fun element(kind: PathElementKind, id: UUID?, projectId: UUID): ScopedElement? {
        val found = id?.let { pathElements.find(kind, it) } ?: return null
        val owner = member(found.ownerId, projectId) ?: return null
        return ScopedElement(found, owner)
    }

    /** The refusal for a stored proposal whose target is gone or out of scope since, or null when it is fine. */
    fun missing(kind: PathElementKind, id: UUID?, projectId: UUID): String? =
        goneSince(kind).takeIf { element(kind, id, projectId) == null }

    /** The member [memberId] names on [projectId], or null. */
    fun member(memberId: UUID?, projectId: UUID): ProjectMember? =
        memberId?.let { id -> projectMembershipApi.getProjectMembers(projectId).firstOrNull { it.userId == id } }

    /** The names of the other projects [userId] is on, sorted; empty when they are on this one only. */
    fun alsoOn(userId: UUID, projectId: UUID): List<String> =
        userApi
            .getUsersByIds(listOf(userId))
            .firstOrNull()
            ?.projects
            .orEmpty()
            .filter { it.projectId != projectId }
            .map { it.name }
            .sorted()

    /**
     * The sentence a preview adds when the path it changes also belongs to other projects, or empty.
     *
     * Said whatever the change is: nothing about a person's path is project-scoped, so there is no
     * change that stays here.
     */
    fun sharedNote(owner: ProjectMember, projectId: UUID): String {
        val others = alsoOn(owner.userId, projectId)
        return if (others.isEmpty()) {
            ""
        } else {
            "${owner.displayName} is also on ${others.joinToString(", ")}, and has one onboarding path for " +
                "all of their projects — so this changes it there too."
        }
    }
}

internal const val NOT_A_MEMBER_HERE =
    "That person is not on this project. Call find_member for the people who are, and pass the member_id " +
        "it gives."

internal const val LEFT_SINCE_HERE = "That person is no longer on this project, so nothing was changed."

/** What to tell the model when an id is not in scope, per kind. */
internal fun notInScope(kind: PathElementKind): String =
    "That ${kind.noun} is not on the onboarding path of anybody on this project. Call get_member_path for " +
        "somebody who is, and pass an id from it."

/** Why a stored proposal's target is no longer there: gone, or no longer on a member's path. */
internal fun goneSince(kind: PathElementKind): String =
    "That ${kind.noun} is gone, or no longer on the path of anybody on this project, so nothing was changed."
