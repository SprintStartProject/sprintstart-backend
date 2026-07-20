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
 */
@Serializable
data class AiFaqGroup(
    val question: String,
    val count: Int,
    val questions: List<String>,
    val documents: List<AiFaqDocument>,
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
