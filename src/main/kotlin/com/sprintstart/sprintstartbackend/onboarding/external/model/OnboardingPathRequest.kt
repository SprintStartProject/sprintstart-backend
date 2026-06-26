package com.sprintstart.sprintstartbackend.onboarding.external.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OnboardingPathRequest(
    @SerialName("working_area")
    val workingArea: String,
    val experience: String? = null,
    val skills: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
)
