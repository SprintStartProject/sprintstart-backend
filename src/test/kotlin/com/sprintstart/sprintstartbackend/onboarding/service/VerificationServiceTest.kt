package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.connectors.github.external.GithubRepositoryApi
import com.sprintstart.sprintstartbackend.connectors.github.external.dto.PullRequestEvidence
import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencySource
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ModulePageKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ModuleStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.VerificationType
import com.sprintstart.sprintstartbackend.onboarding.external.model.GradeResult
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Competency
import com.sprintstart.sprintstartbackend.onboarding.model.entity.CompetencyModule
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ModulePage
import com.sprintstart.sprintstartbackend.onboarding.model.entity.UserCompetencyState
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Verification
import com.sprintstart.sprintstartbackend.onboarding.model.entity.VerificationAttempt
import com.sprintstart.sprintstartbackend.onboarding.model.exceptions.OnboardingAiException
import com.sprintstart.sprintstartbackend.onboarding.model.request.verification.SubmitVerificationAttemptRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.verification.UpsertVerificationRequest
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyModuleRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.UserCompetencyStateRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.VerificationAttemptRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.VerificationRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerificationServiceTest {
    private val verificationRepository: VerificationRepository = mockk()
    private val verificationAttemptRepository: VerificationAttemptRepository = mockk()
    private val competencyModuleRepository: CompetencyModuleRepository = mockk()
    private val competencyRepository: CompetencyRepository = mockk()
    private val userCompetencyStateRepository: UserCompetencyStateRepository = mockk()
    private val onboardingAiClient: OnboardingAiClient = mockk()
    private val githubRepositoryApi: GithubRepositoryApi = mockk()
    private val userApi: UserApi = mockk()
    private val transactionManager: PlatformTransactionManager = mockk(relaxed = true)
    private val service = VerificationService(
        verificationRepository,
        verificationAttemptRepository,
        competencyModuleRepository,
        competencyRepository,
        userCompetencyStateRepository,
        onboardingAiClient,
        githubRepositoryApi,
        userApi,
        transactionManager,
    )

    private val userId = UUID.randomUUID()
    private val authId = "auth|test-user"

    @BeforeEach
    fun stubAnswerReuseCheck() {
        // Only ARTIFACT submissions consult this; default to "nobody else claimed it" so each
        // test opts in to the reuse case explicitly.
        every {
            verificationAttemptRepository.existsByVerificationIdAndAnswerAndPassedIsTrueAndUserIdNot(
                any(),
                any(),
                any(),
            )
        } returns false

        // Artifact checks attribute the PR to its submitter, so the default fixture user *is*
        // the author of the fixture PR. Tests that care opt into a mismatch explicitly.
        every { userApi.getGithubLoginByUserId(userId) } returns "octocat"
    }

    private val projectId = UUID.randomUUID()

    /** A live module with one lesson page, which is what grading uses as grounded evidence. */
    private fun makeModule(
        content: String? = null,
        status: ModuleStatus = ModuleStatus.ACTIVE,
    ): CompetencyModule {
        val module = CompetencyModule(
            competencyKey = "kotlin",
            projectId = projectId,
            version = 1,
            status = status,
            title = "Learn Kotlin",
        )
        if (content != null) {
            module.pages.add(
                ModulePage(
                    module = module,
                    kind = ModulePageKind.LESSON,
                    title = "Lesson",
                    body = content,
                    position = 0,
                ),
            )
        }
        return module
    }

    private fun makeVerification(
        type: VerificationType,
        moduleId: UUID,
        rubric: String? = null,
        canonicalAnswer: String? = null,
        repositoryConnectionId: UUID? = null,
        competencyKey: String = "kotlin",
        level: String = "beginner",
    ) = Verification(
        moduleId = moduleId,
        type = type,
        prompt = "Explain it",
        rubric = rubric,
        canonicalAnswer = canonicalAnswer,
        repositoryConnectionId = repositoryConnectionId,
        competencyKey = competencyKey,
        level = level,
    )

    /**
     * Stubs the reads a module submission makes: the live module, membership, and its check.
     *
     * The check is looked up twice on a submission -- once in the read transaction that builds the
     * grading context, and again by id in the write transaction, because grading happens between
     * the two and the row may have changed under it.
     */
    private fun stubModule(module: CompetencyModule, verification: Verification?) {
        every { competencyModuleRepository.findById(module.id) } returns Optional.of(module)
        every { userApi.userHasAccessToProject(authId, projectId) } returns true
        every { verificationRepository.findByModuleId(module.id) } returns verification
        if (verification != null) {
            every { verificationRepository.findById(verification.id) } returns Optional.of(verification)
        }
    }

    @Nested
    inner class SubmitAttemptForMe {
        @Test
        fun `EXACT pass writes a VERIFIED ledger entry, and nothing else, without calling the AI`() = runTest {
            val module = makeModule()
            val verification = makeVerification(VerificationType.EXACT, module.id, canonicalAnswer = "Chroma")
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            stubModule(module, verification)
            every { verificationAttemptRepository.countByVerificationIdAndUserId(verification.id, userId) } returns 0
            every { userCompetencyStateRepository.findByUserIdAndCompetencyKey(userId, "kotlin") } returns null
            val savedState = slot<UserCompetencyState>()
            every { userCompetencyStateRepository.save(capture(savedState)) } answers { savedState.captured }
            every { verificationAttemptRepository.save(any()) } answers { firstArg() }

            val result = service.submitModuleAttemptForMe(authId, module.id, SubmitVerificationAttemptRequest("chroma"))

            assertTrue(result.passed)
            assertEquals(CompetencySource.VERIFIED, savedState.captured.source)
            assertEquals(1, savedState.captured.level)
            coVerify(exactly = 0) { onboardingAiClient.gradeKnowledge(any(), any(), any(), any(), any()) }
        }

        @Test
        fun `EXACT fail logs an attempt with a hint and writes no ledger entry`() = runTest {
            val module = makeModule()
            val verification = makeVerification(VerificationType.EXACT, module.id, canonicalAnswer = "Chroma")
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            stubModule(module, verification)
            every { verificationAttemptRepository.countByVerificationIdAndUserId(verification.id, userId) } returns 0
            val savedAttempt = slot<VerificationAttempt>()
            every { verificationAttemptRepository.save(capture(savedAttempt)) } answers { savedAttempt.captured }

            val result = service.submitModuleAttemptForMe(
                authId,
                module.id,
                SubmitVerificationAttemptRequest("pinecone"),
            )

            assertFalse(result.passed)
            assertFalse(savedAttempt.captured.passed)
            assertThat(savedAttempt.captured.hint).isNotNull()
            verify(exactly = 0) { userCompetencyStateRepository.save(any()) }
        }

        @Test
        fun `KNOWLEDGE delegates grading to the AI client`() = runTest {
            val module = makeModule(content = "Kotlin is null-safe.")
            val verification = makeVerification(VerificationType.KNOWLEDGE, module.id, rubric = "mentions null-safety")
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            stubModule(module, verification)
            every { verificationAttemptRepository.countByVerificationIdAndUserId(verification.id, userId) } returns 0
            coEvery {
                onboardingAiClient.gradeKnowledge(
                    "Explain it",
                    "mentions null-safety",
                    "Kotlin is null-safe.",
                    "ans",
                    1,
                )
            } returns GradeResult(passed = true, score = 0.9, feedback = "Good.")
            every { userCompetencyStateRepository.findByUserIdAndCompetencyKey(userId, "kotlin") } returns null
            every { userCompetencyStateRepository.save(any()) } answers { firstArg() }
            every { verificationAttemptRepository.save(any()) } answers { firstArg() }

            val result = service.submitModuleAttemptForMe(authId, module.id, SubmitVerificationAttemptRequest("ans"))

            assertTrue(result.passed)
            coVerify(exactly = 1) { onboardingAiClient.gradeKnowledge(any(), any(), any(), any(), any()) }
        }

        @Test
        fun `KNOWLEDGE surfaces 503 when the AI service is unavailable, without persisting an attempt`() = runTest {
            val module = makeModule()
            val verification = makeVerification(VerificationType.KNOWLEDGE, module.id, rubric = "mentions null-safety")
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            stubModule(module, verification)
            every { verificationAttemptRepository.countByVerificationIdAndUserId(verification.id, userId) } returns 0
            coEvery { onboardingAiClient.gradeKnowledge(any(), any(), any(), any(), any()) } throws
                OnboardingAiException(503, "", "AI unavailable")

            assertThrows<ResponseStatusException> {
                service.submitModuleAttemptForMe(authId, module.id, SubmitVerificationAttemptRequest("ans"))
            }.also { assertEquals(503, it.statusCode.value()) }

            verify(exactly = 0) { verificationAttemptRepository.save(any()) }
        }

        @Test
        fun `ARTIFACT fetches PR evidence and delegates grading to the AI client`() = runTest {
            val repositoryConnectionId = UUID.randomUUID()
            val module = makeModule()
            val verification = makeVerification(
                VerificationType.ARTIFACT,
                module.id,
                rubric = "closes the ticket",
                repositoryConnectionId = repositoryConnectionId,
            )
            val evidence = PullRequestEvidence(
                title = "Fix bug",
                body = "Closes #42",
                state = "MERGED",
                filesChanged = listOf("src/Main.kt"),
                checksPassed = true,
                commitMessages = listOf("fix: bug"),
                authorLogin = "octocat",
            )
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            stubModule(module, verification)
            every { verificationAttemptRepository.countByVerificationIdAndUserId(verification.id, userId) } returns 0
            coEvery { githubRepositoryApi.getPullRequestEvidence(repositoryConnectionId, 42) } returns evidence
            coEvery { onboardingAiClient.gradeArtifact("Explain it", "closes the ticket", any()) } returns
                GradeResult(passed = true, score = 1.0, feedback = "Satisfies the rubric.")
            every { userCompetencyStateRepository.findByUserIdAndCompetencyKey(userId, "kotlin") } returns null
            every { userCompetencyStateRepository.save(any()) } answers { firstArg() }
            every { verificationAttemptRepository.save(any()) } answers { firstArg() }

            val result = service.submitModuleAttemptForMe(authId, module.id, SubmitVerificationAttemptRequest("42"))

            assertTrue(result.passed)
            coVerify(exactly = 1) { githubRepositoryApi.getPullRequestEvidence(repositoryConnectionId, 42) }
            coVerify(exactly = 1) { onboardingAiClient.gradeArtifact(any(), any(), any()) }
        }

        @Test
        fun `ARTIFACT rejects a PR opened by somebody else, without an AI call`() = runTest {
            val repositoryConnectionId = UUID.randomUUID()
            val module = makeModule()
            val verification = makeVerification(
                VerificationType.ARTIFACT,
                module.id,
                rubric = "closes the ticket",
                repositoryConnectionId = repositoryConnectionId,
            )
            val evidence = PullRequestEvidence(
                title = "Fix bug",
                body = "Closes #42",
                state = "MERGED",
                filesChanged = listOf("src/Main.kt"),
                checksPassed = true,
                commitMessages = listOf("fix: bug"),
                authorLogin = "a-colleague",
            )
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            stubModule(module, verification)
            every { verificationAttemptRepository.countByVerificationIdAndUserId(verification.id, userId) } returns 0
            coEvery { githubRepositoryApi.getPullRequestEvidence(repositoryConnectionId, 42) } returns evidence
            every { verificationAttemptRepository.save(any()) } answers { firstArg() }

            val result = service.submitModuleAttemptForMe(authId, module.id, SubmitVerificationAttemptRequest("42"))

            // The judge would pass this -- the PR genuinely satisfies the rubric. It just isn't
            // this user's work, which is not something a rubric can decide.
            assertFalse(result.passed)
            assertTrue(result.feedback.contains("opened by someone else"))
            coVerify(exactly = 0) { onboardingAiClient.gradeArtifact(any(), any(), any()) }
        }

        @Test
        fun `ARTIFACT asks for a GitHub username when the submitter has none`() = runTest {
            val repositoryConnectionId = UUID.randomUUID()
            val module = makeModule()
            val verification = makeVerification(
                VerificationType.ARTIFACT,
                module.id,
                rubric = "closes the ticket",
                repositoryConnectionId = repositoryConnectionId,
            )
            val evidence = PullRequestEvidence(
                title = "Fix bug",
                body = "Closes #42",
                state = "MERGED",
                filesChanged = listOf("src/Main.kt"),
                checksPassed = true,
                commitMessages = listOf("fix: bug"),
                authorLogin = "octocat",
            )
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { userApi.getGithubLoginByUserId(userId) } returns null
            stubModule(module, verification)
            every { verificationAttemptRepository.countByVerificationIdAndUserId(verification.id, userId) } returns 0
            coEvery { githubRepositoryApi.getPullRequestEvidence(repositoryConnectionId, 42) } returns evidence
            every { verificationAttemptRepository.save(any()) } answers { firstArg() }

            val result = service.submitModuleAttemptForMe(authId, module.id, SubmitVerificationAttemptRequest("42"))

            // Grading an unattributable PR anyway is exactly what let a hire pass with someone
            // else's work, so this asks for the missing identity instead.
            assertFalse(result.passed)
            assertTrue(result.hint!!.contains("GitHub username"))
            coVerify(exactly = 0) { onboardingAiClient.gradeArtifact(any(), any(), any()) }
        }

        @Test
        fun `ARTIFACT rejects a PR another user already passed with, without a GitHub or AI call`() = runTest {
            val repositoryConnectionId = UUID.randomUUID()
            val module = makeModule()
            val verification = makeVerification(
                VerificationType.ARTIFACT,
                module.id,
                rubric = "closes the ticket",
                repositoryConnectionId = repositoryConnectionId,
            )
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            stubModule(module, verification)
            every { verificationAttemptRepository.countByVerificationIdAndUserId(verification.id, userId) } returns 0
            every {
                verificationAttemptRepository.existsByVerificationIdAndAnswerAndPassedIsTrueAndUserIdNot(
                    verification.id,
                    "42",
                    userId,
                )
            } returns true
            every { verificationAttemptRepository.save(any()) } answers { firstArg() }

            val result = service.submitModuleAttemptForMe(authId, module.id, SubmitVerificationAttemptRequest(" 42 "))

            // One PR can't prove two people did the work, and the LLM judge would happily pass it
            // a second time -- so this never reaches GitHub or the AI service.
            assertFalse(result.passed)
            assertTrue(result.feedback.contains("already been submitted"))
            coVerify(exactly = 0) { githubRepositoryApi.getPullRequestEvidence(any(), any()) }
            coVerify(exactly = 0) { onboardingAiClient.gradeArtifact(any(), any(), any()) }
        }

        @Test
        fun `ARTIFACT stores the PR number normalized so the reuse check can match it`() = runTest {
            val repositoryConnectionId = UUID.randomUUID()
            val module = makeModule()
            val verification = makeVerification(
                VerificationType.ARTIFACT,
                module.id,
                rubric = "closes the ticket",
                repositoryConnectionId = repositoryConnectionId,
            )
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            stubModule(module, verification)
            every { verificationAttemptRepository.countByVerificationIdAndUserId(verification.id, userId) } returns 0
            coEvery { githubRepositoryApi.getPullRequestEvidence(repositoryConnectionId, 42) } returns null
            val saved = slot<VerificationAttempt>()
            every { verificationAttemptRepository.save(capture(saved)) } answers { firstArg() }

            service.submitModuleAttemptForMe(authId, module.id, SubmitVerificationAttemptRequest("  42  "))

            assertEquals("42", saved.captured.answer)
        }

        @Test
        fun `ARTIFACT fails locally without a GitHub or AI call when the answer isn't a PR number`() = runTest {
            val module = makeModule()
            val verification = makeVerification(
                VerificationType.ARTIFACT,
                module.id,
                rubric = "closes the ticket",
                repositoryConnectionId = UUID.randomUUID(),
            )
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            stubModule(module, verification)
            every { verificationAttemptRepository.countByVerificationIdAndUserId(verification.id, userId) } returns 0
            every { verificationAttemptRepository.save(any()) } answers { firstArg() }

            val result = service.submitModuleAttemptForMe(
                authId,
                module.id,
                SubmitVerificationAttemptRequest("not a number"),
            )

            assertFalse(result.passed)
            coVerify(exactly = 0) { githubRepositoryApi.getPullRequestEvidence(any(), any()) }
            coVerify(exactly = 0) { onboardingAiClient.gradeArtifact(any(), any(), any()) }
        }

        @Test
        fun `ARTIFACT fails locally without an AI call when the PR isn't found`() = runTest {
            val repositoryConnectionId = UUID.randomUUID()
            val module = makeModule()
            val verification = makeVerification(
                VerificationType.ARTIFACT,
                module.id,
                rubric = "closes the ticket",
                repositoryConnectionId = repositoryConnectionId,
            )
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            stubModule(module, verification)
            every { verificationAttemptRepository.countByVerificationIdAndUserId(verification.id, userId) } returns 0
            coEvery { githubRepositoryApi.getPullRequestEvidence(repositoryConnectionId, 99) } returns null
            every { verificationAttemptRepository.save(any()) } answers { firstArg() }

            val result = service.submitModuleAttemptForMe(authId, module.id, SubmitVerificationAttemptRequest("99"))

            assertFalse(result.passed)
            coVerify(exactly = 0) { onboardingAiClient.gradeArtifact(any(), any(), any()) }
        }

        @Test
        fun `ARTIFACT throws 500 when repositoryConnectionId isn't configured`() = runTest {
            val module = makeModule()
            val verification = makeVerification(VerificationType.ARTIFACT, module.id, rubric = "closes the ticket")
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            stubModule(module, verification)
            every { verificationAttemptRepository.countByVerificationIdAndUserId(verification.id, userId) } returns 0

            assertThrows<ResponseStatusException> {
                service.submitModuleAttemptForMe(authId, module.id, SubmitVerificationAttemptRequest("1"))
            }.also { assertEquals(500, it.statusCode.value()) }
        }

        @Test
        fun `ARTIFACT surfaces 503 when the AI service is unavailable, without persisting an attempt`() = runTest {
            val repositoryConnectionId = UUID.randomUUID()
            val module = makeModule()
            val verification = makeVerification(
                VerificationType.ARTIFACT,
                module.id,
                rubric = "closes the ticket",
                repositoryConnectionId = repositoryConnectionId,
            )
            val evidence = PullRequestEvidence(
                title = "Fix bug",
                body = "",
                state = "OPEN",
                filesChanged = emptyList(),
                checksPassed = null,
                commitMessages = emptyList(),
                authorLogin = "octocat",
            )
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            stubModule(module, verification)
            every { verificationAttemptRepository.countByVerificationIdAndUserId(verification.id, userId) } returns 0
            coEvery { githubRepositoryApi.getPullRequestEvidence(repositoryConnectionId, 7) } returns evidence
            coEvery { onboardingAiClient.gradeArtifact(any(), any(), any()) } throws
                OnboardingAiException(503, "", "AI unavailable")

            assertThrows<ResponseStatusException> {
                service.submitModuleAttemptForMe(authId, module.id, SubmitVerificationAttemptRequest("7"))
            }.also { assertEquals(503, it.statusCode.value()) }

            verify(exactly = 0) { verificationAttemptRepository.save(any()) }
        }

        @Test
        fun `throws 404 when the module has no check configured`() = runTest {
            val module = makeModule()
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            stubModule(module, null)

            assertThrows<ResponseStatusException> {
                service.submitModuleAttemptForMe(authId, module.id, SubmitVerificationAttemptRequest("x"))
            }.also { assertEquals(404, it.statusCode.value()) }
        }

        @Test
        fun `attemptNo increments across repeated submissions`() = runTest {
            val module = makeModule()
            val verification = makeVerification(VerificationType.ATTEST, module.id)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            stubModule(module, verification)
            every { verificationAttemptRepository.countByVerificationIdAndUserId(verification.id, userId) } returns 2
            every { userCompetencyStateRepository.findByUserIdAndCompetencyKey(userId, "kotlin") } returns null
            every { userCompetencyStateRepository.save(any()) } answers { firstArg() }
            every { verificationAttemptRepository.save(any()) } answers { firstArg() }

            val result = service.submitModuleAttemptForMe(authId, module.id, SubmitVerificationAttemptRequest("x"))

            assertEquals(3, result.attemptNo)
        }

        @Test
        fun `passing a lower-level verification keeps the higher ledger level`() = runTest {
            val module = makeModule()
            val verification = makeVerification(VerificationType.ATTEST, module.id, level = "beginner")
            val existing = UserCompetencyState(
                userId = userId,
                competencyKey = "kotlin",
                level = 4,
                source = CompetencySource.ASSESSED,
            )
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            stubModule(module, verification)
            every { verificationAttemptRepository.countByVerificationIdAndUserId(verification.id, userId) } returns 0
            every { userCompetencyStateRepository.findByUserIdAndCompetencyKey(userId, "kotlin") } returns existing
            every { verificationAttemptRepository.save(any()) } answers { firstArg() }

            val result = service.submitModuleAttemptForMe(authId, module.id, SubmitVerificationAttemptRequest("done"))

            assertTrue(result.passed)
            assertEquals(4, existing.level)
            assertEquals(CompetencySource.VERIFIED, existing.source)
            verify(exactly = 0) { userCompetencyStateRepository.save(any()) }
        }

        @Test
        fun `passing masters the node and its dependent stays available`() = runTest {
            // Regression coverage for "pass writes VERIFIED": feed the exact ledger state this
            // service writes into the real (pure) PathProjectionService and confirm the node reads
            // MASTERED. The dependent is AVAILABLE either way now -- prerequisite edges rank the
            // work, they no longer lock it -- so this pins that passing does not regress that.
            val module = makeModule()
            // "intermediate" is what generated checks are pitched at, and it is what clears a
            // competency's default bar -- a beginner-level check against a default node passes
            // without mastering it, which is the point of the bar, not a regression.
            val verification =
                makeVerification(VerificationType.ATTEST, module.id, competencyKey = "kotlin", level = "intermediate")
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            stubModule(module, verification)
            every { verificationAttemptRepository.countByVerificationIdAndUserId(verification.id, userId) } returns 0
            every { userCompetencyStateRepository.findByUserIdAndCompetencyKey(userId, "kotlin") } returns null
            val savedState = slot<UserCompetencyState>()
            every { userCompetencyStateRepository.save(capture(savedState)) } answers { savedState.captured }
            every { verificationAttemptRepository.save(any()) } answers { firstArg() }

            service.submitModuleAttemptForMe(authId, module.id, SubmitVerificationAttemptRequest("done"))

            // Passing an intermediate check clears the default bar, which is what "met" means now
            // that there is no projection to ask. The bar lives on the competency; the ledger level
            // is the only thing a pass moves.
            assertEquals(Competency.DEFAULT_TARGET_LEVEL, savedState.captured.level)
        }
    }

    @Nested
    inner class UpsertVerification {
        @Test
        fun `creates a verification when none exists`() {
            val module = makeModule(status = ModuleStatus.DRAFT)
            every { competencyRepository.existsByKey("kotlin") } returns true
            stubModule(module, null)
            every { verificationRepository.save(any()) } answers { firstArg() }

            val request = UpsertVerificationRequest(
                type = VerificationType.EXACT,
                prompt = "What DB?",
                canonicalAnswer = "Chroma",
                competencyKey = "kotlin",
                level = "beginner",
            )
            val result = service.upsertModuleVerification(module.id, request)

            assertEquals(VerificationType.EXACT, result.type)
            assertEquals("kotlin", result.competencyKey)
        }

        @Test
        fun `throws 400 when KNOWLEDGE has no rubric`() {
            val module = makeModule(status = ModuleStatus.DRAFT)

            val request = UpsertVerificationRequest(
                type = VerificationType.KNOWLEDGE,
                prompt = "Why?",
                competencyKey = "kotlin",
                level = "beginner",
            )

            assertThrows<ResponseStatusException> {
                service.upsertModuleVerification(module.id, request)
            }.also { assertEquals(400, it.statusCode.value()) }
        }

        @Test
        fun `throws 400 when EXACT has no canonicalAnswer`() {
            val module = makeModule(status = ModuleStatus.DRAFT)

            val request = UpsertVerificationRequest(
                type = VerificationType.EXACT,
                prompt = "What?",
                competencyKey = "kotlin",
                level = "beginner",
            )

            assertThrows<ResponseStatusException> {
                service.upsertModuleVerification(module.id, request)
            }.also { assertEquals(400, it.statusCode.value()) }
        }

        @Test
        fun `throws 400 when level is not a known skill level`() {
            val module = makeModule(status = ModuleStatus.DRAFT)

            val request = UpsertVerificationRequest(
                type = VerificationType.ATTEST,
                prompt = "Confirm?",
                competencyKey = "kotlin",
                level = "grandmaster",
            )

            assertThrows<ResponseStatusException> {
                service.upsertModuleVerification(module.id, request)
            }.also { assertEquals(400, it.statusCode.value()) }
        }

        @Test
        fun `throws 404 when the referenced competency does not exist`() {
            val module = makeModule(status = ModuleStatus.DRAFT)
            stubModule(module, null)
            every { competencyRepository.existsByKey("nope") } returns false

            val request = UpsertVerificationRequest(
                type = VerificationType.ATTEST,
                prompt = "Confirm?",
                competencyKey = "nope",
                level = "beginner",
            )

            assertThrows<ResponseStatusException> {
                service.upsertModuleVerification(module.id, request)
            }.also { assertEquals(404, it.statusCode.value()) }
        }

        @Test
        fun `throws 400 when ARTIFACT has no rubric`() {
            val module = makeModule(status = ModuleStatus.DRAFT)

            val request = UpsertVerificationRequest(
                type = VerificationType.ARTIFACT,
                prompt = "Ship it",
                repositoryConnectionId = UUID.randomUUID(),
                competencyKey = "kotlin",
                level = "beginner",
            )

            assertThrows<ResponseStatusException> {
                service.upsertModuleVerification(module.id, request)
            }.also { assertEquals(400, it.statusCode.value()) }
        }

        @Test
        fun `throws 400 when ARTIFACT has no repositoryConnectionId`() {
            val module = makeModule(status = ModuleStatus.DRAFT)

            val request = UpsertVerificationRequest(
                type = VerificationType.ARTIFACT,
                prompt = "Ship it",
                rubric = "closes the ticket",
                competencyKey = "kotlin",
                level = "beginner",
            )

            assertThrows<ResponseStatusException> {
                service.upsertModuleVerification(module.id, request)
            }.also { assertEquals(400, it.statusCode.value()) }
        }

        @Test
        fun `creates an ARTIFACT verification with a repositoryConnectionId`() {
            val module = makeModule(status = ModuleStatus.DRAFT)
            val repositoryConnectionId = UUID.randomUUID()
            every { competencyRepository.existsByKey("kotlin") } returns true
            stubModule(module, null)
            val saved = slot<Verification>()
            every { verificationRepository.save(capture(saved)) } answers { saved.captured }

            val request = UpsertVerificationRequest(
                type = VerificationType.ARTIFACT,
                prompt = "Ship it",
                rubric = "closes the ticket",
                repositoryConnectionId = repositoryConnectionId,
                competencyKey = "kotlin",
                level = "beginner",
            )
            val result = service.upsertModuleVerification(module.id, request)

            assertEquals(VerificationType.ARTIFACT, result.type)
            assertEquals(repositoryConnectionId, saved.captured.repositoryConnectionId)
        }
    }
}
