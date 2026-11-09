package com.sprintstart.sprintstartbackend.connectors.git.github.service

import com.sprintstart.sprintstartbackend.connectors.git.github.GithubClient
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.initial.GithubRepositoryConnectionInitiatedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.initial.GithubRepositoryConnectionInitiationFailedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.git.github.models.GithubUserPat
import com.sprintstart.sprintstartbackend.connectors.git.github.models.api.requests.ConnectRepositoryRequest
import com.sprintstart.sprintstartbackend.connectors.git.github.models.exceptions.GithubUserPatNotFoundException
import com.sprintstart.sprintstartbackend.connectors.git.github.models.exceptions.RepositoryNotFoundException
import com.sprintstart.sprintstartbackend.connectors.git.github.models.exceptions.UserWithAuthIdNotFoundException
import com.sprintstart.sprintstartbackend.connectors.git.github.repository.GithubUserRepository
import com.sprintstart.sprintstartbackend.connectors.git.github.service.internal.GithubCommitsService
import com.sprintstart.sprintstartbackend.connectors.git.github.service.internal.GithubFileService
import com.sprintstart.sprintstartbackend.connectors.git.github.service.internal.GithubIssuesService
import com.sprintstart.sprintstartbackend.connectors.git.github.service.internal.GithubOrgService
import com.sprintstart.sprintstartbackend.connectors.git.github.service.internal.GithubPullRequestsService
import com.sprintstart.sprintstartbackend.user.external.UserApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.springframework.context.ApplicationEventPublisher
import java.util.UUID

/**
 * What it takes to accept a repository nobody has connected yet: resolving the caller's token,
 * announcing the attempt, and checking the repository exists before anything is stored.
 */
internal class GithubNewConnection(
    private val githubUserRepository: GithubUserRepository,
    private val githubClient: GithubClient,
    private val eventPublisher: ApplicationEventPublisher,
    private val userApi: UserApi,
    private val ioDispatcher: CoroutineDispatcher,
) {
    /**
     * Builds the connection for [request] on behalf of [subject], once the repository is known to exist.
     *
     * @throws UserWithAuthIdNotFoundException when [subject] has no local user.
     * @throws GithubUserPatNotFoundException when the caller has no token with the requested name.
     * @throws RepositoryNotFoundException when GitHub does not know the repository; a failure event is published.
     */
    suspend fun accept(
        subject: String,
        request: ConnectRepositoryRequest,
        transaction: UUID,
    ): GithubRepositoryConnection {
        val user = userApi.getUserIdByAuthId(subject).orElseThrow { UserWithAuthIdNotFoundException(subject) }

        eventPublisher.publishEvent(
            GithubRepositoryConnectionInitiatedEvent(transaction, request.owner, request.name),
        )

        val token = withContext(ioDispatcher) {
            githubUserRepository.findById(GithubUserPat(authId = subject, name = request.tokenName))
        }.orElseThrow {
            GithubUserPatNotFoundException(request.tokenName, user.toString())
        }

        val connection = GithubRepositoryConnection(
            owner = request.owner,
            name = request.name,
            user = token,
            projectIdsInternal = mutableSetOf(request.projectId),
        )

        if (!githubClient.repositoryExists(connection)) {
            val ex = RepositoryNotFoundException(request.owner, request.name)
            eventPublisher.publishEvent(
                GithubRepositoryConnectionInitiationFailedEvent(transaction, request.owner, request.name, ex.message),
            )
            throw ex
        }
        return connection
    }
}

/** Starts the background jobs that ingest a freshly connected repository. */
internal class GithubInitialFetch(
    private val applicationScope: CoroutineScope,
    private val fileService: GithubFileService,
    private val commitsService: GithubCommitsService,
    private val issuesService: GithubIssuesService,
    private val pullRequestsService: GithubPullRequestsService,
    private val orgService: GithubOrgService,
) {
    /** Launches the file, commit, issue, pull request and organisation fetches of [repository]. */
    fun start(repository: GithubRepositoryConnection, transaction: UUID) {
        applicationScope.launch {
            fileService.fetchAndIngestAllFiles(repository.id, repository.owner, repository.name, transaction)
        }
        applicationScope.launch {
            commitsService.fetchAndIngestCommits(repository, transaction)
        }
        applicationScope.launch {
            issuesService.fetchAndIngestAllIssues(repository.id, repository.owner, repository.name, transaction)
        }
        applicationScope.launch {
            pullRequestsService.fetchAndIngestAllPullRequests(
                repository.id,
                repository.owner,
                repository.name,
                transaction,
            )
        }
        applicationScope.launch {
            orgService.connectGithubOrgIfNecessary(repository.owner, repository.user.token, transaction)
        }
    }
}
