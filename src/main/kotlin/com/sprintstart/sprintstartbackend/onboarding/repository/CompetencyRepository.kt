package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencyKind
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Competency
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface CompetencyRepository : JpaRepository<Competency, UUID> {
    fun findByKey(key: String): Competency?

    fun findAllByKeyIn(keys: Collection<String>): List<Competency>

    fun existsByKey(key: String): Boolean

    fun findAllByKind(kind: CompetencyKind): List<Competency>

    /**
     * The areas currently in use, for grouping and for steering the generator toward them.
     *
     * Distinct on the stored spelling: [com.sprintstart.sprintstartbackend.onboarding.service.CompetencyAreaNormalizer]
     * makes sure two rows never differ only by case or spacing, so this is already the canonical list.
     */
    @Query("select distinct c.area from Competency c where c.area is not null order by c.area")
    fun findDistinctAreas(): List<String>
}
