package com.sprintstart.sprintstartbackend.user.model.request

/**
 * Which onboarding track a project role puts its people on.
 *
 * A null or blank [onboardingTrackKey] clears the role's track rather than being rejected:
 * "not decided" is a real answer, and it resolves to the default track, which is exactly what
 * every role did before tracks existed.
 */
data class SetProjectRoleTrackRequest(
    val onboardingTrackKey: String? = null,
)
