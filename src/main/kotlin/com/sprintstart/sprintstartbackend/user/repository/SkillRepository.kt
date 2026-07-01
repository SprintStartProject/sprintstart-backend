package com.sprintstart.sprintstartbackend.user.repository

import com.sprintstart.sprintstartbackend.user.model.entity.Skill
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SkillRepository : JpaRepository<Skill, UUID> {
    fun findByName(name: String): Skill?
}
