package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.connectors.github.external.GithubRepositoryApi
import com.sprintstart.sprintstartbackend.connectors.github.external.GithubRepositoryRef
import com.sprintstart.sprintstartbackend.connectors.github.external.GithubSourcesApi
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * A repository SprintStart has connected, and what it means for the turn's project.
 *
 * @property otherProjects How many other projects share it. A repository is one connection however many
 * projects are linked to it, so linking, unlinking and syncing all reach past the turn's project.
 */
data class ConnectedRepository(
    val id: UUID,
    val ref: GithubRepositoryRef,
    val linkedHere: Boolean,
    val otherProjects: Int,
)

/**
 * Which GitHub repositories a project's manager may act on, and the credential check that goes with them.
 *
 * Connecting a repository to a project is how its material reaches that project, and a sync or an unlink
 * reaches every project sharing the connection. So a repository is in scope for a sync or an unlink only
 * when it is linked to the turn's project; for a link it must exist, and whether the manager may see it on
 * GitHub is asked of GitHub itself when they confirm.
 *
 * Credentials are only ever names. [tokenProblem] is the one place a token name is judged, and it never
 * repeats what it was given: a person who pasted a token into the conversation must not have it echoed
 * into a preview or a stored proposal.
 */
@Component
class GithubSourcesScope(
    private val githubRepositoryApi: GithubRepositoryApi,
    private val githubSourcesApi: GithubSourcesApi,
) {
    /** The connected repository [owner]/[name] names, or null when nothing is connected under it. */
    fun find(owner: String, name: String, projectId: UUID): ConnectedRepository? {
        val id = githubRepositoryApi.getRepositoryIdByOwnerAndName(owner, name) ?: return null
        val projects = runCatching { githubRepositoryApi.getRepositoryProjectIdsById(id) }
            .getOrElse { if (it is NoSuchElementException) return null else throw it }
        return ConnectedRepository(
            id = id,
            ref = GithubRepositoryRef(owner, name),
            linkedHere = projectId in projects,
            otherProjects = projects.count { it != projectId },
        )
    }

    /** Why [tokenName] cannot be used for [authId], or null when it names one of their own tokens. */
    fun tokenProblem(authId: String, tokenName: String): String? {
        if (tokenName.isEmpty()) return "A token name is needed. Call list_my_credential_names for the ones there are."
        if (TOKEN_SHAPE.containsMatchIn(tokenName)) {
            return "That looks like a token itself rather than the name of one. Never paste a token here: tell the " +
                "manager to store it under a name on the settings page, and use that name."
        }
        return if (tokenName in githubSourcesApi.getTokenNames(authId)) {
            null
        } else {
            "You have no GitHub token with that name. Call list_my_credential_names for the ones you do have."
        }
    }

    /** "owner/name" for a preview. */
    fun label(ref: GithubRepositoryRef): String = "${ref.owner}/${ref.name}"

    /** The sentences a preview adds when other projects share the connection, or empty. */
    fun sharedNote(repository: ConnectedRepository, consequence: String): String =
        when (repository.otherProjects) {
            0 -> ""
            1 -> "One other project shares this repository. $consequence"
            else -> "${repository.otherProjects} other projects share this repository. $consequence"
        }

    companion object {
        /** The prefixes GitHub gives its tokens: `ghp_`, `gho_`, `ghu_`, `ghs_`, `ghr_` and `github_pat_`. */
        private val TOKEN_SHAPE = Regex("^(gh[pousr]_|github_pat_)", RegexOption.IGNORE_CASE)
    }
}
