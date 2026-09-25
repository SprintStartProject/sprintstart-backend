package com.sprintstart.sprintstartbackend.connectors.github.util

import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.shared.git.GitRepositoryCoordinates

/** GitHub's Git host. Also the on-disk namespace the shared cache clones into. */
private const val GITHUB_HOST = "github.com"

/**
 * Static user name GitHub accepts for token-authenticated HTTPS clones.
 *
 * Using it instead of the user's login keeps cloning independent of which account the stored PAT
 * belongs to — the token alone decides what can be read.
 */
private const val GITHUB_CLONE_USERNAME = "x-access-token"

/**
 * Maps a GitHub connection onto the provider-neutral clone coordinates of the shared cache.
 *
 * The connection's stored PAT becomes the clone secret, so the result carries a credential: it must
 * not be logged. [GitRepositoryCoordinates.toString] masks it for that reason.
 *
 * @return The coordinates of this repository, ready for the shared on-disk cache.
 */
fun GithubRepositoryConnection.toGitRepositoryCoordinates(): GitRepositoryCoordinates =
    GitRepositoryCoordinates(
        host = GITHUB_HOST,
        namespace = owner,
        name = name,
        username = GITHUB_CLONE_USERNAME,
        secret = user.token,
    )
