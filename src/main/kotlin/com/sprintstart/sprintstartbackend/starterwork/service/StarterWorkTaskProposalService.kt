package com.sprintstart.sprintstartbackend.starterwork.service

import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.path.external.model.AiProgressEvent
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.starterwork.external.StarterWorkAiClient
import com.sprintstart.sprintstartbackend.starterwork.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.starterwork.external.model.StarterWorkOutcome
import com.sprintstart.sprintstartbackend.starterwork.model.entity.StarterWorkTaskProposal
import com.sprintstart.sprintstartbackend.starterwork.model.mapper.toResponse
import com.sprintstart.sprintstartbackend.starterwork.model.request.starterwork.CreateStarterWorkTaskRequest
import com.sprintstart.sprintstartbackend.starterwork.model.request.starterwork.PromoteStarterWorkCandidateRequest
import com.sprintstart.sprintstartbackend.starterwork.model.response.starterwork.GenerateStarterWorkResponse
import com.sprintstart.sprintstartbackend.starterwork.model.response.starterwork.RankedStarterWorkTaskResponse
import com.sprintstart.sprintstartbackend.starterwork.model.response.starterwork.StarterWorkCandidateResponse
import com.sprintstart.sprintstartbackend.starterwork.model.response.starterwork.StarterWorkTaskProposalResponse
import com.sprintstart.sprintstartbackend.starterwork.model.response.starterwork.UnreviewedStarterWorkResponse
import com.sprintstart.sprintstartbackend.starterwork.repository.CompetencyRepository
import com.sprintstart.sprintstartbackend.starterwork.repository.StarterWorkTaskProposalRepository
import com.sprintstart.sprintstartbackend.starterwork.repository.UserCompetencyStateRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

/**
 * Orchestrates AI-mined starter-work task proposals, PM review, and hire-fit matching.
 *
 * Reviewing a proposal marks the row and nothing else.
 *
 * [StarterWorkTaskProposal.competencyKeys] is one of the four signals [StarterWorkMatcher] ranks a
 * task by. It says what the work exercises; it does not gate who may claim it.
 */
