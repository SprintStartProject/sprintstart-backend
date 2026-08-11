package com.sprintstart.sprintstartbackend.onboarding.blueprint.repository

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPath
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BlueprintPathRepository : JpaRepository<BlueprintPath, UUID>
