package com.sprintstart.sprintstartbackend.onboarding.model.request.arrival

import com.sprintstart.sprintstartbackend.onboarding.external.enums.Rigor
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import java.util.UUID

/**
 * Creates an arrival step.
 *
 * [projectId] omitted means company-wide, which is the normal case: account creation and paperwork
 * are the same on every project.
 */
@Schema(description = "Create an arrival step")
data class CreateArrivalStepRequest(
    @field:NotBlank
    @field:Schema(description = "Stable key, immutable once created", example = "github-account")
    val key: String,
    @field:Schema(description = "Omit for a company-wide step")
    val projectId: UUID? = null,
    @field:NotBlank
    val title: String,
    val description: String? = null,
    val href: String? = null,
    val position: Int = 0,
    @field:Schema(description = "Defaults to DECLARED; OBSERVED requires a derivation behind the key")
    val settledBy: Rigor = Rigor.DECLARED,
)

/**
 * Updates an arrival step. Omitted fields are left unchanged.
 *
 * There is deliberately no `key` field: state points at the key, so renaming one would orphan every
 * hire's record of having done the step while leaving the row looking healthy.
 */
@Schema(description = "Update an arrival step; omitted fields are unchanged")
data class UpdateArrivalStepRequest(
    val title: String? = null,
    val description: String? = null,
    val href: String? = null,
    val position: Int? = null,
    val settledBy: Rigor? = null,
)

/**
 * Applies a whole ordering at once.
 *
 * The complete list, not a from/to pair — two people reordering concurrently cannot interleave into
 * an order neither of them chose.
 */
@Schema(description = "The complete ordering of a scope's steps")
data class ReorderArrivalStepsRequest(
    val orderedKeys: List<String>,
)
