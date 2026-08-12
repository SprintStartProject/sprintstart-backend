package com.sprintstart.sprintstartbackend.onboarding.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.external.enums.VerificationType
import com.sprintstart.sprintstartbackend.onboarding.model.entity.CompetencyModule
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ModulePage
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ModulePageCitation
import com.sprintstart.sprintstartbackend.onboarding.model.response.module.CompetencyModuleResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.module.ModulePageCitationResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.module.ModulePageResponse

fun ModulePageCitation.toResponse(): ModulePageCitationResponse =
    ModulePageCitationResponse(filename = filename, chunkId = chunkId, sourceUrl = sourceUrl)

fun ModulePage.toResponse(): ModulePageResponse =
    ModulePageResponse(
        id = id,
        kind = kind,
        title = title,
        body = body,
        position = position,
        provenance = provenance,
        citations = citations.map { it.toResponse() },
        updatedAt = updatedAt,
    )

/**
 * Maps a module to its API response.
 *
 * @param competencyLabel The graph node's label, joined in by the caller. Falls back to the bare
 * key when the competency has been removed — the module is still real and still editable, so it
 * must not vanish from the authoring surface just because its node did.
 * @param verificationType The module check's grading type, when one is configured.
 */
fun CompetencyModule.toResponse(
    competencyLabel: String? = null,
    verificationType: VerificationType? = null,
): CompetencyModuleResponse =
    CompetencyModuleResponse(
        id = id,
        competencyKey = competencyKey,
        competencyLabel = competencyLabel ?: competencyKey,
        projectId = projectId,
        version = version,
        status = status,
        origin = origin,
        title = title,
        summary = summary,
        pages = pages.map { it.toResponse() },
        verificationType = verificationType,
        updatedAt = updatedAt,
    )
