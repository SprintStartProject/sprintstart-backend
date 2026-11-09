package com.sprintstart.sprintstartbackend.blueprint.model.response.checkquestion

import com.sprintstart.sprintstartbackend.blueprint.model.response.subGraphNode.UpdateBlueprintSubGraphNodeResponse

data class DeleteBlueprintCheckQuestionResponse(
    val updatedQuestions: List<UpdateBlueprintSubGraphNodeResponse>,
)
