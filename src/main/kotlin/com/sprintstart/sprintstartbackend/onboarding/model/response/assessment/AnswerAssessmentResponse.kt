package com.sprintstart.sprintstartbackend.onboarding.model.response.assessment

data class AnswerAssessmentResponse(
    val done: Boolean,
    val question: String? = null,
)
