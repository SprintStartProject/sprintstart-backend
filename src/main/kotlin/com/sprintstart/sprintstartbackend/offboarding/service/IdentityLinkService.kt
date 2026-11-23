package com.sprintstart.sprintstartbackend.offboarding.service

import com.sprintstart.sprintstartbackend.offboarding.model.entity.PersonAlias
import com.sprintstart.sprintstartbackend.offboarding.model.exception.IdentityNotLinkedException
import com.sprintstart.sprintstartbackend.offboarding.repository.PersonAliasRepository
import com.sprintstart.sprintstartbackend.offboarding.scoring.AliasLink
import com.sprintstart.sprintstartbackend.offboarding.scoring.Identities
import com.sprintstart.sprintstartbackend.offboarding.scoring.PersonKey
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Keeps the confirmed links between the identities of one project's evidence. */
@Service
class IdentityLinkService(
    private val aliases: PersonAliasRepository,
) {
    /** The links confirmed for a project, ordered by alias. */
    @Transactional(readOnly = true)
    fun links(projectId: ProjectId): List<AliasLink> =
        aliases
            .findAllByProjectId(projectId.value)
            .map { AliasLink(PersonKey(it.alias), PersonKey(it.person)) }
            .sortedBy { it.alias.value }

    /** Who each identity of a project counts as. */
    @Transactional(readOnly = true)
    fun identities(projectId: ProjectId): Identities = Identities(links(projectId))

    /**
     * Confirms that two identities are one person.
     *
     * Whoever either identity already counts as is what gets joined, so linking an identity that is
     * already an alias merges the two people instead of chaining. Linking identities that already
     * count as one person changes nothing.
     *
     * @param projectId The project the confirmation is for.
     * @param alias One of the identities.
     * @param person The other identity; the merged person is named after what it counts as.
     * @return The project's links after the change.
     */
    @Transactional
    fun link(projectId: ProjectId, alias: PersonKey, person: PersonKey): List<AliasLink> {
        val identities = identities(projectId)
        val folded = identities.resolve(alias)
        val kept = identities.resolve(person)
        if (folded != kept) {
            aliases.findAllByProjectId(projectId.value).filter { it.person == folded.value }.forEach {
                it.person = kept.value
            }
            aliases.save(PersonAlias(projectId = projectId.value, alias = folded.value, person = kept.value))
        }
        return links(projectId)
    }

    /**
     * Takes back the link that folds an identity into a person.
     *
     * @throws IdentityNotLinkedException when the project has no such link.
     */
    @Transactional
    fun unlink(projectId: ProjectId, alias: PersonKey) {
        val removed = aliases.deleteByProjectIdAndAlias(projectId.value, alias.value)
        if (removed == 0) throw IdentityNotLinkedException(alias.value)
    }
}
