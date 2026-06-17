package com.sprintstart.sprintstartbackend.github.service.internal

import com.sprintstart.sprintstartbackend.github.GithubClient
import com.sprintstart.sprintstartbackend.github.external.events.CommitsSyncStartedEvent
import com.sprintstart.sprintstartbackend.github.external.events.GithubCommitFetchedEvent
import com.sprintstart.sprintstartbackend.github.models.GithubRepositorySnapshot
import com.sprintstart.sprintstartbackend.github.models.client.AiIngestRequest
import com.sprintstart.sprintstartbackend.github.models.client.dto.Commit
import com.sprintstart.sprintstartbackend.github.util.CustomOnDiskCache
import com.sprintstart.sprintstartbackend.github.util.GitOperationRunner
import com.sprintstart.sprintstartbackend.github.util.OnDiskOperations
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

private const val SHORT_SHA_LENGTH = 7

@Service
class GithubCommitsService(
    private val onDiskOperations: OnDiskOperations,
    private val customCache: CustomOnDiskCache,
    private val eventPublisher: ApplicationEventPublisher,
    private val gitRunner: GitOperationRunner,
    private val githubClient: GithubClient,
) {
    /**
     * Fetches and processes the latest commits from a GitHub repository based on the given snapshot.
     * Commits are retrieved from the local repository cache and ingested into the system.
     *
     * @param latestSnapshot The snapshot of the GitHub repository, which includes synchronization metadata.
     * @param transactionId The unique identifier for the transaction associated with this synchronization.
     * @param doSyncAll Indicates whether to fetch all commits from the repository (`true`) or only those
     * after the last synchronization timestamp (`false`).
     */
    suspend fun fetchAndIngestLatestCommits(
        latestSnapshot: GithubRepositorySnapshot,
        transactionId: UUID,
        doSyncAll: Boolean = false,
    ) {
        val localCopyPath =
            customCache.getLocalRepositoryPath(latestSnapshot.repository.owner, latestSnapshot.repository.name)
        val newCommits = if (doSyncAll) {
            gitRunner.exec(localCopyPath, onDiskOperations.gitCommits())
        } else {
            gitRunner.exec(localCopyPath, onDiskOperations.gitCommitsAfter(latestSnapshot.lastCommitsSyncAt))
        }

        val commits = newCommits.lines().filter { it.isNotBlank() }.map { parseCommit(it) }

        val startJobEvent = CommitsSyncStartedEvent(
            transactionId = transactionId,
            shas = commits.map { it.sha },
        )
        eventPublisher.publishEvent(startJobEvent)

        commits
            .forEach { commit ->
                ingestCommit(commit, transactionId)
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
    @Suppress("DestructuringDeclarationWithTooManyEntries", "MagicNumber")
    private fun parseCommit(raw: String): Commit {
        val parts = raw.split(" - ")
        require(parts.size >= 4) { "Invalid commit format: $raw" }
        val dateStr = parts[0]
        val sha = parts[1]
        val author = parts[2]
        val msg = parts.drop(3).joinToString("")
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
     * @param commit The commit object containing details such as author, date, commit SHA, and message.
     * @param transactionId The unique transaction identifier associated with the synchronization process.
     */
    private suspend fun ingestCommit(commit: Commit, transactionId: UUID) {
        val event = GithubCommitFetchedEvent(
            transactionId = transactionId,
            author = commit.author,
            date = commit.date,
            sha = commit.sha,
            msg = commit.msg,
        )
        eventPublisher.publishEvent(event)

        githubClient.ingest(
            AiIngestRequest(
                artifactId = commit.sha,
                filename = "commit-${commit.sha.take(SHORT_SHA_LENGTH)}.txt",
                content = commit.msg,
            ),
        )
    }
}
