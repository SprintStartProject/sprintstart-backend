package com.sprintstart.sprintstartbackend.offboarding.model.request

import com.sprintstart.sprintstartbackend.offboarding.scoring.AliasLink
import com.sprintstart.sprintstartbackend.offboarding.scoring.PersonKey
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/**
 * A confirmation that two identities are one person.
 *
 * @property alias One identity, an email or a GitHub login.
 * @property person The other identity, an email or a GitHub login.
 */
data class LinkIdentitiesRequest(
    val alias: String,
    val person: String,
) {
    /**
     * Reads both identities.
     *
     * @throws ResponseStatusException with status 400 when either identity is blank.
     */
    fun toLink(): AliasLink = AliasLink(parseIdentity(alias), parseIdentity(person))
}

/**
 * Reads an identity somebody typed in: an email or a GitHub login.
 *
 * @throws ResponseStatusException with status 400 when [raw] is blank.
 */
fun parseIdentity(raw: String): PersonKey =
    try {
        PersonKey.parse(raw)
    } catch (invalid: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.message, invalid)
    }
