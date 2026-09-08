package com.sprintstart.sprintstartbackend.user.external

import java.time.Instant
import java.util.UUID

/**
 * Who belongs to a project, for modules that reason about a team rather than about a person.
 *
 * Split from [UserApi] rather than added to it: that interface answers "tell me about this user",
 * this one answers "tell me about this project's people", and they are used by different callers
 * for different reasons. (It also keeps both under the per-type function budget, which is the
 * mechanical reason the split happened when it did — but the seam is a real one.)
 */
interface ProjectMembershipApi {
    /**
     * Everyone assigned to a project, with the two facts onboarding measurement needs: when they
     * joined, and which GitHub account their work is attributable to.
     *
     * @param projectId The project whose members to read.
     * @return One entry per assigned member; empty when the project has none or does not exist.
     */
    fun getProjectMembers(projectId: UUID): List<ProjectMember>
}

/**
 * A project member, as onboarding measurement sees them.
 *
 * [joinedAt] is nullable because assignments made before it was recorded have no honest value to
 * backfill — a member with no join date is reported as "clock unknown", never as instantaneous.
 * [githubLogin] is nullable because nobody is forced to declare one; without it their pull requests
 * cannot be attributed and their timeline is necessarily incomplete, which the metrics say out loud
 * rather than reporting as zero activity.
 */
data class ProjectMember(
    val userId: UUID,
    val displayName: String,
    val githubLogin: String?,
    val joinedAt: Instant?,
    /**
     * The name this member appears under in a connected issue tracker, if they declared one.
     *
     * Not the same thing as [displayName], and never a substitute for it. [displayName] is
     * whatever SprintStart renders from their first and last name; this is what *Jira* renders,
     * which is a different string as often as not. Falling back to one for the other would attribute
     * somebody's issues by a coincidence of spelling — silently right often enough to look fine.
     */
    val jiraDisplayName: String? = null,
)
