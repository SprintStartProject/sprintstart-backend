package com.sprintstart.sprintstartbackend.offboarding.model.response

import java.time.Instant

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
