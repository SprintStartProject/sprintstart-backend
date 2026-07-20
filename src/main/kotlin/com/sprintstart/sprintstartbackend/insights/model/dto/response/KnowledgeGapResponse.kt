package com.sprintstart.sprintstartbackend.insights.model.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

@Schema(description = "A component that is missing critical documentation.")
data class KnowledgeGapResponse(
    @field:Schema(description = "Stable identifier of the gap.")
    val id: UUID,
    @field:Schema(description = "Name of the component that has gaps.")
    val component: String,
    @field:Schema(description = "Document types the component is missing, for example runbook or adr.")
    val missingTypes: List<String>,
    @field:Schema(description = "Document types the component already has.")
    val presentTypes: List<String>,
    @field:Schema(description = "When the component was last written into the AI index (most recent ingestion).")
    val lastIngested: Instant,
    @field:Schema(description = "When the component was first ingested. Null when it has no ingested artifacts.")
    val firstIngested: Instant?,
    @field:Schema(description = "When this gap was last (re)analyzed by a knowledge-gaps refresh.")
    val refreshedAt: Instant,
    @field:Schema(
        description = "People responsible for the component. Enriched by the backend; empty until owners are assigned.",
    )
    val owners: List<KnowledgeGapOwnerResponse>,
    @field:Schema(description = "Impact level of the gap: high, medium or low.")
    val severity: String,
)

@Schema(description = "A person responsible for a component with a knowledge gap.")
data class KnowledgeGapOwnerResponse(
    @field:Schema(description = "Identifier of the owning user.")
    val id: String,
    @field:Schema(description = "Username of the owner.")
    val username: String,
    @field:Schema(description = "First name of the owner.")
    val firstname: String,
    @field:Schema(description = "Last name of the owner.")
    val lastname: String,
    @field:Schema(description = "Project role of the owner, for example Backend Developer. May be null.")
    val role: String?,
)
