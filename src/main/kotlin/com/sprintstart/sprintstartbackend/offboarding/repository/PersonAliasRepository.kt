package com.sprintstart.sprintstartbackend.offboarding.repository

import com.sprintstart.sprintstartbackend.offboarding.model.entity.PersonAlias
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface PersonAliasRepository : JpaRepository<PersonAlias, UUID> {
    /** Every link confirmed for one project. */
    fun findAllByProjectId(projectId: UUID): List<PersonAlias>

    /**
     * Removes the link that folds one identity into another, if the project has one.
     *
     * @param projectId The project the link belongs to.
     * @param alias The folded identity.
     * @return The number of removed rows, 0 when there was no such link.
     */
    @Modifying
    @Query("DELETE FROM PersonAlias a WHERE a.projectId = :projectId AND a.alias = :alias")
    fun deleteByProjectIdAndAlias(
        @Param("projectId") projectId: UUID,
        @Param("alias") alias: String,
    ): Int
}
