package com.sprintstart.sprintstartbackend.insights.model.ai

import kotlinx.serialization.Serializable

/**
 * Grouped recurring questions returned by the AI service.
 */
@Serializable
data class AiFaqGroupingResponse(
    val groups: List<AiFaqGroup>,
)

/**
 * A single cluster of semantically similar questions.
 *
 * @property question the representative question describing the cluster
 * @property count total number of questions assigned to the cluster; may exceed [questions] size
 * @property questions a redacted sample of the questions in the cluster
 * @property documents the knowledge-base documents that answered questions in the cluster
 * @property title a short generated title naming what the cluster is about
 * @property questionIds ids of every question in the cluster, not just the sampled ones. The AI
 * service keeps no history, so these are what let this module recover when each question was asked
 * and rebuild the group's recency from it.
 */
@Serializable
data class AiFaqGroup(
    val question: String,
    val count: Int,
    val questions: List<AiFaqSampleQuestion> = emptyList(),
    val documents: List<AiFaqDocument> = emptyList(),
    val title: String? = null,
    val questionIds: List<String> = emptyList(),
)

/**
 * One redacted phrasing, with every ask that used it.
 *
 * All of them, not just the first: a phrasing used four times is four asks at four different
 * moments, and this module needs each one to keep the entry's trend exact and to say when the
 * phrasing was *last* used.
 *
 * @property ids identifiers of the chat messages asked in exactly this wording
 * @property text the redacted question text
 */
@Serializable
data class AiFaqSampleQuestion(
    val ids: List<String> = emptyList(),
    val text: String,
)

/**
 * A knowledge-base document reference returned by the AI service.
 *
 * @property id identifier of the document in the upstream knowledge base
 * @property source origin system of the document, for example confluence or github; may be absent
 */
@Serializable
data class AiFaqDocument(
    val id: String,
    val title: String,
    val source: String? = null,
)
