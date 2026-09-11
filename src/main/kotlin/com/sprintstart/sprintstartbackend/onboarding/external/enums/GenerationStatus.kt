package com.sprintstart.sprintstartbackend.onboarding.external.enums

/** Describes whether and how an onboarding phase received AI-generated content. */
enum class GenerationStatus {
    NOT_APPLICABLE,
    GENERATED,
    EMPTY,
    SKIPPED,
    FAILED,
    TIMED_OUT,
    ;

    fun isHiddenFromUser(): Boolean =
        this == EMPTY || this == SKIPPED || this == FAILED || this == TIMED_OUT
}
