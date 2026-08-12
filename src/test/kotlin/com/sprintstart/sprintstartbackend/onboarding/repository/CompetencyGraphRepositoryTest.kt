package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencyKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencySource
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Competency
import com.sprintstart.sprintstartbackend.onboarding.model.entity.UserCompetencyState
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.encrypt.BytesEncryptor
import org.springframework.security.crypto.encrypt.Encryptors
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

/**
 * Repository slice tests for the Phase 0a competency graph + ledger.
 *
 * Runs against the configured in-memory H2 database. The onboarding entity model registers the
 * encrypted [SymmetricEncryptedStringConverter], which Hibernate resolves as a Spring bean, so the
 * slice needs a [BytesEncryptor] present. [CryptoTestConfig] supplies one directly to keep the
 * slice self-contained instead of pulling in the full application configuration.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CompetencyGraphRepositoryTest.CryptoTestConfig::class)
@ActiveProfiles("test")
class CompetencyGraphRepositoryTest
    @Autowired
    constructor(
        private val competencyRepository: CompetencyRepository,
        private val userCompetencyStateRepository: UserCompetencyStateRepository,
    ) {
        @Test
        fun `persists and reads back a competency by its stable key`() {
            competencyRepository.saveAndFlush(
                Competency(
                    key = "kotlin",
                    label = "Kotlin",
                    description = "Primary backend language",
                    kind = CompetencyKind.SKILL,
                    repoRef = "build.gradle.kts",
                ),
            )

            val found = competencyRepository.findByKey("kotlin")

            assertThat(found).isNotNull
            assertThat(found!!.label).isEqualTo("Kotlin")
            assertThat(found.description).isEqualTo("Primary backend language")
            assertThat(found.kind).isEqualTo(CompetencyKind.SKILL)
            assertThat(found.repoRef).isEqualTo("build.gradle.kts")
        }

        @Test
        fun `findAllByKeyIn returns only the requested competencies`() {
            competencyRepository.saveAll(
                listOf(
                    Competency(key = "git", label = "Git", kind = CompetencyKind.SKILL),
                    Competency(key = "kotlin", label = "Kotlin", kind = CompetencyKind.SKILL),
                    Competency(key = "spring-boot", label = "Spring Boot", kind = CompetencyKind.SKILL),
                ),
            )

            val found = competencyRepository.findAllByKeyIn(listOf("git", "spring-boot"))

            assertThat(found.map { it.key }).containsExactlyInAnyOrder("git", "spring-boot")
        }

        @Test
        fun `rejects a second competency with a duplicate key`() {
            competencyRepository.saveAndFlush(Competency(key = "git", label = "Git", kind = CompetencyKind.SKILL))

            assertThatThrownBy {
                competencyRepository.saveAndFlush(
                    Competency(key = "git", label = "Git (dup)", kind = CompetencyKind.SKILL),
                )
            }.isInstanceOf(DataIntegrityViolationException::class.java)
        }

        @Test
        fun `persists and looks up a user competency state entry`() {
            val userId = UUID.randomUUID()
            userCompetencyStateRepository.saveAndFlush(
                UserCompetencyState(
                    userId = userId,
                    competencyKey = "kotlin",
                    level = 3,
                    source = CompetencySource.ASSESSED,
                ),
            )

            val found = userCompetencyStateRepository.findByUserIdAndCompetencyKey(userId, "kotlin")

            assertThat(found).isNotNull
            assertThat(found!!.level).isEqualTo(3)
            assertThat(found.source).isEqualTo(CompetencySource.ASSESSED)
            assertThat(userCompetencyStateRepository.findAllByUserId(userId)).hasSize(1)
        }

        @Test
        fun `rejects a second state row for the same user and competency`() {
            val userId = UUID.randomUUID()
            userCompetencyStateRepository.saveAndFlush(
                UserCompetencyState(
                    userId = userId,
                    competencyKey = "kotlin",
                    level = 1,
                    source = CompetencySource.DECLARED,
                ),
            )

            assertThatThrownBy {
                userCompetencyStateRepository.saveAndFlush(
                    UserCompetencyState(
                        userId = userId,
                        competencyKey = "kotlin",
                        level = 4,
                        source = CompetencySource.VERIFIED,
                    ),
                )
            }.isInstanceOf(DataIntegrityViolationException::class.java)
        }

        @TestConfiguration
        class CryptoTestConfig {
            @Bean
            fun bytesEncryptor(): BytesEncryptor = Encryptors.stronger("deadbeef", "deadbeef")
        }
    }
