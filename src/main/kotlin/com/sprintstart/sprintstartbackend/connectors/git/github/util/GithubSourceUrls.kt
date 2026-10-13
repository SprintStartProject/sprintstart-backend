package com.sprintstart.sprintstartbackend.connectors.git.github.util

import com.sprintstart.sprintstartbackend.connectors.git.utils.GitSourceUrls

/**
 * Builds the GitHub browser URLs stored on artifacts.
 *
 * GitHub spells these differently from Bitbucket: a file at a revision lives under `/blob/` rather
 * than `/src/`, and a commit lives under `/commit/`. Grouping them here keeps those shapes out of
 * the services that produce events.
 */
class GithubSourceUrls : GitSourceUrls {
    override fun repositoryUrl(namespacePath: List<String>, name: String): String =
        "$BASE/${path(namespacePath, name)}"

    override fun fileUrl(namespacePath: List<String>, name: String, revision: String, path: String): String =
        "$BASE/${path(namespacePath, name)}/blob/$revision/$path"

    override fun commitUrl(namespacePath: List<String>, name: String, sha: String): String =
        "$BASE/${path(namespacePath, name)}/commit/$sha"

    private fun path(namespacePath: List<String>, name: String): String =
        (namespacePath + name).joinToString("/")

    private companion object {
        /** Browser root of GitHub. */
        const val BASE = "https://github.com"
    }
}
