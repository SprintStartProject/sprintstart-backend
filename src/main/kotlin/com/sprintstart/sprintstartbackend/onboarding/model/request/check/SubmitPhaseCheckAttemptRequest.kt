package com.sprintstart.sprintstartbackend.onboarding.model.request.check

import java.util.UUID

data class SubmitPhaseCheckAttemptRequest(
    val answers: List<SubmitCheckAnswerRequest> = emptyList(),
)

data class SubmitCheckAnswerRequest(
    val questionId: UUID,
    val selectedOptionIds: List<UUID> = emptyList(),
    val textAnswer: String? = null,
)
