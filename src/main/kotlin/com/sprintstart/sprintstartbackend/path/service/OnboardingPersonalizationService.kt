package com.sprintstart.sprintstartbackend.path.service

import com.sprintstart.sprintstartbackend.blueprint.external.BlueprintApi
import com.sprintstart.sprintstartbackend.blueprint.external.BlueprintSnapshot
import com.sprintstart.sprintstartbackend.blueprint.external.enums.BlueprintPhaseType
import com.sprintstart.sprintstartbackend.path.external.PhaseContentAiClient
import com.sprintstart.sprintstartbackend.path.external.enums.GenerationStatus
import com.sprintstart.sprintstartbackend.path.factory.GeneratedPhaseContent
import com.sprintstart.sprintstartbackend.path.factory.OnboardingPathFromBlueprintFactory
import com.sprintstart.sprintstartbackend.path.model.mapper.toGetForUserResponse
import com.sprintstart.sprintstartbackend.path.model.response.path.GetOnboardingPathForUserResponse
import com.sprintstart.sprintstartbackend.path.model.response.path.OnboardingSseEvent
import com.sprintstart.sprintstartbackend.path.repository.OnboardingPathRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.shared.config.ApplicationConfig
import com.sprintstart.sprintstartbackend.shared.ids.BlueprintId
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.user.external.ProjectIndustryApi
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.UserOnboardingProfile
import jakarta.persistence.EntityManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
    private val blueprintApi: BlueprintApi,
    private val onboardingPathFactory: OnboardingPathFromBlueprintFactory,
    private val phaseContentAiClient: PhaseContentAiClient,
    private val userApi: UserApi,
    private val projectIndustryApi: ProjectIndustryApi,
    private val json: Json,
    private val entityManager: EntityManager,
    transactionManager: PlatformTransactionManager,
    private val applicationConfig: ApplicationConfig,
) {
    private val txTemplate = TransactionTemplate(transactionManager)
    private val logger = LoggerFactory.getLogger(javaClass)
    private val phaseAssembler = PhaseAssembler(phaseContentAiClient, json)

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
        val profile = userApi.onboardingProfileInProject(authId, projectId)
        val projectRoleIds = profile.projectRoles[projectId]
            .orEmpty()
            .map { it.roleId }
            .toSet()
        val skillIds = profile.skills.map { it.skillId }.toSet()

        return channelFlow { personalizeInto(profile, projectId, projectRoleIds, skillIds) }
            .catch { error -> reportFailure(error, authId, projectId) }
    }

    private suspend fun ProducerScope<OnboardingSseEvent>.personalizeInto(
        profile: UserOnboardingProfile,
        project: UUID,
        projectRoleIds: Set<UUID>,
        skillIds: Set<UUID>,
    ) {
        val industry = projectIndustryApi.getOrEvaluateIndustry(project)
        val blueprint = loadBlueprintSelection(project, projectRoleIds, skillIds)
        logger.info("Personalizing user {} in project {} from blueprint {}", profile.id, project, blueprint.id)

        val generated = generatePhases(blueprint.aiEnhancedPhases, project, industry) { event -> send(event) }

        logger.info("Persisting path for user {} ({} generated phase(s))", profile.id, generated.content.size)
        val response = persistPath(
            blueprint = blueprint,
            userId = profile.id,
            projectRoleIds = projectRoleIds,
            skillIds = skillIds,
            generated = generated.content,
            generationStatuses = generated.statuses,
        )
        logger.info("Persisted path {} for user {} ({} phase(s))", response.id, profile.id, response.phases.size)
        send(OnboardingSseEvent(type = "path", path = response))
        send(OnboardingSseEvent(type = "done"))
    }

    private suspend fun generatePhases(
        phases: List<PhaseAssemblyTarget>,
        project: UUID,
        industry: String?,
        send: suspend (OnboardingSseEvent) -> Unit,
    ): GeneratedPhases {
        logger.info("Generating {} AI-enhanced phase(s) in project {}", phases.size, project)
        val generated = GeneratedPhases()
        val limits = PhaseLimits(
            phaseTimeoutMillis = applicationConfig.onboarding.phaseTimeoutSeconds * 1_000L,
            totalTimeoutMillis = applicationConfig.onboarding.totalTimeoutSeconds * 1_000L,
            semaphore = Semaphore(applicationConfig.onboarding.phaseConcurrency),
        )

        supervisorScope {
            phases.forEach { phase ->
                logger.info(
                    "Requesting AI content for blueprint phase {} ('{}') in project {}",
                    phase.id,
                    phase.title,
                    project,
                )
                launch {
                    val result = generateOne(PhaseJob(phase, ProjectId(project), industry), limits, send)
                    generated.content[phase.id] = result.content
                    generated.statuses[phase.id] = result.status
                }
            }
        }
        return generated
    }

    /** Assembles one phase; whatever goes wrong, the phase ends as an empty, failed one rather than the run. */
    private suspend fun generateOne(
        job: PhaseJob,
        limits: PhaseLimits,
        send: suspend (OnboardingSseEvent) -> Unit,
    ): PhaseGenerationResult =
        try {
            phaseAssembler.generatePhase(
                job = job,
                limits = limits,
                emitStage = { detail ->
                    send(OnboardingSseEvent(type = "stage", name = job.phase.title, detail = detail))
                },
            )
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            logger.error(
                "Phase generation failed for '{}' ({}) on project {}",
                job.phase.title,
                job.phase.id,
                job.project.value,
                cause,
            )
            PhaseGenerationResult(GeneratedPhaseContent(), GenerationStatus.FAILED)
        }

    private suspend fun FlowCollector<OnboardingSseEvent>.reportFailure(
        error: Throwable,
        subject: String,
        project: UUID,
    ) {
        if (error is EmptyOnboardingPathException) {
            logger.warn(
                "Onboarding personalization for subject {} in project {} produced no phases ({})",
                subject,
                project,
                error.reason,
            )
        } else {
            logger.error(
                "Onboarding personalization failed for subject {} in project {}",
                subject,
                project,
                error,
            )
        }
        emit(
            OnboardingSseEvent(
                type = "error",
                reason = (error as? EmptyOnboardingPathException)?.reason,
                message = error.message,
            ),
        )
    }

    /** Selects the active blueprint and picks out the phases the slow AI step has to assemble. */
    private suspend fun loadBlueprintSelection(
        projectId: UUID,
        projectRoleIds: Set<UUID>,
        skillIds: Set<UUID>,
    ): BlueprintSelection =
        withContext(Dispatchers.IO) {
            val blueprint = selectActiveBlueprint(projectId)
            BlueprintSelection(
                id = blueprint.id,
                aiEnhancedPhases = blueprint.phases
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
                val snapshot = blueprintApi.snapshot(BlueprintId(blueprint.id))
                    ?: error("Selected blueprint ${blueprint.id} no longer exists")
                val onboardingPath = onboardingPathFactory.createFrom(
                    blueprint = snapshot,
                    userId = userId,
                    projectRoleIds = projectRoleIds,
                    skillIds = skillIds,
                    generatedContentByBlueprintPhaseId = generated,
                    generationStatusByBlueprintPhaseId = generationStatuses,
                )
                val response = onboardingPath.toGetForUserResponse()
                // A path nobody can work through is not saved: the one the hire had stays theirs.
                if (response.phases.isEmpty()) throw EmptyOnboardingPathException.from(generationStatuses.values)
                onboardingPathRepository.deleteByUserId(userId)
                onboardingPathRepository.flush()
                entityManager.persist(onboardingPath)
                response
            }
        }

    private fun selectActiveBlueprint(projectId: UUID): BlueprintSnapshot {
        val activeBlueprints = blueprintApi.activeBlueprints(ProjectId(projectId))
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
}

private class GeneratedPhases {
    val content = mutableMapOf<UUID, GeneratedPhaseContent>()
    val statuses = mutableMapOf<UUID, GenerationStatus>()
}
