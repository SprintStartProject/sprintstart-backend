package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.model.entity.ModulePage
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingFeedbackRepository
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduledExecutor
import org.springframework.stereotype.Service

/**
 * Closes the content-quality loop: once a module page accumulates enough "not helpful"
 * [com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingFeedback], asks the AI to
 * re-draft that module.
 *
 * The re-draft is a **proposal**, not a replacement: it lands as a new PROPOSED version for a PM to
 * review, and what hires are reading right now is untouched until somebody approves it. Content
 * people are graded against does not get silently rewritten because three of them clicked a thumb.
 *
 * Fires exactly once per threshold crossing (`count == threshold`, not `>=`) -- the unhelpful count
 * is cumulative and never resets, so `>=` would re-propose on every subsequent unhelpful click. The
 * AI call is dispatched via [ScheduledExecutor] so submitting feedback never blocks on AI latency.
 */
@Service
class ContentQualityService(
    private val onboardingFeedbackRepository: OnboardingFeedbackRepository,
    private val competencyModuleService: CompetencyModuleService,
    private val scheduledExecutor: ScheduledExecutor,
) {
    fun checkAndTriggerRegeneration(page: ModulePage) {
        val unhelpfulCount = onboardingFeedbackRepository.countByPageIdAndHelpfulFalse(page.id)
        if (unhelpfulCount != UNHELPFUL_FEEDBACK_THRESHOLD) return

        val module = page.module
        scheduledExecutor.launch("Re-drafting the module for ${module.competencyKey}") {
            competencyModuleService.proposeFromCorpus(module.competencyKey, module.projectId)
        }
    }

    private companion object {
        const val UNHELPFUL_FEEDBACK_THRESHOLD = 3L
    }
}
