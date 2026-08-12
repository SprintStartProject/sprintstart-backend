package com.sprintstart.sprintstartbackend.onboarding.external.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Ask the AI service to draw one subject from the project's own material.
 *
 * [subject] is the *question* — "how a request reaches the database" — and the one part of a diagram
 * a model chose. It aims retrieval and is asserted nowhere: every node comes back derived from a
 * retrieved chunk and cited, so a subject the corpus cannot support draws nothing.
 */
@Serializable
data class AssembleDiagramRequest(
    val subject: String,
    /**
     * The corpus fingerprint the cached picture was drawn from, when there is one.
     *
     * What makes a card that hydrates on every board load affordable: an unchanged corpus answers
     * `unchanged` with no retrieval and no generation.
     */
    @SerialName("last_fingerprint") val lastFingerprint: String? = null,
)

/**
 * One box: something the evidence shows this project has.
 *
 * Never uncited. An ungrounded node is dropped by the AI service before it is returned — unlike a
 * module page, there is no kind of node exempt from citing, because every box asserts that this
 * project contains this part.
 */
@Serializable
data class DiagramNodeSchema(
    val id: String,
    val label: String,
    val kind: String = "OTHER",
    val summary: String = "",
    val citations: List<CitationRefSchema> = emptyList(),
)

/** One arrow. Both endpoints reference nodes in the same diagram; the AI drops any that do not. */
@Serializable
data class DiagramEdgeSchema(
    @SerialName("from_id") val fromId: String,
    @SerialName("to_id") val toId: String,
    val kind: String = "RELATES_TO",
    val label: String = "",
)

/** A piece of existing material the picture drew on, listed on the diagram itself. */
@Serializable
data class DiagramSourceSchema(
    val filename: String,
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("artifact_type") val artifactType: String? = null,
)

@Serializable
data class DiagramSchema(
    val subject: String,
    val summary: String = "",
    val nodes: List<DiagramNodeSchema> = emptyList(),
    val edges: List<DiagramEdgeSchema> = emptyList(),
    val sources: List<DiagramSourceSchema> = emptyList(),
)

@Serializable
data class DiagramOutcome(
    val status: String,
    val diagram: DiagramSchema? = null,
    val provenance: AiProvenanceSchema? = null,
    @SerialName("chunks_retrieved") val chunksRetrieved: Int = 0,
    @SerialName("chunks_collapsed") val chunksCollapsed: Int = 0,
    @SerialName("nodes_dropped") val nodesDropped: Int = 0,
    @SerialName("edges_dropped") val edgesDropped: Int = 0,
    val notes: List<String> = emptyList(),
)
