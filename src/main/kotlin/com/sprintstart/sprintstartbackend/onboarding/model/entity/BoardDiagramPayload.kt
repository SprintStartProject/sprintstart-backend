package com.sprintstart.sprintstartbackend.onboarding.model.entity

import kotlinx.serialization.Serializable

/**
 * A cached diagram's picture, as stored on [BoardDiagram].
 *
 * Deliberately its *own* model rather than the AI service's wire DTO stored verbatim. A cache that
 * is the wire format is a cache that changes meaning whenever the contract does, and rows written
 * before a rename would decode into something subtly wrong rather than failing. This is the shape
 * the board serves, and mapping into it at store time is where a contract change gets noticed.
 */
@Serializable
data class BoardDiagramPayload(
    val summary: String? = null,
    val nodes: List<BoardDiagramNodePayload> = emptyList(),
    val edges: List<BoardDiagramEdgePayload> = emptyList(),
    val sources: List<BoardDiagramSourcePayload> = emptyList(),
)

/**
 * One box.
 *
 * [citations] is never empty on a node that reached here: the AI service drops an ungrounded node
 * before it is ever returned, so an empty list means something went wrong upstream rather than that
 * the node is fine. A box is an assertion that this project has this part, and the citation is what
 * lets a reader check it.
 */
@Serializable
data class BoardDiagramNodePayload(
    val id: String,
    val label: String,
    val kind: String,
    val summary: String? = null,
    val citations: List<BoardDiagramCitationPayload> = emptyList(),
)

/** One arrow. Both endpoints are ids of nodes in the same payload — the AI drops any that are not. */
@Serializable
data class BoardDiagramEdgePayload(
    val fromId: String,
    val toId: String,
    val kind: String,
    val label: String? = null,
)

@Serializable
data class BoardDiagramCitationPayload(
    val filename: String,
    val chunkId: String? = null,
    val sourceUrl: String? = null,
)

/** A piece of material the picture drew on, so "this is wrong" has somewhere to point. */
@Serializable
data class BoardDiagramSourcePayload(
    val filename: String,
    val sourceUrl: String? = null,
    val artifactType: String? = null,
)
