package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkquestion.GetBlueprintCheckQuestionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.GetBlueprintStepResponse
import java.util.UUID

data class UpdateBlueprintPhaseResponse(
    val id: UUID,
    val blueprintPathId: UUID,
    val position: Int,
    val title: String,
    val description: String,
    val blueprintSteps: List<GetBlueprintStepResponse>,
    val blueprintCheckQuestions: List<GetBlueprintCheckQuestionResponse>,
)
