package com.sprintstart.sprintstartbackend.offboarding.service

import com.sprintstart.sprintstartbackend.offboarding.scoring.AliasLink
import com.sprintstart.sprintstartbackend.offboarding.scoring.MemberMatch
import com.sprintstart.sprintstartbackend.offboarding.scoring.SeenIdentity
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Matches the identities in a project's evidence to the project's registered members.
 *
 * An identity belongs to a member when it is exactly their registered email or their declared GitHub
 * login. Nothing is linked here: where one member owns several identities the matches only suggest the
 * links, and a manager confirms them.
 */
@Service
class MemberIdentityService(
    private val seenIdentities: SeenIdentityService,
    private val members: ProjectMembershipApi,
    private val users: UserApi,
) {
    /**
     * Every member of a project with the identities that are theirs, ordered by name.
     *
     * @param projectId The project whose members and evidence are read.
     * @param asOf Ignore evidence from after this moment; the moment of the request when left open.
     */
    fun matches(projectId: ProjectId, asOf: Instant?): List<MemberMatch> {
        val ownedBy = seenIdentities.seen(projectId, asOf).groupBy(::ownerOf)
        return members
            .getProjectMembers(projectId.value)
            .map { member ->
                val identities = ownedBy[member.userId].orEmpty()
                MemberMatch(
                    member = UserId(member.userId),
                    displayName = member.displayName,
                    githubLogin = member.githubLogin,
                    identities = identities,
                    suggestions = suggestionsFor(identities),
                )
            }.sortedBy { it.displayName.lowercase() }
    }

    private fun ownerOf(seen: SeenIdentity): UUID? =
        users.findByExactEmailOrGithubLogin(seen.identity.value).map { it.userId }.orElse(null)

    /** Folds every person the member counts as into the one they are most active as, preferring a login. */
    private fun suggestionsFor(identities: List<SeenIdentity>): List<AliasLink> {
        val traces = identities
            .groupBy {
                it.person
            }.mapValues { (_, parts) -> parts.sumOf { it.touches.values.sum() } }
        if (traces.size < 2) return emptyList()
        val kept = traces.keys
            .sortedWith(
                compareBy({ '@' in it.value }, { -traces.getValue(it) }, { it.value }),
            ).first()
        return traces.keys
            .filter { it != kept }
            .sortedBy { it.value }
            .map { AliasLink(alias = it, person = kept) }
    }
}
