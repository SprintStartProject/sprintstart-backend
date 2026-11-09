package com.sprintstart.sprintstartbackend.path.repository

import com.sprintstart.sprintstartbackend.path.model.entity.PhaseCheckQuestion
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PhaseCheckQuestionRepository : JpaRepository<PhaseCheckQuestion, UUID>
