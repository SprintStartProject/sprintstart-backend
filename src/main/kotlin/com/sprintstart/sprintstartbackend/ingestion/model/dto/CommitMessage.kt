package com.sprintstart.sprintstartbackend.ingestion.model.dto

import java.util.UUID

/**
 * The message of one stored commit.
 *
 * @property commit The commit artifact.
 * @property message The full commit message; `null` when the provider sent none.
 */
data class CommitMessage(
    val commit: UUID,
    val message: String?,
)
