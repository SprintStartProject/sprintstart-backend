package com.sprintstart.sprintstartbackend.path.repository

import com.sprintstart.sprintstartbackend.path.model.entity.OnboardingPhase
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

@Suppress("ktlint:standard:function-naming", "FunctionNaming")
interface OnboardingPhaseRepository : JpaRepository<OnboardingPhase, UUID> {
    fun findByIdAndPathUserId(id: UUID, pathUserId: UUID): Optional<OnboardingPhase>

    fun countByPathId(pathId: UUID): Long

    fun findByPathIdAndPositionGreaterThanEqualOrderByPositionDesc(
        pathId: UUID,
        positionIsGreaterThan: Int,
    ): MutableList<OnboardingPhase>

    fun findByPathIdAndPositionBetween(
        pathId: UUID,
        positionAfter: Int,
        positionBefore: Int,
    ): MutableList<OnboardingPhase>

    fun findAllByPathUserId(pathUserId: UUID): MutableList<OnboardingPhase>

    fun findAllByPathIdAndPositionGreaterThan(pathId: UUID, positionIsGreaterThan: Int): MutableList<OnboardingPhase>

    fun findAllByPathId(pathId: UUID): MutableList<OnboardingPhase>
}
