package com.sprintstart.sprintstartbackend.insights.model.ai

import kotlinx.serialization.Serializable

/**
 * Asks the AI service where a single freshly asked question belongs.
 *
 * The AI service holds no history, so the existing FAQ structure travels with the request. It is
 * the *structure* rather than the question history: [categories] is bounded by the category
 * ceiling and [groups] by a candidate limit, which is what keeps this affordable to run on every
 * chat message.
 *
 * @property question the question a user just asked
 * @property categories categories the project already uses
 * @property groups candidate groups the question could join
 */
@Serializable
data class AiFaqClassifyRequest(
    val projectId: String,
    val question: String,
    val categories: List<AiFaqCategory> = emptyList(),
    val groups: List<AiFaqGroupRef> = emptyList(),
)

/**
 * A category the project already uses, with its current weight.
 *
 * The counts let the AI service tell an established topic from a barely-used one, which is what
 * keeps it from absorbing a large category into a small one when consolidating.
 */
@Serializable
data class AiFaqCategory(
    val name: String,
    val groupCount: Int,
    val questionCount: Int,
)

/**
 * A group as the AI service sees it: enough to judge a match, nothing more.
 */
@Serializable
data class AiFaqGroupRef(
    val id: String,
    val question: String,
    val category: String,
    val count: Int,
)

/**
 * Where the AI service decided one question belongs.
 *
 * @property relevant false for greetings and smalltalk; the rest of the response is meaningless
 * then and the question is dropped rather than surfaced as an FAQ
 * @property question the question's text with personally identifiable information removed
 * @property category the category to file it under, possibly one this module has not seen before
 * @property groupId the existing group it joins, or null to open a new one
 * @property documents documents answering a newly opened group; empty when it joined an existing
 * one, which already carries its own
 */
@Serializable
data class AiFaqClassifyResponse(
    val relevant: Boolean,
    val question: String = "",
    val category: String? = null,
    val groupId: String? = null,
    val documents: List<AiFaqDocument> = emptyList(),
)

/**
 * Asks the AI service to reduce an over-grown category set back under [targetMax].
 *
 * Only category metadata travels — no question text — which is what makes this cheap enough to run
 * whenever the ceiling is crossed.
 */
@Serializable
data class AiFaqConsolidateCategoriesRequest(
    val categories: List<AiFaqCategory>,
    val targetMax: Int,
)

/**
 * Asks the AI service to fold duplicate groups inside one over-full category together.
 */
@Serializable
data class AiFaqMergeGroupsRequest(
    val groups: List<AiFaqGroupRef>,
    val targetMax: Int,
)

/**
 * A merge plan returned by the AI service.
 *
 * It is a proposal, not an instruction: this module applies it and stays the owner of the data. An
 * empty list means nothing was safely mergeable, in which case staying over the limit is the
 * intended outcome — merging distinct topics would be worse.
 */
@Serializable
data class AiFaqMergeResponse(
    val merges: List<AiFaqMerge> = emptyList(),
)

/**
 * Fold [sources] into [into].
 *
 * For categories these are names and [into] may be one the model invented; for groups they are ids
 * and [into] is always an existing group, since it keeps the stored samples and documents.
 */
@Serializable
data class AiFaqMerge(
    val into: String,
    val sources: List<String> = emptyList(),
)
