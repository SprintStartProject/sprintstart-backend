package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckQuestion
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PhaseCheckQuestionRepository : JpaRepository<PhaseCheckQuestion, UUID>
