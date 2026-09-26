package com.sprintstart.sprintstartbackend.connectors.github.service

import com.sprintstart.sprintstartbackend.connectors.github.external.GithubConnectResultDto
import com.sprintstart.sprintstartbackend.connectors.github.external.GithubDiscoveredRepositoryDto
import com.sprintstart.sprintstartbackend.connectors.github.external.GithubOwnerKind
import com.sprintstart.sprintstartbackend.connectors.github.external.GithubRepositoryRef
import com.sprintstart.sprintstartbackend.connectors.github.external.GithubSourcesApi
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.ConnectRepositoryRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.DiscoverRepositoriesRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.UpdateRepositoryRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.GithubUserPatNotFoundException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.ProjectAccessDeniedException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.RepositoryNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.RepositoryNotFoundException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.RepositoryNotInitializedException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/**
 * The GitHub connector as other modules may use it: the same services the REST controllers call, with the
 * connector's own exceptions turned into ones that say something to a person.
 *
 * Deliberately thin. The rules — who may see a repository, what a link does to artifacts, what a sync
 * fetches — live in the services behind this, and this does not restate them.
 */
@Service
class GithubSourcesApiService(
    private val githubUserService: GithubUserService,
    private val githubConnectorService: GithubConnectorService,
    private val visibilityService: GithubRepositoryVisibilityService,
    private val githubRepositoryProjectService: GithubRepositoryProjectService,
    private val githubUpdatesService: GithubUpdatesService,
) : GithubSourcesApi {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun getTokenNames(authId: String): List<String> = githubUserService.getAllPATNames(authId)

    override suspend fun discoverRepositories(
        authId: String,
        kind: GithubOwnerKind,
        owner: String,
        tokenName: String,
        page: Int,
        pageSize: Int,
    ): List<GithubDiscoveredRepositoryDto> {
        val request = DiscoverRepositoriesRequest(owner, authId, tokenName, page, pageSize)
        val found = translated {
            when (kind) {
                GithubOwnerKind.ORGANISATION -> githubConnectorService.discoverRepositoriesOfOrg(request)
                GithubOwnerKind.USER -> githubConnectorService.discoverRepositoriesOfUser(request)
            }
        }
        return found.repositories.map {
            GithubDiscoveredRepositoryDto(it.name, it.isPrivate, it.url, it.alreadyConnected, it.isEnabled)
        }
    }

    override suspend fun connectRepositories(
        authId: String,
        projectId: UUID,
        tokenName: String,
        repositories: List<GithubRepositoryRef>,
    ): List<GithubConnectResultDto> =
        repositories.map { repository ->
            try {
                val outcome = githubConnectorService.connectRepositoryIfNecessary(
                    authId,
                    ConnectRepositoryRequest(repository.owner, repository.name, tokenName, projectId),
                )
                GithubConnectResultDto(repository, outcome.wasReused, failure = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                GithubConnectResultDto(repository, reused = false, failure = reasonOf(e, repository))
            }
        }

    override suspend fun linkRepository(authId: String, projectId: UUID, repositoryId: UUID): Set<UUID> {
        // Checked before the transactional link, as the REST endpoint does, because it suspends.
        translated { visibilityService.requireCallerCanSeeConnection(authId, repositoryId) }
        return translated { githubRepositoryProjectService.addProjectToRepository(authId, repositoryId, projectId) }
    }

    override fun unlinkRepository(authId: String, projectId: UUID, repositoryId: UUID): Set<UUID> =
        translatedBlocking {
            githubRepositoryProjectService.removeProjectFromRepository(
                authId,
                repositoryId,
                projectId,
            )
        }

    override fun syncRepository(repository: GithubRepositoryRef): UUID =
        translatedBlocking {
            githubUpdatesService
                .updateRepository(UpdateRepositoryRequest(repository.owner, repository.name), true)
                .transactionId
        }

    private suspend fun <T> translated(block: suspend () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw asStatusException(e)
        }

    private fun <T> translatedBlocking(block: () -> T): T =
        try {
            block()
        } catch (e: Exception) {
            throw asStatusException(e)
        }

    private fun asStatusException(e: Exception): Exception =
        when (e) {
            is ResponseStatusException -> e
            is GithubUserPatNotFoundException ->
                ResponseStatusException(HttpStatus.NOT_FOUND, "There is no GitHub token named “${e.name}”.")
            is RepositoryNotFoundException -> ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
            is RepositoryNotConnectedException -> ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
            is ProjectAccessDeniedException -> ResponseStatusException(HttpStatus.FORBIDDEN, e.message)
            is RepositoryNotInitializedException ->
                ResponseStatusException(HttpStatus.BAD_REQUEST, "Its first fetch has not finished yet.")
            else -> {
                logger.warn("GitHub connector call failed", e)
                ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub or the connector could not complete that.")
            }
        }

    private fun reasonOf(e: Exception, repository: GithubRepositoryRef): String =
        (asStatusException(e) as? ResponseStatusException)?.reason
            ?: "${repository.owner}/${repository.name} could not be connected."
}
