package com.sprintstart.sprintstartbackend.starterwork.repository

import com.sprintstart.sprintstartbackend.starterwork.external.enums.CompetencyKind
import com.sprintstart.sprintstartbackend.starterwork.model.entity.Competency
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CompetencyRepository : JpaRepository<Competency, UUID> {
    fun findByKey(key: String): Competency?

    fun findAllByKeyIn(keys: Collection<String>): List<Competency>

    fun existsByKey(key: String): Boolean

    fun findAllByKind(kind: CompetencyKind): List<Competency>
}
