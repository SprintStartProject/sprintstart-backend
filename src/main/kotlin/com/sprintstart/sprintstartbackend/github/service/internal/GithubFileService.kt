package com.sprintstart.sprintstartbackend.github.service.internal

import com.sprintstart.sprintstartbackend.github.external.events.files.GithubFileDeletedEvent
import com.sprintstart.sprintstartbackend.github.external.events.files.GithubFileFetchFailedEvent
import com.sprintstart.sprintstartbackend.github.external.events.files.GithubFileFetchedEvent
import com.sprintstart.sprintstartbackend.github.external.events.files.GithubFilesFetchCompletedEvent
import com.sprintstart.sprintstartbackend.github.external.events.files.GithubFilesFetchFailedEvent
import com.sprintstart.sprintstartbackend.github.external.events.files.GithubFilesFetchStartedEvent
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.springframework.context.ApplicationEventPublisher
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
import kotlin.streams.asSequence

private const val BUFFER_SIZE = 32

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
    private val eventPublisher: ApplicationEventPublisher,
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
    internal suspend fun fetchAndIngestAllFiles(
        githubRepositoryId: UUID,
        repositoryOwner: String,
        repositoryName: String,
        transactionId: UUID,
    ) {
        eventPublisher.publishEvent(GithubFilesFetchStartedEvent(transactionId, repositoryOwner, repositoryName))

        val githubRepository = withContext(Dispatchers.IO) {
            repoConnectionRepository.findById(githubRepositoryId)
        }

        if (githubRepository.isEmpty) {
            eventPublisher.publishEvent(
                GithubFilesFetchFailedEvent(
                    transactionId,
                    repositoryOwner,
                    repositoryName,
                    "Internal server error",
                ),
            )
            // Internal server error as this function is only used internally,
            // so we expect the repository id to be valid.
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

        eventPublisher.publishEvent(GithubFilesFetchCompletedEvent(transactionId, repositoryOwner, repositoryName))
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
        eventPublisher.publishEvent(
            GithubFilesFetchStartedEvent(
                transactionId,
                githubRepository.owner,
                githubRepository.name,
            ),
        )

        runCatching {
            if (githubRepository.lastSha.isBlank()) {
                eventPublisher.publishEvent(
                    GithubFilesFetchFailedEvent(transactionId, githubRepository.owner, githubRepository.name),
                )
                throw RepositoryNotInitializedException(githubRepository.owner, githubRepository.name)
            }
            fetchAndIngestFileUpdatesIfNecessary(githubRepository, transactionId)
        }.onFailure { e ->
            eventPublisher.publishEvent(
                GithubFilesFetchFailedEvent(
                    transactionId,
                    githubRepository.owner,
                    githubRepository.name,
                    e.message ?: "Unknown error",
                ),
            )
            throw e
        }

        eventPublisher.publishEvent(
            GithubFilesFetchCompletedEvent(
                transactionId,
                githubRepository.owner,
                githubRepository.name,
            ),
        )
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
        fetchAndIngestFileUpdates(localFsPath, githubRepository, latestSha, ghUrl, transactionId)

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
     * @param repo The sha representing the last time the repository was updated.
     * @param latestSha The newest sha representing the state to update to.
     * @param transactionId The UUID of the overall transaction, this action is a part of.
     */
    private suspend fun fetchAndIngestFileUpdates(
        localFsPath: Path,
        repo: GithubRepositoryConnection,
        latestSha: String,
        ghUrl: String,
        transactionId: UUID,
    ) {
        val output = gitRunner.exec(localFsPath, onDiskOperations.gitDiffCmp(repo.lastSha, latestSha))
        val changedFiles = output.lines().filter { it.isNotBlank() }
        val failures = mutableListOf<String>()

        changedFiles.forEach { filePath ->
            runCatching {
                val sourceUrl = "$ghUrl/blob/$latestSha/$filePath"
                when (val changedFile = fetchFileUpdate(localFsPath, filePath)) {
                    is ModifiedFile -> {
                        eventPublisher.publishEvent(
                            GithubFileFetchedEvent(
                                transactionId,
                                repo.owner,
                                repo.name,
                                changedFile.relativePath,
                                changedFile.content,
                                sourceUrl,
                            ),
                        )
                    }

                    is DeletedFile -> {
                        eventPublisher.publishEvent(
                            GithubFileDeletedEvent(
                                transactionId,
                                repo.owner,
                                repo.name,
                                changedFile.relativePath,
                            ),
                        )
                    }

                    else -> {} // binary — skip
                }
            }.onFailure { e ->
                eventPublisher.publishEvent(
                    GithubFileFetchFailedEvent(
                        transactionId,
                        repo.owner,
                        repo.name,
                        filePath,
                        e.message ?: "Unknown error",
                    ),
                )
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
     * Streams files from disk, processes them, and ingests their data into the specified GitHub repository.
     *
     * This method performs concurrent file processing, publishes events for success and failure cases,
     * and handles batching of file snapshots for ingestion.
     *
     * @param transactionId A unique identifier for the transaction associated with this file processing operation.
     * @param githubRepository The connection details for the target GitHub repository where file data will be ingested.
     * @param repositoryPath The local path to the repository where the files are stored on disk.
     * @param revision The specific revision or branch of the repository being processed.
     * @throws GithubFilesStreamFromDiskFailedPartialException If there are any failures during file processing.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun streamFilesFromDiskAndIngest(
        transactionId: UUID,
        githubRepository: GithubRepositoryConnection,
        repositoryPath: Path,
        revision: String,
    ) {
        val concurrency = Runtime.getRuntime().availableProcessors()
        val failures = mutableListOf<String>()
        val snapshotBuffer = mutableListOf<GithubFileSnapshot>()

        discoverFilePaths(repositoryPath)
            .asFlow()
            .flatMapMerge(concurrency = concurrency) { path ->
                // Concurrently process discovered files
                flow {
                    emit(
                        processSingleFile(path, repositoryPath, githubRepository, revision),
                    )
                }.flowOn(Dispatchers.IO)
            }.collect { result ->
                when (result) {
                    is FileProcessingResult.Success -> {
                        // Batch snapshots and flush them in batches of 32
                        snapshotBuffer += result.snapshot
                        if (snapshotBuffer.size >= BUFFER_SIZE) flushSnapshots(snapshotBuffer)

                        eventPublisher.publishEvent(
                            GithubFileFetchedEvent(
                                transactionId,
                                githubRepository.owner,
                                githubRepository.name,
                                result.payload.path,
                                result.payload.content,
                                result.payload.sourceUrl,
                            ),
                        )
                    }

                    is FileProcessingResult.Failure -> {
                        eventPublisher.publishEvent(
                            GithubFileFetchFailedEvent(
                                transactionId,
                                githubRepository.owner,
                                githubRepository.name,
                                result.path,
                                result.reason,
                            ),
                        )
                        failures += "${result.path}: ${result.reason}"
                    }
                }
            }

        // Flush any remaining snapshots
        flushSnapshots(snapshotBuffer)

        if (failures.isNotEmpty()) {
            throw GithubFilesStreamFromDiskFailedPartialException(failures.joinToString("\n"))
        }
    }

    /**
     * Discovers all regular file paths within a given repository path, excluding files in the ".git" directory
     * and binary files.
     *
     * @param repositoryPath the root path of the repository to be scanned for file paths
     * @return a list of paths representing all discovered files that are not binary and not within the ".git" directory
     */
    private fun discoverFilePaths(repositoryPath: Path): List<Path> =
        Files.walk(repositoryPath).use { stream ->
            stream
                .asSequence()
                .filter { Files.isRegularFile(it) }
                .filter { !it.startsWith(repositoryPath.resolve(".git")) }
                .filter { !it.isBinary() }
                .toList()
        }

    /**
     * Processes a single file by reading its content, computing its hash, generating a source URL,
     * and creating a file snapshot for the specified GitHub repository.
     *
     * @param filePath The path to the file being processed.
     * @param repositoryPath The root path of the repository,
     * used to compute the relative path of the file.
     * @param githubRepository The connection details for the GitHub repository.
     * @param revision The revision identifier (e.g., branch, tag, or commit hash)
     * to construct the source URL.
     * @return A [FileProcessingResult] that represents either the successful processing
     * result with file payload and snapshot
     * or a failure result with a reason for the failure.
     */
    private suspend fun processSingleFile(
        filePath: Path,
        repositoryPath: Path,
        githubRepository: GithubRepositoryConnection,
        revision: String,
    ): FileProcessingResult = runCatching {
        val (content, sha256) = readFileWithHash(filePath)
        val relativePath = repositoryPath.relativize(filePath).toString()
        val sourceUrl =
            "https://github.com/${githubRepository.owner}/${githubRepository.name}/blob/$revision/$relativePath"
        val snapshotId = GithubFileSnapshotSharedId(
            repositoryId = githubRepository.id,
            path = filePath.toString(),
        )
        val snapshot = GithubFileSnapshot(id = snapshotId, sha = sha256, repository = githubRepository)

        FileProcessingResult.Success(
            payload = GithubFilePayload(path = relativePath, content = content, sourceUrl = sourceUrl),
            snapshot = snapshot,
        )
    }.getOrElse { e ->
        FileProcessingResult.Failure(
            path = filePath.toString(),
            reason = "Could not read file content: ${e.message}",
        )
    }

    /**
     * Persists the provided list of file snapshots and clears the buffer.
     *
     * @param buffer A mutable list of `GithubFileSnapshot` objects to be saved.
     * The buffer is cleared after the snapshots are saved.
     */
    private suspend fun flushSnapshots(buffer: MutableList<GithubFileSnapshot>) {
        if (buffer.isEmpty()) return
        withContext(Dispatchers.IO) {
            fileSnapshotRepository.saveAll(buffer.toList())
            buffer.clear()
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

    /**
     * Reads the content of a file located at the specified path and computes its SHA-256 hash.
     *
     * It's basically a collection function. Instead of reading the file content first,
     * then hashing it,
     * this function does both in one go to half the work.
     *
     * @param filePath The path of the file to be read.
     * @return A pair where the first element is the file's content as a string,
     * and the second element is the SHA-256 hash of the file's content as a hexadecimal string.
     */
    private suspend fun readFileWithHash(filePath: Path): Pair<String, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = withContext(Dispatchers.IO) {
            Files.readAllBytes(filePath)
        }
        digest.update(bytes)
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        return String(bytes, Charsets.UTF_8) to hash
    }

    /**
     * Reads the content of a file at the given path as a string, handling any malformed input exceptions.
     * If the file's content cannot be read due to a `MalformedInputException`, null is returned.
     *
     * @param path the path to the file to be read
     * @return the content of the file as a string, or null if a malformed input exception occurs
     */
    private fun readTextSafely(path: Path): String? {
        return try {
            Files.readString(path)
        } catch (@Suppress("SwallowedException") e: MalformedInputException) {
            null
        }
    }

    /**
     * Represents the result of processing a file within the system.
     * This sealed interface distinguishes between successful and failed processing outcomes.
     */
    private sealed interface FileProcessingResult {
        data class Success(
            val payload: GithubFilePayload,
            val snapshot: GithubFileSnapshot,
        ) : FileProcessingResult

        data class Failure(
            val path: String,
            val reason: String,
        ) : FileProcessingResult
    }

    /**
     * Represents the payload of a file in a GitHub repository.
     *
     * @property path The file path within the repository.
     * @property content The content of the file, typically as a string.
     * @property sourceUrl The source URL of the file in the repository.
     */
    private data class GithubFilePayload(
        val path: String,
        val content: String,
        val sourceUrl: String,
    )
}
