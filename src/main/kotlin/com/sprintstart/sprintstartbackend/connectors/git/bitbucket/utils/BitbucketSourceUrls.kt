package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.utils

import com.sprintstart.sprintstartbackend.connectors.git.utils.GitSourceUrls

/**
 * Builds the Bitbucket Cloud browser URLs stored on artifacts.
 *
 * Bitbucket spells these differently from GitHub: a file at a revision lives under `/src/`, not
 * under `/blob/`, and a commit lives under `/commits/`. Grouping them here keeps those shapes out
 * of the services that produce events.
 */
internal class BitbucketSourceUrls : GitSourceUrls {
    override fun repositoryUrl(namespacePath: List<String>, name: String): String = "$BASE/${path(namespacePath, name)}"

    override fun fileUrl(namespacePath: List<String>, name: String, revision: String, path: String): String =
        "$BASE/${path(namespacePath, name)}/src/$revision/$path"

    override fun commitUrl(namespacePath: List<String>, name: String, sha: String): String =
        "$BASE/${path(namespacePath, name)}/commits/$sha"

    private fun path(namespacePath: List<String>, name: String): String =
        (namespacePath + name).joinToString("/")

    private companion object {
        /** Browser root of Bitbucket Cloud. */
        const val BASE = "https://bitbucket.org"
    }
}
