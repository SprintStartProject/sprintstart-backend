package com.sprintstart.sprintstartbackend.ingestion.external.model.dto

/**
 * How long a repository takes to answer a pull request, and how many go unanswered.
 *
 * [medianHoursToFirstResponse] is null when no ingested pull request here has been answered at
 * all — which is *worse* than a slow median, not unknown. Callers must not read it as "no data".
 */
data class RepositoryResponsiveness(
    val repositoryFullName: String,
    val medianHoursToFirstResponse: Long?,
    val answeredCount: Int,
    val unansweredCount: Int,
)
