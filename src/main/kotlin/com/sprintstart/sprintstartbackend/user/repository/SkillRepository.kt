package com.sprintstart.sprintstartbackend.user.repository

import com.sprintstart.sprintstartbackend.user.model.entity.Skill
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SkillRepository : JpaRepository<Skill, UUID> {
    fun findByName(name: String): Skill?

    fun findAllByProjectRolesId(roleId: UUID): List<Skill>

    @Query("SELECT s FROM Skill s WHERE LOWER(TRIM(s.name)) = LOWER(TRIM(:name))")
    fun findByNormalizedName(name: String): Skill?

    @Query("SELECT COUNT(s) > 0 FROM Skill s WHERE LOWER(TRIM(s.name)) = LOWER(TRIM(:name)) AND s.id <> :excludeId")
    fun existsByNormalizedNameExcluding(name: String, excludeId: UUID): Boolean

    @Query("SELECT COUNT(s) > 0 FROM Skill s WHERE LOWER(TRIM(s.name)) = LOWER(TRIM(:name))")
    fun existsByNormalizedName(name: String): Boolean
}
