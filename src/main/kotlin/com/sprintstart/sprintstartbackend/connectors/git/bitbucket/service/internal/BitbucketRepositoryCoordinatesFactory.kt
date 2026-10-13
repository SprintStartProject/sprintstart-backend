package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal

import com.sprintstart.sprintstartbackend.connectors.atlassian.external.AtlassianCredentialApi
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.exception.AtlassianCredentialNotFoundException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.utils.BitbucketGitProvider
import com.sprintstart.sprintstartbackend.shared.git.GitRepositoryCoordinates
import org.springframework.stereotype.Component

/**
 * Maps a connected Bitbucket repository onto the provider-neutral clone coordinates of the shared
 * on-disk cache.
 *
 * The connection stores only the name of the credential and the id it belongs to, never the token
 * itself, so the decrypted token is resolved here on each use — the same way the REST calls of
 * [com.sprintstart.sprintstartbackend.connectors.git.bitbucket.BitbucketClient] resolve it. That is why
 * this is a lookup rather than a mapping of stored fields.
 *
 * The host and the clone user name come from [BitbucketGitProvider] instead of being repeated here,
 * so pointing the connector at a different Git host is a change in one place.
 *
 * The result carries an API token and must not be logged; [GitRepositoryCoordinates] masks it in
 * its `toString` for that reason.
 */
@Component
internal class BitbucketRepositoryCoordinatesFactory(
    private val credentialApi: AtlassianCredentialApi,
    private val provider: BitbucketGitProvider,
) {
    /**
     * Builds the clone coordinates of one connected repository.
     *
     * @param connection The connected Bitbucket repository to clone.
     * @return Coordinates pointing at `<host>/<workspace>/<slug>`.
     * @throws AtlassianCredentialNotFoundException if the credential the connection refers to no
     *         longer exists, so no clone can be authenticated.
     */
    fun of(connection: BitbucketConnection): GitRepositoryCoordinates {
        val credential = credentialApi.findSecret(connection.credentialAuthId, connection.credentialName)
            ?: throw AtlassianCredentialNotFoundException(connection.credentialAuthId, connection.credentialName)

        val descriptor = provider.descriptor
        return GitRepositoryCoordinates(
            host = descriptor.host,
            namespace = connection.workspace,
            name = connection.slug,
            username = descriptor.cloneUsername,
            secret = credential.apiToken,
        )
    }
}
