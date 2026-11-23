package com.sprintstart.sprintstartbackend.ingestion.model.exceptions

/**
 * Evidence about an artifact was recorded before the artifact it belongs to was stored.
 *
 * The artifact is persisted immediately before its evidence is recorded, so this means that step was
 * skipped or failed, not that the artifact is unknown.
 */
class EvidenceArtifactNotFoundException(
    sourceId: String,
) : RuntimeException("Artifact $sourceId not found, so the evidence about it cannot be recorded")
