package com.sprintstart.sprintstartbackend.ingestion.external.model.dto

/**
 * How many of the lines a file has now one author wrote.
 *
 * @property repository The repository key, for example `github:owner/repo`.
 * @property path The path of the file in the repository.
 * @property authorEmail The lower-cased email of the author, exactly as Git recorded it.
 * @property lines How many lines of the file the author wrote.
 */
data class FileAuthorshipReading(
    val repository: String,
    val path: String,
    val authorEmail: String,
    val lines: Int,
)
