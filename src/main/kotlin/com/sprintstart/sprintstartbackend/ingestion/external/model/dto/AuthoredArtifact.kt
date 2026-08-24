package com.sprintstart.sprintstartbackend.ingestion.external.model.dto

/**
 * One artifact a person authored, reduced to what a prior can be built from.
 *
 * Carries no title or body: only *that* somebody worked here, and on what kind of thing.
 */
data class AuthoredArtifact(
    val artifactType: String,
    val repositoryFullName: String?,
    val labels: List<String>,
)
