package com.sprintstart.sprintstartbackend.offboarding.model.request

import com.sprintstart.sprintstartbackend.offboarding.scoring.PersonKey
import com.sprintstart.sprintstartbackend.offboarding.scoring.ScoringProfile
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

/** A departure to simulate, with the date left open when it means "now". */
data class RiskRequest(
    val departing: Set<PersonKey>,
    val asOf: Instant?,
    val profile: ScoringProfile,
)

/**
 * The query parameters of a risk request, as they arrive.
 *
 * @property leaving The leaving people, each an email or a GitHub login; repeat the parameter for several.
 * @property asOf Replay the departure as of this moment instead of now.
 * @property halfLifeDays Override how fast old changes fade.
 * @property inactiveAfterDays Override how long without a change makes a person count as gone.
 * @property authorThreshold Override the fraction of a file's top degree of authorship that makes an author.
 * @property limit How many files to list; the summary always counts all of them.
 */
data class RiskParameters(
    val leaving: List<String> = emptyList(),
    val asOf: Instant? = null,
    val halfLifeDays: Double? = null,
    val inactiveAfterDays: Long? = null,
    val authorThreshold: Double? = null,
    val limit: Int? = null,
) {
    /** How many files to list. */
    val listLimit: Int get() = (limit ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)

    /**
     * Turns the parameters into a request.
     *
     * @throws ResponseStatusException with status 400 when nobody leaves, a person is blank, or a
     *         setting is out of range.
     */
    fun toRequest(): RiskRequest {
        try {
            require(leaving.isNotEmpty()) { "Name at least one leaving person with `leaving`" }
            val defaults = ScoringProfile()
            return RiskRequest(
                departing = leaving.map(PersonKey::parse).toSet(),
                asOf = asOf,
                profile = defaults.copy(
                    halfLifeDays = halfLifeDays ?: defaults.halfLifeDays,
                    inactiveAfterDays = inactiveAfterDays ?: defaults.inactiveAfterDays,
                    authorThreshold = authorThreshold ?: defaults.authorThreshold,
                ),
            )
        } catch (invalid: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.message, invalid)
        }
    }

    private companion object {
        const val DEFAULT_LIMIT = 100
        const val MAX_LIMIT = 1000
    }
}
