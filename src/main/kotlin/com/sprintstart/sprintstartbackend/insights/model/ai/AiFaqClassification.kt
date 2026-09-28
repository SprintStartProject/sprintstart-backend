package com.sprintstart.sprintstartbackend.insights.model.ai

import kotlinx.serialization.Serializable

/**
 * Asks the AI service where a single freshly asked question belongs.
 *
 * The AI service holds no history, so the candidate entries travel with the request. They are a
 * bounded selection rather than everything the project ever asked, which is what keeps this
 * affordable to run on every chat message.
 *
 * @property question the question a user just asked
 * @property groups candidate entries the question could join
 */
@Serializable
data class AiFaqClassifyRequest(
    val projectId: String,
    val question: String,
    val groups: List<AiFaqGroupRef> = emptyList(),
)

/**
 * An entry as the AI service sees it: enough to judge a match, nothing more.
 *
 * Both [title] and [question] travel. The title is the summary the model matches the topic
 * against; the verbatim question is what keeps "start the frontend" and "start the backend"
 * distinguishable, which a summarised title can lose.
 */
@Serializable
data class AiFaqGroupRef(
    val id: String,
    val question: String,
    val title: String,
    val count: Int,
)

/**
 * Where the AI service decided one question belongs.
 *
 * @property relevant false for greetings and smalltalk; the rest of the response is meaningless
 * then and the question is dropped rather than surfaced as an FAQ
 * @property question the question's text with personally identifiable information removed
 * @property title the title of the entry it belongs to; for a matched entry, its existing one
 * @property groupId the existing entry it joins, or null to open a new one
 * @property documents documents answering a newly opened entry; empty when it joined an existing
 * one, which already carries its own
 */
@Serializable
data class AiFaqClassifyResponse(
    val relevant: Boolean,
    val question: String = "",
    val title: String = "",
    val groupId: String? = null,
    val documents: List<AiFaqDocument> = emptyList(),
)

/**
 * Asks the AI service to fold duplicate entries together once the FAQ is over its ceiling.
 *
 * Only titles, representative questions and counts travel — never the stored phrasings — which is
 * what makes this cheap enough to run whenever the ceiling is crossed.
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
 * intended outcome — merging two distinct questions would be worse.
 */
@Serializable
data class AiFaqMergeResponse(
    val merges: List<AiFaqMerge> = emptyList(),
)

/**
 * Fold [sources] into [into].
 *
 * All are entry ids, and [into] is always an existing entry: it keeps the stored samples, title
 * and documents.
 */
@Serializable
data class AiFaqMerge(
    val into: String,
    val sources: List<String> = emptyList(),
)
