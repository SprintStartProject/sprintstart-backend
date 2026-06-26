package com.sprintstart.sprintstartbackend.onboarding.external.exceptions

class OnboardingAiException(
    val statusCode: Int,
    val body: String,
    message: String,
) : RuntimeException(message)
