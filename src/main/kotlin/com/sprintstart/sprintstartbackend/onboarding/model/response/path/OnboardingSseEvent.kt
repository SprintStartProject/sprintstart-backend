package com.sprintstart.sprintstartbackend.onboarding.model.response.path

/**
 * One event on the onboarding generation stream.
 *
 * @property type `stage` (progress on one phase), `path` (the path so far), `done`, or `error`.
 * @property name The phase a `stage` event is about.
 * @property detail What is happening in that phase, for `stage`.
 * @property path The generated path, for `path`.
 * @property message A human-readable explanation, for `error`.
 * @property reason Why an `error` happened, as a code the client can branch on:
 * `not-enough-knowledge`, `ai-unavailable` or `no-phases` (see `EmptyOnboardingPathException`); null
 * for any other failure.
 */
data class OnboardingSseEvent(
    val type: String,
    val name: String? = null,
    val detail: String? = null,
    val path: GetOnboardingPathForUserResponse? = null,
    val message: String? = null,
    val reason: String? = null,
)
