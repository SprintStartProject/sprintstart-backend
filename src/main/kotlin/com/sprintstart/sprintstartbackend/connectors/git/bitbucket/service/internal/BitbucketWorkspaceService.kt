package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal

import com.sprintstart.sprintstartbackend.connectors.atlassian.external.AtlassianCredentialApi
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.exception.AtlassianCredentialNotFoundException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.BitbucketClient
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.workspace.BitbucketWorkspaceMetadataFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.workspace.BitbucketWorkspaceMetadataFetchingCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.workspace.BitbucketWorkspaceMetadataFetchingFailedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.workspace.BitbucketWorkspaceMetadataFetchingStartedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.workspace.BitbucketWorkspaceMetadataMember
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.client.WorkspaceMembersResponse
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.client.WorkspaceMetadataResponse
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketWorkspace
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketWorkspaceRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches the metadata and members of a Bitbucket workspace and triggers their ingestion.
 *
 * The Bitbucket counterpart of the GitHub organization fetch: a workspace is fetched on the first
 * repository connect or update that names it, then refreshed once its stored fetch goes stale,
 * and the [BitbucketWorkspaceRepository] marker row written after a successful fetch carries that
 * timestamp. Skipping a fresh workspace is reported as a completion rather than suppressed,
 * because an ingestion run waits for the workspace phase to report before it can finish.
 *
 * Parallel connects and updates of one workspace take turns on a per-workspace lock, so only the
 * first one pays the API calls: the rest re-check inside the lock and find fresh metadata.
 *
 * Like the repository collectors, the fetch phase is announced before any fallible work and every
 * failure after that point reports itself, so a run can never sit open behind a fetch that died.
 *
 * @constructor Creates the service from its client, marker repository, credential source and
 *        publisher.
 * @param bitbucketClient Performs the workspace API calls.
 * @param workspaceRepository Owns the fetched-workspace marker rows.
 * @param credentialApi Resolves the named credential a connection was connected with.
 * @param eventPublisher Publishes the fetch lifecycle events the ingestion module reacts to.
 */
@Service
internal class BitbucketWorkspaceService(
    private val bitbucketClient: BitbucketClient,
    private val workspaceRepository: BitbucketWorkspaceRepository,
    private val credentialApi: AtlassianCredentialApi,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val workspaceLocks = ConcurrentHashMap<String, Mutex>()

    /**
     * Fetches the metadata and members of one workspace unless a fresh fetch is already stored.
     *
     * @param workspace The workspace to fetch metadata of.
     * @param authId The id of the user whose stored credential to authenticate with.
     * @param credentialName The name of that user's stored credential to authenticate with.
     * @param transactionId The ingestion run the fetch reports under.
     * @throws AtlassianCredentialNotFoundException when the named credential cannot be resolved and
     *         no fresh fetch is stored.
     */
    @Tracked("Connecting Bitbucket workspace level data")
    suspend fun connectWorkspaceIfNecessary(
        workspace: String,
        authId: String,
        credentialName: String,
        transactionId: UUID,
    ) {
        eventPublisher.publishEvent(BitbucketWorkspaceMetadataFetchingStartedEvent(transactionId))

        workspaceLocks.computeIfAbsent(workspace) { Mutex() }.withLock {
            val marker = withContext(Dispatchers.IO) { workspaceRepository.findById(workspace) }.orElse(null)
            if (marker != null && !isStale(marker)) {
                eventPublisher.publishEvent(BitbucketWorkspaceMetadataFetchingCompletedEvent(transactionId))
                return@withLock
            }

            connectWorkspace(workspace, authId, credentialName, transactionId)

            eventPublisher.publishEvent(BitbucketWorkspaceMetadataFetchingCompletedEvent(transactionId))
        }
    }

    /**
     * Fetches the workspace and triggers ingestion of what was read.
     *
     * @param workspace The workspace to fetch metadata of.
     * @param authId The id of the user whose stored credential to authenticate with.
     * @param credentialName The name of that user's stored credential to authenticate with.
     * @param transactionId The ingestion run the fetch reports under.
     * @throws AtlassianCredentialNotFoundException when the named credential cannot be resolved.
     */
    @Suppress("ThrowsCount")
    private suspend fun connectWorkspace(
        workspace: String,
        authId: String,
        credentialName: String,
        transactionId: UUID,
    ) {
        try {
            val credential = credentialApi.findSecret(authId, credentialName)
                ?: throw AtlassianCredentialNotFoundException(authId, credentialName)

            val metadata = bitbucketClient.fetchWorkspaceMetadata(workspace, credential)
            val members = bitbucketClient.getWorkspaceMembers(workspace, credential)

            eventPublisher.publishEvent(buildFetchedEvent(transactionId, workspace, metadata, members))

            withContext(Dispatchers.IO) {
                workspaceRepository.save(
                    BitbucketWorkspace(slug = workspace, name = metadata.name, fetchedAt = Instant.now()),
                )
            }
        } catch (e: CancellationException) {
            // Never swallow cancellation: the run is abandoned, not failed, and reporting a fetch
            // that did not happen would close a phase whose work never ran.
            throw e
        } catch (e: Exception) {
            eventPublisher.publishEvent(BitbucketWorkspaceMetadataFetchingFailedEvent(transactionId, e.message))
            throw e
        }
    }

    /**
     * Whether the stored fetch of one workspace has aged out.
     *
     * A missing timestamp reads as stale: rows written before the column existed refresh once on
     * their next update rather than being trusted sight unseen.
     *
     * @param marker The stored workspace marker row.
     * @return `true` when the workspace should be fetched again.
     */
    private fun isStale(marker: BitbucketWorkspace): Boolean {
        val fetchedAt = marker.fetchedAt ?: return true
        return fetchedAt.isBefore(Instant.now().minus(STALE_AFTER))
    }

    private companion object {
        /** How old a stored workspace fetch may be before the next update refreshes it. */
        private val STALE_AFTER: Duration = Duration.ofHours(24)
    }

    /**
     * Builds the [BitbucketWorkspaceMetadataFetchedEvent] off what the client returned.
     *
     * @param transactionId The ingestion run the fetch belongs to.
     * @param workspace The workspace the data was fetched of.
     * @param metadata The workspace metadata the client returned.
     * @param members The workspace members the client returned.
     * @return The event the ingestion module maps into the workspace artifact.
     */
    private fun buildFetchedEvent(
        transactionId: UUID,
        workspace: String,
        metadata: WorkspaceMetadataResponse,
        members: WorkspaceMembersResponse,
    ): BitbucketWorkspaceMetadataFetchedEvent {
        return BitbucketWorkspaceMetadataFetchedEvent(
            transactionId = transactionId,
            workspace = workspace,
            uuid = metadata.uuid,
            name = metadata.name,
            isPrivate = metadata.isPrivate,
            createdOn = metadata.createdOn,
            url = metadata.url,
            members = members.members.map { member ->
                BitbucketWorkspaceMetadataMember(
                    accountId = member.user.accountId,
                    nickname = member.user.nickname,
                    displayName = member.user.displayName,
                )
            },
        )
    }
}
