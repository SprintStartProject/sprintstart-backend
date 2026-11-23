package com.sprintstart.sprintstartbackend.starterwork.service

import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.SourceId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.starterwork.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.starterwork.external.enums.TaskType
import com.sprintstart.sprintstartbackend.starterwork.model.entity.StarterWorkTaskProposal
import com.sprintstart.sprintstartbackend.starterwork.model.mapper.toResponse
import com.sprintstart.sprintstartbackend.starterwork.model.response.starterwork.RankedStarterWorkTaskResponse
import com.sprintstart.sprintstartbackend.starterwork.repository.StarterWorkTaskProposalRepository
import com.sprintstart.sprintstartbackend.starterwork.repository.UserCompetencyStateRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/**
 * Ranks the live starter-work pool for a hire: who they are, what each task is, and how well they fit.
 *
 * Deterministic and local; the scoring itself is [StarterWorkMatcher]. This gathers its inputs.
 */
internal class StarterWorkMatching(
    private val userApi: UserApi,
    private val starterWorkScope: StarterWorkScope,
    private val starterWorkTaskProposalRepository: StarterWorkTaskProposalRepository,
    private val userCompetencyStateRepository: UserCompetencyStateRepository,
    private val githubHistoryPriorService: GithubHistoryPriorService,
    private val artifactIngestionApi: ArtifactIngestionApi,
) {
    /**
     * Ranks the current `LIVE` starter-work pool by fit for the authenticated user on a project.
     *
     * Deterministic, local and self-explaining (see [StarterWorkMatcher]): no model call on a
     * hire's request path, and every task carries the sentence justifying its rank.
     *
     * @throws ResponseStatusException 404 if no user matches [authSubject].
     */
    @Transactional(readOnly = true)
    fun matchForUser(authSubject: String, projectId: ProjectId): List<RankedStarterWorkTaskResponse> {
        val userId = userApi.getUserIdByAuthId(authSubject).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: $authSubject")
        }
        return matchForUserId(UserId(userId), projectId)
    }

    /**
     * Ranks the current `LIVE` starter-work pool by fit for an already-resolved user on a
     * project.
     *
     * The user-id counterpart of [matchForUser], for callers that hold a user id rather than an
     * auth subject (e.g. the buddy agent ranking tasks for the caller). Only this project's work and
     * the shared pool are ranked (see [StarterWorkScope.forHiresOn]).
     */
    @Transactional(readOnly = true)
    fun matchForUserId(userId: UserId, projectId: ProjectId): List<RankedStarterWorkTaskResponse> {
        val pool = starterWorkScope.forHiresOn(
            starterWorkTaskProposalRepository.findAllByStatus(ProposalStatus.LIVE),
            projectId.value,
        ) { it.sourceId }
        if (pool.isEmpty()) return emptyList()

        val profile = buildProfile(userId)
        val responsivenessByRepo = artifactIngestionApi
            .getRepositoryResponsiveness(projectId.value)
            .associateBy { it.repositoryFullName }

        return pool
            .map { proposal ->
                val features = featuresOf(proposal)
                val responsiveness = features.repositoryFullName
                    ?.let { responsivenessByRepo[it] }
                    ?.let { StarterWorkMatcher.Responsiveness(it.medianHoursToFirstResponse, it.unansweredCount) }
                val scored = StarterWorkMatcher.score(profile, features, responsiveness)
                RankedStarterWorkTaskResponse(
                    task = proposal.toResponse(),
                    score = scored.score,
                    matchedCompetencyKeys = scored.matchedCompetencyKeys,
                    taskType = features.taskType,
                    reasons = scored.reasons,
                )
            }
            // Ties broken by title, so the ranking is stable across reloads.
            .sortedWith(compareByDescending<RankedStarterWorkTaskResponse> { it.score }.thenBy { it.task.title })
    }

    /**
     * What is known about one hire, from data already held.
     *
     * The GitHub-history half is consent-gated and may simply be absent. An empty prior means
     * *no evidence*, never "beginner".
     */
    private fun buildProfile(userId: UserId): StarterWorkMatcher.HireProfile {
        // Level 0 means "unknown/not yet placed", not a competency the hire has met.
        val competencyKeys = userCompetencyStateRepository
            .findAllByUserId(userId.value)
            .filter { it.level > 0 }
            .map { it.competencyKey }
            .toSet()

        // Null when the hire never consented, which is a legitimate and common state.
        val signals = githubHistoryPriorService.getPrior(userId.value)?.signals.orEmpty()
        val repositories = signals.keys
            .filter { it.startsWith(REPO_SIGNAL_PREFIX) }
            .map { it.removePrefix(REPO_SIGNAL_PREFIX) }
            .toSet()
        val labels = signals.keys
            .filter { it.startsWith(LABEL_SIGNAL_PREFIX) }
            .map { it.removePrefix(LABEL_SIGNAL_PREFIX).lowercase() }
            .toSet()

        return StarterWorkMatcher.HireProfile(
            competencyKeys = competencyKeys,
            familiarRepositories = repositories,
            familiarLabels = labels,
            // Derived from the same labels a task's type is read from, so the two cannot
            // disagree about the vocabulary.
            familiarTaskTypes = labels
                .mapNotNull { label ->
                    TaskType.fromLabels(listOf(label)).takeIf { it != TaskType.OTHER }
                }.toSet(),
        )
    }

    /**
     * The ranking features of one pool task.
     *
     * Labels and repository come from the ingested issue, not from the proposal row. A task whose
     * issue is not ingested has no labels, which costs it its type signal rather than inventing one.
     */
    private fun featuresOf(proposal: StarterWorkTaskProposal): StarterWorkMatcher.TaskFeatures {
        val source = artifactIngestionApi.getTaskSource(SourceId(proposal.sourceId))
        val labels = source
            ?.labels
            .orEmpty()
            .map { it.lowercase() }
            .toSet()
        return StarterWorkMatcher.TaskFeatures(
            competencyKeys = proposal.competencyKeys.toSet(),
            taskType = TaskType.fromLabels(labels),
            labels = labels,
            repositoryFullName = repositoryOf(SourceId(proposal.sourceId)),
            reviewed = proposal.reviewed,
            // Only a definite yes demotes; unknown is not "free". Written by
            // StarterWorkPoolReconciler, so ranking the pool stays one read per task.
            assigned = proposal.sourceHasAssignee == true,
        )
    }

    /**
     * The `owner/name` a mined task's source id points at.
     *
     * Source ids are built by `SourceIdFactory` as `github:owner/name:TYPE:number`, so the
     * repository is already in the identifier and needs no extra lookup.
     */
    private fun repositoryOf(sourceId: SourceId): String? =
        with(sourceId.value) { split(':').getOrNull(1)?.takeIf { it.contains('/') } }

    private companion object {
        /** Namespaces used by `GithubHistoryPrior.signals`; see that entity's docs. */
        const val REPO_SIGNAL_PREFIX = "repo:"
        const val LABEL_SIGNAL_PREFIX = "label:"
    }
}
