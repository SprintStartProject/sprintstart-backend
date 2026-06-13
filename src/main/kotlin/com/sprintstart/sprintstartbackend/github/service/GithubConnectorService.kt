package com.sprintstart.sprintstartbackend.github.service

import com.sprintstart.sprintstartbackend.github.GithubClient
import com.sprintstart.sprintstartbackend.github.external.events.*
import com.sprintstart.sprintstartbackend.github.models.*
import com.sprintstart.sprintstartbackend.github.models.client.dto.ChangedFile
import com.sprintstart.sprintstartbackend.github.models.client.dto.Commit
import com.sprintstart.sprintstartbackend.github.models.client.dto.DeletedFile
import com.sprintstart.sprintstartbackend.github.models.client.dto.ModifiedFile
import com.sprintstart.sprintstartbackend.github.repository.GithubFileSnapshotRepository
import com.sprintstart.sprintstartbackend.github.repository.GithubRepositoryConnectionRepository
import com.sprintstart.sprintstartbackend.github.repository.GithubRepositorySnapshotRepository
import com.sprintstart.sprintstartbackend.github.util.CustomOnDiskCache
import com.sprintstart.sprintstartbackend.github.util.OnDiskOperations
import jakarta.transaction.Transactional
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.readText

private val binaryExtensions = setOf(
    // images
    "png",
    "jpg",
    "jpeg",
    "gif",
    "bmp",
    "ico",
    "svg",
    "webp",
    // compiled
    "class",
    "jar",
    "war",
    "ear",
    // archives
    "zip",
    "tar",
    "gz",
    "rar",
    // binaries
    "exe",
    "dll",
    "so",
    "dylib",
    // media
    "mp3",
    "mp4",
    "wav",
    "avi",
    // documents
    "pdf",
    "doc",
    "docx",
    "xls",
    "xlsx",
)

/**
 * Handles the business logic of connecting and managing GitHub repositories.
 */
