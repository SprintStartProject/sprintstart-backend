package com.sprintstart.sprintstartbackend.connectors.github.service.internal

import com.sprintstart.sprintstartbackend.connectors.github.external.events.commits.GithubCommitFetchFailedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.commits.GithubCommitFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.commits.GithubCommitsFetchCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.commits.GithubCommitsFetchFailedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.commits.GithubCommitsFetchStartedEvent
import com.sprintstart.sprintstartbackend.connectors.github.models.ConnectionState
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositorySnapshot
import com.sprintstart.sprintstartbackend.connectors.github.models.client.dto.Commit
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.GithubCommitsFetchFailedPartiallyException
import com.sprintstart.sprintstartbackend.connectors.github.repository.GithubRepositoryConnectionRepository
import com.sprintstart.sprintstartbackend.connectors.github.util.CustomOnDiskCache
import com.sprintstart.sprintstartbackend.connectors.github.util.GitOperationRunner
import com.sprintstart.sprintstartbackend.connectors.github.util.OnDiskOperations
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

@Service
class GithubCommitsService(
    private val repoConnectionRepository: GithubRepositoryConnectionRepository,
    private val onDiskOperations: OnDiskOperations,
    private val customCache: CustomOnDiskCache,
    private val eventPublisher: ApplicationEventPublisher,
    private val gitRunner: GitOperationRunner,
) {
    /**
     * Fetches and processes the latest commits from a GitHub repository snapshot.
     * The method handles fetching commit data, parsing each commit line, and publishing events for
     * the start and completion of the operation. If errors occur during commit processing, an exception is thrown.
     *
     * @param latestSnapshot A snapshot of the GitHub repository containing metadata such as the repository's
     * owner, name, and the timestamp of the last synchronization.
     * @param transactionId A unique identifier for the transaction corresponding to this synchronization process.
     * @throws GithubCommitsFetchFailedPartiallyException If one or more commits fail to process during the
     * operation, this exception is thrown with details of the failures.
     */
    @Tracked("Fetching & ingesting all commits from repository")
    internal suspend fun fetchAndIngestAllCommits(
        latestSnapshot: GithubRepositorySnapshot,
        transactionId: UUID,
    ) {
        eventPublisher.publishEvent(
            GithubCommitsFetchStartedEvent(
                transactionId,
                latestSnapshot.repository.owner,
                latestSnapshot.repository.name,
            ),
        )

        val rawOutput = fetchCommits(latestSnapshot, transactionId, true)
        ingestCommits(latestSnapshot, transactionId, rawOutput)
    }

    /**
     * Fetches the latest commits from a GitHub repository and ingests them into the system.
     *
     * This method triggers the publication of an event to signal the start of the process,
     * retrieves the commit data from the repository, and processes the data to integrate
     * it into the system.
     *
     * @param latestSnapshot The current snapshot of the GitHub repository, containing information
     * about the repository's state and metadata such as the owner and name.
     * @param transactionId The unique identifier for the ongoing transaction to track and log
     * operations performed during the commit-fetching process.
     */
    @Tracked("Fetching & ingesting latest commits from repository")
    internal suspend fun fetchAndIngestLatestCommits(
        latestSnapshot: GithubRepositorySnapshot,
        transactionId: UUID,
    ) {
        eventPublisher.publishEvent(
            GithubCommitsFetchStartedEvent(
                transactionId,
                latestSnapshot.repository.owner,
                latestSnapshot.repository.name,
            ),
        )

        val rawOutput = fetchCommits(latestSnapshot, transactionId, false)
        ingestCommits(latestSnapshot, transactionId, rawOutput)
    }

    /**
     * Verifies the sync status of commits of a given GitHub repository.
     *
     * Given a GitHub repository connected to this application, this function checks if the local state is outdated,
     * and if so marks the repository as [ConnectionState.OUT_OF_DATE], but does not update it.
     *
     * @param githubRepository The GitHub repository to check.
     */
    @Tracked("Verifying commit sync status")
    internal suspend fun verifyCommitSyncStatus(githubRepository: GithubRepositoryConnection, transactionId: UUID) {
        eventPublisher.publishEvent(
            GithubCommitsFetchStartedEvent(
                transactionId,
                githubRepository.owner,
                githubRepository.name,
            ),
        )

        val localFsPath = customCache.getLocalRepositoryPath(githubRepository)

        if (!isRepositoryUpToDate(localFsPath)) {
            githubRepository.connectionState = ConnectionState.OUT_OF_DATE

            withContext(Dispatchers.IO) {
                repoConnectionRepository.save(githubRepository)
            }
        }

        eventPublisher.publishEvent(
            GithubCommitsFetchCompletedEvent(
                transactionId,
                githubRepository.owner,
                githubRepository.name,
            ),
        )
    }

    /**
     * Processes a list of raw commit lines and updates the repository snapshot.
     * Publishes appropriate events based on the success or failure of commit ingestion.
     *
     * @param latestSnapshot The latest snapshot of the GitHub repository to update with commit data.
     * @param transactionId A unique identifier for the transaction processing the commits.
     * @param rawCommits A string containing raw commit data, with each line representing a commit.
     */
    private suspend fun ingestCommits(
        latestSnapshot: GithubRepositorySnapshot,
        transactionId: UUID,
        rawCommits: String,
    ) {
        val failures = mutableListOf<String>()

        rawCommits
            .lines()
            .filter { it.isNotBlank() }
            .forEach { line -> processCommitLine(latestSnapshot.repository, line, transactionId, failures) }

        if (failures.isNotEmpty()) {
            eventPublisher.publishEvent(
                GithubCommitsFetchFailedEvent(
                    transactionId,
                    latestSnapshot.repository.owner,
                    latestSnapshot.repository.name,
                    failures.joinToString("\n"),
                ),
            )
            throw GithubCommitsFetchFailedPartiallyException(failures.joinToString("\n"))
        }

        eventPublisher.publishEvent(
            GithubCommitsFetchCompletedEvent(
                transactionId,
                latestSnapshot.repository.owner,
                latestSnapshot.repository.name,
            ),
        )
    }

    /**
     * Checks the remote if this local copy of the repository is still up to date.
     *
     * @param localFsPath The path to the local copy of the GitHub repository.
     * @return true, if the repository is up to date with remote, otherwise false.
     */
    private suspend fun isRepositoryUpToDate(localFsPath: Path): Boolean {
        val localHead = gitRunner.exec(localFsPath, onDiskOperations.gitRevParse()).trim()
        val remoteHead = gitRunner
            .exec(localFsPath, onDiskOperations.gitLsRemote())
            .trim()
            .substringBefore('\t')
        return localHead == remoteHead
    }

    /**
     * Fetches commit data from a GitHub repository using the local repository cache and returns the raw output.
     * The method utilizes `gitRunner` for fetching either all commits or commits after a specific timestamp,
     * based on the value of `doSyncAll`.
     * If an error occurs during the fetching process, appropriate failure events are published, and the
     * error is rethrown.
     *
     * @param latestSnapshot The snapshot of the repository containing metadata for the latest synchronization,
     * including the last synchronization timestamp.
     * @param doSyncAll A boolean flag indicating whether to fetch all commits from the repository (`true`)
     * or only those after the last synchronization timestamp (`false`).
     * @param transactionId A unique identifier for the transaction associated with this fetch operation.
     * @return A raw string containing the fetched commit data in a line-separated format.
     * @throws Exception If the commit fetching process fails due to any reason, the exception is rethrown after
     * publishing a failure event.
     */
    private suspend fun fetchCommits(
        latestSnapshot: GithubRepositorySnapshot,
        transactionId: UUID,
        doSyncAll: Boolean,
    ): String = runCatching {
        val localCopyPath = customCache.getLocalRepositoryPath(
            latestSnapshot.repository,
        )
        if (doSyncAll) {
            gitRunner.exec(localCopyPath, onDiskOperations.gitCommits())
        } else {
            gitRunner.exec(localCopyPath, onDiskOperations.gitCommitsAfter(latestSnapshot.lastCommitsSyncAt))
        }
    }.getOrElse { e ->
        eventPublisher.publishEvent(
            GithubCommitsFetchFailedEvent(
                transactionId,
                latestSnapshot.repository.owner,
                latestSnapshot.repository.name,
                e.message ?: "Unknown error",
            ),
        )
        throw e
    }

    /**
     * Processes a single line of commit data, parsing it into a structured commit object
     * and ingesting it as part of a synchronization process. If an error occurs during parsing
     * or ingestion, appropriate failure events are published and the error messages
     * are added to the provided failure list.
     *
     * @param repository The repository connection the commit belongs to.
     * @param line The raw string representing the commit data.
     * @param transactionId A unique identifier for the transaction associated with the commit process.
     * @param failures A mutable list to record error messages for any failures encountered during
     * parsing or ingestion of the commit.
     */
    private fun processCommitLine(
        repository: GithubRepositoryConnection,
        line: String,
        transactionId: UUID,
        failures: MutableList<String>,
    ) {
        val commit = runCatching { parseCommit(line) }
            .onFailure { e ->
                eventPublisher.publishEvent(
                    GithubCommitFetchFailedEvent(
                        transactionId,
                        repository.id,
                        repository.owner,
                        repository.name,
                        null,
                        e.message ?: "Unknown error",
                    ),
                )
                failures.add("Parsing $line failed: ${e.message}")
            }.getOrNull() ?: return

        runCatching { ingestCommit(repository, commit, transactionId) }
            .onFailure { e ->
                eventPublisher.publishEvent(
                    GithubCommitFetchFailedEvent(
                        transactionId,
                        repository.id,
                        repository.owner,
                        repository.name,
                        commit.sha,
                        e.message ?: "Unknown error",
                    ),
                )
                failures.add("Ingesting ${commit.sha} failed: ${e.message}")
            }
    }

    /**
     * Parses a raw string representing a commit and converts it into a structured Commit object.
     *
     * The input string is expected to follow a specific format: "date - sha - author - message".
     * If the format is invalid, an exception will be thrown.
     *
     * @param raw The raw string containing commit information, formatted as "date - sha - author - message".
     * @return A Commit object containing the parsed date, sha, author, and message.
     * @throws IllegalArgumentException if the input string does not conform to the expected format.
     */
    @Suppress("DestructuringDeclarationWithTooManyEntries")
    private fun parseCommit(raw: String): Commit {
        val parts = raw.split(" - ")
        require(parts.size >= 4) { "Invalid commit format: $raw" }
        val dateStr = parts[0]
        val sha = parts[1]
        val author = parts[2]
        val msg = parts.drop(3).joinToString(" - ")
        val date = Instant.parse(dateStr)

        return Commit(
            date = date,
            sha = sha,
            author = author,
            msg = msg,
        )
    }

    /**
     * Publishes an event to process a single GitHub commit as part of a synchronization transaction.
     *
     * This method generates a `GithubCommitFetchedEvent` containing detailed metadata about the commit
     * and publishes it to propagate the commit's information within the system.
     *
     * @param repository The repository connection the commit belongs to.
     * @param commit The commit object containing details such as author, date, commit SHA, and message.
     * @param transactionId The unique transaction identifier associated with the synchronization process.
     */
    private fun ingestCommit(repository: GithubRepositoryConnection, commit: Commit, transactionId: UUID) {
        eventPublisher.publishEvent(
            GithubCommitFetchedEvent(
                transactionId,
                repository.id,
                repository.owner,
                repository.name,
                commit.author,
                commit.date,
                commit.sha,
                commit.msg,
            ),
        )
    }
}
