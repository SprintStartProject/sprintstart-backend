package com.sprintstart.sprintstartbackend.onboarding.external.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AssembleOrientationRequest(
    @SerialName("task_title") val taskTitle: String,
    @SerialName("task_body") val taskBody: String = "",
    val labels: List<String> = emptyList(),
    @SerialName("touched_paths") val touchedPaths: List<String> = emptyList(),
    @SerialName("last_fingerprint") val lastFingerprint: String? = null,
)

/**
 * One section of an assembled packet, belonging to exactly one step.
 *
 * Every section carries the chunks it was assembled from. Unlike a module page, there is no kind
 * exempt from citing: a packet section that cites nothing has already been dropped by the AI
 * service, so an empty [citations] list here means something went wrong upstream, not that the
 * section is fine.
 */
@Serializable
data class OrientationSectionSchema(
    val step: String,
    val title: String,
    val body: String = "",
    val citations: List<CitationRefSchema> = emptyList(),
)

/** A piece of existing material the packet drew on, listed on the packet itself. */
@Serializable
data class OrientationSourceSchema(
    val filename: String,
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("artifact_type") val artifactType: String? = null,
)

@Serializable
data class OrientationPacketSchema(
    @SerialName("task_title") val taskTitle: String,
    val summary: String = "",
    val sections: List<OrientationSectionSchema> = emptyList(),
    val sources: List<OrientationSourceSchema> = emptyList(),
)

@Serializable
data class OrientationOutcome(
    val status: String,
    val packet: OrientationPacketSchema? = null,
    val provenance: AiProvenanceSchema? = null,
    @SerialName("chunks_retrieved") val chunksRetrieved: Int = 0,
    @SerialName("chunks_collapsed") val chunksCollapsed: Int = 0,
    @SerialName("sections_dropped") val sectionsDropped: Int = 0,
    val notes: List<String> = emptyList(),
)
