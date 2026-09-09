package com.sprintstart.sprintstartbackend.connectors.github.service

import com.sprintstart.sprintstartbackend.connectors.github.GithubClient
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubUser
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubUserPat
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.GithubUserPatNotFoundException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.RepositoryNotFoundException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.UserWithAuthIdNotFoundException
import com.sprintstart.sprintstartbackend.connectors.github.repository.GithubRepositoryConnectionRepository
import com.sprintstart.sprintstartbackend.connectors.github.repository.GithubUserRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.user.external.UserApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Answers one question: can this caller reach this repository on GitHub with their own credentials?
 *
 * Access to the target project is not enough wherever a repository is *linked* rather than fetched.
 * Linking is how a repository's artifacts and index chunks reach a project, so a caller who cannot
 * see the repository must not be able to pull it into one. Both link paths skipped this and only
 * the fetch path implied it, by resolving a PAT and calling `repositoryExists` before persisting.
 *
 * Deliberately not an ownership rule. Reuse works across PMs by design (#257): the connection keeps
 * the PAT of whoever connected it first, and a second PM may link it to their own project without
 * learning about the first. Requiring a relationship to the existing connection would be exactly
 * the restriction that issue rules out — so this asks only that the caller can reach the repository
 * too.
 *
 * Every failure is `RepositoryNotFoundException`, the same answer an invisible repository gets, so
 * a caller cannot tell "you may not see this" from "this does not exist".
 *
 * Suspending throughout, and therefore never itself transactional: `@Transactional` does not apply
 * to suspending functions against this application's JPA transaction manager, so callers run these
 * checks *before* the transactional work rather than inside it.
 */
@Service
class GithubRepositoryVisibilityService(
    private val githubUserRepository: GithubUserRepository,
    private val repoConnectionRepository: GithubRepositoryConnectionRepository,
    private val githubClient: GithubClient,
    private val userApi: UserApi,
) {
    /**
     * Checks visibility with the one PAT the caller named.
     *
     * Used on the connect path, where the request carries a `tokenName`.
     *
     * @param authId The authenticated caller subject.
     * @param tokenName The caller's own stored PAT alias.
     * @param owner The repository owner.
     * @param name The repository name.
     * @throws UserWithAuthIdNotFoundException when the caller has no user record.
     * @throws GithubUserPatNotFoundException when the caller has no such PAT.
     * @throws RepositoryNotFoundException when that PAT cannot see the repository.
     */
    @Tracked("Checking caller visibility of a GitHub repository")
    suspend fun requireCallerCanSeeRepository(authId: String, tokenName: String, owner: String, name: String) {
        val userId = userApi.getUserIdByAuthId(authId).orElseThrow { UserWithAuthIdNotFoundException(authId) }
        val user = withContext(Dispatchers.IO) {
            githubUserRepository.findById(GithubUserPat(authId = authId, name = tokenName))
        }.orElseThrow {
            GithubUserPatNotFoundException(tokenName, userId.toString())
        }

        if (!canSee(user, owner, name)) {
            throw RepositoryNotFoundException(owner, name)
        }
    }

    /**
     * Checks visibility of the repository behind a connection, with any PAT the caller has stored.
     *
     * Used on the link endpoint, which identifies the repository by connection id and names no PAT.
     * Any of the caller's tokens will do: the question is whether *this person* can reach the
     * repository, not which of their tokens proves it.
     *
     * @param authId The authenticated caller subject.
     * @param repositoryId The connection the caller wants to link.
     * @throws RepositoryNotFoundException when the connection is unknown, or when none of the
     * caller's PATs can see the repository behind it — deliberately indistinguishable.
     */
    @Tracked("Checking caller visibility of a connected GitHub repository")
    suspend fun requireCallerCanSeeConnection(authId: String, repositoryId: UUID) {
        val connection = withContext(Dispatchers.IO) {
            repoConnectionRepository.findById(repositoryId)
        }.orElseThrow {
            RepositoryNotFoundException("", "", "Repository connection with id $repositoryId not found")
        }

        val patNames = withContext(Dispatchers.IO) { githubUserRepository.findAllByAuthId(authId) }
        val visible = patNames.any { patName ->
            val user = withContext(Dispatchers.IO) {
                githubUserRepository.findById(GithubUserPat(authId = authId, name = patName))
            }.orElse(null)
            user != null && canSee(user, connection.owner, connection.name)
        }

        if (!visible) {
            throw RepositoryNotFoundException(connection.owner, connection.name)
        }
    }

    /**
     * Whether the given credentials can reach `owner/name` on GitHub.
     *
     * The connection built here is transient and never saved; it exists only to carry the
     * credentials into the probe, the same shape the connect path builds before it persists.
     */
    private suspend fun canSee(user: GithubUser, owner: String, name: String): Boolean =
        githubClient.repositoryExists(
            GithubRepositoryConnection(owner = owner, name = name, user = user),
        )
}
