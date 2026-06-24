package com.sprintstart.sprintstartbackend.github.service.internal

import com.sprintstart.sprintstartbackend.github.GithubEventPublisher
import com.sprintstart.sprintstartbackend.github.models.GithubFileSnapshot
import com.sprintstart.sprintstartbackend.github.models.GithubFileSnapshotSharedId
import com.sprintstart.sprintstartbackend.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.github.models.client.dto.ChangedFile
import com.sprintstart.sprintstartbackend.github.models.client.dto.DeletedFile
import com.sprintstart.sprintstartbackend.github.models.client.dto.ModifiedFile
import com.sprintstart.sprintstartbackend.github.models.exceptions.GithubFilesStreamFromDiskFailedPartialException
import com.sprintstart.sprintstartbackend.github.models.exceptions.RepositoryNotInitializedException
import com.sprintstart.sprintstartbackend.github.repository.GithubFileSnapshotRepository
import com.sprintstart.sprintstartbackend.github.repository.GithubRepositoryConnectionRepository
import com.sprintstart.sprintstartbackend.github.util.CustomOnDiskCache
import com.sprintstart.sprintstartbackend.github.util.GitOperationRunner
import com.sprintstart.sprintstartbackend.github.util.OnDiskOperations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.nio.charset.MalformedInputException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID
import kotlin.io.path.exists
import kotlin.io.path.extension

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

