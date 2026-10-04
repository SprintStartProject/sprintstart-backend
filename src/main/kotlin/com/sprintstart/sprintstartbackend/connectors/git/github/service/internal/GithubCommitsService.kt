package com.sprintstart.sprintstartbackend.connectors.git.github.service.internal

import com.sprintstart.sprintstartbackend.connectors.ConnectionState
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.commits.GithubCommitFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.commits.GithubCommitsFetchCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.commits.GithubCommitsFetchFailedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.commits.GithubCommitsFetchStartedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.git.github.repository.GithubRepositoryConnectionRepository
import com.sprintstart.sprintstartbackend.connectors.git.utils.GitCommitSink
import com.sprintstart.sprintstartbackend.connectors.git.utils.GitIngestionEngine
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.shared.git.GitCommit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Ingests the commits of a connected GitHub repository through the shared ingestion engine.
 *
 * Commits are read from the repository's local clone rather than from the GitHub REST API, which has
 * three consequences worth stating: the history is identical to what the file ingest sees, no API
 * rate limit applies, and the connector needs no pagination for it.
 *
 * Like the file ingest, a run is full or incremental depending on the connection's stored cursor,
 * and exactly one terminal event is published per run. A failed run leaves the cursor untouched so
 * the next run retries the same commits rather than skipping them.
 */
@Service
class GithubCommitsService(
    private val repoConnectionRepository: GithubRepositoryConnectionRepository,
    private val coordinatesFactory: GithubRepositoryCoordinatesFactory,
    private val ingestionEngine: GitIngestionEngine,
    private val eventPublisher: ApplicationEventPublisher,
) {
    /**
     * Fetches and ingests the commits of a connected GitHub repository.
     *
     * The connection's stored cursor decides whether the whole history is read or only what moved
     * since the last run, so a freshly connected repository reads everything and a nightly update
     * reads almost nothing.
     *
     * @param githubRepository The connected repository to process.
     * @param transactionId The id of the overall transaction this fetch is part of.
     * @throws RuntimeException if the repository cannot be fetched or its history read.
     */
    @Tracked("Fetching & ingesting commits from repository")
    internal suspend fun fetchAndIngestCommits(
        githubRepository: GithubRepositoryConnection,
        transactionId: UUID,
    ) {
        eventPublisher.publishEvent(
            GithubCommitsFetchStartedEvent(transactionId, githubRepository.owner, githubRepository.name),
        )

        val outcome = try {
            val ingested = ingestionEngine.ingestCommitsSince(
                coordinates = coordinatesFactory.of(githubRepository),
                sinceRevision = githubRepository.lastCommitsSyncedSha,
                sink = GithubCommitSink(eventPublisher, githubRepository, transactionId),
            )
            // Inside the try so that a failed cursor write still ends the run with a terminal
            // event. Only the cursor column is written: saving the whole connection would carry
            // this copy's stale file cursor and snapshot into the row.
            withContext(Dispatchers.IO) {
                repoConnectionRepository.updateCommitsCursor(githubRepository.id, ingested.revision)
            }
            ingested
        } catch (e: CancellationException) {
            // Never swallow cancellation: the run is abandoned, not failed, and publishing a
            // terminal failure here would report a fetch that did not happen.
            throw e
        } catch (e: Exception) {
            eventPublisher.publishEvent(
                GithubCommitsFetchFailedEvent(
                    transactionId,
                    githubRepository.owner,
                    githubRepository.name,
                    e.message ?: "Unknown error",
                ),
            )
            throw e
        }

        githubRepository.lastCommitsSyncedSha = outcome.revision

        eventPublisher.publishEvent(
            GithubCommitsFetchCompletedEvent(transactionId, githubRepository.owner, githubRepository.name),
        )
    }

    /**
     * Verifies the sync status of commits of a given GitHub repository.
     *
     * Given a GitHub repository connected to this application, this function checks if the local state is outdated,
     * and if so marks the repository as [ConnectionState.OUT_OF_DATE], but does not update it.
     *
     * @param githubRepository The GitHub repository to check.
     * @param transactionId The id of the overall transaction this sub-action belongs to.
     */
    @Tracked("Verifying commit sync status")
    internal suspend fun verifyCommitSyncStatus(githubRepository: GithubRepositoryConnection, transactionId: UUID) {
        eventPublisher.publishEvent(
            GithubCommitsFetchStartedEvent(transactionId, githubRepository.owner, githubRepository.name),
        )

        if (!ingestionEngine.isUpToDate(coordinatesFactory.of(githubRepository))) {
            githubRepository.connectionState = ConnectionState.OUT_OF_DATE

            withContext(Dispatchers.IO) {
                repoConnectionRepository.save(githubRepository)
            }
        }

        eventPublisher.publishEvent(
            GithubCommitsFetchCompletedEvent(transactionId, githubRepository.owner, githubRepository.name),
        )
    }
}

/** Turns the engine's commits into this connector's commit event. */
private class GithubCommitSink(
    private val eventPublisher: ApplicationEventPublisher,
    private val connection: GithubRepositoryConnection,
    private val transactionId: UUID,
) : GitCommitSink {
    override suspend fun onCommit(commit: GitCommit) {
        eventPublisher.publishEvent(
            GithubCommitFetchedEvent(
                transactionId = transactionId,
                repositoryId = connection.id,
                repositoryOwner = connection.owner,
                repositoryName = connection.name,
                author = commit.authorName,
                date = commit.committedAt,
                sha = commit.sha,
                msg = commit.subject,
            ),
        )
    }
}
