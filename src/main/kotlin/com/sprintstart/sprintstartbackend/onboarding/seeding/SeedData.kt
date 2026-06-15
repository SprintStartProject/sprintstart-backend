package com.sprintstart.sprintstartbackend.onboarding.seeding

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType

data class SeedData(
    val paths: List<OnboardingPathSeed> = emptyList(),
)

data class OnboardingPathSeed(
    val phases: List<OnboardingPhaseSeed> = emptyList(),
)

data class OnboardingPhaseSeed(
    val position: Int,
    val title: String,
    val description: String,
    val steps: List<OnboardingStepSeed> = emptyList(),
)

data class OnboardingStepSeed(
    val position: Int,
    val title: String,
    val description: String,
    val type: StepType,
    val estimatedMinutes: Int,
    val expectedOutcome: String,
    val tasks: List<OnboardingTaskSeed> = emptyList(),
    val resources: List<OnboardingResourceSeed> = emptyList(),
)

data class OnboardingTaskSeed(
    val position: Int,
    val title: String,
    val description: String,
)

data class OnboardingResourceSeed(
    val title: String,
    val description: String,
    val url: String,
)
