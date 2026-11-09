package com.sprintstart.sprintstartbackend.starterwork.model.mapper

import com.sprintstart.sprintstartbackend.starterwork.model.entity.TaskOrientationCitation
import com.sprintstart.sprintstartbackend.starterwork.model.entity.TaskOrientationPacket
import com.sprintstart.sprintstartbackend.starterwork.model.entity.TaskOrientationSection
import com.sprintstart.sprintstartbackend.starterwork.model.entity.TaskOrientationSource
import com.sprintstart.sprintstartbackend.starterwork.model.response.orientation.OrientationCitationResponse
import com.sprintstart.sprintstartbackend.starterwork.model.response.orientation.OrientationPacketResponse
import com.sprintstart.sprintstartbackend.starterwork.model.response.orientation.OrientationSectionResponse
import com.sprintstart.sprintstartbackend.starterwork.model.response.orientation.OrientationSourceResponse

fun TaskOrientationCitation.toResponse(): OrientationCitationResponse =
    OrientationCitationResponse(filename = filename, chunkId = chunkId, sourceUrl = sourceUrl)

fun TaskOrientationSection.toResponse(): OrientationSectionResponse =
    OrientationSectionResponse(
        step = step,
        title = title,
        body = body,
        // Provenance travels with the claim, never summarised away: this is what a hire checks the
        // packet against.
        citations = citations.sortedBy { it.position }.map { it.toResponse() },
    )

fun TaskOrientationSource.toResponse(): OrientationSourceResponse =
    OrientationSourceResponse(filename = filename, sourceUrl = sourceUrl, artifactType = artifactType)

fun TaskOrientationPacket.toResponse(): OrientationPacketResponse =
    OrientationPacketResponse(
        taskId = taskProposalId,
        taskTitle = taskTitle,
        summary = summary,
        sections = sections.sortedBy { it.position }.map { it.toResponse() },
        sources = sources.sortedBy { it.position }.map { it.toResponse() },
        assembledAt = assembledAt,
        origin = origin,
    )