@Service
@Suppress("TooManyFunctions")
class GithubFileService(
    private val onDiskOperations: OnDiskOperations,
    private val repoConnectionRepository: GithubRepositoryConnectionRepository,
    private val fileSnapshotRepository: GithubFileSnapshotRepository,
    private val eventPublisher: GithubEventPublisher,
    private val customCache: CustomOnDiskCache,
    private val gitRunner: GitOperationRunner,
) {
    /**
     * Streams the initial connection and ingestion of a GitHub repository's files.
     *
     * This function handles initializing the local clone of the GitHub repository
     * (if not already existing, at last the clone will be triggered here) by starting
     * the fetch and ingest actions for this repository into the AI system.
     *
     * @param githubRepositoryId The GitHub repository id to initialize.
     * @param transactionId The UUID of the overall transaction, this fetch/ingest is a part of.
     */
    @Suppress("UnusedParameter")
    internal suspend fun fetchAndIngestAllFiles(
        githubRepositoryId: UUID,
        transactionId: UUID,
    ) {
        eventPublisher.publishGithubFilesFetchingStartedEvent(transactionId)

        val githubRepository = withContext(Dispatchers.IO) {
            repoConnectionRepository.findById(githubRepositoryId)
        }

        if (githubRepository.isEmpty) {
            eventPublisher.publishGithubFilesFetchingFailedEvent(transactionId, null)
            // Internal server error as this function is only used internally, so we expect the repository id to be valid.
            throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Repository with id $githubRepositoryId not found",
            )
        }

        val path = customCache.getLocalRepositoryPath(githubRepository.get().owner, githubRepository.get().name)
        val currentRevision = gitRunner.exec(path, onDiskOperations.gitRevParse()).trim()

        streamFilesFromDiskAndIngest(transactionId, githubRepository.get(), path, currentRevision)

        githubRepository.get().lastSha = currentRevision
        withContext(Dispatchers.IO) {
            repoConnectionRepository.save(githubRepository.get())
        }

        eventPublisher.publishGithubFilesFetchingCompletedEvent(transactionId)
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
    suspend fun fetchAndIngestFileUpdatesIncremental(
        githubRepository: GithubRepositoryConnection,
        transactionId: UUID,
    ) {
        eventPublisher.publishGithubFilesFetchingStartedEvent(transactionId)

        try {
            if (githubRepository.lastSha.isBlank()) {
                throw RepositoryNotInitializedException(githubRepository.owner, githubRepository.name)
            }
            fetchAndIngestFileUpdatesIfNecessary(githubRepository, transactionId)
        } catch (@Suppress("TooGenericException") e: Exception) {
            eventPublisher.publishGithubFilesFetchingFailedEvent(transactionId, e.message)
            throw e
        }

        eventPublisher.publishGithubFilesFetchingCompletedEvent(transactionId)
    }

    /**
     * Fetches and ingests updates to files from a GitHub repository into the system.
     * Updates the last processed SHA after successfully ingesting the changes.
     *
     * @param localFsPath The local filesystem path where files are stored.
     * @param githubRepository The connection details of the GitHub repository.
     * @param latestSha The latest commit SHA to fetch updates from.
     * @param transactionId The unique identifier for the current transaction.
     */
    private suspend fun fetchAndIngestFileUpdates(
        localFsPath: Path,
        githubRepository: GithubRepositoryConnection,
        latestSha: String,
        transactionId: UUID,
    ) {
        val ghUrl = "https://github.com/${githubRepository.owner}/${githubRepository.name}"
        fetchAndIngestFileUpdates(localFsPath, githubRepository.lastSha, latestSha, ghUrl, transactionId)

        githubRepository.lastSha = latestSha
        withContext(Dispatchers.IO) {
            repoConnectionRepository.save(githubRepository)
        }
    }

    /**
     * Handles the fetching and ingestion of a local file into the AI system.
     *
     * This function, given information on the file and on the timespan of which changes are
     * supposed to be fetched, fetches the content of the changed file and orchestrates
     * what to do with that information, e.g., un-ingest file that was deleted in the code,
     * or re-ingest ones that changed.
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
        ghUrl: String,
        transactionId: UUID,
    ) {
        val output = gitRunner.exec(localFsPath, onDiskOperations.gitDiffCmp(lastSha, latestSha))
        val changedFiles = output.lines().filter { it.isNotBlank() }
        val failures = mutableListOf<String>()

        changedFiles.forEach { filePath ->
            runCatching {
                val sourceUrl = "$ghUrl/blob/$latestSha/$filePath"
                when (val changedFile = fetchFileUpdate(localFsPath, filePath)) {
                    is ModifiedFile -> {
                        eventPublisher.publishGithubFileFetchedEvent(
                            transactionId,
                            changedFile.relativePath,
                            changedFile.content,
                            sourceUrl,
                        )
                    }

                    is DeletedFile -> {
                        eventPublisher.publishGithubFileDeletedEvent(transactionId, changedFile.relativePath)
                    }

                    else -> {} // binary — skip
                }
            }.onFailure { e ->
                eventPublisher.publishGithubFileFetchFailedEvent(transactionId, filePath, e.message ?: "Unknown error")
                failures.add("$filePath: ${e.message}")
            }
        }

        if (failures.isNotEmpty()) {
            throw GithubFilesStreamFromDiskFailedPartialException(failures.joinToString("\n"))
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
            else -> readTextSafely(absolutePath)?.let { ModifiedFile(relativeFilePath, it) }
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
     */
    private suspend fun streamFilesFromDiskAndIngest(
        transactionId: UUID,
        githubRepository: GithubRepositoryConnection,
        repositoryPath: Path,
        revision: String,
    ) {
        val failures = mutableListOf<String>()
        withContext(Dispatchers.IO) {
            Files.walk(repositoryPath).use { stream ->
                stream
                    .iterator()
                    .asSequence()
                    .filter { Files.isRegularFile(it) }
                    .filter { !it.startsWith(repositoryPath.resolve(".git")) }
                    .filter { !it.isBinary() }
                    .map { filePath ->
                        val relativePath = repositoryPath.relativize(filePath).toString()
                        val content: String = try {
                            Files.readString(filePath)
                        } catch (@Suppress("SwallowedException", "TooGenericException") e: RuntimeException) {
                            failures.add("Reading text content of $filePath failed: ${e.message}")
                            eventPublisher.publishGithubFileFetchFailedEvent(
                                transactionId,
                                filePath.toString(),
                                "Could not read file content: ${e.message}",
                            )

                            // Just pass empty content
                            ""
                        }

                        val fileSnapshotId = GithubFileSnapshotSharedId(
                            repositoryId = githubRepository.id,
                            path = filePath.toString(),
                        )
                        val fileSnapshot = GithubFileSnapshot(
                            id = fileSnapshotId,
                            sha = content.sha256(),
                            repository = githubRepository,
                        )
                        fileSnapshotRepository.save(fileSnapshot)

                        val sourceUrl =
                            "https://github.com/${githubRepository.owner}/${githubRepository.name}/blob/$revision/$relativePath"

                        GithubFilePayload(
                            path = relativePath,
                            content = content,
                            sourceUrl = sourceUrl,
                        )
                    }.forEach {
                        eventPublisher.publishGithubFileFetchedEvent(
                            transactionId,
                            it.path,
                            it.content,
                            it.sourceUrl,
                        )
                    }
            }
        }

        if (failures.isNotEmpty()) {
            throw GithubFilesStreamFromDiskFailedPartialException(failures.joinToString("\n"))
        }
    }

    /**
     * Checks for updates in the specified GitHub repository and processes file updates if necessary.
     *
     * This method synchronizes the local repository state with the latest changes from the remote
     * GitHub repository. If updates are detected, it fetches and processes the new or changed files.
     *
     * @param githubRepository The connection details of the GitHub repository, including owner and name.
     * @param transactionId The unique identifier for the current transaction or operation.
     */
    private suspend fun fetchAndIngestFileUpdatesIfNecessary(
        githubRepository: GithubRepositoryConnection,
        transactionId: UUID,
    ) {
        val localFsPath = customCache.getLocalRepositoryPath(githubRepository.owner, githubRepository.name)
        val latestSha = updateLocalRepository(localFsPath)

        if (githubRepository.lastSha == latestSha) {
            return // Up to date, nothing to do
        }

        fetchAndIngestFileUpdates(localFsPath, githubRepository, latestSha, transactionId)
    }

    /**
     * Updates the local Git repository by fetching and merging changes from the remote repository.
     * Executes operations within the context of a designated IO dispatcher.
     *
     * @param localFsPath The file system path to the local Git repository.
     * @return The current commit hash of the repository after the update process.
     */
    private suspend fun updateLocalRepository(localFsPath: Path): String {
        withContext(Dispatchers.IO) {
            gitRunner.exec(localFsPath, onDiskOperations.gitFetch())
            gitRunner.exec(localFsPath, onDiskOperations.gitMerge())
        }
        return gitRunner.exec(localFsPath, onDiskOperations.gitRevParse()).trim()
    }

    /**
     * Attaches a custom binary check function to [Path].
     */
    private fun Path.isBinary() = extension.lowercase() in binaryExtensions

    private fun readTextSafely(path: Path): String? {
        return try {
            Files.readString(path)
        } catch (@Suppress("SwallowedException") e: MalformedInputException) {
            null
        }
    }

    /**
     * Calculates the SHA-256 hash of a string.
     */
    private fun String.sha256(): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(toByteArray())
            .joinToString("") {
                "%02x".format(it)
            }

    private data class GithubFilePayload(
        val path: String,
        val content: String,
        val sourceUrl: String,
    )
}
