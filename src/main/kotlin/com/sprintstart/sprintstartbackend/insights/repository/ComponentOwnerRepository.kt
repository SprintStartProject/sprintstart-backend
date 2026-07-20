package com.sprintstart.sprintstartbackend.insights.repository

import com.sprintstart.sprintstartbackend.insights.model.entity.ComponentOwner
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ComponentOwnerRepository : JpaRepository<ComponentOwner, UUID> {
    fun findAllByComponentIn(components: Collection<String>): List<ComponentOwner>

    fun deleteByComponent(component: String)
}
