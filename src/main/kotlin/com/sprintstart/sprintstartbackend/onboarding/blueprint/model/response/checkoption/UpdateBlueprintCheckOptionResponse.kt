package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkoption

import java.util.UUID

data class UpdateBlueprintCheckOptionResponse(
    val id: UUID,
    val blueprintCheckQuestionId: UUID,
    val position: Int,
    val label: String,
    val correct: Boolean,
)
