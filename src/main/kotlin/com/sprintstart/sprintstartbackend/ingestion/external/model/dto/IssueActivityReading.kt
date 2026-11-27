package com.sprintstart.sprintstartbackend.ingestion.external.model.dto

import java.time.Instant

/**
 * One person working on a ticket at one moment.
 *
 * @property issueKey The ticket's key, for example `APP-42`.
 * @property accountId The person's account ID at the tracker; `null` when the tracker sent none.
 * @property displayName The person's name as the tracker shows it.
 * @property at When the person did it.
 */
data class IssueActivityReading(
    val issueKey: String,
    val accountId: String?,
    val displayName: String,
    val at: Instant,
)
