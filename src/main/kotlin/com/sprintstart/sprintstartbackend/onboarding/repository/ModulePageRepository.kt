package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.ModulePage
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ModulePageRepository : JpaRepository<ModulePage, UUID>
