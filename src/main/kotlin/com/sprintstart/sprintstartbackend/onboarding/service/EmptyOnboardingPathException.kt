package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.GenerationStatus

/**
 * A personalization that produced no phase a hire could work through.
 *
 * Raised instead of saving the path: an empty path replaced whatever the hire had before, played the
 * "your path is ready" reveal over nothing, and left them on an error page with a retry that could
 * only repeat the result. Nothing is saved, so a previous path survives, and [reason] tells the
 * client which of the two causes it was -- the AI service could not be reached, or the project's
 * knowledge does not cover any phase yet.
 *
 * @property reason A stable code for clients: [AI_UNAVAILABLE], [NOT_ENOUGH_KNOWLEDGE] or [NO_PHASES].
 */
class EmptyOnboardingPathException(
    val reason: String,
    message: String,
) : IllegalStateException(message) {
    companion object {
        const val AI_UNAVAILABLE = "ai-unavailable"
        const val NOT_ENOUGH_KNOWLEDGE = "not-enough-knowledge"
        const val NO_PHASES = "no-phases"

        /**
         * Why a path came out empty, from the outcome of every AI-assembled phase.
         *
         * Failures and timeouts are about the service, and trying again later can help. Empty and
         * skipped phases are about the material: the AI ran and found nothing to build from, and
         * only more (or fully synced) knowledge changes that.
         */
        fun from(statuses: Collection<GenerationStatus>): EmptyOnboardingPathException = when {
            statuses.isEmpty() -> EmptyOnboardingPathException(
                NO_PHASES,
                "The blueprint has no phases for your role and skills.",
            )

            statuses.all { it == GenerationStatus.FAILED || it == GenerationStatus.TIMED_OUT } ->
                EmptyOnboardingPathException(
                    AI_UNAVAILABLE,
                    "The AI service could not assemble any phase of your path.",
                )

            else -> EmptyOnboardingPathException(
                NOT_ENOUGH_KNOWLEDGE,
                "The project's knowledge base does not cover any phase of the blueprint yet.",
            )
        }
    }
}
