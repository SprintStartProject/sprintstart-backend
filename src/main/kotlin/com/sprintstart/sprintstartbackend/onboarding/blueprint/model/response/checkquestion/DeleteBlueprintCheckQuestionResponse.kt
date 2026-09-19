package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkquestion

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.subGraphNode.UpdateBlueprintSubGraphNodeResponse

data class DeleteBlueprintCheckQuestionResponse(
    val updatedQuestions: List<UpdateBlueprintSubGraphNodeResponse>,
)
