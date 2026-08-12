package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.OrientationOrigin
import com.sprintstart.sprintstartbackend.onboarding.external.enums.OrientationStep
import com.sprintstart.sprintstartbackend.onboarding.external.model.AiProgressEvent
import com.sprintstart.sprintstartbackend.onboarding.external.model.OrientationOutcome
import com.sprintstart.sprintstartbackend.onboarding.external.model.OrientationPacketSchema
import com.sprintstart.sprintstartbackend.onboarding.model.entity.StarterWorkTaskProposal
import com.sprintstart.sprintstartbackend.onboarding.model.entity.TaskOrientationCitation
import com.sprintstart.sprintstartbackend.onboarding.model.entity.TaskOrientationPacket
import com.sprintstart.sprintstartbackend.onboarding.model.entity.TaskOrientationSection
import com.sprintstart.sprintstartbackend.onboarding.model.entity.TaskOrientationSource
import com.sprintstart.sprintstartbackend.onboarding.model.exceptions.OnboardingAiException
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toResponse
import com.sprintstart.sprintstartbackend.onboarding.model.request.orientation.AuthorOrientationRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.orientation.MyOrientationResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.orientation.OrientationPacketResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.StarterWorkTaskProposalRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.TaskOrientationPacketRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.TaskZeroAssignmentRepository
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
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
import java.time.Clock
import java.util.UUID

/**
 * Serving of task-scoped orientation: what this project already says about doing the task a hire has.
 *
 * ⚠️ The three cache rules, which are not interchangeable:
 *
 * * **The cache is validated, never trusted.** Every read sends the fingerprint of the corpus the
 *   cached packet was built from. An unchanged corpus comes back `unchanged` with no retrieval or
 *   LLM pass and the cache is served; a corpus that has moved is re-assembled.
 * * **`skipped` deletes the cache.** The AI service compared against the *current* corpus and could
 *   not ground a packet, so whatever is cached describes a corpus that no longer exists.
 * * **A transport failure serves the cache.** An unreachable AI service is no evidence at all about
 *   staleness.
 *
 * **Nothing is ever fabricated.** "No packet" is an ordinary returned state carrying the reason —
 * never an empty packet, never an error.
 *
 * ⚠️ Reading orientation never assigns anything: the hire's current task is read straight from the
 * assignment table, not through [TaskZeroService.getForHire], which assigns on read.
 *
 * ⚠️ A **human-authored** packet is pinned [OrientationOrigin.HUMAN] and every cache rule above is
 * switched off for it: [getForHire] serves it as-is and never calls the AI, so it is never
 * re-assembled, never fingerprinted and never auto-deleted as stale.
 */
