package com.sprintstart.sprintstartbackend.onboarding.model.response.module

import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContentProvenance
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ModulePageKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ModuleStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.VerificationType
import java.time.Instant
import java.util.UUID

data class ModulePageCitationResponse(
    val filename: String,
    val chunkId: String,
    val sourceUrl: String? = null,
)

data class ModulePageResponse(
    val id: UUID,
    val kind: ModulePageKind,
    val title: String,
    val body: String? = null,
    val position: Int,
    // Whether the AI or a human wrote this page. Shown to the author so they can see what a
    // re-synthesis pass would leave alone.
    val provenance: ContentProvenance,
    // The page's grounding, kept on persist so a claim can be followed back to its source.
    val citations: List<ModulePageCitationResponse> = emptyList(),
    val updatedAt: Instant,
)

/**
 * A module and its ordered pages.
 *
 * [verificationType] is echoed rather than the whole check: the rubric and canonical answer are
 * what the hire is being graded against, so they never travel on a hire-readable response. PMs
 * read the full check through the verification endpoint.
 */
data class CompetencyModuleResponse(
    val id: UUID,
    val competencyKey: String,
    val competencyLabel: String,
    val projectId: UUID,
    val version: Int,
    val status: ModuleStatus,
    val origin: ContentProvenance,
    val title: String,
    val summary: String? = null,
    val pages: List<ModulePageResponse>,
    val verificationType: VerificationType? = null,
    val updatedAt: Instant,
)

data class CompetencyModulesResponse(
    val modules: List<CompetencyModuleResponse>,
)
