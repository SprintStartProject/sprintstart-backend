package com.sprintstart.sprintstartbackend.blueprint.model.response.checkquestion

import com.sprintstart.sprintstartbackend.blueprint.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.blueprint.model.response.checkoption.GetBlueprintCheckOptionResponse
import java.util.UUID

data class GetBlueprintCheckQuestionResponse(
    val id: UUID,
    val blueprintPhaseId: UUID,
    val revision: Long,
    val title: String,
    val position: Int,
    val type: CheckQuestionType,
    val question: String,
    val explanation: String?,
    val correctAnswer: String?,
    val blueprintCheckOptions: List<GetBlueprintCheckOptionResponse>,
)
