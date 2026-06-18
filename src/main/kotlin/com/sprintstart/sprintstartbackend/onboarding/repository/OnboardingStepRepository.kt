package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

@Suppress("ktlint:standard:function-naming", "FunctionNaming")
interface OnboardingStepRepository : JpaRepository<OnboardingStep, UUID> {
    fun findAllByPhaseId(phaseId: UUID): MutableList<OnboardingStep>

    fun countByPhaseId(phaseId: UUID): Long

    fun findByPhaseIdAndPositionGreaterThanEqualOrderByPositionDesc(
        phaseId: UUID,
        positionIsGreaterThan: Int,
    ): MutableList<OnboardingStep>

    fun findAllByPhaseIdAndPhasePathUserId(phaseId: UUID, phasePathUserId: UUID): MutableList<OnboardingStep>

    fun findByIdAndPhasePathUserId(id: UUID, phasePathUserId: UUID): Optional<OnboardingStep>

    fun findByPhaseIdAndPositionBetween(
        phaseId: UUID,
        positionAfter: Int,
        positionBefore: Int,
    ): MutableList<OnboardingStep>

    fun findAllByPhaseIdAndPositionGreaterThan(phaseId: UUID, positionIsGreaterThan: Int): MutableList<OnboardingStep>
}
