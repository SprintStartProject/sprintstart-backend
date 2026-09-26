package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.shared.crypto.CryptoConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * `OnboardingPathService.deleteOnboardingPathByUserId` is not transactional, so it reaches
 * `deleteByUserId` with no transaction of its own — which is how the buddy's confirm calls it, on a
 * coroutine with none around it. Every other test of the path runs inside the test's transaction, where
 * a derived delete cannot fail this way, so this one deliberately opts out of it.
 */
@ActiveProfiles("test")
@DataJpaTest
@Import(CryptoConfiguration::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OnboardingPathDeleteTest {
    @Autowired
    private lateinit var repository: OnboardingPathRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private fun inTransaction(block: () -> Unit) {
        TransactionTemplate(transactionManager).executeWithoutResult { block() }
    }

    @Test
    fun `deleting a path outside any transaction removes it with what hangs off it`() {
        val userId = UUID.randomUUID()
        inTransaction {
            val path = OnboardingPath(userId = userId)
            val phase = OnboardingPhase(path = path, position = 0, title = "Setup", description = "d")
            phase.steps += OnboardingStep(
                phase = phase,
                position = 0,
                title = "Install",
                description = "d",
                type = StepType.TASK,
                estimatedMinutes = 5,
                expectedOutcome = "it runs",
                status = StepStatus.WAITING,
            )
            path.phases += phase
            repository.save(path)
        }
        assertThat(repository.existsByUserId(userId)).isTrue()

        repository.deleteByUserId(userId)

        assertThat(repository.existsByUserId(userId)).isFalse()
    }
}
