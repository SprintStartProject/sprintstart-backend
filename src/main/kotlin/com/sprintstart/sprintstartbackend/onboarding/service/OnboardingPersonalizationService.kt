package com.sprintstart.sprintstartbackend.onboarding.service

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
import com.sprintstart.sprintstartbackend.onboarding.external.model.AiProgressEvent
import com.sprintstart.sprintstartbackend.onboarding.external.model.AssemblePhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.PhaseContentOutcome
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toGetForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.OnboardingSseEvent
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPathRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.user.external.UserApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
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
    transactionManager: PlatformTransactionManager,
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
     * a time; several active alternatives are an inconsistent state and fail instead of being
     * disambiguated by the caller.
     *
     * `AI_ENHANCED` phases are filled at personalization time: for each included phase that carries
     * an author's prompt, the AI service is asked (streamed) to assemble the phase's steps, tasks,
     * resources and knowledge check from the project's corpus, and the result is persisted onto the
     * user's onboarding phase. A phase the AI cannot ground is persisted as an honest empty phase
     * rather than blocking the whole path.
     *
     * @param authId External authentication identifier from the JWT subject.
     * @param projectId The project whose active blueprint seeds the path.
     * @return A cold flow containing the copied path followed by a terminal `done` event.
     * @throws ResponseStatusException If the user, project assignment, or blueprint selection is invalid.
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

        return flow {
            val blueprint = loadBlueprintSelection(projectId, projectRoleIds, skillIds)
            logger.info(
                "Starting onboarding personalization for user {} in project {} from blueprint {}",
                profile.id,
                projectId,
                blueprint.id,
            )

            val generated = mutableMapOf<UUID, GeneratedPhaseContent>()
            logger.info(
                "Blueprint {} contains {} included AI-enhanced phase(s)",
                blueprint.id,
                blueprint.aiEnhancedPhases.size,
            )

            for (phase in blueprint.aiEnhancedPhases) {
                logger.info(
                    "Requesting AI content for blueprint phase {} ('{}') in project {}",
                    phase.id,
                    phase.title,
                    projectId,
                )
                emit(
                    OnboardingSseEvent(
                        type = "stage",
                        name = phase.title,
                        detail = "Filling the phase from the project's material",
                    ),
                )
                generated[phase.id] = streamPhaseContent(phase, projectId.toString()) { detail ->
                    emit(OnboardingSseEvent(type = "stage", name = phase.title, detail = detail))
                }
            }

            logger.info(
                "Persisting personalized path for user {} with generated results for {} phase(s)",
                profile.id,
                generated.size,
            )
            val response = persistPath(blueprint, profile.id, projectRoleIds, skillIds, generated)
            logger.info(
                "Personalized onboarding path {} persisted for user {} with {} phase(s)",
                response.id,
                profile.id,
                response.phases.size,
            )
            emit(OnboardingSseEvent(type = "path", path = response))
            emit(OnboardingSseEvent(type = "done"))
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
                )
                onboardingPathRepository.deleteByUserId(userId)
                onboardingPathRepository.save(onboardingPath).toGetForUserResponse()
            }
        } ?: throw IllegalStateException("Onboarding path transaction returned no result")

    /**
     * Streams the AI assembly for one `AI_ENHANCED` phase and returns the assembled content.
     *
     * Relays the AI service's live progress onward via [relay] (each upstream `stage`/`item`/
     * `warning` becomes a stage event carrying its label), and takes the content from the terminal
     * `done` event's `result`, so it is byte-for-byte what the non-streaming call would return. A
     * stream that fails mid-way, a `skipped`/`unchanged` outcome, or a result that cannot be decoded
     * all yield an empty phase — never a fabricated one.
     */
    private suspend fun streamPhaseContent(
        phase: PhaseAssemblyTarget,
        projectId: String,
        relay: suspend (detail: String) -> Unit,
    ): GeneratedPhaseContent {
        val request = AssemblePhaseRequest(
            phaseTitle = phase.title,
            phaseDescription = phase.description,
            phasePrompt = phase.prompt,
            projectId = projectId,
        )
        var content: GeneratedPhaseContent? = null
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
                            outcome == null -> logger.warn(
                                "AI phase stream completed without a decodable result for phase {}",
                                phase.id,
                            )

                            outcome.status != "assembled" -> logger.warn(
                                "AI phase assembly did not produce content for phase {}: status={}, " +
                                    "chunksRetrieved={}, stepsDropped={}, questionsDropped={}, notes={}",
                                phase.id,
                                outcome.status,
                                outcome.chunksRetrieved,
                                outcome.stepsDropped,
                                outcome.questionsDropped,
                                outcome.notes.joinToString("; ").take(2_000),
                            )

                            else -> {
                                content = outcome.toGeneratedPhaseContent()
                                logger.info(
                                    "AI phase assembly completed for phase {} with {} step(s) and {} question(s)",
                                    phase.id,
                                    content?.steps?.size,
                                    content?.checkQuestions?.size,
                                )
                            }
                        }
                    }

                    AiProgressEvent.ERROR -> logger.error(
                        "AI phase stream returned an error event for phase {}: {}",
                        phase.id,
                        event.message ?: event.label ?: "No error message supplied",
                    )

                    else -> {
                        relay(event.label ?: event.message ?: event.type)
                    }
                }
            }.catch { cause ->
                logger.error(
                    "Phase assembly stream failed for '{}' ({}) on project {}",
                    phase.title,
                    phase.id,
                    projectId,
                    cause,
                )
            }.collect { }
        if (content == null) {
            logger.warn(
                "Using empty generated content for blueprint phase {} ('{}') in project {}",
                phase.id,
                phase.title,
                projectId,
            )
        }
        return content ?: GeneratedPhaseContent()
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
                title = step.title,
                description = step.description,
                tasks = step.tasks.map { GeneratedTask(title = it.title, description = it.description) },
                resources = step.resources.map { GeneratedResource(title = it.title, url = it.url) },
                estimatedMinutes = step.estimatedMinutes,
                expectedOutcome = step.expectedOutcome,
            )
        },
        checkQuestions = checkQuestions.mapNotNull { question ->
            val type = runCatching { CheckQuestionType.valueOf(question.type) }.getOrNull()
                ?: return@mapNotNull null
            GeneratedQuestion(
                type = type,
                question = question.question,
                explanation = question.explanation,
                correctAnswer = question.correctAnswer,
                options = question.options.map { GeneratedOption(label = it.label, correct = it.correct) },
            )
        },
    )

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
}
