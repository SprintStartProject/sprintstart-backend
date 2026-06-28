package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.model.BlueprintSchema
import com.sprintstart.sprintstartbackend.onboarding.external.model.GeneratedBlueprint
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Blueprint
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BlueprintStep
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toSchema
import com.sprintstart.sprintstartbackend.onboarding.model.response.blueprint.BlueprintOutcomeResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.blueprint.BlueprintResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.blueprint.BlueprintStepResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.blueprint.GenerateBlueprintsResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.blueprint.VersionListResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.BlueprintRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException

@Service
class BlueprintService(
    private val onboardingAiClient: OnboardingAiClient,
    private val blueprintRepository: BlueprintRepository,
    transactionManager: PlatformTransactionManager,
) {
    // The AI call is a long-running suspend operation, so it must not run inside
    // a transaction (a DB connection would be pinned for its whole duration).
    // The surrounding DB reads/writes use explicit transactions on Dispatchers.IO
    // so the JPA session stays bound to the thread doing the work.
    private val txTemplate = TransactionTemplate(transactionManager)
    private val readTxTemplate =
        TransactionTemplate(transactionManager).apply { isReadOnly = true }

    /**
     * Triggers AI blueprint generation for the given scopes and persists the results.
     *
     * The current active blueprints are loaded and sent to the stateless AI service so
     * it can number versions and skip an unchanged corpus. Only `created`/`updated`
     * outcomes are activated: each archives the previous ACTIVE for its scope and is
     * saved as the new ACTIVE. `escalated`/`unchanged`/`skipped` outcomes are not
     * persisted. The AI call runs outside any transaction to avoid pinning a DB
     * connection for its duration.
     *
     * @param scopes The scopes to (re)generate, or `null` to refresh all known scopes.
     * @return The per-scope generation outcomes.
     */
    suspend fun generateBlueprints(scopes: List<String>?): GenerateBlueprintsResponse {
        // The AI service is stateless: pass it the current active blueprints so
        // it can number versions and skip an unchanged corpus.
        val active = withContext(Dispatchers.IO) {
            readTxTemplate.execute { loadActiveSchemas(scopes) }.orEmpty()
        }
        val response = onboardingAiClient.generateBlueprints(scopes, active)
        withContext(Dispatchers.IO) {
            txTemplate.executeWithoutResult {
                for (outcome in response.outcomes) {
                    val generated = outcome.blueprint
                    // Only freshly created/updated blueprints are activated.
                    // `escalated` means a protected step was violated and must be
                    // reviewed — it is never silently activated — and
                    // `unchanged`/`skipped` carry no blueprint.
                    if (generated != null && outcome.status in ACTIVATABLE_STATUSES) {
                        activate(generated)
                    }
                }
            }
        }
        return GenerateBlueprintsResponse(
            outcomes = response.outcomes.map { outcome ->
                BlueprintOutcomeResponse(
                    scope = outcome.scope,
                    status = outcome.status,
                    message = outcome.notes.firstOrNull(),
                )
            },
        )
    }

    /** Archive the current ACTIVE for the scope and persist [generated] as ACTIVE. */
    private fun activate(generated: GeneratedBlueprint) {
        blueprintRepository
            .findByScopeAndStatus(generated.scope, BlueprintStatus.ACTIVE)
            ?.let { it.status = BlueprintStatus.ARCHIVED }
        val blueprint = Blueprint(
            scope = generated.scope,
            version = generated.version,
            status = BlueprintStatus.ACTIVE,
            corpusFingerprint = generated.provenance?.corpusFingerprint,
        )
        generated.steps.forEachIndexed { index, step ->
            blueprint.steps.add(
                BlueprintStep(
                    blueprint = blueprint,
                    stepId = step.id,
                    title = step.title,
                    description = step.description?.takeIf { it.isNotBlank() },
                    minExperience = step.minExperience,
                    audience = step.audience.joinToString(","),
                    position = index,
                    requirement = step.requirement,
                    invariant = step.invariant,
                ),
            )
        }
        blueprintRepository.save(blueprint)
    }

    /**
     * Loads the ACTIVE blueprints for the given [scopes] (or all scopes when `null`)
     * and maps them to the wire schema sent to the AI service.
     */
    private fun loadActiveSchemas(scopes: List<String>?): List<BlueprintSchema> {
        val active = if (scopes == null) {
            blueprintRepository.findAllByStatus(BlueprintStatus.ACTIVE)
        } else {
            scopes.mapNotNull {
                blueprintRepository.findByScopeAndStatus(it, BlueprintStatus.ACTIVE)
            }
        }
        return active.map { it.toSchema() }
    }

    /**
     * Returns the archived (rollback-able) version identifiers retained for [scope].
     *
     * @param scope The blueprint scope to list versions for.
     * @return The scope and its archived version identifiers.
     */
    @Transactional(readOnly = true)
    fun listVersions(scope: String): VersionListResponse {
        val versions = blueprintRepository
            .findAllByScopeAndStatus(scope, BlueprintStatus.ARCHIVED)
            .map { it.version }
        return VersionListResponse(scope = scope, versions = versions)
    }

    /**
     * Restores a previously archived blueprint [version] as the new ACTIVE for [scope].
     *
     * The current ACTIVE is archived and the archived version is copied into a new
     * ACTIVE blueprint.
     *
     * @param scope The blueprint scope to roll back.
     * @param version The archived version identifier to restore.
     * @return The restored, now-active blueprint.
     * @throws ResponseStatusException 404 if no archived blueprint matches [scope]/[version].
     */
    @Transactional
    fun rollback(scope: String, version: String): BlueprintResponse {
        val archived = blueprintRepository.findByScopeAndStatusAndVersion(scope, BlueprintStatus.ARCHIVED, version)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No version $version for scope: $scope")
        blueprintRepository
            .findByScopeAndStatus(scope, BlueprintStatus.ACTIVE)
            ?.let { it.status = BlueprintStatus.ARCHIVED }
        val newBlueprint = Blueprint(
            scope = archived.scope,
            version = archived.version,
            status = BlueprintStatus.ACTIVE,
            corpusFingerprint = archived.corpusFingerprint,
        )
        archived.steps.forEach { step ->
            newBlueprint.steps.add(
                BlueprintStep(
                    blueprint = newBlueprint,
                    stepId = step.stepId,
                    title = step.title,
                    description = step.description,
                    minExperience = step.minExperience,
                    audience = step.audience,
                    position = step.position,
                    requirement = step.requirement,
                    invariant = step.invariant,
                ),
            )
        }
        blueprintRepository.save(newBlueprint)
        blueprintRepository.delete(archived)
        return newBlueprint.toResponse()
    }

    /**
     * Ensures an ACTIVE blueprint exists for each of [scopes], generating the missing
     * ones on demand. Scopes that already have an ACTIVE blueprint are left untouched.
     *
     * @param scopes The scopes that must have an ACTIVE blueprint.
     */
    suspend fun ensureScopesExist(scopes: List<String>) {
        val existing = scopes.filter { scope ->
            blueprintRepository.findByScopeAndStatus(scope, BlueprintStatus.ACTIVE) != null
        }
        val missing = scopes.toSet() - existing.toSet()
        if (missing.isNotEmpty()) {
            generateBlueprints(missing.toList())
        }
    }

    /** Maps a persisted [Blueprint] entity to its outward API response. */
    private fun Blueprint.toResponse(): BlueprintResponse =
        BlueprintResponse(
            scope = scope,
            version = version,
            steps = steps.map {
                BlueprintStepResponse(
                    id = it.stepId,
                    title = it.title,
                    description = it.description,
                    requirement = it.requirement,
                    invariant = it.invariant,
                )
            },
        )

    private companion object {
        val ACTIVATABLE_STATUSES = setOf("created", "updated")
    }
}
