package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * How a step's [com.sprintstart.sprintstartbackend.onboarding.model.entity.Verification] is graded.
 *
 * Mirrors the AI service's `GradingType` (`sprintstart-ai`'s `onboarding/verification.py`):
 * [KNOWLEDGE] is an LLM-judge against a rubric plus the step's grounded lesson content, [EXACT] is
 * a normalized string match against a canonical answer, [ATTEST] is a self-confirmation, and
 * [ARTIFACT] is an LLM-judge against a rubric plus real repo state Kotlin deterministically
 * gathers for a hire-submitted PR number (the highest-rigor rung of the verification ladder). Both
 * [EXACT] and [ATTEST] are graded locally in Kotlin rather than delegated to the AI service, since
 * neither needs a judgment call.
 */
enum class VerificationType {
    KNOWLEDGE,
    EXACT,
    ATTEST,
    ARTIFACT,
}
