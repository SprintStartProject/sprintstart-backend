package com.sprintstart.sprintstartbackend.github.service

import com.sprintstart.sprintstartbackend.github.GithubClient
import com.sprintstart.sprintstartbackend.github.models.GithubFileSnapshot
import com.sprintstart.sprintstartbackend.github.models.GithubFileSnapshotSharedId
import com.sprintstart.sprintstartbackend.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.github.models.GithubRepositorySnapshot
import com.sprintstart.sprintstartbackend.github.models.client.FileResponse
import com.sprintstart.sprintstartbackend.github.models.client.TreeEntry
import com.sprintstart.sprintstartbackend.github.repository.GithubFileSnapshotRepository
import com.sprintstart.sprintstartbackend.github.repository.GithubRepositoryConnectionRepository
import com.sprintstart.sprintstartbackend.github.repository.GithubRepositorySnapshotRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.util.Base64

val DECODER: Base64.Decoder = Base64.getDecoder()

@Service
class GithubConnectorService(
    val repoConnectionRepository: GithubRepositoryConnectionRepository,
    val repoSnapshotRepository: GithubRepositorySnapshotRepository,
    val fileSnapshotRepository: GithubFileSnapshotRepository,
    val applicationScope: CoroutineScope,
    val githubClient: GithubClient,
    val clock: Clock = Clock.systemUTC(),
) {
    fun connectRepository(owner: String, name: String) {
        // Save an initial snapshot of the repository
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
        applicationScope.launch {
            launch { fetchAndIngestAllFiles(owner, name, repoConnection) }
            launch { fetchAndIngestAllCommits(owner, name) }
            launch { fetchAndIngestAllIssues(owner, name) }
            launch { fetchAndIngestAllPullRequests(owner, name) }
            // TODO: Handle more resources
            // TODO: Start CRON job
        }
    }

    private suspend fun fetchAndIngestAllFiles(owner: String, name: String, repository: GithubRepositoryConnection) {
        val fileTree = githubClient.fetchFileTree(owner, name)

        // Compute files in batches of 10, to improve performance while not overloading the connection pool.
        // Fine-tune later if needed
        fileTree
            .tree
            .filter { it.type == "blob" }
            // TODO: Filter binaries etc...
            .chunked(10)
            .forEach { batch ->
                coroutineScope {
                    batch
                        .map { file ->
                            async { fetchAndIngestFileIfNecessary(owner, name, file, repository) }
                        }.awaitAll()
                }
            }
    }

    private suspend fun fetchAndIngestFileIfNecessary(
        owner: String,
        name: String,
        file: TreeEntry,
        repository: GithubRepositoryConnection,
    ) {
        val fileSnapshot = fileSnapshotRepository.findByCombinedId(repository.id, file.path)
        if (fileSnapshot == null) {
            fetchAndIngestFile(owner, name, file, repository)
        } else {
            fetchAndIngestFileInc(owner, name, fileSnapshot)
        }
    }

    private suspend fun fetchAndIngestFileInc(owner: String, name: String, fileSnapshot: GithubFileSnapshot) {
        val fileResponse = fetchFile(owner, name, fileSnapshot.id.path)
        val decodedContent = DECODER.decode(fileResponse.content).toString(Charsets.UTF_8)

        fileSnapshot.lastIngestedAt = Instant.now(clock)
        fileSnapshot.sha = fileResponse.sha
        fileSnapshotRepository.save(fileSnapshot)

        // TODO: Ingest with decoded content
    }

    private suspend fun fetchAndIngestFile(
        owner: String,
        name: String,
        file: TreeEntry,
        repository: GithubRepositoryConnection,
    ) {
        val fileResponse = fetchFile(owner, name, file.path)
        val decodedContent = DECODER.decode(fileResponse.content).toString(Charsets.UTF_8)

        val fileSnapshotId = GithubFileSnapshotSharedId(
            repositoryId = repository.id,
            path = file.path,
        )
        val fileSnapshot = GithubFileSnapshot(
            id = fileSnapshotId,
            sha = file.sha,
        )
        fileSnapshotRepository.save(fileSnapshot)

        // TODO: Ingest with decoded content
    }

    private suspend fun fetchFile(owner: String, name: String, path: String): FileResponse {
        val file = githubClient.fetchFile(owner, name, path)

        require(file.type == "file") {
            "Received resource with type ${file.type} but expected type 'file'"
        }
        require(file.encoding == "base64") {
            "Received resource content encoded with ${file.encoding} but expected 'base64'"
        }

        return file
    }

    private suspend fun fetchAndIngestAllCommits(owner: String, name: String) {
        val commits = githubClient.fetchAllCommits(owner, name)

        // TODO: Ingest
    }

    private suspend fun fetchAndIngestAllIssues(owner: String, name: String) {
        val issues = githubClient.fetchAllIssues(owner, name)

        // TODO: Ingest
    }

    private suspend fun fetchAndIngestAllPullRequests(owner: String, name: String) {
        val pullRequests = githubClient.fetchAllPullRequests(owner, name)

        // TODO: Ingest
    }
}
