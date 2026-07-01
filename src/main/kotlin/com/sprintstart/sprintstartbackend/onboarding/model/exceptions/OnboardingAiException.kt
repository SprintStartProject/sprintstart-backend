package com.sprintstart.sprintstartbackend.onboarding.model.exceptions

class OnboardingAiException(
    val statusCode: Int,
    val body: String,
    message: String,
) : RuntimeException(message)
