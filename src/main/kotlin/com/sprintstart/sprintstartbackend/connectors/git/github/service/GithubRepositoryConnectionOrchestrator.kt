package com.sprintstart.sprintstartbackend.connectors.git.github.service

import com.sprintstart.sprintstartbackend.connectors.git.github.models.api.requests.ConnectRepositoriesRequest
import com.sprintstart.sprintstartbackend.connectors.git.github.models.api.responses.ConnectRepositoriesResponse
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Service class responsible for orchestrating the connection of GitHub repositories.
 *
 * This orchestrator service is needed to keep the connection transactions scoped per repository.
 * That means that each repository to connect will either be completely connected or not at all.
 * The magic for this behavior lies in the `@Transactional()` annotation, however, that annotation only works via
 * a service-level proxy, invoked at runtime. As [connectRepositoriesIfExist] just calls `connectRepositoryIfNecessary`,
 * for each requested repository, doing this from within the same service would lead to the necessary proxy to not spin
 * up, hence the transactions not to work. Therefore, this is done via this orchestrator class.
 */
@Service
class GithubRepositoryConnectionOrchestrator(
    private val connectorService: GithubConnectorService,
) {
    /**
     * Attempts to connect a list of GitHub repositories if they exist. For each repository,
     * a connection transaction is initiated and its transaction ID is recorded.
     *
     * Repositories are connected in order and the batch aborts on the first failure, so the
     * entries after a failing repository are not attempted. Connections stored before the failure
     * stay stored, but their transaction ids are not part of the response: the batch answers with
     * the failure instead of a per-repository result.
     *
     * @param authId The authentication identifier for the operation.
     * @param request The request containing the list of repositories to connect.
     * @return A response containing a mapping of repository identifiers in the format "owner/name"
     * to their corresponding transaction IDs, only when every repository was accepted.
     */
    @Tracked("Attempting to connect a list of GitHub repositories")
    suspend fun connectRepositoriesIfExist(
        authId: String,
        request: ConnectRepositoriesRequest,
    ): ConnectRepositoriesResponse {
        val transactionIdsByRepoIds = mutableMapOf<String, UUID>() // "owner/name" -> transactionId
        val reused = mutableSetOf<String>()
        request.repositories.forEach {
            val outcome = connectorService.connectRepositoryIfNecessary(authId, it)
            val repositoryId = "${it.owner}/${it.name}"
            transactionIdsByRepoIds[repositoryId] = outcome.transactionId
            if (outcome.wasReused) reused.add(repositoryId)
        }
        return ConnectRepositoriesResponse(transactionIdsByRepoIds, reused)
    }
}
