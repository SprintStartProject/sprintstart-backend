package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyCitation
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

internal interface BuddyCitationRepository : JpaRepository<BuddyCitation, UUID> {
}
