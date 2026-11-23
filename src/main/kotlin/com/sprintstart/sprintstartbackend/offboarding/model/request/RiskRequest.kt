package com.sprintstart.sprintstartbackend.offboarding.model.request

import com.sprintstart.sprintstartbackend.offboarding.scoring.KnowledgeModel
import com.sprintstart.sprintstartbackend.offboarding.scoring.KnowledgeSettings
import com.sprintstart.sprintstartbackend.offboarding.scoring.PersonKey
import com.sprintstart.sprintstartbackend.offboarding.scoring.SourceId
import com.sprintstart.sprintstartbackend.offboarding.scoring.WeightProfile
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

/** A departure to simulate, with the date left open when it means "now". */
data class RiskRequest(
    val departing: Set<PersonKey>,
    val asOf: Instant?,
    val profile: WeightProfile,
)

/**
 * The query parameters of a risk request, as they arrive.
 *
 * @property leaving The leaving people, each an email or a GitHub login; repeat the parameter for several.
 * @property asOf Replay the departure as of this moment instead of now.
 * @property halfLifeDays Override how fast old changes fade.
 * @property inactiveAfterDays Override how long without a change makes a person count as gone.
 * @property authorThreshold Override the fraction of a file's top degree of authorship that makes an author.
 * @property reviewWeight Let reviews count with this weight next to commits, which count 1; left out, reviews
 *           are not used.
 * @property knowledge How to decide who knows a file: `authorship` (the default) or `time-weighted`.
 * @property knowsAtLeast With `time-weighted`, the weight of evidence that makes a person know a file.
 * @property limit How many files to list; the summary always counts all of them.
 */
data class RiskParameters(
    val leaving: List<String> = emptyList(),
    val asOf: Instant? = null,
    val halfLifeDays: Double? = null,
    val inactiveAfterDays: Long? = null,
    val authorThreshold: Double? = null,
    val reviewWeight: Double? = null,
    val knowledge: String? = null,
    val knowsAtLeast: Double? = null,
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
            val defaults = WeightProfile()
            return RiskRequest(
                departing = leaving.map(PersonKey::parse).toSet(),
                asOf = asOf,
                profile = defaults.copy(
                    halfLifeDays = halfLifeDays ?: defaults.halfLifeDays,
                    inactiveAfterDays = inactiveAfterDays ?: defaults.inactiveAfterDays,
                    authorThreshold = authorThreshold ?: defaults.authorThreshold,
                    sourceWeights = sourceWeights(),
                    knowledge = knowledgeSettings(defaults.knowledge),
                ),
            )
        } catch (invalid: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.message, invalid)
        }
    }

    private fun knowledgeSettings(defaults: KnowledgeSettings): KnowledgeSettings =
        defaults.copy(
            model = knowledge?.let(::modelNamed) ?: defaults.model,
            knowsAtLeast = knowsAtLeast ?: defaults.knowsAtLeast,
        )

    private fun modelNamed(name: String): KnowledgeModel =
        KnowledgeModel.entries.firstOrNull { it.name.equals(name.replace('-', '_'), ignoreCase = true) }
            ?: throw IllegalArgumentException("Unknown knowledge model `$name`; use authorship or time-weighted")

    private fun sourceWeights(): Map<SourceId, Double> =
        mapOf(SourceId.COMMITS to 1.0) + listOfNotNull(reviewWeight?.let { SourceId.REVIEWS to it })

    private companion object {
        const val DEFAULT_LIMIT = 100
        const val MAX_LIMIT = 1000
    }
}
