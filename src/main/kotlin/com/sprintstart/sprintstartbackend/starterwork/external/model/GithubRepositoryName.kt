package com.sprintstart.sprintstartbackend.starterwork.external.model

import com.sprintstart.sprintstartbackend.shared.ids.SourceId

/** A GitHub repository as a starter-work source id names it. */
data class GithubRepositoryName(
    val owner: String,
    val name: String,
) {
    override fun toString(): String = "$owner/$name"
}

/** How every source id of a GitHub issue starts. */
const val GITHUB_SOURCE_PREFIX = "github:"

/**
 * The repository a source id of the shape `github:<owner>/<name>:<TYPE>:<unique>` points at.
 *
 * Null for every other shape — a hand-authored task (`authored:`), a Jira issue, or anything
 * malformed — so none of those is ever mistaken for a repository's work.
 */
fun githubRepositoryOf(sourceId: SourceId): GithubRepositoryName? {
    if (!sourceId.value.startsWith(GITHUB_SOURCE_PREFIX)) return null
    val parts = sourceId.value.removePrefix(GITHUB_SOURCE_PREFIX).split(':')
    if (parts.size < 3) return null
    val ownerAndName = parts[0].split('/')
    if (ownerAndName.size != 2 || ownerAndName.any { it.isBlank() }) return null
    return GithubRepositoryName(ownerAndName[0], ownerAndName[1])
}
