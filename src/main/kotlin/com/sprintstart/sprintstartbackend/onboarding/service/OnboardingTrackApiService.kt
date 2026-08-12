package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingTrackApi
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Read-only adapter over onboarding tracks for other modules.
 *
 * Deliberately thin, mirroring `ProjectMembershipApiService`: it answers which keys exist and
 * nothing else. What a track *means* — its vocabulary, which evidence it admits — stays inside
 * onboarding, because no other module has any business acting on it.
 */
@Service
internal class OnboardingTrackApiService(
    private val trackService: TrackService,
) : OnboardingTrackApi {
    @Transactional(readOnly = true)
    override fun trackKeys(): Set<String> {
        return trackService.listTracks().map { it.key }.toSet()
    }
}
