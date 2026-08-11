package com.sprintstart.sprintstartbackend.connectors.github.models

import java.util.UUID

/**
 * Describes the outcome of connecting or reusing a GitHub repository.
 *
 * The result intentionally contains no project associations or connection ownership details so it
 * can be mapped to a privacy-safe API response.
 */
data class GithubRepositoryConnectionResult(
    val transactionId: UUID,
    val wasReused: Boolean,
)
