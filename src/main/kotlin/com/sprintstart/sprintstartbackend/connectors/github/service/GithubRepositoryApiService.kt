package com.sprintstart.sprintstartbackend.connectors.github.service

import com.sprintstart.sprintstartbackend.connectors.ConnectionState
import com.sprintstart.sprintstartbackend.connectors.github.GithubClient
import com.sprintstart.sprintstartbackend.connectors.github.external.GithubRepositoryApi
import com.sprintstart.sprintstartbackend.connectors.github.external.GithubSourceInstanceDto
import com.sprintstart.sprintstartbackend.connectors.github.external.dto.ChangedFileDiff
import com.sprintstart.sprintstartbackend.connectors.github.external.dto.PullRequestEvidence
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.PullRequestFileResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql.PullRequest
import com.sprintstart.sprintstartbackend.connectors.github.repository.GithubRepositoryConnectionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Repository-backed implementation of the GitHub module API exposed to other modules.
 */
@Service
class GithubRepositoryApiService(
    private val githubRepositoryConnectionRepository: GithubRepositoryConnectionRepository,
    private val githubClient: GithubClient,
) : GithubRepositoryApi {
    /**
     * Resolves the project ids currently associated with one GitHub repository connection.
     *
     * @param id The internal repository connection identifier.
     * @return The set of linked SprintStart project ids.
     * @throws NoSuchElementException When no repository connection exists for the given id.
     */
    override fun getRepositoryProjectIdsById(id: UUID): Set<UUID> {
        val repo = githubRepositoryConnectionRepository.findById(id).orElseThrow {
            NoSuchElementException("Repository with id $id not found")
        }
        return repo.projectIds
    }

    override suspend fun getPullRequestEvidence(repositoryConnectionId: UUID, prNumber: Int): PullRequestEvidence? {
        val repo = githubRepositoryConnectionRepository.findById(repositoryConnectionId).orElseThrow {
            NoSuchElementException("Repository with id $repositoryConnectionId not found")
        }
        val pullRequest = githubClient.fetchPullRequest(repo, prNumber) ?: return null
        // A second call, and only once the pull request is known to exist -- see
        // GithubClient.fetchPullRequestFiles for why the diff cannot come from the GraphQL query.
        val budgeted = budget(githubClient.fetchPullRequestFiles(repo, prNumber))
        return pullRequest.toEvidence(budgeted)
    }

    /**
     * Fits the diffs into a fixed character budget, and reports what did not fit.
     *
     * ⚠️ **Two caps, because they fail differently.** A per-file cap stops one enormous generated
     * file crowding out every real change; a total cap stops fifty small ones doing the same. Files
     * are kept in GitHub's own order rather than ranked — picking "the interesting ones" would mean
     * this service deciding what the judge may weigh, which is exactly the judgement it exists to
     * make.
     *
     * What is dropped is *counted*, never silently discarded: [PullRequestEvidence.omittedFileCount]
     * is what lets the judge tell "they changed nothing else" from "I was not shown the rest".
     */
    private fun budget(files: List<PullRequestFileResponse>): BudgetedDiffs {
        var remaining = TOTAL_PATCH_BUDGET_CHARS
        val kept = mutableListOf<ChangedFileDiff>()
        var omitted = 0

        files.forEach { file ->
            val patch = file.patch
            when {
                // No patch to spend budget on: a binary or over-large file still belongs in the
                // list, because "this file changed and I cannot show you how" is real evidence.
                patch == null -> kept += ChangedFileDiff(file.filename, file.additions, file.deletions, null)
                remaining <= 0 -> omitted++
                else -> {
                    val allowance = minOf(remaining, PER_FILE_PATCH_BUDGET_CHARS)
                    val truncated = patch.length > allowance
                    val text = if (truncated) patch.take(allowance) else patch
                    remaining -= text.length
                    kept += ChangedFileDiff(file.filename, file.additions, file.deletions, text, truncated)
                }
            }
        }

        return BudgetedDiffs(kept, omitted)
    }

    private data class BudgetedDiffs(
        val diffs: List<ChangedFileDiff>,
        val omitted: Int,
    )

    private fun PullRequest.toEvidence(budgeted: BudgetedDiffs): PullRequestEvidence =
        PullRequestEvidence(
            title = title,
            body = body ?: "",
            state = state,
            filesChanged = files?.nodes?.map { it.path } ?: emptyList(),
            checksPassed = when (statusCheckRollup?.state) {
                "SUCCESS" -> true
                "FAILURE", "ERROR" -> false
                else -> null
            },
            fileDiffs = budgeted.diffs,
            omittedFileCount = budgeted.omitted,
            commitMessages = commits?.nodes?.map { it.commit.message } ?: emptyList(),
            authorLogin = author?.login?.lowercase(),
        )

    override fun getRepositoryIdByOwnerAndName(owner: String, name: String): UUID? =
        githubRepositoryConnectionRepository.findByOwnerAndName(owner, name)?.id

    override fun getRepositoryIdsByProject(projectId: UUID): List<UUID> =
        githubRepositoryConnectionRepository.findAllByProjectId(projectId).map { it.id }

    @Transactional(readOnly = true)
    override fun getSourceInstances(projectId: UUID?): List<GithubSourceInstanceDto> {
        val connections =
            if (projectId != null) {
                githubRepositoryConnectionRepository.findAllByProjectId(projectId)
            } else {
                githubRepositoryConnectionRepository.findAll()
            }

        return connections
            .sortedWith(compareBy({ it.owner }, { it.name }))
            .map { it.toSourceInstanceDto() }
    }

    @Transactional
    override fun removeProjectFromAllRepositories(projectId: UUID) {
        val connections = githubRepositoryConnectionRepository.findAllByProjectId(projectId)
        connections.forEach { it.projectIdsInternal.remove(projectId) }
        githubRepositoryConnectionRepository.saveAll(connections)
    }

    private companion object {
        // Characters, not tokens: the budget guards the prompt, and a character count is the one
        // measure this service can compute without pulling a tokenizer into a connector. Roughly
        // 3k tokens of diff — a starter task's worth of change, nowhere near a context limit. The
        // cap is here to stop a runaway pull request eating the prompt, not to squeeze it.
        const val TOTAL_PATCH_BUDGET_CHARS = 12_000

        // No single file may take more than a third of it, so one generated lockfile cannot crowd
        // out the change that matters.
        const val PER_FILE_PATCH_BUDGET_CHARS = 4_000
    }

    private fun GithubRepositoryConnection.toSourceInstanceDto(): GithubSourceInstanceDto {
        val snapshot = snapshot
        return GithubSourceInstanceDto(
            repositoryId = id,
            owner = owner,
            name = name,
            status = toSourceStatus(),
            enabled = sourceEnabled,
            lastCommitsSyncAt = snapshot?.lastCommitsSyncAt,
            lastIssuesSyncAt = snapshot?.lastIssuesSyncAt,
            lastPullRequestsSyncAt = snapshot?.lastPullRequestsSyncAt,
        )
    }
}

/**
 * Maps a GitHub repository connection to the stable source-status vocabulary shared by the
 * connector overview and the ingestion status APIs.
 *
 * A disabled source always reports `DISABLED`, regardless of its underlying connection state, so a
 * paused repository is not shown as actively connected. Kept in the GitHub module as the single
 * owner of this mapping.
 */
internal fun GithubRepositoryConnection.toSourceStatus(): String {
    if (!sourceEnabled) {
        return "DISABLED"
    }

    return when (connectionState) {
        ConnectionState.UP_TO_DATE -> "CONNECTED"
        ConnectionState.UPDATING -> "UPDATING"
        ConnectionState.OUT_OF_DATE -> "OUT_OF_DATE"
        ConnectionState.FAILED -> "FAILED"
    }
}
