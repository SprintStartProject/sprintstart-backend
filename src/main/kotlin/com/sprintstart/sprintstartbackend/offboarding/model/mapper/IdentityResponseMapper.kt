package com.sprintstart.sprintstartbackend.offboarding.model.mapper

import com.sprintstart.sprintstartbackend.offboarding.model.response.AliasLinkResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.IdentitiesResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.SeenIdentityResponse
import com.sprintstart.sprintstartbackend.offboarding.scoring.AliasLink
import com.sprintstart.sprintstartbackend.offboarding.scoring.SeenIdentity

/** Converts the links and the seen identities of a project to their response. */
fun toIdentitiesResponse(links: List<AliasLink>, seen: List<SeenIdentity>): IdentitiesResponse =
    IdentitiesResponse(links.map(AliasLink::toResponse), seen.map(SeenIdentity::toResponse))

/** Converts a link to its response. */
fun AliasLink.toResponse() = AliasLinkResponse(alias = alias.value, person = person.value)

private fun SeenIdentity.toResponse() = SeenIdentityResponse(
    identity = identity.value,
    person = person.value,
    touches = touches.mapKeys { it.key.value }.toSortedMap(),
    lastSeenAt = lastSeenAt,
)
