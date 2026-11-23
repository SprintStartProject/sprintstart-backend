package com.sprintstart.sprintstartbackend.offboarding.service

import com.sprintstart.sprintstartbackend.offboarding.scoring.EvidenceSource
import com.sprintstart.sprintstartbackend.offboarding.scoring.SeenIdentity
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

/** Lists who left traces in a project's evidence, so a manager can see which identities still need linking. */
@Service
class SeenIdentityService(
    private val sources: List<EvidenceSource>,
    private val links: IdentityLinkService,
    private val clock: Clock,
) {
    /**
     * Every identity the evidence of a project names, most recently active first.
     *
     * @param projectId The project whose evidence is read.
     * @param asOf Ignore traces left after this moment; the moment of the request when left open.
     */
    fun seen(projectId: ProjectId, asOf: Instant?): List<SeenIdentity> {
        val moment = asOf ?: clock.instant()
        val identities = links.identities(projectId)
        return sources
            .flatMap { it.histories(projectId, moment) }
            .flatMap { it.touches }
            .groupBy { it.person }
            .map { (identity, touches) ->
                SeenIdentity(
                    identity = identity,
                    person = identities.resolve(identity),
                    touches = touches.groupingBy { it.source }.eachCount(),
                    lastSeenAt = touches.maxOf { it.at },
                )
            }.sortedWith(compareByDescending<SeenIdentity> { it.lastSeenAt }.thenBy { it.identity.value })
    }
}
