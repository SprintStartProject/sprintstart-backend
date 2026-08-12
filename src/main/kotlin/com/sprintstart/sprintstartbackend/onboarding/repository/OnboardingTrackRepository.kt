package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingTrack
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OnboardingTrackRepository : JpaRepository<OnboardingTrack, UUID> {
    fun findByKey(key: String): OnboardingTrack?
}
