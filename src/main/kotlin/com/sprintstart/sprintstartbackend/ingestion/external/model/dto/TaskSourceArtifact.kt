package com.sprintstart.sprintstartbackend.ingestion.external.model.dto

/**
 * The text of the artifact a task came from.
 *
 * Carries body and labels, unlike [AuthoredArtifact]: the retrieval this drives has to see the
 * task's own words.
 */
data class TaskSourceArtifact(
    val title: String?,
    val body: String?,
    val labels: List<String>,
    val sourceUrl: String?,
)
