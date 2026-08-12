package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.VocabularyGenerationState
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface VocabularyGenerationStateRepository : JpaRepository<VocabularyGenerationState, UUID>