@Service
@Suppress("TooManyFunctions")
class StarterWorkTaskProposalService(
    private val starterWorkAiClient: StarterWorkAiClient,
    private val competencyRepository: CompetencyRepository,
    private val starterWorkTaskProposalRepository: StarterWorkTaskProposalRepository,
    private val userCompetencyStateRepository: UserCompetencyStateRepository,
    private val githubHistoryPriorService: GithubHistoryPriorService,
    private val artifactIngestionApi: ArtifactIngestionApi,
    private val userApi: UserApi,
    private val starterWorkScope: StarterWorkScope,
    private val json: Json,
    transactionManager: PlatformTransactionManager,
) {
    // The AI call is a long-running suspend operation, so it must not run inside a
    // transaction (a DB connection would be pinned for its whole duration).
    private val txTemplate = TransactionTemplate(transactionManager)
    private val readTxTemplate = TransactionTemplate(transactionManager).apply { isReadOnly = true }
    private val logger = LoggerFactory.getLogger(javaClass)
    private val matching = StarterWorkMatching(
        userApi,
        starterWorkScope,
        starterWorkTaskProposalRepository,
        userCompetencyStateRepository,
        githubHistoryPriorService,
        artifactIngestionApi,
    )
    private val candidates = StarterWorkCandidates(starterWorkTaskProposalRepository, artifactIngestionApi)

    /**
     * Triggers AI starter-work mining and persists the results as `LIVE`.
     *
     * Issues already decided (`LIVE` or `REJECTED`) and the current live competency keys are sent
     * to the stateless AI service so it can dedupe and ground competency tags. The AI call runs
     * outside any transaction.
     */
    suspend fun generate(projectId: UUID): GenerateStarterWorkResponse {
        val (activeSourceIds, activeCompetencyKeys) = withContext(Dispatchers.IO) {
            readTxTemplate.execute { loadActiveState() }!!
        }
        val outcome = starterWorkAiClient.proposeStarterWork(
            projectIds = listOf(projectId),
            activeSourceIds = activeSourceIds,
            activeCompetencyKeys = activeCompetencyKeys,
        )
        val persisted = withContext(Dispatchers.IO) {
            txTemplate.execute { persistProposals(outcome) }!!
        }
        return GenerateStarterWorkResponse(
            status = outcome.status,
            tasksProposed = persisted.tasksPersisted,
            notes = outcome.notes + persisted.skipped,
        )
    }

    /**
     * The streaming twin of [generate].
     *
     * Relays the AI's `stage`/`item` events, and on the terminal `done` persists through the very
     * same [persistProposals] the sync path uses, so the stored pool is identical either way. A
     * task the backend re-gates away on persist (already pooled) is announced as a `warning` before
     * the `done`, never silently dropped. A stream failure becomes a synthesised terminal `error`.
     */
    suspend fun streamGenerate(projectId: UUID): Flow<AiProgressEvent> {
        val active = withContext(Dispatchers.IO) { readTxTemplate.execute { loadActiveState() }!! }
        return flow {
            starterWorkAiClient
                .streamStarterWork(
                    projectIds = listOf(projectId),
                    activeSourceIds = active.sourceIds,
                    activeCompetencyKeys = active.competencyKeys,
                ).collect { event ->
                    if (event.type == AiProgressEvent.DONE) {
                        decodeOutcome(event)?.let { outcome ->
                            val persisted = withContext(Dispatchers.IO) {
                                txTemplate.execute { persistProposals(outcome) }!!
                            }
                            persisted.skipped.forEach { emit(AiProgressEvent.warning(STARTER_WORK, it)) }
                        }
                    }
                    emit(event)
                }
        }.catch { cause ->
            logger.warn("Starter-work mining stream failed: {}", cause.message)
            emit(AiProgressEvent.error(STARTER_WORK, "Starter-work mining is temporarily unavailable"))
        }
    }

    private fun decodeOutcome(event: AiProgressEvent): StarterWorkOutcome? =
        event.result?.let {
            runCatching { json.decodeFromJsonElement<StarterWorkOutcome>(it) }
                .onFailure { e -> logger.warn("Could not decode streamed starter-work result: {}", e.message) }
                .getOrNull()
        }

    private data class ActiveState(
        val sourceIds: List<String>,
        val competencyKeys: List<String>,
    )

    private fun loadActiveState(): ActiveState {
        val sourceIds = starterWorkTaskProposalRepository
            .findAllByStatusIn(listOf(ProposalStatus.LIVE, ProposalStatus.REJECTED, ProposalStatus.STALE))
            .map { it.sourceId }
        val competencyKeys = competencyRepository.findAll().map { it.key }
        return ActiveState(sourceIds, competencyKeys)
    }

    /**
     * Persists the outcome's tasks as `LIVE` rows, re-applying the backend's own dedup: a task
     * whose issue is already in the pool is not mined again. The AI dedupes against the active
     * pool the caller sends, but this is the authoritative check at write time — it closes the
     * window where two mining runs could double-pool an issue. Returns how many landed and a note
     * per skipped task, so the streaming path can surface each skip as a `warning`.
     */
    private fun persistProposals(outcome: StarterWorkOutcome): PersistResult {
        // REJECTED counts as already-pooled: a rejection is sticky, so a task somebody turned
        // down must never be mined back into existence.
        val alreadyPooled = starterWorkTaskProposalRepository
            .findAllByStatusIn(listOf(ProposalStatus.LIVE, ProposalStatus.REJECTED, ProposalStatus.STALE))
            .map { it.sourceId }
            .toMutableSet()
        val skipped = mutableListOf<String>()
        var tasks = 0
        outcome.tasks.forEach { proposed ->
            if (!alreadyPooled.add(proposed.sourceId)) {
                skipped.add("\"${proposed.title}\" is already in the pool — not re-proposed")
                return@forEach
            }
            starterWorkTaskProposalRepository.save(
                StarterWorkTaskProposal(
                    sourceId = proposed.sourceId,
                    title = proposed.title,
                    summary = proposed.summary.takeIf { it.isNotBlank() },
                    rationale = proposed.rationale.takeIf { it.isNotBlank() },
                    sourceUrl = proposed.citations.firstOrNull()?.sourceUrl,
                    competencyKeys = proposed.competencyKeys.toMutableList(),
                    // Claimable immediately; the matcher demotes it until somebody reviews it.
                    reviewed = false,
                ),
            )
            tasks++
        }
        return PersistResult(tasks, skipped)
    }

    private data class PersistResult(
        val tasksPersisted: Int,
        val skipped: List<String>,
    )

    /**
     * Returns the starter-work tasks currently awaiting PM review.
     */
    @Transactional(readOnly = true)
    fun listUnreviewed(): UnreviewedStarterWorkResponse =
        UnreviewedStarterWorkResponse(
            tasks = starterWorkTaskProposalRepository
                .findAllByStatusAndReviewedFalse(ProposalStatus.LIVE)
                .map { it.toResponse() },
        )

    /**
     * Returns the starter-work pool at one status, for a PM choosing a task to author orientation
     * for or reviewing what closed at its source.
     *
     * Ordered by title and not scoped to a project — tasks are a global pool (the entity has no
     * `projectId`). `REJECTED` is refused: that status is a person's sticky decision, not a shape
     * of pool this endpoint means to expose, and the controller keeps that distinction at 400.
     *
     * @throws ResponseStatusException 400 if [status] is `REJECTED`.
     */
    @Transactional(readOnly = true)
    fun listPool(status: ProposalStatus): List<StarterWorkTaskProposalResponse> {
        if (status == ProposalStatus.REJECTED) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be LIVE or STALE")
        }
        return starterWorkTaskProposalRepository
            .findAllByStatus(status)
            .sortedBy { it.title }
            .map { it.toResponse() }
    }

    /**
     * Records that a person has looked at this task and is happy with it.
     *
     * Reviewing does not admit the task to the pool — it was already claimable. It lifts the
     * demotion [StarterWorkMatcher] applies while nobody has vouched for it. Idempotent.
     *
     * @throws ResponseStatusException 404 if no task matches [id]; 409 if it was rejected.
     */
    @Transactional
    fun markReviewed(id: UUID): StarterWorkTaskProposalResponse {
        val proposal = findLiveProposal(id)
        proposal.reviewed = true
        proposal.decidedAt = Instant.now()
        return proposal.toResponse()
    }

    /**
     * Records a PM's judgement that this task is a good first one for somebody ("Task 0").
     *
     * The flag rides on the pool entry and nothing acts on it: the pool filters and badges by it so
     * a PM can see which tasks they vouched for as gentle starts, and that is the whole of it.
     * Nothing assigns a flagged task to anybody -- #311 left the path their PM's blueprint
     * prescribes as the one thing that onboards a hire, and picking up work is a hire's own move.
     * Idempotent.
     *
     * @throws ResponseStatusException 404 if no task matches [id]; 409 if it is no longer live --
     * vouching for a task nobody can claim is a judgement about nothing.
     */
    @Transactional
    fun setTaskZeroEligibility(id: UUID, eligible: Boolean): StarterWorkTaskProposalResponse {
        val proposal = findLiveProposal(id)
        proposal.taskZeroEligible = eligible
        proposal.decidedAt = Instant.now()
        return proposal.toResponse()
    }

    /**
     * Creates a hand-authored starter-work task, with no AI mining in the loop.
     *
     * Born reviewed: writing a task is vouching for it, so it never joins the unreviewed queue.
     * The task has no ingested source, so a synthetic source id is minted — see
     * `HAND_AUTHORED_SOURCE_PREFIX` for why its shape matters.
     *
     * @throws ResponseStatusException 400 if the title is blank.
     */
    @Transactional
    fun createTask(request: CreateStarterWorkTaskRequest): StarterWorkTaskProposalResponse {
        if (request.title.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "title must not be blank")
        }
        val proposal = starterWorkTaskProposalRepository.save(
            StarterWorkTaskProposal(
                sourceId = HAND_AUTHORED_SOURCE_PREFIX + UUID.randomUUID(),
                title = request.title.trim(),
                summary = request.summary?.trim()?.takeIf { it.isNotBlank() },
                rationale = null,
                sourceUrl = request.sourceUrl?.trim()?.takeIf { it.isNotBlank() },
                competencyKeys = request.competencyKeys.toMutableList(),
                status = ProposalStatus.LIVE,
                reviewed = true,
                decidedAt = Instant.now(),
            ),
        )
        return proposal.toResponse()
    }

    /**
     * Takes a task out of the pool, permanently.
     *
     * Sticky: the row is kept and mining consults it, so a task somebody turned down is
     * never mined back into existence.
     *
     * @throws ResponseStatusException 404 if no task matches [id]; 409 if it was already rejected.
     */
    @Transactional
    fun reject(id: UUID, reason: String?): StarterWorkTaskProposalResponse {
        val proposal = findLiveProposal(id)
        proposal.status = ProposalStatus.REJECTED
        proposal.decidedAt = Instant.now()
        proposal.rejectionReason = reason
        return proposal.toResponse()
    }

    /** See [StarterWorkMatching.matchForUser]. */
    @Transactional(readOnly = true)
    fun matchForUser(authId: String, projectId: UUID): List<RankedStarterWorkTaskResponse> =
        matching.matchForUser(authId, ProjectId(projectId))

    /** See [StarterWorkMatching.matchForUserId]. */
    @Transactional(readOnly = true)
    fun matchForUserId(userId: UUID, projectId: UUID): List<RankedStarterWorkTaskResponse> =
        matching.matchForUserId(UserId(userId), ProjectId(projectId))

    /** See [StarterWorkCandidates.listCandidates]. */
    @Transactional(readOnly = true)
    fun listCandidates(projectId: UUID): List<StarterWorkCandidateResponse> =
        candidates.listCandidates(ProjectId(projectId))

    /** See [StarterWorkCandidates.promoteCandidate]. */
    @Transactional
    fun promoteCandidate(request: PromoteStarterWorkCandidateRequest): StarterWorkTaskProposalResponse =
        candidates.promoteCandidate(request)

    companion object {
        // Matches the AI service's operation name and the frontend consumer's switch.
        private const val STARTER_WORK = "starter_work"

        /**
         * Source-id namespace for a hand-authored task. Not the `github:owner/name:...` shape a
         * mined task carries: `StarterWorkMatching.repositoryOf` finds no `owner/name` in it and
         * `ArtifactIngestionApi.getTaskSource` finds no ingested artifact, so both degrade to "no
         * signal" rather than misattributing.
         */
        private const val HAND_AUTHORED_SOURCE_PREFIX = "authored:"
    }

    private fun findLiveProposal(id: UUID): StarterWorkTaskProposal {
        val proposal = starterWorkTaskProposalRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "No starter-work task found with id: $id")
        }
        // Each refusal says which it is: a rejection is a person's permanent decision, a closed issue the
        // tracker's and reversible.
        val reason = when (proposal.status) {
            ProposalStatus.LIVE -> return proposal
            ProposalStatus.REJECTED -> "Starter-work task $id was rejected and is no longer in the pool"
            ProposalStatus.STALE -> "Starter-work task $id is closed at its source and is not claimable"
        }
        throw ResponseStatusException(HttpStatus.CONFLICT, reason)
    }
}
