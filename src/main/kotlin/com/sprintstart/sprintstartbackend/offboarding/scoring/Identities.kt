package com.sprintstart.sprintstartbackend.offboarding.scoring

import java.time.Instant

/**
 * Two identities a project's managers confirmed as one person.
 *
 * @property alias The identity that is folded into [person].
 * @property person The identity the alias stands for.
 */
data class AliasLink(
    val alias: PersonKey,
    val person: PersonKey,
)

/**
 * Who has left traces in the evidence, and which person they count as.
 *
 * @property identity The email or login as the evidence recorded it.
 * @property person The identity it counts as once the confirmed links are applied; the same as
 *           [identity] when nothing was linked.
 * @property touches How many traces each kind of evidence holds for the identity.
 * @property lastSeenAt When the identity last left a trace.
 */
data class SeenIdentity(
    val identity: PersonKey,
    val person: PersonKey,
    val touches: Map<SourceId, Int>,
    val lastSeenAt: Instant,
)

/**
 * Which identities are the same person.
 *
 * One developer shows up under a plain email in commits, a no-reply address in others and a login in
 * reviews. The links a project's managers confirmed fold those into one [PersonKey], so the scoring
 * counts them as one person. Every link points at a person that is not itself an alias, so resolving
 * once is enough.
 */
class Identities(
    links: List<AliasLink> = emptyList(),
) {
    private val personOf: Map<PersonKey, PersonKey> = links.associate { it.alias to it.person }

    /** The person [identity] counts as. */
    fun resolve(identity: PersonKey): PersonKey = personOf[identity] ?: identity

    /** The people [identities] count as, each once. */
    fun resolve(identities: Set<PersonKey>): Set<PersonKey> = identities.mapTo(mutableSetOf(), ::resolve)

    /** [history] with every touch credited to the person it counts as. */
    fun resolve(history: FileHistory): FileHistory =
        if (personOf.isEmpty()) {
            history
        } else {
            FileHistory(history.repository, history.path, history.touches.map { it.copy(person = resolve(it.person)) })
        }
}
