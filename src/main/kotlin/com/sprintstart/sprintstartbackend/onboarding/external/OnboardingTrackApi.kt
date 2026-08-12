package com.sprintstart.sprintstartbackend.onboarding.external

/**
 * Exported onboarding-track API for other backend modules.
 *
 * Exists for one reason: a project role points at a track by key, and the `user` module owns roles
 * while `onboarding` owns tracks. Rather than let the user module reach into onboarding's
 * repository — or let it store any string it likes and discover the mistake as silently-default
 * behaviour months later — it validates through this narrow read.
 */
interface OnboardingTrackApi {
    /**
     * Every track key a role may point at.
     *
     * @return The live track keys; empty when none are seeded, which callers must treat as "cannot
     * validate" rather than "nothing is valid".
     */
    fun trackKeys(): Set<String>
}
