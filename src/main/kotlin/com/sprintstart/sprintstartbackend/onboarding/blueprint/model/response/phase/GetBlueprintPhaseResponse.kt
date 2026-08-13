package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintCheckQuestion
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkquestion.GetBlueprintCheckQuestionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.GetBlueprintStepResponse
import java.util.UUID

data class GetBlueprintPhaseResponse(
    val id: UUID,
    val blueprintPathId: UUID,
    val revision: Long,
    val position: Int,
    val title: String,
    val description: String,
    val blueprintSteps: List<GetBlueprintStepResponse>,
    val blueprintCheckQuestions: List<GetBlueprintCheckQuestionResponse>,
)
