package com.sprintstart.sprintstartbackend.connectors.git.github.service.internal

import com.sprintstart.sprintstartbackend.connectors.git.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.git.github.util.GithubGitProvider
import com.sprintstart.sprintstartbackend.shared.git.GitRepositoryCoordinates
import org.springframework.stereotype.Component

/**
 * Maps a connected GitHub repository onto the provider-neutral clone coordinates of the shared
 * on-disk cache.
 *
 * The host and the clone user name come from [GithubGitProvider] instead of being repeated here, so
 * pointing the connector at GitHub Enterprise is a change in one place.
 *
 * The result carries the connection's PAT and must not be logged; [GitRepositoryCoordinates] masks
 * it in its `toString` for that reason.
 */
@Component
class GithubRepositoryCoordinatesFactory(
    private val provider: GithubGitProvider,
) {
    /**
     * Builds the clone coordinates of one connected repository.
     *
     * @param connection The connected GitHub repository to clone.
     * @return Coordinates pointing at `<host>/<owner>/<name>`.
     */
    fun of(connection: GithubRepositoryConnection): GitRepositoryCoordinates {
        val descriptor = provider.descriptor
        return GitRepositoryCoordinates(
            host = descriptor.host,
            namespace = connection.owner,
            name = connection.name,
            username = descriptor.cloneUsername,
            secret = connection.user.token,
        )
    }
}
