package com.sprintstart.sprintstartbackend.ingestion.external.model.dto

import java.time.Instant

/**
 * One reviewer having seen one file of a pull request, as the project's repositories recorded it.
 *
 * A review is of the whole pull request; it is listed once for every file the pull request changed,
 * because what it says about a person is which files they have looked at.
 *
 * @property repository The repository key, for example `github:owner/repo`; a path is only
 *           comparable within its own repository.
 * @property path The file's path when the pull request changed it.
 * @property reviewerLogin The reviewer's account login at the source, lower-cased.
 * @property reviewedAt When the reviewer submitted their latest review of the pull request.
 */
data class FileReview(
    val repository: String,
    val path: String,
    val reviewerLogin: String,
    val reviewedAt: Instant,
)
