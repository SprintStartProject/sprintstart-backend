package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencySource
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ModulePageKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ModuleStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.VerificationType
import com.sprintstart.sprintstartbackend.onboarding.model.entity.CompetencyModule
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ModulePage
import com.sprintstart.sprintstartbackend.onboarding.model.entity.UserCompetencyState
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Verification
import com.sprintstart.sprintstartbackend.onboarding.model.entity.VerificationAttempt
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.security.crypto.encrypt.BytesEncryptor
import org.springframework.security.crypto.encrypt.Encryptors
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

/**
 * Guards the content/progress boundary at the persistence layer.
 *
 * Content is disposable: a module version is archived, replaced, re-drafted, deleted. What a hire
 * has *proven* is not — `UserCompetencyState` is the durable record, and `VerificationAttempt` is
 * the audit trail behind it. This test proves that throwing content away never reaches either, the
 * risk being an accidental JPA relationship or a shared cascade.
 *
 * A local [BytesEncryptor] is supplied because the onboarding entity model registers an encrypted
 * attribute converter the JPA slice would otherwise fail to construct.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(LedgerProjectionBoundaryTest.CryptoTestConfig::class)
@ActiveProfiles("test")
class LedgerProjectionBoundaryTest
    @Autowired
    constructor(
        private val competencyModuleRepository: CompetencyModuleRepository,
        private val verificationRepository: VerificationRepository,
        private val verificationAttemptRepository: VerificationAttemptRepository,
        private val userCompetencyStateRepository: UserCompetencyStateRepository,
    ) {
        @Test
        fun `deleting a module leaves the ledger and the attempts that earned it untouched`() {
            val userId = UUID.randomUUID()
            val projectId = UUID.randomUUID()

            userCompetencyStateRepository.saveAndFlush(
                UserCompetencyState(
                    userId = userId,
                    competencyKey = "kotlin",
                    level = 3,
                    source = CompetencySource.VERIFIED,
                ),
            )

            val module = CompetencyModule(
                competencyKey = "kotlin",
                projectId = projectId,
                version = 1,
                status = ModuleStatus.ACTIVE,
                title = "Kotlin here",
            )
            module.pages.add(
                ModulePage(module = module, kind = ModulePageKind.LESSON, title = "How it works", position = 0),
            )
            competencyModuleRepository.saveAndFlush(module)

            val check = verificationRepository.saveAndFlush(
                Verification(
                    moduleId = module.id,
                    type = VerificationType.ATTEST,
                    prompt = "Confirm",
                    competencyKey = "kotlin",
                    level = "advanced",
                ),
            )
            verificationAttemptRepository.saveAndFlush(
                VerificationAttempt(
                    verification = check,
                    userId = userId,
                    answer = "yes",
                    passed = true,
                    score = 1.0,
                    feedback = "Self-attested.",
                    attemptNo = 1,
                ),
            )

            competencyModuleRepository.delete(module)
            competencyModuleRepository.flush()

            assertThat(competencyModuleRepository.findById(module.id)).isEmpty
            val ledger = userCompetencyStateRepository.findAllByUserId(userId)
            assertThat(ledger).hasSize(1)
            assertThat(ledger[0].level).isEqualTo(3)
            assertThat(ledger[0].source).isEqualTo(CompetencySource.VERIFIED)
            assertThat(
                verificationAttemptRepository.countByVerificationIdAndUserId(check.id, userId),
            ).isEqualTo(1)
        }

        @TestConfiguration
        class CryptoTestConfig {
            @Bean
            fun bytesEncryptor(): BytesEncryptor = Encryptors.stronger("deadbeef", "deadbeef")
        }
    }
