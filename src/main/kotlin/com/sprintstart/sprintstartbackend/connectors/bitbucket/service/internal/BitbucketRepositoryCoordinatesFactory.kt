package com.sprintstart.sprintstartbackend.connectors.bitbucket.service.internal

import com.sprintstart.sprintstartbackend.connectors.atlassian.external.AtlassianCredentialApi
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.exception.AtlassianCredentialNotFoundException
import com.sprintstart.sprintstartbackend.connectors.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.shared.git.GitRepositoryCoordinates
import org.springframework.stereotype.Component

/**
 * Maps a connected Bitbucket repository onto the provider-neutral clone coordinates of the shared
 * on-disk cache.
 *
 * The connection stores only the name of the credential and the id it belongs to, never the token
 * itself, so the decrypted token is resolved here on each use — the same way the REST calls of
 * [com.sprintstart.sprintstartbackend.connectors.bitbucket.BitbucketClient] resolve it.
 *
 * The result carries an API token and must not be logged; [GitRepositoryCoordinates] masks it in
 * its `toString` for that reason.
 */
@Component
internal class BitbucketRepositoryCoordinatesFactory(
    private val credentialApi: AtlassianCredentialApi,
) {
    /**
     * Builds the clone coordinates of one connected repository.
     *
     * @param connection The connected Bitbucket repository to clone.
     * @return Coordinates pointing at `bitbucket.org/<workspace>/<slug>`.
     * @throws AtlassianCredentialNotFoundException if the credential the connection refers to no
     *         longer exists, so no clone can be authenticated.
     */
    fun of(connection: BitbucketConnection): GitRepositoryCoordinates {
        val credential = credentialApi.findSecret(connection.credentialAuthId, connection.credentialName)
            ?: throw AtlassianCredentialNotFoundException(connection.credentialAuthId, connection.credentialName)

        return GitRepositoryCoordinates(
            host = CLONE_HOST,
            namespace = connection.workspace,
            name = connection.slug,
            username = CLONE_USERNAME,
            secret = credential.apiToken,
        )
    }

    private companion object {
        /**
         * Bitbucket Cloud's Git host.
         *
         * Deliberately separate from `sprintstart.bitbucket.base-url`: that one addresses the REST
         * API (`api.bitbucket.org`), while clones go over Git.
         */
        const val CLONE_HOST = "bitbucket.org"

        /**
         * The static user name Bitbucket accepts instead of a Bitbucket username.
         *
         * Using it means cloning does not need to know which Bitbucket account an API token belongs
         * to, so a connection only has to store the token's name.
         */
        const val CLONE_USERNAME = "x-bitbucket-api-token-auth"
    }
}
