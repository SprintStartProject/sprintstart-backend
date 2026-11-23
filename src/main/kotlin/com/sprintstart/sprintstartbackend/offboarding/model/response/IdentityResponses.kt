package com.sprintstart.sprintstartbackend.offboarding.model.response

import java.time.Instant
import java.util.UUID

/**
 * Who the evidence of a project names and which of them a manager has linked.
 *
 * @property links The confirmed links.
 * @property seen Every identity the evidence names, most recently active first.
 */
data class IdentitiesResponse(
    val links: List<AliasLinkResponse>,
    val seen: List<SeenIdentityResponse>,
)

/** One identity confirmed to be another person's. */
data class AliasLinkResponse(
    val alias: String,
    val person: String,
)

/**
 * An identity the evidence names.
 *
 * @property identity The email or login as the evidence recorded it.
 * @property person Who it counts as; the same as `identity` when nothing was linked.
 * @property touches How many traces each kind of evidence holds for it.
 */
data class SeenIdentityResponse(
    val identity: String,
    val person: String,
    val touches: Map<String, Int>,
    val lastSeenAt: Instant,
)

/**
 * Which registered members the evidence can be attributed to.
 *
 * @property matched How many members have at least one identity in the evidence.
 * @property total How many members the project has.
 * @property members Every member with the identities that are theirs.
 */
data class MembersResponse(
    val matched: Int,
    val total: Int,
    val members: List<MemberMatchResponse>,
)

/**
 * A registered member and the identities in the evidence that are theirs.
 *
 * @property matched Whether any trace in the evidence is attributed to them; a member without one
 *           cannot be ranked.
 * @property suggestions Links that would make their identities count as one person, to be confirmed.
 * @property inactive Whether they have left no trace for so long that the ranking counts them as gone.
 * @property lastSeenAt When they last left a trace under any identity; `null` when they never did.
 */
data class MemberMatchResponse(
    val member: UUID,
    val displayName: String,
    val githubLogin: String?,
    val matched: Boolean,
    val identities: List<SeenIdentityResponse>,
    val suggestions: List<AliasLinkResponse>,
    val inactive: Boolean = false,
    val lastSeenAt: Instant? = null,
)
