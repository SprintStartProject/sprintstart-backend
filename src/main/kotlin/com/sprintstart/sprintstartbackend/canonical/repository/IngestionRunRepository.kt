package com.sprintstart.sprintstartbackend.canonical.repository

import com.sprintstart.sprintstartbackend.canonical.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.canonical.model.entity.IngestionRunStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface IngestionRunRepository : JpaRepository<IngestionRun, UUID> {
    fun findAllByStatus(status: IngestionRunStatus) : List<IngestionRun>
}