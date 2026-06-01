package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

@Suppress("ktlint:standard:function-naming", "FunctionNaming")
interface OnboardingStepRepository : JpaRepository<OnboardingStep, UUID> {
    fun findByPhase_IdAndPositionGreaterThanEqual(
        phaseId: UUID,
        positionIsGreaterThan: Int,
    ): MutableList<OnboardingStep>

    fun findByPhase_IdAndPositionBetween(
        phaseId: UUID,
        positionAfter: Int,
        positionBefore: Int,
    ): MutableList<OnboardingStep>

    fun findAllByPhase_Id(phaseId: UUID): MutableList<OnboardingStep>

    fun findByPhase_IdAndPositionGreaterThan(phaseId: UUID, positionIsGreaterThan: Int): MutableList<OnboardingStep>
}
