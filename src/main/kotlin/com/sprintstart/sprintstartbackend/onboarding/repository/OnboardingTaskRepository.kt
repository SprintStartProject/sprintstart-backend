package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingTask
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

@Suppress("ktlint:standard:function-naming", "FunctionNaming")
interface OnboardingTaskRepository : JpaRepository<OnboardingTask, UUID> {
    fun findByStep_IdAndPositionGreaterThanEqual(stepId: UUID, positionIsGreaterThan: Int): MutableList<OnboardingTask>

    fun findByStep_IdAndPositionBetween(
        stepId: UUID,
        positionAfter: Int,
        positionBefore: Int,
    ): MutableList<OnboardingTask>

    fun findAllByStep_Id(stepId: UUID): MutableList<OnboardingTask>

    fun findByStep_IdAndPositionGreaterThan(stepId: UUID, positionIsGreaterThan: Int): MutableList<OnboardingTask>
}
