package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingResource
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OnboardingResourceRepository : JpaRepository<OnboardingResource, UUID> {
    fun findAllByStep_Id(stepId: UUID): MutableList<OnboardingResource>
}
