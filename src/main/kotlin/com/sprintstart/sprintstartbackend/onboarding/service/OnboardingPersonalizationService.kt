package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintPhaseType
import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.blueprint.factory.GeneratedOption
import com.sprintstart.sprintstartbackend.onboarding.blueprint.factory.GeneratedPhaseContent
import com.sprintstart.sprintstartbackend.onboarding.blueprint.factory.GeneratedQuestion
import com.sprintstart.sprintstartbackend.onboarding.blueprint.factory.GeneratedResource
import com.sprintstart.sprintstartbackend.onboarding.blueprint.factory.GeneratedStep
import com.sprintstart.sprintstartbackend.onboarding.blueprint.factory.GeneratedTask
import com.sprintstart.sprintstartbackend.onboarding.blueprint.factory.OnboardingPathFromBlueprintFactory
import com.sprintstart.sprintstartbackend.onboarding.blueprint.factory.isIncludedFor
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPath
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintPathRepository
import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.onboarding.external.enums.GenerationStatus
import com.sprintstart.sprintstartbackend.onboarding.external.model.AiProgressEvent
import com.sprintstart.sprintstartbackend.onboarding.external.model.AssemblePhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.PhaseContentOutcome
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toGetForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.OnboardingSseEvent
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPathRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.user.external.UserApi
import jakarta.persistence.EntityManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/** Creates user onboarding paths from active rich blueprint paths. */
@Service
class OnboardingPersonalizationService(
    private val onboardingPathRepository: OnboardingPathRepository,
    private val blueprintPathRepository: BlueprintPathRepository,
    private val onboardingPathFactory: OnboardingPathFromBlueprintFactory,
    private val onboardingAiClient: OnboardingAiClient,
    private val userApi: UserApi,
    private val json: Json,
    private val entityManager: EntityManager,
    transactionManager: PlatformTransactionManager,
    private val applicationConfig: ApplicationConfig,
) {
    private val txTemplate = TransactionTemplate(transactionManager)
    private val readTxTemplate = TransactionTemplate(transactionManager).apply { isReadOnly = true }
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Replaces the authenticated user's path with a copy of the selected project's active blueprint.
     *
     * Onboarding is project-scoped: only blueprints of the passed [projectId] are eligible, never
     * the shared global templates. The caller picks the project — e.g. the one selected in the
     * frontend — rather than the service silently choosing among the user's memberships, so a user
     * assigned to several projects always builds from the project they are looking at. A user who
     * is not assigned to the requested project is rejected instead of being given a path for a
     * project they do not belong to. A project is expected to have exactly one active blueprint at
     * a time; several active alternatives are an inconsistent state and are reported as an error
     * event instead of being disambiguated by the caller.
     *
     * `AI_ENHANCED` phases are filled at personalization time: for each included phase that carries
     * an author's prompt, the AI service is asked (streamed) to assemble the phase's steps, tasks,
     * resources and knowledge check from the project's corpus, and the result is persisted onto the
     * user's onboarding phase. Phases assemble concurrently — bounded by the configured concurrency
     * limit — so a slow phase no longer holds up the whole path. Each phase runs under its own
     * timeout, and the complete generation runs under a total timeout; a phase that crosses either
     * is persisted as an honest `TIMED_OUT` placeholder (hidden from the journey, listed in the
     * generation issues) rather than blocking the path. A phase the AI cannot ground is persisted as
     * an honest empty phase rather than blocking the whole path.
     *
     * Every progress event carries the phase title in `name`, so interleaved parallel phase events
     * stay understandable (`Project Overview — Searching the project for: ...`).
     *
     * @param authId External authentication identifier from the JWT subject.
     * @param projectId The project whose active blueprint seeds the path.
     * @return A cold flow that emits `stage` progress events while the phases assemble, then the
     * copied path in a `path` event followed by a terminal `done` event. A failure once streaming
     * has started — including blueprint selection (no active blueprint, or several) — is caught
     * and emitted as a terminal `error` event instead of propagating, so an open SSE stream
     * always ends with an event rather than a broken connection.
     * @throws ResponseStatusException 404 if no user exists for [authId], 403 if the user is not
     * assigned to [projectId]. Both are thrown before the flow is created; failures raised while
     * the flow is collected are delivered as `error` events, not thrown.
     */
    @Tracked("Creating onboarding path from blueprint")
    fun personalize(authId: String, projectId: UUID): Flow<OnboardingSseEvent> {
        val profile = userApi
            .getOnboardingProfileByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User with authId: $authId not found") }
        if (projectId !in profile.projectIds) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "User with authId: $authId is not assigned to project: $projectId",
            )
        }
        val projectRoleIds = profile.projectRoles[projectId]
            .orEmpty()
            .map { it.roleId }
            .toSet()
        val skillIds = profile.skills.map { it.skillId }.toSet()

        return channelFlow {
            val blueprint = loadBlueprintSelection(projectId, projectRoleIds, skillIds)
            logger.info(
                "Starting onboarding personalization for user {} in project {} from blueprint {}",
                profile.id,
                projectId,
                blueprint.id,
            )

            val generated = mutableMapOf<UUID, GeneratedPhaseContent>()
            val generationStatuses = mutableMapOf<UUID, GenerationStatus>()
            val phases = blueprint.aiEnhancedPhases
            logger.info(
                "Blueprint {} contains {} included AI-enhanced phase(s)",
                blueprint.id,
                phases.size,
            )

            val sendEvent: suspend (OnboardingSseEvent) -> Unit = { event -> send(event) }
            val semaphore = Semaphore(applicationConfig.onboarding.phaseConcurrency)
            val phaseTimeoutMillis = applicationConfig.onboarding.phaseTimeoutSeconds * 1_000L
            val totalTimeoutMillis = applicationConfig.onboarding.totalTimeoutSeconds * 1_000L

            supervisorScope {
                phases.forEach { phase ->
                    logger.info(
                        "Requesting AI content for blueprint phase {} ('{}') in project {}",
                        phase.id,
                        phase.title,
                        projectId,
                    )
                    launch {
                        try {
                            generatePhase(
                                phase = phase,
                                projectId = projectId.toString(),
                                phaseTimeoutMillis = phaseTimeoutMillis,
                                totalTimeoutMillis = totalTimeoutMillis,
                                semaphore = semaphore,
                                emitStage = { detail ->
                                    sendEvent(
                                        OnboardingSseEvent(type = "stage", name = phase.title, detail = detail),
                                    )
                                },
                            ).also { result ->
                                generated[phase.id] = result.content
                                generationStatuses[phase.id] = result.status
                            }
                        } catch (cause: CancellationException) {
                            throw cause
                        } catch (cause: Throwable) {
                            logger.error(
                                "Phase generation failed for '{}' ({}) on project {}",
                                phase.title,
                                phase.id,
                                projectId,
                                cause,
                            )
                            generated[phase.id] = GeneratedPhaseContent()
                            generationStatuses[phase.id] = GenerationStatus.FAILED
                        }
                    }
                }
            }

            logger.info(
                "Persisting personalized path for user {} with generated results for {} phase(s)",
                profile.id,
                generated.size,
            )
            val response = persistPath(
                blueprint = blueprint,
                userId = profile.id,
                projectRoleIds = projectRoleIds,
                skillIds = skillIds,
                generated = generated,
                generationStatuses = generationStatuses,
            )
            logger.info(
                "Personalized onboarding path {} persisted for user {} with {} phase(s)",
                response.id,
                profile.id,
                response.phases.size,
            )
            sendEvent(OnboardingSseEvent(type = "path", path = response))
            sendEvent(OnboardingSseEvent(type = "done"))
        }.catch { error ->
            logger.error(
                "Onboarding personalization failed for authId {} in project {}",
                authId,
                projectId,
                error,
            )
            emit(OnboardingSseEvent(type = "error", message = error.message))
        }
    }

    /**
     * Selects the active blueprint and snapshots the fields needed by the slow AI phase.
     *
     * Blueprint relationships are lazy JPA collections. They must be filtered and copied while
     * this read transaction is open; returning the entity itself would detach those collections
     * before the cold personalization flow starts consuming them.
     */
    private suspend fun loadBlueprintSelection(
        projectId: UUID,
        projectRoleIds: Set<UUID>,
        skillIds: Set<UUID>,
    ): BlueprintSelection =
        withContext(Dispatchers.IO) {
            readTxTemplate.execute {
                val blueprint = selectActiveBlueprint(projectId)
                BlueprintSelection(
                    id = blueprint.id,
                    aiEnhancedPhases = blueprint.blueprintPhases
                        .filter { it.isIncludedFor(projectRoleIds, skillIds) }
                        .filter { it.type == BlueprintPhaseType.AI_ENHANCED && !it.aiPrompt.isNullOrBlank() }
                        .sortedBy { it.position }
                        .map { phase ->
                            PhaseAssemblyTarget(
                                id = phase.id,
                                title = phase.title,
                                description = phase.description.orEmpty(),
                                prompt = phase.aiPrompt.orEmpty(),
                            )
                        },
                )
            }
        } ?: throw IllegalStateException("Blueprint selection returned no result")

    private suspend fun persistPath(
        blueprint: BlueprintSelection,
        userId: UUID,
        projectRoleIds: Set<UUID>,
        skillIds: Set<UUID>,
        generated: Map<UUID, GeneratedPhaseContent>,
        generationStatuses: Map<UUID, GenerationStatus>,
    ): GetOnboardingPathForUserResponse =
        withContext(Dispatchers.IO) {
            txTemplate.execute {
                val blueprintEntity = blueprintPathRepository
                    .findById(blueprint.id)
                    .orElseThrow {
                        IllegalStateException(
                            "Selected blueprint ${blueprint.id} no longer exists",
                        )
                    }
                val onboardingPath = onboardingPathFactory.createFrom(
                    blueprintPath = blueprintEntity,
                    userId = userId,
                    projectRoleIds = projectRoleIds,
                    skillIds = skillIds,
                    generatedContentByBlueprintPhaseId = generated,
                    generationStatusByBlueprintPhaseId = generationStatuses,
                )
                onboardingPathRepository.deleteByUserId(userId)
                onboardingPathRepository.flush()
                entityManager.persist(onboardingPath)
                onboardingPath.toGetForUserResponse()
            }
        } ?: throw IllegalStateException("Onboarding path transaction returned no result")

    /**
     * Runs one phase's AI assembly under the concurrency and timeout bounds.
     *
     * The worker emits a `Waiting` stage before it enters the concurrency queue, then assembles the
     * phase inside a phase-level timeout. The whole worker — including the wait for a concurrency
     * permit — runs inside the overall generation timeout, so a run past the total deadline neither
     * starts new phases nor lets a running one finish late. A timeout is not a failure: it returns
     * an empty result with [GenerationStatus.TIMED_OUT] so siblings keep working and the timed-out
     * phase is persisted as a hidden placeholder instead of blocking the path.
     */
    private suspend fun generatePhase(
        phase: PhaseAssemblyTarget,
        projectId: String,
        phaseTimeoutMillis: Long,
        totalTimeoutMillis: Long,
        semaphore: Semaphore,
        emitStage: suspend (detail: String) -> Unit,
    ): PhaseGenerationResult {
        emitStage("Waiting")
        val result = withTimeoutOrNull(totalTimeoutMillis) {
            semaphore.withPermit {
                runPhaseAssembly(phase, projectId, phaseTimeoutMillis, emitStage)
            }
        }
        return result ?: timedOut(phase, projectId, emitStage, totalTimeoutMillis)
    }

    /**
     * Assembles one phase, cancelling it with a `TIMED_OUT` placeholder when the phase-level
     * timeout elapses and relaying a `Completed` stage when the AI produced content in time.
     */
    private suspend fun runPhaseAssembly(
        phase: PhaseAssemblyTarget,
        projectId: String,
        phaseTimeoutMillis: Long,
        emitStage: suspend (detail: String) -> Unit,
    ): PhaseGenerationResult {
        val result = withTimeoutOrNull(phaseTimeoutMillis) {
            streamPhaseContent(phase, projectId) { detail -> emitStage(detail) }
        }
        if (result == null) {
            return timedOut(phase, projectId, emitStage, phaseTimeoutMillis)
        }
        if (result.status == GenerationStatus.GENERATED) {
            emitStage("Completed")
        }
        return result
    }

    private suspend fun timedOut(
        phase: PhaseAssemblyTarget,
        projectId: String,
        emitStage: suspend (detail: String) -> Unit,
        timeoutMillis: Long,
    ): PhaseGenerationResult {
        emitStage("Timed out after ${timeoutMillis / 1_000} seconds")
        // A timed-out phase is cancelled mid-stream, so streamPhaseContent's own outcome log
        // never runs; log the status and which deadline caught it here so it is always visible.
        logger.warn(
            "AI phase assembly for blueprint phase {} ('{}') in project {} ended with status {}: " +
                "timed out after {} seconds",
            phase.id,
            phase.title,
            projectId,
            GenerationStatus.TIMED_OUT,
            timeoutMillis / 1_000,
        )
        return PhaseGenerationResult(
            content = GeneratedPhaseContent(),
            status = GenerationStatus.TIMED_OUT,
        )
    }

    /**
     * Streams the AI assembly for one `AI_ENHANCED` phase and returns the assembled content.
     *
     * Relays the AI service's live progress onward via [relay] (each upstream `stage`/`item`/
     * `warning` becomes a stage event carrying its label), and takes the content from the terminal
     * `done` event's `result`, so it is byte-for-byte what the non-streaming call would return. A
     * stream that fails mid-way, a `skipped`/`unchanged` outcome, or a result that cannot be decoded
     * all yield empty content with an explicit generation status — never fabricated content.
     */
    @Suppress("CyclomaticComplexMethod")
    private suspend fun streamPhaseContent(
        phase: PhaseAssemblyTarget,
        projectId: String,
        relay: suspend (detail: String) -> Unit,
    ): PhaseGenerationResult {
        val request = AssemblePhaseRequest(
            phaseTitle = phase.title,
            phaseDescription = phase.description,
            phasePrompt = phase.prompt,
            projectId = projectId,
        )
        var content: GeneratedPhaseContent? = null
        var generationStatus = GenerationStatus.FAILED
        var outcomeReason = "stream ended without a done event"
        onboardingAiClient
            .streamPhase(request)
            .onEach { event ->
                logger.info(
                    "AI phase stream event for phase {}: type={}, operation={}, seq={}, label={}",
                    phase.id,
                    event.type,
                    event.operation,
                    event.seq,
                    event.label ?: event.message,
                )
                when (event.type) {
                    AiProgressEvent.DONE -> {
                        val outcome = decodeOutcome(event)
                        when {
                            outcome == null -> {
                                generationStatus = GenerationStatus.FAILED
                                outcomeReason = "done event carried no decodable result"
                                logger.warn(
                                    "AI phase stream completed without a decodable result for phase {}",
                                    phase.id,
                                )
                            }

                            outcome.status != "assembled" -> {
                                generationStatus = when (outcome.status) {
                                    "skipped", "unchanged" -> GenerationStatus.SKIPPED
                                    else -> GenerationStatus.FAILED
                                }
                                outcomeReason = "AI reported status '${outcome.status}'"
                                logger.warn(
                                    "AI phase assembly did not produce content for phase {}: status={}, " +
                                        "chunksRetrieved={}, stepsDropped={}, questionsDropped={}, notes={}",
                                    phase.id,
                                    outcome.status,
                                    outcome.chunksRetrieved,
                                    outcome.stepsDropped,
                                    outcome.questionsDropped,
                                    outcome.notes.joinToString("; ").take(2_000),
                                )
                            }

                            else -> {
                                content = outcome.toGeneratedPhaseContent()
                                generationStatus = if (content?.isEmpty() == true) {
                                    outcomeReason = "AI assembled an empty phase"
                                    GenerationStatus.EMPTY
                                } else {
                                    GenerationStatus.GENERATED
                                }
                                logger.info(
                                    "AI phase assembly completed for phase {} with {} step(s) and {} question(s)",
                                    phase.id,
                                    content?.steps?.size,
                                    content?.checkQuestions?.size,
                                )
                            }
                        }
                    }

                    AiProgressEvent.ERROR -> {
                        outcomeReason =
                            "stream returned an error event: ${event.message ?: event.label ?: "No error message supplied"}"
                        logger.error(
                            "AI phase stream returned an error event for phase {}: {}",
                            phase.id,
                            event.message ?: event.label ?: "No error message supplied",
                        )
                    }

                    else -> {
                        relay(event.label ?: event.message ?: event.type)
                    }
                }
            }.catch { cause ->
                outcomeReason = "stream failed: ${cause.message ?: cause.javaClass.simpleName}"
                logger.error(
                    "Phase assembly stream failed for '{}' ({}) on project {}",
                    phase.title,
                    phase.id,
                    projectId,
                    cause,
                )
            }.collect { }
        if (generationStatus != GenerationStatus.GENERATED) {
            logger.warn(
                "AI phase assembly for blueprint phase {} ('{}') in project {} ended with status {}: {}",
                phase.id,
                phase.title,
                projectId,
                generationStatus,
                outcomeReason,
            )
        }
        if (content == null) {
            logger.warn(
                "Using empty generated content for blueprint phase {} ('{}') in project {}",
                phase.id,
                phase.title,
                projectId,
            )
        }
        return PhaseGenerationResult(
            content = content ?: GeneratedPhaseContent(),
            status = generationStatus,
        )
    }

    private fun decodeOutcome(event: AiProgressEvent): PhaseContentOutcome? =
        event.result?.let {
            runCatching { json.decodeFromJsonElement<PhaseContentOutcome>(it) }
                .onFailure { e -> logger.warn("Could not decode streamed phase result: {}", e.message) }
                .getOrNull()
        }

    private fun PhaseContentOutcome.toGeneratedPhaseContent(): GeneratedPhaseContent = GeneratedPhaseContent(
        steps = steps.map { step ->
            GeneratedStep(
                key = step.key,
                title = step.title,
                description = step.description,
                tasks = step.tasks.map { GeneratedTask(title = it.title, description = it.description) },
                resources = step.resources.map { GeneratedResource(title = it.title, url = it.url) },
                estimatedMinutes = step.estimatedMinutes,
                expectedOutcome = step.expectedOutcome,
                blockedBy = step.blockedBy,
            )
        },
        checkQuestions = checkQuestions.mapNotNull { question ->
            val type = runCatching { CheckQuestionType.valueOf(question.type) }.getOrNull()
                ?: return@mapNotNull null
            GeneratedQuestion(
                key = question.key,
                type = type,
                question = question.question,
                explanation = question.explanation,
                correctAnswer = question.correctAnswer,
                options = question.options.map { GeneratedOption(label = it.label, correct = it.correct) },
                blockedBy = question.blockedBy,
            )
        },
    )

    private fun GeneratedPhaseContent.isEmpty(): Boolean = steps.isEmpty() && checkQuestions.isEmpty()

    private fun selectActiveBlueprint(projectId: UUID): BlueprintPath {
        val activeBlueprints = blueprintPathRepository.findAllByProjectIdAndStatus(projectId, BlueprintStatus.ACTIVE)
        return when (activeBlueprints.size) {
            0 -> throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No active blueprint found for project: $projectId",
            )

            1 -> activeBlueprints.single()

            else -> throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Multiple active blueprints exist for project: $projectId; expected exactly one",
            )
        }
    }

    private data class BlueprintSelection(
        val id: UUID,
        val aiEnhancedPhases: List<PhaseAssemblyTarget>,
    )

    private data class PhaseAssemblyTarget(
        val id: UUID,
        val title: String,
        val description: String,
        val prompt: String,
    )

    private data class PhaseGenerationResult(
        val content: GeneratedPhaseContent,
        val status: GenerationStatus,
    )
}
