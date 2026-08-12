package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.ModulePageKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ModuleStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.CompetencyModule
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ModulePage
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingFeedbackRepository
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduledExecutor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class ContentQualityServiceTest {
    private val onboardingFeedbackRepository: OnboardingFeedbackRepository = mockk()
    private val competencyModuleService: CompetencyModuleService = mockk()
    private val scheduledExecutor: ScheduledExecutor = mockk()
    private val service =
        ContentQualityService(onboardingFeedbackRepository, competencyModuleService, scheduledExecutor)

    private val projectId = UUID.randomUUID()

    private fun makePage(): ModulePage {
        val module = CompetencyModule(
            competencyKey = "deploy-runbook",
            projectId = projectId,
            version = 1,
            status = ModuleStatus.ACTIVE,
            title = "Deploying",
        )
        return ModulePage(module = module, kind = ModulePageKind.LESSON, title = "How it works", position = 0)
    }

    @Nested
    inner class CheckAndTriggerRegeneration {
        @Test
        fun `does not re-draft below the unhelpful threshold`() {
            val page = makePage()
            every { onboardingFeedbackRepository.countByPageIdAndHelpfulFalse(page.id) } returns 2L

            service.checkAndTriggerRegeneration(page)

            verify(exactly = 0) { scheduledExecutor.launch(any(), any()) }
        }

        @Test
        fun `proposes a new module version exactly at the unhelpful threshold`() = runTest {
            val page = makePage()
            every { onboardingFeedbackRepository.countByPageIdAndHelpfulFalse(page.id) } returns 3L
            val job = slot<suspend () -> Unit>()
            every { scheduledExecutor.launch(any(), capture(job)) } returns Unit
            coEvery { competencyModuleService.proposeFromCorpus("deploy-runbook", projectId) } returns null

            service.checkAndTriggerRegeneration(page)
            job.captured.invoke()

            // A proposal, never a replacement: what hires are reading stays live until a PM
            // approves the re-draft.
            coVerify(exactly = 1) { competencyModuleService.proposeFromCorpus("deploy-runbook", projectId) }
        }

        @Test
        fun `does not re-trigger once the threshold has already been crossed`() {
            val page = makePage()
            every { onboardingFeedbackRepository.countByPageIdAndHelpfulFalse(page.id) } returns 4L

            service.checkAndTriggerRegeneration(page)

            verify(exactly = 0) { scheduledExecutor.launch(any(), any()) }
        }
    }
}
