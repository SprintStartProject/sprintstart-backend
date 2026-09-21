package com.sprintstart.sprintstartbackend.connectors.github.external

import java.util.UUID

/** Whether an owner name is an organisation or a personal account, which GitHub lists differently. */
enum class GithubOwnerKind {
    ORGANISATION,
    USER,
}

/** A repository somebody names by its two parts. */
data class GithubRepositoryRef(
    val owner: String,
    val name: String,
)

/**
 * One repository GitHub lists for an owner, and whether SprintStart already has it.
 *
 * @property alreadyConnected Whether some project already has this repository connected.
 * @property enabled Whether that connection is enabled for ingestion, or null when it is not connected.
 */
data class GithubDiscoveredRepositoryDto(
    val name: String,
    val isPrivate: Boolean,
    val url: String,
    val alreadyConnected: Boolean,
    val enabled: Boolean?,
)

/**
 * How connecting one repository went.
 *
 * @property failure Why it did not connect, in words for the person who asked; null when it did.
 * @property reused True when the repository was already connected for another project, so the project
 * was linked to it and nothing was fetched again.
 */
data class GithubConnectResultDto(
    val repository: GithubRepositoryRef,
    val reused: Boolean,
    val failure: String?,
)

/**
 * What other modules may do with the GitHub connector on somebody's behalf: read their token names,
 * look at what GitHub lists, connect repositories, link and unlink them, and start a sync.
 *
 * Everything takes the caller's `authId` because GitHub access here is always the caller's own: the
 * tokens are theirs, and whether they may see a repository is asked of GitHub with those tokens. Nothing
 * here returns or accepts a token value — only the names people gave their tokens.
 *
 * Failures are [org.springframework.web.server.ResponseStatusException]s with a message that is safe to
 * show the person, so a caller need not know the connector's own exception types.
 */
interface GithubSourcesApi {
    /** The names of the personal access tokens [authId] has stored. Never the tokens. */
    fun getTokenNames(authId: String): List<String>

    /**
     * What GitHub lists for [owner], read with the token [tokenName] of [authId].
     *
     * @throws org.springframework.web.server.ResponseStatusException 404 when that token does not exist
     * for the caller.
     */
    suspend fun discoverRepositories(
        authId: String,
        kind: GithubOwnerKind,
        owner: String,
        tokenName: String,
        page: Int,
        pageSize: Int,
    ): List<GithubDiscoveredRepositoryDto>

    /**
     * Connects each repository to [projectId], one at a time and each on its own.
     *
     * A repository that is already connected for another project is linked instead of fetched again,
     * after checking that the caller can see it. One repository failing does not stop the others, so
     * the result says how each went. A new repository starts fetching its files, commits, issues and
     * pull requests in the background.
     */
    suspend fun connectRepositories(
        authId: String,
        projectId: UUID,
        tokenName: String,
        repositories: List<GithubRepositoryRef>,
    ): List<GithubConnectResultDto>

    /**
     * Links an already-connected repository to [projectId], after checking that the caller can see it
     * on GitHub with one of their own tokens.
     *
     * @return The projects the repository is linked to afterwards.
     * @throws org.springframework.web.server.ResponseStatusException 404 when it is not connected or the
     * caller cannot see it — the same answer for both.
     */
    suspend fun linkRepository(authId: String, projectId: UUID, repositoryId: UUID): Set<UUID>

    /**
     * Takes [projectId] off a repository. The connection and what was fetched stay; only this project's
     * membership goes.
     *
     * @return The projects the repository is linked to afterwards.
     */
    fun unlinkRepository(authId: String, projectId: UUID, repositoryId: UUID): Set<UUID>

    /**
     * Starts fetching a connected repository's latest state in the background.
     *
     * Carries no project: it updates the one connection every linked project shares.
     *
     * @return The id the update reports its progress under.
     * @throws org.springframework.web.server.ResponseStatusException 404 when it is not connected, 400
     * when its first fetch has not finished.
     */
    fun syncRepository(repository: GithubRepositoryRef): UUID
}
