package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service

import com.sprintstart.sprintstartbackend.connectors.atlassian.external.AtlassianCredentialApi
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.BitbucketClient
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryConnectionNotFoundException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketConnectionRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Answers one question: can this caller reach this Bitbucket repository with their own credentials?
 *
 * Access to the target project is not enough wherever a repository is *linked* rather than fetched.
 * Linking is how a repository's artifacts and index chunks reach a project, so a caller who cannot
 * see the repository must not be able to pull it into one — a connection id alone, which source
 * overview responses hand out, would otherwise be enough to attach somebody else's private
 * repository to a project of one's own.
 *
 * Deliberately not an ownership rule: reuse works across PMs by design, the connection keeps the
 * credential of whoever connected it first, and a second PM may link it to their own project
 * without learning about the first. This asks only that the caller can reach the repository too.
 *
 * Every failure is [BitbucketRepositoryConnectionNotFoundException], the same answer an unknown
 * connection gets, so a caller cannot tell "you may not see this" from "this does not exist".
 *
 * Suspending throughout, and therefore never itself transactional: `@Transactional` does not apply
 * to suspending functions against this application's JPA transaction manager, so callers run these
 * checks *before* the transactional work rather than inside it.
 */
@Service
internal class BitbucketRepositoryVisibilityService(
    private val connectionRepository: BitbucketConnectionRepository,
    private val credentialApi: AtlassianCredentialApi,
    private val bitbucketClient: BitbucketClient,
) {
    /**
     * Checks visibility of the repository behind a connection, with any credential the caller has
     * stored.
     *
     * Any of the caller's Atlassian credentials will do: the question is whether *this person* can
     * reach the repository, not which of their tokens proves it. The tokens are tried one after
     * another and the first that sees the repository ends the search, so a caller only pays one
     * Bitbucket call per stored credential when none of them can.
     *
     * @param authId The authenticated caller subject.
     * @param repositoryId The connection the caller wants to link.
     * @throws BitbucketRepositoryConnectionNotFoundException when the connection is unknown, or when
     *         none of the caller's credentials can see the repository behind it — deliberately
     *         indistinguishable.
     */
    @Tracked("Checking caller visibility of a connected Bitbucket repository")
    suspend fun requireCallerCanSeeConnection(authId: String, repositoryId: UUID) {
        val connection = withContext(Dispatchers.IO) {
            connectionRepository.findById(repositoryId)
        }.orElseThrow {
            BitbucketRepositoryConnectionNotFoundException(repositoryId)
        }

        val credentials = withContext(Dispatchers.IO) {
            credentialApi.findAllSecretsByAuthId(authId)
        }
        val visible = credentials.any { credential ->
            bitbucketClient.repositoryExists(connection.workspace, connection.slug, credential)
        }

        if (!visible) {
            throw BitbucketRepositoryConnectionNotFoundException(repositoryId)
        }
    }
}
