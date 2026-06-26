package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.model.GenerateBlueprintsRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.blueprint.BlueprintDiffResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.blueprint.BlueprintOutcomeResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.blueprint.BlueprintResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.blueprint.BlueprintStepResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.blueprint.DiffChangeResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.blueprint.DraftListResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.blueprint.DraftSummaryResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.blueprint.GenerateBlueprintsResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.blueprint.VersionListResponse
import org.springframework.stereotype.Service

@Service
class BlueprintService(
    private val onboardingAiClient: OnboardingAiClient,
) {
    suspend fun generateBlueprints(scopes: List<String>?): GenerateBlueprintsResponse {
        val response = onboardingAiClient.generateBlueprints(
            GenerateBlueprintsRequest(scopes = scopes),
        )
        return GenerateBlueprintsResponse(
            outcomes = response.outcomes.map { outcome ->
                BlueprintOutcomeResponse(
                    scope = outcome.scope,
                    status = outcome.status,
                    message = outcome.message,
                )
            },
        )
    }

    suspend fun listDrafts(): DraftListResponse {
        val response = onboardingAiClient.listDrafts()
        return DraftListResponse(
            items = response.items.map { draft ->
                DraftSummaryResponse(
                    scope = draft.scope,
                    createdAt = draft.createdAt,
                    summary = draft.summary,
                )
            },
        )
    }

    suspend fun getDraftDiff(scope: String): BlueprintDiffResponse {
        val response = onboardingAiClient.getDraftDiff(scope)
        return BlueprintDiffResponse(
            scope = response.scope,
            changes = response.changes.map { change ->
                DiffChangeResponse(
                    action = change.action,
                    stepId = change.stepId,
                    description = change.description,
                )
            },
            blocked = response.blocked,
        )
    }

    suspend fun approveDraft(scope: String): BlueprintResponse {
        val response = onboardingAiClient.approveDraft(scope)
        return response.toResponse()
    }

    suspend fun discardDraft(scope: String) {
        onboardingAiClient.discardDraft(scope)
    }

    suspend fun listVersions(scope: String): VersionListResponse {
        val response = onboardingAiClient.listVersions(scope)
        return VersionListResponse(
            scope = response.scope,
            versions = response.versions,
        )
    }

    suspend fun rollback(scope: String, version: String): BlueprintResponse {
        val response = onboardingAiClient.rollback(scope, version)
        return response.toResponse()
    }

    private fun com.sprintstart.sprintstartbackend.onboarding.external.model.Blueprint.toResponse(): BlueprintResponse =
        BlueprintResponse(
            scope = this.scope,
            version = this.version,
            steps = this.steps.map { step ->
                BlueprintStepResponse(
                    id = step.id,
                    title = step.title,
                    description = step.description,
                )
            },
        )
}
