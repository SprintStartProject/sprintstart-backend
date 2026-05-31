package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingTask
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OnboardingTaskRepository : JpaRepository<OnboardingTask, UUID>