@Service
@Suppress("TooManyFunctions")
class TaskOrientationService(
    private val taskOrientationPacketRepository: TaskOrientationPacketRepository,
    private val taskZeroAssignmentRepository: TaskZeroAssignmentRepository,
    private val starterWorkTaskProposalRepository: StarterWorkTaskProposalRepository,
    private val projectMembershipApi: ProjectMembershipApi,
    private val artifactIngestionApi: ArtifactIngestionApi,
    private val onboardingAiClient: OnboardingAiClient,
    private val json: Json,
    transactionManager: PlatformTransactionManager,
    private val clock: Clock = Clock.systemUTC(),
) {
    // The AI call is a long-running suspend operation, so it must not run inside a transaction --
    // same read-tx -> AI -> write-tx split as CompetencyModuleService.
    private val txTemplate = TransactionTemplate(transactionManager)
    private val readTxTemplate = TransactionTemplate(transactionManager).apply { isReadOnly = true }
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Orientation for the task the hire currently has on [projectId].
     *
     * @throws ResponseStatusException 404 when the hire is not a member of the project.
     */
    suspend fun getForHire(hireId: UUID, projectId: UUID): MyOrientationResponse {
        val context = withContext(Dispatchers.IO) {
            readTxTemplate.execute { loadContext(hireId, projectId) }
        } ?: return NO_TASK

        // A human-authored packet is served exactly as written: no fingerprint sent, no AI call, no
        // staleness check. Its author stands behind it, so none of the cache guardrails apply.
        context.cachedPacket
            ?.takeIf { it.origin == OrientationOrigin.HUMAN }
            ?.let { return context.respond(it) }

        val outcome = try {
            onboardingAiClient.assembleOrientation(
                taskTitle = context.title,
                taskBody = context.body,
                labels = context.labels,
                // Not knowable from an issue, so sent empty rather than guessed at.
                touchedPaths = emptyList(),
                lastFingerprint = context.cachedFingerprint,
            )
        } catch (e: OnboardingAiException) {
            logger.warn("Orientation assembly unavailable for task {}: {}", context.proposalId, e.message)
            return context.cachedOrEmpty("Orientation is temporarily unavailable")
        }

        return withContext(Dispatchers.IO) {
            txTemplate.execute { apply(context, outcome) }!!
        }
    }

    /**
     * Streams the assembly of the hire's current-task orientation as [AiProgressEvent]s.
     *
     * The live twin of [getForHire]: it relays the AI's stage/item/warning events to the caller and,
     * on the terminal `done`, persists the packet **exactly as [getForHire] would** — the same
     * [apply] against the same cache — before the `done` reaches the browser, so a client that
     * re-reads on `done` sees the committed result. A human-authored packet and a hire with no task
     * are both handled without touching the AI, each a single synthesised `done`.
     *
     * @throws ResponseStatusException 404 when the hire is not a member of the project.
     */
    suspend fun streamForHire(hireId: UUID, projectId: UUID): Flow<AiProgressEvent> {
        val context = withContext(Dispatchers.IO) {
            readTxTemplate.execute { loadContext(hireId, projectId) }
        } ?: return flowOf(AiProgressEvent.done(ORIENTATION, "You have no current task yet"))

        // A human-authored packet is served as-is: nothing to assemble, nothing to persist.
        if (context.cachedPacket?.origin == OrientationOrigin.HUMAN) {
            return flowOf(
                AiProgressEvent.done(ORIENTATION, "This task's orientation was written by a person"),
            )
        }

        return onboardingAiClient
            .streamOrientation(
                taskTitle = context.title,
                taskBody = context.body,
                labels = context.labels,
                touchedPaths = emptyList(),
                lastFingerprint = context.cachedFingerprint,
            ).onEach { event ->
                // Persist on `done`, before the event is relayed downstream, so the cache is committed
                // by the time the client reacts. Decoding the same OrientationOutcome the sync path
                // gets and running the same `apply` is what keeps the streamed result identical.
                if (event.type == AiProgressEvent.DONE) {
                    decodeOutcome(event)?.let { outcome ->
                        withContext(Dispatchers.IO) { txTemplate.execute { apply(context, outcome) } }
                    }
                }
            }.catch { cause ->
                logger.warn(
                    "Orientation stream failed for hire {} on project {}: {}",
                    hireId,
                    projectId,
                    cause.message,
                )
                emit(AiProgressEvent.error(ORIENTATION, "Orientation is temporarily unavailable"))
            }
    }

    private fun decodeOutcome(event: AiProgressEvent): OrientationOutcome? =
        event.result?.let {
            runCatching { json.decodeFromJsonElement<OrientationOutcome>(it) }
                .onFailure { e -> logger.warn("Could not decode streamed orientation result: {}", e.message) }
                .getOrNull()
        }

    // ========================== Human authoring ==========================
    //
    // None of the methods below touch the AI service: authoring is pure DB work, so unlike getForHire
    // there is no read-tx -> AI -> write-tx split, just a plain transaction.

    /**
     * The current orientation for a task, for a PM to author from -- the cached packet if there is one,
     * otherwise a shell (task title and link, no sections) so a PM can start from blank. Never calls
     * the AI and never assembles: authoring is not a place to trigger generation.
     *
     * @throws ResponseStatusException 404 when no task proposal matches [taskProposalId].
     */
    @Transactional(readOnly = true)
    fun getForAuthoring(taskProposalId: UUID, projectId: UUID): MyOrientationResponse {
        val proposal = requireProposal(taskProposalId)
        val cached = taskOrientationPacketRepository.findByTaskProposalIdAndProjectId(taskProposalId, projectId)
        return MyOrientationResponse(
            taskId = proposal.id,
            taskTitle = proposal.title,
            taskUrl = proposal.sourceUrl,
            packet = cached?.toResponse(),
            reason = null,
        )
    }

    /**
     * Pins a human-authored orientation packet for a task (PM surface), replacing whatever was there.
     *
     * @throws ResponseStatusException 404 when no task proposal matches [taskProposalId]; 400 when the
     *   request has no section, or a section with a blank title or body.
     */
    @Transactional
    fun authorPacket(
        taskProposalId: UUID,
        projectId: UUID,
        request: AuthorOrientationRequest,
    ): OrientationPacketResponse = persistHumanPacket(requireProposal(taskProposalId), projectId, request)

    /**
     * Drops a task's packet so the next hire read re-assembles it from the corpus (PM surface).
     *
     * Idempotent: reverting a task that has no cached packet is a no-op, not an error.
     *
     * @throws ResponseStatusException 404 when no task proposal matches [taskProposalId].
     */
    @Transactional
    fun revertToAi(taskProposalId: UUID, projectId: UUID) {
        requireProposal(taskProposalId)
        taskOrientationPacketRepository.deleteByTaskProposalIdAndProjectId(taskProposalId, projectId)
    }

    /**
     * Pins a human-authored packet for the hire's *own* current task (fix-in-place on `/first-week`).
     *
     * @throws ResponseStatusException 404 when the hire is not a member of the project or has no
     *   current task; 400 on an invalid request (see [authorPacket]).
     */
    @Transactional
    fun authorForHire(
        hireId: UUID,
        projectId: UUID,
        request: AuthorOrientationRequest,
    ): OrientationPacketResponse =
        persistHumanPacket(resolveCurrentTaskProposal(hireId, projectId), projectId, request)

    /**
     * Drops the packet for the hire's own current task, restoring AI assembly (fix-in-place).
     *
     * @throws ResponseStatusException 404 when the hire is not a member of the project or has no
     *   current task.
     */
    @Transactional
    fun revertForHire(hireId: UUID, projectId: UUID) {
        val proposal = resolveCurrentTaskProposal(hireId, projectId)
        taskOrientationPacketRepository.deleteByTaskProposalIdAndProjectId(proposal.id, projectId)
    }

    private fun persistHumanPacket(
        proposal: StarterWorkTaskProposal,
        projectId: UUID,
        request: AuthorOrientationRequest,
    ): OrientationPacketResponse {
        validate(request)

        // Replaced wholesale: the request is the whole packet, so there is nothing to merge.
        taskOrientationPacketRepository
            .findByTaskProposalIdAndProjectId(proposal.id, projectId)
            ?.let { taskOrientationPacketRepository.delete(it) }
        taskOrientationPacketRepository.flush()

        val packet = TaskOrientationPacket(
            taskProposalId = proposal.id,
            projectId = projectId,
            taskTitle = proposal.title,
            origin = OrientationOrigin.HUMAN,
            summary = request.summary?.trim()?.takeIf { it.isNotBlank() },
            // A human packet stands on nothing the corpus fingerprint tracks, so it carries none: this
            // is exactly why getForHire never revalidates it.
            corpusFingerprint = null,
            model = null,
            assembledAt = clock.instant(),
        )

        request.sections.forEachIndexed { index, section ->
            packet.sections.add(
                TaskOrientationSection(
                    packet = packet,
                    step = section.step,
                    title = section.title.trim(),
                    body = section.body.trim(),
                    position = index,
                ).apply {
                    section.citations.forEachIndexed { citationIndex, citation ->
                        citations.add(
                            TaskOrientationCitation(
                                section = this,
                                filename = citation.filename.trim(),
                                // A human citation has no retrieval chunk; the client never reads
                                // chunkId, so an empty string keeps the column non-null harmlessly.
                                chunkId = "",
                                sourceUrl = citation.sourceUrl?.trim()?.takeIf { it.isNotBlank() },
                                position = citationIndex,
                            ),
                        )
                    }
                },
            )
        }

        // Sources are derived from the distinct citation links, never authored separately. A
        // packet with no citations simply has no sources.
        request.sections
            .flatMap { it.citations }
            .map { it.filename.trim() to it.sourceUrl?.trim()?.takeIf { url -> url.isNotBlank() } }
            .distinct()
            .forEachIndexed { index, (filename, sourceUrl) ->
                packet.sources.add(
                    TaskOrientationSource(
                        packet = packet,
                        filename = filename,
                        sourceUrl = sourceUrl,
                        artifactType = null,
                        position = index,
                    ),
                )
            }

        return taskOrientationPacketRepository.save(packet).toResponse()
    }

    private fun validate(request: AuthorOrientationRequest) {
        val invalid = request.sections.isEmpty() ||
            request.sections.any { it.title.isBlank() || it.body.isBlank() }
        if (invalid) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Orientation must have at least one section, each with a non-blank title and body",
            )
        }
    }

    private fun requireProposal(taskProposalId: UUID): StarterWorkTaskProposal =
        starterWorkTaskProposalRepository.findById(taskProposalId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "No starter-work task found with id: $taskProposalId")
        }

    private fun resolveCurrentTaskProposal(hireId: UUID, projectId: UUID): StarterWorkTaskProposal {
        requireMember(hireId, projectId)
        val assignment = taskZeroAssignmentRepository.findByHireIdAndProjectId(hireId, projectId)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "You have no current task to orient on project $projectId",
            )
        return starterWorkTaskProposalRepository.findById(assignment.proposalId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "No starter-work task found for your assignment")
        }
    }

    private fun apply(context: TaskContext, outcome: OrientationOutcome): MyOrientationResponse {
        val packet = outcome.packet
        return when {
            outcome.status == ASSEMBLED && packet != null ->
                context.respond(store(context, outcome, packet).toResponse())

            outcome.status == UNCHANGED -> context.cachedOrEmpty(outcome.notes.firstOrNull())

            else -> {
                // The AI service looked at the *current* corpus and could not ground a packet, so
                // anything cached describes a corpus that is gone.
                taskOrientationPacketRepository.deleteByTaskProposalIdAndProjectId(
                    context.proposalId,
                    context.projectId,
                )
                context.respond(null, outcome.notes.firstOrNull() ?: NOT_ASSEMBLED)
            }
        }
    }

    private fun store(
        context: TaskContext,
        outcome: OrientationOutcome,
        schema: OrientationPacketSchema,
    ): TaskOrientationPacket {
        // Replaced wholesale: an AI packet holds no human edits, so there is nothing to merge.
        taskOrientationPacketRepository
            .findByTaskProposalIdAndProjectId(context.proposalId, context.projectId)
            ?.let { taskOrientationPacketRepository.delete(it) }
        taskOrientationPacketRepository.flush()

        val packet = TaskOrientationPacket(
            taskProposalId = context.proposalId,
            projectId = context.projectId,
            taskTitle = schema.taskTitle.ifBlank { context.title },
            origin = OrientationOrigin.AI,
            summary = schema.summary.takeIf { it.isNotBlank() },
            corpusFingerprint = outcome.provenance?.corpusFingerprint,
            model = outcome.provenance?.model,
            assembledAt = clock.instant(),
        )

        schema.sections
            .mapNotNull { section -> section.step.toStepOrNull()?.let { it to section } }
            .forEachIndexed { index, (step, section) ->
                packet.sections.add(
                    TaskOrientationSection(
                        packet = packet,
                        step = step,
                        title = section.title,
                        body = section.body,
                        position = index,
                    ).apply {
                        section.citations.forEachIndexed { citationIndex, citation ->
                            citations.add(
                                TaskOrientationCitation(
                                    section = this,
                                    filename = citation.filename,
                                    chunkId = citation.chunkId,
                                    sourceUrl = citation.sourceUrl,
                                    position = citationIndex,
                                ),
                            )
                        }
                    },
                )
            }

        schema.sources.forEachIndexed { index, source ->
            packet.sources.add(
                TaskOrientationSource(
                    packet = packet,
                    filename = source.filename,
                    sourceUrl = source.sourceUrl,
                    artifactType = source.artifactType,
                    position = index,
                ),
            )
        }

        return taskOrientationPacketRepository.save(packet)
    }

    /** An unrecognised step is dropped rather than stored: the enum is the contract, not a hint. */
    private fun String.toStepOrNull(): OrientationStep? =
        OrientationStep.entries.firstOrNull { it.name == trim().uppercase() }

    private fun loadContext(hireId: UUID, projectId: UUID): TaskContext? {
        requireMember(hireId, projectId)

        val assignment = taskZeroAssignmentRepository.findByHireIdAndProjectId(hireId, projectId) ?: return null
        val proposal = starterWorkTaskProposalRepository.findById(assignment.proposalId).orElse(null) ?: return null

        // The task's own words, not the one-line summary mining wrote about it -- when the issue it
        // was mined from is still ingested.
        val source = artifactIngestionApi.getTaskSource(proposal.sourceId)
        val cached = taskOrientationPacketRepository.findByTaskProposalIdAndProjectId(proposal.id, projectId)

        return TaskContext(
            proposalId = proposal.id,
            projectId = projectId,
            title = source?.title?.takeIf { it.isNotBlank() } ?: proposal.title,
            body = source?.body?.takeIf { it.isNotBlank() } ?: proposal.summary.orEmpty(),
            labels = source?.labels.orEmpty(),
            taskUrl = proposal.sourceUrl ?: source?.sourceUrl,
            cachedFingerprint = cached?.corpusFingerprint,
            cachedPacket = cached?.toResponse(),
        )
    }

    private fun requireMember(hireId: UUID, projectId: UUID) {
        projectMembershipApi.getProjectMembers(projectId).firstOrNull { it.userId == hireId }
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "User $hireId is not a member of project $projectId",
            )
    }

    private data class TaskContext(
        val proposalId: UUID,
        val projectId: UUID,
        val title: String,
        val body: String,
        val labels: List<String>,
        val taskUrl: String?,
        val cachedFingerprint: String?,
        val cachedPacket: OrientationPacketResponse?,
    ) {
        fun respond(packet: OrientationPacketResponse?, reason: String? = null) =
            MyOrientationResponse(
                taskId = proposalId,
                taskTitle = title,
                taskUrl = taskUrl,
                packet = packet,
                reason = reason,
            )

        /**
         * The cached packet when there is one, otherwise the honest empty state with [reason]. The
         * reason is dropped when a packet is served, so a served packet never carries an apology.
         */
        fun cachedOrEmpty(reason: String?) = respond(cachedPacket, reason.takeIf { cachedPacket == null })
    }

    private companion object {
        const val ORIENTATION = "orientation"
        const val ASSEMBLED = "assembled"
        const val UNCHANGED = "unchanged"
        const val NOT_ASSEMBLED = "No orientation could be assembled from this project's material"

        /** No current task is not an error: there is simply nothing to orient somebody for yet. */
        val NO_TASK = MyOrientationResponse(
            taskId = null,
            taskTitle = null,
            taskUrl = null,
            packet = null,
            reason = null,
        )
    }
}
