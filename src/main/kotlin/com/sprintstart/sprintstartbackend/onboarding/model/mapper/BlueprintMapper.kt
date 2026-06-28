package com.sprintstart.sprintstartbackend.onboarding.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.external.model.BlueprintProvenanceSchema
import com.sprintstart.sprintstartbackend.onboarding.external.model.BlueprintSchema
import com.sprintstart.sprintstartbackend.onboarding.external.model.BlueprintStepSchema
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Blueprint

/**
 * Maps a persisted [Blueprint] entity to the wire schema sent to the stateless AI
 * service. The corpus fingerprint is carried via [BlueprintProvenanceSchema] so the AI
 * can short-circuit regeneration when the corpus is unchanged.
 */
fun Blueprint.toSchema(): BlueprintSchema =
    BlueprintSchema(
        scope = scope,
        version = version,
        source = "generated",
        steps = steps.map { step ->
            BlueprintStepSchema(
                id = step.stepId,
                title = step.title,
                description = step.description ?: "",
                audience = step.audience.split(",").filter { it.isNotBlank() },
                minExperience = step.minExperience,
                requirement = step.requirement,
                invariant = step.invariant,
            )
        },
        provenance = corpusFingerprint?.let { BlueprintProvenanceSchema(corpusFingerprint = it) },
    )
