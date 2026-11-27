package com.sprintstart.sprintstartbackend.ingestion.external.model.dto

import java.util.UUID

/**
 * A ticket a commit names in its message.
 *
 * @property commit The commit artifact, as [FileContribution.commit] names it.
 * @property issueKey The ticket's key, for example `APP-42`.
 */
data class CommitTicket(
    val commit: UUID,
    val issueKey: String,
)
