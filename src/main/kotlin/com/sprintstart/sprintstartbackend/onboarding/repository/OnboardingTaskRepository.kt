package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingTask
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

@Suppress("ktlint:standard:function-naming", "FunctionNaming")
interface OnboardingTaskRepository : JpaRepository<OnboardingTask, UUID> {
    fun countByStepId(stepId: UUID): Long

    fun findByStepIdAndPositionGreaterThanEqualOrderByPositionDesc(
        stepId: UUID,
        positionIsGreaterThan: Int,
    ): MutableList<OnboardingTask>

    fun findByStepIdAndPositionBetween(
        stepId: UUID,
        positionAfter: Int,
        positionBefore: Int,
    ): MutableList<OnboardingTask>

    fun findAllByStepId(stepId: UUID): MutableList<OnboardingTask>

    fun findAllByStepIdAndPositionGreaterThan(stepId: UUID, positionIsGreaterThan: Int): MutableList<OnboardingTask>

    fun findAllByStepIdAndStepPhasePathUserId(stepId: UUID, stepPhasePathUserId: UUID): MutableList<OnboardingTask>

    fun findByIdAndStepPhasePathUserId(id: UUID, stepPhasePathUserId: UUID): Optional<OnboardingTask>
}
