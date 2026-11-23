package com.sprintstart.sprintstartbackend.offboarding.scoring

import com.sprintstart.sprintstartbackend.shared.ids.UserId

/**
 * A registered member of a project and the identities in the evidence that belong to them.
 *
 * The evidence names people by commit email or login; a member only appears in a risk map under the
 * identities that were matched to them. A member with none cannot be ranked at all.
 *
 * @property member The member's account.
 * @property displayName The name SprintStart shows for them.
 * @property githubLogin The GitHub account they declared, `null` when they did not.
 * @property identities The identities in the evidence that are theirs, most recently active first.
 * @property suggestions The links that would make those identities count as one person, for a
 *           manager to confirm; empty when they already do.
 */
data class MemberMatch(
    val member: UserId,
    val displayName: String,
    val githubLogin: String?,
    val identities: List<SeenIdentity>,
    val suggestions: List<AliasLink>,
) {
    /** Whether any trace in the evidence is attributed to this member. */
    val matched: Boolean get() = identities.isNotEmpty()
}
