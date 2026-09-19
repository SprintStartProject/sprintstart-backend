package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkquestion

import java.util.UUID

data class UpdateBlueprintCheckQuestionPositionResponse(
    val id: UUID,
    val revision: Long,
    val position: Int,
)
