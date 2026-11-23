package com.sprintstart.sprintstartbackend.ingestion.external.model.dto

import java.time.Instant

/**
 * One change of one file by one commit author, as the project's repositories recorded it.
 *
 * @property repository The repository key, for example `github:owner/repo`; a path is only
 *           comparable within its own repository.
 * @property path The file's path after the commit.
 * @property renamedFrom The path the file had before the commit when the commit moved it, `null`
 *           when it kept its path.
 * @property authorEmail The commit author's email, exactly as Git recorded it.
 * @property changedAt When the commit was made.
 */
data class FileContribution(
    val repository: String,
    val path: String,
    val renamedFrom: String?,
    val authorEmail: String,
    val changedAt: Instant,
)
