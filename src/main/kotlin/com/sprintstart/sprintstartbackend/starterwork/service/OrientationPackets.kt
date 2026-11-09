package com.sprintstart.sprintstartbackend.starterwork.service

import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.starterwork.external.enums.OrientationOrigin
import com.sprintstart.sprintstartbackend.starterwork.external.enums.OrientationStep
import com.sprintstart.sprintstartbackend.starterwork.external.model.OrientationOutcome
import com.sprintstart.sprintstartbackend.starterwork.external.model.OrientationPacketSchema
import com.sprintstart.sprintstartbackend.starterwork.model.entity.StarterWorkTaskProposal
import com.sprintstart.sprintstartbackend.starterwork.model.entity.TaskOrientationCitation
import com.sprintstart.sprintstartbackend.starterwork.model.entity.TaskOrientationPacket
import com.sprintstart.sprintstartbackend.starterwork.model.entity.TaskOrientationSection
import com.sprintstart.sprintstartbackend.starterwork.model.entity.TaskOrientationSource
import com.sprintstart.sprintstartbackend.starterwork.model.request.orientation.AuthorOrientationRequest
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

/** An authored packet needs at least one section, each with a title and a body. */
internal fun requireValidAuthoring(request: AuthorOrientationRequest) {
    val invalid = request.sections.isEmpty() ||
        request.sections.any { it.title.isBlank() || it.body.isBlank() }
    if (invalid) {
        throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Orientation must have at least one section, each with a non-blank title and body",
        )
    }
}

/** The packet a person wrote for [proposal], ready to save. */
internal fun humanOrientationPacket(
    proposal: StarterWorkTaskProposal,
    project: ProjectId,
    request: AuthorOrientationRequest,
    assembledAt: Instant,
): TaskOrientationPacket {
    val packet = TaskOrientationPacket(
        taskProposalId = proposal.id,
        projectId = project.value,
        taskTitle = proposal.title,
        origin = OrientationOrigin.HUMAN,
        summary = request.summary?.trim()?.takeIf { it.isNotBlank() },
        // A human packet stands on nothing the corpus fingerprint tracks, so it carries none: this
        // is exactly why getForHire never revalidates it.
        corpusFingerprint = null,
        model = null,
        assembledAt = assembledAt,
    )

    packet.addAuthoredSections(request)
    packet.addAuthoredSources(request)

    return packet
}

/** The packet the AI service assembled for [context]'s task, ready to save. */
internal fun aiOrientationPacket(
    context: TaskContext,
    outcome: OrientationOutcome,
    schema: OrientationPacketSchema,
    assembledAt: Instant,
): TaskOrientationPacket {
    val packet = TaskOrientationPacket(
        taskProposalId = context.proposalId,
        projectId = context.projectId,
        taskTitle = schema.taskTitle.ifBlank { context.title },
        origin = OrientationOrigin.AI,
        summary = schema.summary.takeIf { it.isNotBlank() },
        corpusFingerprint = outcome.provenance?.corpusFingerprint,
        model = outcome.provenance?.model,
        assembledAt = assembledAt,
    )

    packet.addAssembledSections(schema)
    packet.addAssembledSources(schema)

    return packet
}

private fun TaskOrientationPacket.addAuthoredSections(request: AuthorOrientationRequest) {
    request.sections.forEachIndexed { index, section ->
        sections.add(
            TaskOrientationSection(
                packet = this,
                step = section.step,
                title = section.title.trim(),
                body = section.body.trim(),
                position = index,
            ).apply {
                section.citations.forEachIndexed { citationIndex, citation ->
                    citations.add(
                        TaskOrientationCitation(
                            section = this,
                            filename = citation.filename.trim(),
                            // A human citation has no retrieval chunk; the client never reads
                            // chunkId, so an empty string keeps the column non-null harmlessly.
                            chunkId = "",
                            sourceUrl = citation.sourceUrl?.trim()?.takeIf { it.isNotBlank() },
                            position = citationIndex,
                        ),
                    )
                }
            },
        )
    }
}

/**
 * Sources are derived from the distinct citation links, never authored separately. A packet with
 * no citations simply has no sources.
 */
private fun TaskOrientationPacket.addAuthoredSources(request: AuthorOrientationRequest) {
    request.sections
        .flatMap { it.citations }
        .map { it.filename.trim() to it.sourceUrl?.trim()?.takeIf { url -> url.isNotBlank() } }
        .distinct()
        .forEachIndexed { index, (filename, sourceUrl) ->
            sources.add(
                TaskOrientationSource(
                    packet = this,
                    filename = filename,
                    sourceUrl = sourceUrl,
                    artifactType = null,
                    position = index,
                ),
            )
        }
}

private fun TaskOrientationPacket.addAssembledSections(schema: OrientationPacketSchema) {
    schema.sections
        .mapNotNull { section -> section.step.toStepOrNull()?.let { it to section } }
        .forEachIndexed { index, (step, section) ->
            sections.add(
                TaskOrientationSection(
                    packet = this,
                    step = step,
                    title = section.title,
                    body = section.body,
                    position = index,
                ).apply {
                    section.citations.forEachIndexed { citationIndex, citation ->
                        citations.add(
                            TaskOrientationCitation(
                                section = this,
                                filename = citation.filename,
                                chunkId = citation.chunkId,
                                sourceUrl = citation.sourceUrl,
                                position = citationIndex,
                            ),
                        )
                    }
                },
            )
        }
}

private fun TaskOrientationPacket.addAssembledSources(schema: OrientationPacketSchema) {
    schema.sources.forEachIndexed { index, source ->
        sources.add(
            TaskOrientationSource(
                packet = this,
                filename = source.filename,
                sourceUrl = source.sourceUrl,
                artifactType = source.artifactType,
                position = index,
            ),
        )
    }
}

/** An unrecognised step is dropped rather than stored: the enum is the contract, not a hint. */
private fun String.toStepOrNull(): OrientationStep? =
    OrientationStep.entries.firstOrNull { it.name == trim().uppercase() }
