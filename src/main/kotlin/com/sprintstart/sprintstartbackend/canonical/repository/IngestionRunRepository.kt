package com.sprintstart.sprintstartbackend.canonical.repository

import com.sprintstart.sprintstartbackend.canonical.model.entity.IngestionRun
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface IngestionRunRepository : JpaRepository<IngestionRun, UUID> {
}