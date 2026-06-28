package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.model.BlueprintSchema
import com.sprintstart.sprintstartbackend.onboarding.external.model.OnboardingAiPathEvent
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toEntities
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toGetForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toSchema
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.OnboardingSseEvent
import com.sprintstart.sprintstartbackend.onboarding.repository.BlueprintRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPathRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.enums.WorkingArea
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class OnboardingPersonalizationService(
    private val onboardingAiClient: OnboardingAiClient,
    private val onboardingPathRepository: OnboardingPathRepository,
    private val blueprintRepository: BlueprintRepository,
    private val blueprintService: BlueprintService,
    private val userApi: UserApi,
    transactionManager: PlatformTransactionManager,
) {
    // Used for the short DB phase only; blueprint generation (a slow AI call) runs
    // outside any transaction so a connection is never pinned for its duration.
    private val txTemplate = TransactionTemplate(transactionManager)

    /**
     * Generates a personalized onboarding path for the user identified by [authId].
     *
     * The user's profile (working area + experience) is read up front so a missing user
     * fails fast with 404 before any streaming begins. The returned cold [Flow], once
     * collected, then: auto-generates any missing blueprints for the user's scope
     * (`global` + `area:<workingArea>`) — a potentially slow AI call made with no
     * transaction held — opens a short transaction to delete the user's existing path
     * and read the active blueprints, and finally streams the AI personalization events.
     * Each event is mapped to an [OnboardingSseEvent]; a `path` event is persisted before
     * being forwarded.
     *
     * @param authId External authentication identifier from the JWT subject.
     * @return A cold [Flow] of [OnboardingSseEvent] emitted during path generation.
     * @throws ResponseStatusException 404 if no user exists for [authId].
     */
    fun personalize(authId: String): Flow<OnboardingSseEvent> {
        val profile = userApi
            .getOnboardingProfileByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User with authId: $authId not found") }

        if (profile.workingArea == WorkingArea.NO_WORKING_AREA) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "User has no working area set")
        }

        val workingArea = profile.workingArea.toAiScope()
        val experience = profile.experience
        val requiredScopes = listOf("global", "area:$workingArea")

        return flow {
            // Generate any missing blueprints. This calls the AI service and may take a
            // while, so it runs outside a transaction (no DB connection held meanwhile).
            blueprintService.ensureScopesExist(requiredScopes)

            // Short transaction on an IO thread: replace the old path and read the active
            // blueprints (their steps are lazy, so a session must be open while mapping).
            val blueprints = withContext(Dispatchers.IO) {
                txTemplate.execute {
                    onboardingPathRepository.deleteByUserId(profile.id)
                    loadActiveBlueprints(requiredScopes)
                }
            }.orEmpty()

            emitAll(
                onboardingAiClient
                    .generatePath(workingArea, experience, blueprints)
                    .map { event -> event.toSseEvent(profile.id) },
            )
        }.catch { e ->
            emit(OnboardingSseEvent(type = "error", message = e.message))
        }
    }

    /**
     * Maps an AI [OnboardingAiPathEvent] to the outward [OnboardingSseEvent]. A `path`
     * event's generated path is persisted (in its own repository transaction) before the
     * saved view is forwarded.
     */
    private fun OnboardingAiPathEvent.toSseEvent(userId: UUID): OnboardingSseEvent =
        when (type) {
            "stage" -> OnboardingSseEvent(type = "stage", name = name, detail = detail)

            "path" -> {
                val savedPath = path?.let { aiPath ->
                    val entity = aiPath.toEntities(userId)
                    onboardingPathRepository.save(entity)
                    entity.toGetForUserResponse()
                }
                OnboardingSseEvent(type = "path", path = savedPath)
            }

            "error" -> OnboardingSseEvent(type = "error", message = message)

            else -> OnboardingSseEvent(type = type)
        }

    /**
     * Loads the ACTIVE blueprints for [scopes] and maps them to the wire schema the AI
     * service consumes. Scopes without an ACTIVE blueprint are skipped. Must be called
     * within a transaction so each blueprint's lazy steps can be read.
     */
    private fun loadActiveBlueprints(scopes: List<String>): List<BlueprintSchema> =
        scopes
            .mapNotNull { scope ->
                blueprintRepository.findByScopeAndStatus(scope, BlueprintStatus.ACTIVE)
            }.map { it.toSchema() }
}
