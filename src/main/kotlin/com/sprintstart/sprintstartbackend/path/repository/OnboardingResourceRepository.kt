package com.sprintstart.sprintstartbackend.path.repository

import com.sprintstart.sprintstartbackend.path.model.entity.OnboardingResource
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

@Suppress("ktlint:standard:function-naming", "FunctionNaming")
interface OnboardingResourceRepository : JpaRepository<OnboardingResource, UUID> {
    fun findAllByStepId(stepId: UUID): MutableList<OnboardingResource>

    fun findByStepIdAndStepPhasePathUserId(stepId: UUID, stepPhasePathUserId: UUID): MutableList<OnboardingResource>

    fun findByIdAndStepPhasePathUserId(id: UUID, stepPhasePathUserId: UUID): Optional<OnboardingResource>
}
