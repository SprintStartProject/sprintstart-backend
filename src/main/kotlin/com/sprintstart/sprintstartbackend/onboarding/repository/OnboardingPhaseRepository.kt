package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

@Suppress("ktlint:standard:function-naming", "FunctionNaming")
interface OnboardingPhaseRepository : JpaRepository<OnboardingPhase, UUID> {
    fun findByPath_IdAndPositionGreaterThanEqual(pathId: UUID, positionIsGreaterThan: Int): MutableList<OnboardingPhase>

    fun findAllByPath_Id(pathId: UUID): MutableList<OnboardingPhase>

    fun findByPath_IdAndPositionGreaterThan(pathId: UUID, positionIsGreaterThan: Int): MutableList<OnboardingPhase>

    fun findByPath_IdAndPositionBetween(
        pathId: UUID, positionAfter: Int,
        positionBefore: Int,
    ): MutableList<OnboardingPhase>
}