@Service
class GithubConnectorService(
    private val applicationScope: CoroutineScope,
    private val eventPublisher: ApplicationEventPublisher,
    private val repoConnectionRepository: GithubRepositoryConnectionRepository,
    private val repoSnapshotRepository: GithubRepositorySnapshotRepository,
    private val fileSnapshotRepository: GithubFileSnapshotRepository,
    private val githubClient: GithubClient,
    private val customCache: CustomOnDiskCache,
    private val onDiskOperations: OnDiskOperations,
) {
    /**
     * Connect a new repository.
     *
     * Given a `owner` and a `name` of a GitHub repository, this connects the repository
     * to the SprintStart application and starts all processing jobs in the background.
     * Tasks started for background execution include:
     *
     * * Fetching the repository code
     * * Fetching the repository commits
     * * Fetching the repository issues
     * * Fetching the repository pull requests
     * * Starting a CRON job that checks for upates every night.
     *
     * _**Schema:** `https://github.com/{owner}/{name}`_
     *
     * @param owner The name of the GitHub user/org that owns the repository to connect.
     * @param name The name of the repository to connect.
     *
     * @throws IllegalStateException If on one of the processed file resources, the GitHub api
     * returns malformed responses.
     */
    @Transactional
    fun connectRepository(owner: String, name: String) {
        // Save an initial snapshot of the repository
        val transactionId = UUID.randomUUID()
        val repoConnection = GithubRepositoryConnection(
            owner = owner,
            name = name,
        )
        val repoSnapshot = GithubRepositorySnapshot(
            repository = repoConnection,
        )

        repoConnection.snapshot = repoSnapshot
        repoConnectionRepository.save(repoConnection)
        repoSnapshotRepository.save(repoSnapshot)

        // Launch data collectors/processors
        applicationScope.launch { fetchAndIngestAllFiles(repoConnection, transactionId) }
        applicationScope.launch { fetchAndIngestLatestCommits(repoSnapshot, transactionId) }
        applicationScope.launch { fetchAndIngestAllIssues(repoConnection, transactionId) }
        applicationScope.launch { fetchAndIngestAllPullRequests(repoConnection, transactionId) }
    }

    /**
     * The public entry-point for updating an entire repository.
     *
     * This function, given owner and name of a **connected** GitHub repository,
     * updates all registries/resources to the newest state.
     *
     * @param owner The owner of the repository.
     * @param name The name of the repository.
     */
    suspend fun updateRepository(owner: String, name: String) {
        val transactionId = UUID.randomUUID()
        val repository = repoConnectionRepository.findByOwnerAndName(owner, name)
            ?: throw IllegalStateException("Github repository $owner/$name could not be updated - not connected!")
        val latestSnapshot = repoSnapshotRepository.findLatestByRepository(repository.id)
        val newSnapshot = GithubRepositorySnapshot(
            repository = repository,
        )

        applicationScope.launch { fetchAndIngestFileUpdatesIfNecessary(repository, transactionId) }
    }

    /**
     * Streams the initial connection and ingestion of a GitHub repository's files.
     *
     * This function handles initializing the local clone of the GitHub repository
     * (if not already existing, at last the clone will be triggered here) by starting
     * the fetch and ingest actions for this repository into the AI system.
     *
     * @param githubRepository The GitHub repository to initialize.
     * @param transactionId The UUID of the overall transaction, this fetch/ingest is a part of.
     */
    private suspend fun fetchAndIngestAllFiles(
        githubRepository: GithubRepositoryConnection,
        transactionId: UUID,
    ) {
        val path = customCache.getLocalRepositoryPath(githubRepository.owner, githubRepository.name)
        try {
            streamFilesFromDiskAndIngest(githubRepository, path, transactionId)
            val latestSha = OnDiskOperations.exec(path, onDiskOperations.gitRevParse()).trim()
            githubRepository.lastSha = latestSha
        } catch (e: Exception) {
            githubRepository.status = ConnectionStatus.FAILED
            throw e
        } finally {
            repoConnectionRepository.save(githubRepository)
        }
    }

    /**
     * Prepares the fetching and ingestion of file updates on the local copy of the GitHub repository.
     *
     * This function lays the ground for [fetchAndIngestFileUpdates], by updating the local copy of
     * the GitHub repository, filtering out files that have not changed, and updating the state afterward.
     *
     * @param githubRepository The GitHub repository to fetch/ingest on.
     * @param transactionId The UUID of the overall transaction, this action is a part of.
     */
    private suspend fun fetchAndIngestFileUpdatesIfNecessary(
        githubRepository: GithubRepositoryConnection,
        transactionId: UUID,
    ) {
        val localFsPath = customCache.getLocalRepositoryPath(githubRepository.owner, githubRepository.name)
        withContext(Dispatchers.IO) {
            OnDiskOperations.exec(localFsPath, onDiskOperations.gitFetch())
            OnDiskOperations.exec(localFsPath, onDiskOperations.gitMerge())
        }
        val latestSha = OnDiskOperations.exec(localFsPath, onDiskOperations.gitRevParse()).trim()
        if (githubRepository.lastSha.isBlank()) {
            throw IllegalStateException(
                "Update of repository ${githubRepository.owner}/${githubRepository.name} failed - not initialized!",
            )
        }

        if (githubRepository.lastSha == latestSha) {
            return // Up to date, nothing to do
        }

        fetchAndIngestFileUpdates(localFsPath, githubRepository.lastSha, latestSha, transactionId)

        // Update state
        githubRepository.lastSha = latestSha
        repoConnectionRepository.save(githubRepository)
    }

    /**
     * Handles the fetching and ingestion of a local file into the AI system.
     *
     * This function, given information on the file and on the timespan of which's changes are
     * supposed to be fetched, fetches the content of the changed file, and orchestrates
     * what to do with that information, e.g. un-ingest file that were deleted in the code,
     * or re-ingest the ones that changes.
     *
     * @param localFsPath The path to the local copy of the GitHub repository.
     * @param lastSha The sha representing the last time the repository was updated.
     * @param latestSha The newest sha representing the state to update to.
     * @param transactionId The UUID of the overall transaction, this action is a part of.
     */
    private suspend fun fetchAndIngestFileUpdates(
        localFsPath: Path,
        lastSha: String,
        latestSha: String,
        transactionId: UUID,
    ) {
        val output = OnDiskOperations.exec(localFsPath, onDiskOperations.gitDiffCmp(lastSha, latestSha))
        val changedFiles = output.lines().filter { it.isNotBlank() }

        changedFiles.forEach { filePath ->
            when (val changedFile = fetchFileUpdate(localFsPath, filePath)) {
                is ModifiedFile -> {
                    ingestFile(changedFile.relativePath, changedFile.content, transactionId)
                }

                is DeletedFile -> {
                    unIngestFile(changedFile.relativePath, transactionId)
                }

                else -> {
                    // Ignore - path was filtered out as binary
                }
            }
        }
    }

    /**
     * Fetches the update of a file from the local copy of a GitHub repository.
     *
     * This function, given the path to a cloned GitHub repository, fetches a changed file
     * from that repository from disk.
     *
     * @param localFsPath The path to the local copy of the GitHub repository.
     * @param relativeFilePath The repository-relative path to the file to fetch updates from.
     */
    private suspend fun fetchFileUpdate(localFsPath: Path, relativeFilePath: String): ChangedFile? {
        val absolutePath = localFsPath.resolve(relativeFilePath)
        return when {
            absolutePath.isBinary() -> null
            !absolutePath.exists() -> DeletedFile(relativeFilePath)
            else -> ModifiedFile(relativeFilePath, absolutePath.readText())
        }
    }

    /**
     * Reads files from disk and directly streams the ingestion of each file.
     *
     * This function, given the path to a local GitHub repository, reads all files it can find from the disk
     * and ingests them into the AI system.
     *
     * # Ignored
     *
     * * `.git`
     * * All binary file extensions (see [binaryExtensions])
     *
     * @see binaryExtensions
     *
     * @param githubRepository The GitHub repository to ingest.
     * @param repositoryPath The path to the GitHub repository, locally.
     * @param transactionId The UUID of the overall transaction, this action is a part of.
     */
    private fun streamFilesFromDiskAndIngest(
        githubRepository: GithubRepositoryConnection,
        repositoryPath: Path,
        transactionId: UUID,
    ) {
        Files.walk(repositoryPath).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { !it.startsWith(repositoryPath.resolve(".git")) }
                .filter { !it.isBinary() }
                .forEach { filePath ->
                    val relativePath = repositoryPath.relativize(filePath).toString()
                    val content = Files.readString(filePath)

                    val fileSnapshotId = GithubFileSnapshotSharedId(
                        repositoryId = githubRepository.id,
                        path = filePath.toString(),
                    )
                    val fileSnapshot = GithubFileSnapshot(
                        id = fileSnapshotId,
                        sha = content.hashCode().toString(),
                        repository = githubRepository,
                    )
                    fileSnapshotRepository.save(fileSnapshot)

                    ingestFile(relativePath, content, transactionId)
                }
        }
    }

    /**
     * Publishes a spring event to ingest the given resource into the AI system.
     *
     * This function publishes a [GithubFileFetchedEvent], that a handler in the upload/ingestion
     * module waits for, picks up, and then handles the ingestion of.
     *
     * @see GithubFileFetchedEvent
     *
     * @param path The relative path to the file to ingest.
     * @param content The actual content of the resource.
     * @param transactionId The UUID of the overall transaction, this fetch/ingest is a part of.
     */
    private fun ingestFile(path: String, content: String, transactionId: UUID) {
        val event = GithubFileFetchedEvent(
            transactionId = transactionId,
            path = path,
            content = content,
        )
        eventPublisher.publishEvent(event)
    }

    /**
     * Publishes a spring event to un-ingest the resource under the given path from the AI system.
     *
     * This function publishes a [GithubFileDeletedEvent], that a handler in the upload/ingestion
     * module waits for, picks up, and then handles the un-ingestion of.
     *
     * @see GithubFileDeletedEvent
     *
     * @param path The path to the deleted file.
     * @param transactionId The UUID of the overall transaction, this action is a part of.
     */
    private fun unIngestFile(path: String, transactionId: UUID) {
        val event = GithubFileDeletedEvent(
            transactionId = transactionId,
            path = path,
        )
        eventPublisher.publishEvent(event)
    }

    /**
     * Fetches and ingests **all** commits of a given GitHub repository.
     *
     * Given the `name` and `owner` of a GitHub repository, this function fetches all
     * commits of that repository and ingests them into the AI system.
     *
     * @see GithubCommitFetchedEvent
     *
     * @param githubRepository The GitHub repository (as handled internally) this resource belongs to.
     * @param transactionId The UUID of the overall transaction, this fetch/ingest is a part of.
     */
    private suspend fun fetchAndIngestLatestCommits(
        latestSnapshot: GithubRepositorySnapshot,
        transactionId: UUID,
        doSyncAll: Boolean = false
    ) {
        val localCopyPath =
            customCache.getLocalRepositoryPath(latestSnapshot.repository.owner, latestSnapshot.repository.name)
        val newCommits = if (doSyncAll) {
            OnDiskOperations.exec(localCopyPath, onDiskOperations.gitCommits())
        } else {
            OnDiskOperations.exec(localCopyPath, onDiskOperations.gitCommitsAfter(latestSnapshot.lastCommitsSyncAt))
        }

        newCommits.lines()
            .stream()
            .parallel()
            .forEach { rawCommit ->
                val commit = parseCommit(rawCommit)
                ingestCommit(commit, transactionId)
            }
    }

    private fun parseCommit(raw: String): Commit {
        val (dateStr, sha, author, msg) = raw.split(" - ")
        val date = Instant.parse(dateStr)

        return Commit(
            date = date,
            sha = sha,
            author = author,
            msg = msg,
        )
    }

    private fun ingestCommit(commit: Commit, transactionId: UUID) {
        val event = GithubCommitFetchedEvent(
            transactionId = transactionId,
            author = commit.author,
            date = commit.date,
            sha = commit.sha,
            msg = commit.msg,
        )
        eventPublisher.publishEvent(event)
    }

    /**
     * Fetches and ingests **all** issues of a given GitHub repository.
     *
     * Given the `name` and `owner` of a GitHub repository, this function fetches all
     * issues of that repository and ingests them into the AI system.
     *
     * @see GithubIssueFetchedEvent
     *
     * @param githubRepository The GitHub repository (as handled internally) this resource belongs to.
     * @param transactionId The UUID of the overall transaction, this fetch/ingest is a part of.
     */
    private suspend fun fetchAndIngestAllIssues(githubRepository: GithubRepositoryConnection, transactionId: UUID) {
        val issues = githubClient.fetchAllIssues(githubRepository.owner, githubRepository.name)

        issues.forEach { issue ->
            val event = GithubIssueFetchedEvent(
                transactionId = transactionId,
                number = issue.number,
                title = issue.title,
                state = issue.state,
                createdAt = issue.createdAt,
                closedAt = issue.closedAt,
                url = issue.url,
                author = issue.author?.login,
                labels = issue.labels.nodes.map { it.name },
                assignees = issue.assignees.nodes.map { it.login },
                comments = issue.comments.nodes.map { GithubIssueComment(it.body, it.author?.login, it.createdAt) },
            )
            eventPublisher.publishEvent(event)
        }
    }

    /**
     * Fetches and ingests **all** pull requests of a given GitHub repository.
     *
     * Given the `name` and `owner` of a GitHub repository, this function fetches all
     * pull requests of that repository and ingests them into the AI system.
     *
     * @see GithubPullRequestFetchedEvent
     *
     * @param githubRepository The GitHub repository (as handled internally) this resource belongs to.
     * @param transactionId The UUID of the overall transaction, this fetch/ingest is a part of.
     */
    private suspend fun fetchAndIngestAllPullRequests(
        githubRepository: GithubRepositoryConnection,
        transactionId: UUID,
    ) {
        val pullRequests = githubClient.fetchAllPullRequests(githubRepository.owner, githubRepository.name)

        pullRequests.forEach { pullRequest ->
            val event = GithubPullRequestFetchedEvent(
                transactionId = transactionId,
                number = pullRequest.number,
                body = pullRequest.body,
                state = pullRequest.state,
                createdAt = pullRequest.createdAt,
                mergedAt = pullRequest.mergedAt,
                url = pullRequest.url,
                author = pullRequest.author?.login,
                labels = pullRequest.labels?.nodes?.map { it.name },
                reviews = pullRequest.reviews?.nodes?.map {
                    GithubPullRequestReview(
                        it.body,
                        it.state,
                        it.author?.login,
                    )
                },
                comments = pullRequest.comments?.nodes?.map {
                    GithubPullRequestComment(
                        it.body,
                        it.author?.login,
                        it.createdAt,
                    )
                },
                reviewThreads = pullRequest.reviewThreads?.nodes?.map { reviewThread ->
                    GithubPullRequestReviewThread(
                        reviewThread.comments.nodes.map {
                            GithubPullRequestReviewThreadComment(
                                it.body,
                                it.author?.login,
                                it.path,
                            )
                        },
                    )
                },
            )

            eventPublisher.publishEvent(event)
        }
    }

    /**
     * Attaches a custom binary check function to [Path].
     */
    private fun Path.isBinary() = extension.lowercase() in binaryExtensions
}
