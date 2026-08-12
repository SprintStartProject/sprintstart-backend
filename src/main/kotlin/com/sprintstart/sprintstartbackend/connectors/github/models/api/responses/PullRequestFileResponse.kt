package com.sprintstart.sprintstartbackend.connectors.github.models.api.responses

import kotlinx.serialization.Serializable

/**
 * One changed file of a pull request, as GitHub's REST API reports it.
 *
 * ⚠️ **REST, not GraphQL, and that is why this type exists.** GitHub's GraphQL
 * `PullRequestChangedFile` exposes the path, the counts and the change type — but **not the diff**.
 * The patch text comes only from `GET /repos/{owner}/{repo}/pulls/{n}/files`, so artifact
 * verification pays for one extra REST call rather than judging a change it cannot see.
 *
 * [patch] is absent for binary files and for files GitHub considers too large to inline. That is a
 * real state rather than an error — some real work has no readable diff — and the judge is told so
 * rather than left to infer that nothing changed.
 */
@Serializable
data class PullRequestFileResponse(
    val filename: String,
    val status: String = "",
    val additions: Int = 0,
    val deletions: Int = 0,
    val patch: String? = null,
)
