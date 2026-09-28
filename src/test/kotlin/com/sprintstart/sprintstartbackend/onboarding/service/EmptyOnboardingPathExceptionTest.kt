package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.GenerationStatus
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EmptyOnboardingPathExceptionTest {
    @Test
    fun `failures and timeouts only are the AI service's doing`() {
        val error = EmptyOnboardingPathException.from(listOf(GenerationStatus.FAILED, GenerationStatus.TIMED_OUT))

        assertEquals(EmptyOnboardingPathException.AI_UNAVAILABLE, error.reason)
    }

    @Test
    fun `an empty or skipped phase means the material does not cover the path yet`() {
        val error = EmptyOnboardingPathException.from(listOf(GenerationStatus.FAILED, GenerationStatus.SKIPPED))

        assertEquals(EmptyOnboardingPathException.NOT_ENOUGH_KNOWLEDGE, error.reason)
    }

    @Test
    fun `no phase to assemble at all is a blueprint without phases for this hire`() {
        assertEquals(EmptyOnboardingPathException.NO_PHASES, EmptyOnboardingPathException.from(emptyList()).reason)
    }
}
