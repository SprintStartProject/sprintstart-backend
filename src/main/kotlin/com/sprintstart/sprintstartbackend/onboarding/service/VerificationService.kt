package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.connectors.github.external.GithubRepositoryApi
import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencySource
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ModuleStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.VerificationType
import com.sprintstart.sprintstartbackend.onboarding.external.model.ArtifactEvidenceDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.FileDiffDto
import com.sprintstart.sprintstartbackend.onboarding.model.entity.CompetencyModule
import com.sprintstart.sprintstartbackend.onboarding.model.entity.UserCompetencyState
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Verification
import com.sprintstart.sprintstartbackend.onboarding.model.entity.VerificationAttempt
import com.sprintstart.sprintstartbackend.onboarding.model.exceptions.OnboardingAiException
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toResponse
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toSubmitResponse
import com.sprintstart.sprintstartbackend.onboarding.model.request.verification.SubmitVerificationAttemptRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.verification.UpsertVerificationRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.verification.SubmitVerificationAttemptResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.verification.VerificationResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyModuleRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.UserCompetencyStateRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.VerificationAttemptRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.VerificationRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

/**
 * Manages a module's graded check: config, grading orchestration, and the resulting unlock.
 *
 * [VerificationType.EXACT]/[VerificationType.ATTEST] are graded locally in Kotlin, mirroring the
 * AI service's own `grade_exact`/`grade_attest` logic exactly so a step behaves identically
 * regardless of which side happens to grade it. [VerificationType.KNOWLEDGE] and
 * [VerificationType.ARTIFACT] are delegated to the AI service (Seam 1) -- unlike
 * [PhaseCheckService]'s fallback-to-exact-match on AI failure, a failed AI call here surfaces a
 * retryable `503` rather than a fabricated grade, since a rubric has no safe local approximation.
 * [VerificationType.ARTIFACT] additionally gathers real GitHub state itself (via
 * [GithubRepositoryApi]) for the PR number a hire submits as their answer, before handing that
 * evidence to the AI's judge -- the highest-rigor rung of the ladder.
 *
 * Passing writes [UserCompetencyState] with [CompetencySource.VERIFIED] and nothing else. There is
 * no per-user row to complete and no separate "unlock":
 * [PathProjectionService][com.sprintstart.sprintstartbackend.onboarding.service.PathProjectionService]
 * derives a competency's state, and its dependents' availability, purely from the ledger. "Testing
 * out" -- passing without ever opening the lesson -- therefore needs no special case; it is just a
 * submission from somebody who read nothing.
 */
@Suppress("TooManyFunctions")
@Service
class VerificationService(
    private val verificationRepository: VerificationRepository,
    private val verificationAttemptRepository: VerificationAttemptRepository,
    private val competencyModuleRepository: CompetencyModuleRepository,
    private val competencyRepository: CompetencyRepository,
    private val userCompetencyStateRepository: UserCompetencyStateRepository,
    private val onboardingAiClient: OnboardingAiClient,
    private val githubRepositoryApi: GithubRepositoryApi,
    private val userApi: UserApi,
    transactionManager: PlatformTransactionManager,
) {
    // The AI call is a long-running suspend operation, so it must not run inside a transaction --
    // a DB connection would be pinned for its whole duration.
    private val txTemplate = TransactionTemplate(transactionManager)
    private val readTxTemplate = TransactionTemplate(transactionManager).apply { isReadOnly = true }
    private val logger = LoggerFactory.getLogger(javaClass)

//  ========================== Methods for users ==========================

//  ================= Methods for users: module-owned checks =================

    /**
     * Returns the check config for a live module, without revealing the rubric or canonical answer.
     *
     * Access is by project membership, not by "this row is in your path": a module is shared, so
     * there is no per-user copy to look the hire up in. That is the point of the rework.
     *
     * @throws ResponseStatusException 404 if the module or its check doesn't exist; 403 if the
     * user is not a member of the module's project.
     */
    @Transactional(readOnly = true)
    fun getModuleVerificationForMe(authId: String, moduleId: UUID): VerificationResponse {
        val module = findModuleForUser(authId, moduleId)
        return findModuleVerification(module.id).toResponse()
    }

    /**
     * Grades a submitted answer against a module's check and, on pass, writes the ledger.
     *
     * Unlike the step-owned path, nothing per-user is completed here -- there is no per-user row to
     * complete. Passing writes [UserCompetencyState], and the projection derives the node's state
     * and its dependents' availability from the ledger, which is what "the path is a disposable
     * projection" has always meant.
     *
     * @throws ResponseStatusException 404 if the module or its check doesn't exist; 403 if the
     * user is not a member of the module's project; 503 if grading needs the AI service and it's
     * unavailable.
     */
    suspend fun submitModuleAttemptForMe(
        authId: String,
        moduleId: UUID,
        request: SubmitVerificationAttemptRequest,
    ): SubmitVerificationAttemptResponse {
        val context = withContext(Dispatchers.IO) {
            readTxTemplate.execute { loadModuleSubmissionContext(authId, moduleId, request.answer) }!!
        }

        val answer = if (context.verification.type == VerificationType.ARTIFACT) {
            request.answer.trim()
        } else {
            request.answer
        }
        val graded = grade(context, answer)

        return withContext(Dispatchers.IO) {
            txTemplate.execute { persistGradedModuleAttempt(context, answer, graded) }!!
        }
    }

    private fun loadModuleSubmissionContext(authId: String, moduleId: UUID, answer: String): SubmissionContext {
        val userId = resolveUserId(authId)
        val module = findModuleForUser(authId, moduleId)
        val verification = findModuleVerification(module.id)
        val attemptNo = verificationAttemptRepository.countByVerificationIdAndUserId(verification.id, userId) + 1
        return SubmissionContext(
            userId = userId,
            verification = verification,
            lessonContent = module.lessonEvidence(),
            attemptNo = attemptNo,
            answerAlreadyClaimed = verification.type == VerificationType.ARTIFACT &&
                verificationAttemptRepository.existsByVerificationIdAndAnswerAndPassedIsTrueAndUserIdNot(
                    verification.id,
                    answer.trim(),
                    userId,
                ),
            githubLogin = if (verification.type == VerificationType.ARTIFACT) {
                userApi.getGithubLoginByUserId(userId)
            } else {
                null
            },
        )
    }

    private fun persistGradedModuleAttempt(
        context: SubmissionContext,
        answer: String,
        graded: GradedResult,
    ): SubmitVerificationAttemptResponse {
        val verification = verificationRepository
            .findById(context.verification.id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Verification no longer exists") }

        val attempt = verificationAttemptRepository.save(
            VerificationAttempt(
                verification = verification,
                userId = context.userId,
                answer = answer,
                passed = graded.passed,
                score = graded.score,
                feedback = graded.feedback,
                hint = graded.hint,
                attemptNo = context.attemptNo,
            ),
        )

        if (graded.passed) {
            writeVerifiedCompetencyState(context.userId, verification.competencyKey, verification.level)
        }

        return attempt.toSubmitResponse()
    }

    /**
     * The module's prose, as grounded evidence for rubric grading: the pages that teach, in order.
     * A hire is judged against what the module actually said, not against the corpus at large.
     */
    private fun CompetencyModule.lessonEvidence(): String =
        pages
            .filter { it.kind in LESSON_PAGE_KINDS }
            .mapNotNull { page -> page.body?.takeIf { it.isNotBlank() } }
            .joinToString(System.lineSeparator() + System.lineSeparator())

//  ========================== Methods for admins ==========================

    /**
     * Creates or replaces a module's check.
     *
     * @throws ResponseStatusException 404 if the module or referenced competency doesn't exist;
     * 400 if a type-required field is missing; 409 if the module is live or archived -- a live
     * module's gate is what earlier hires were held to, so it changes by new version, not in place.
     */
    @Transactional
    fun upsertModuleVerification(moduleId: UUID, request: UpsertVerificationRequest): VerificationResponse {
        // The request is validated before anything is looked up: a malformed check is malformed
        // whatever module it names, and saying so without a database round trip is the honest order.
        validateGradingConfig(request)
        val module = competencyModuleRepository
            .findById(moduleId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No module found with id: $moduleId") }
        if (module.status == ModuleStatus.ACTIVE || module.status == ModuleStatus.ARCHIVED) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Module $moduleId is ${module.status}; create a new version to change its check",
            )
        }
        if (!competencyRepository.existsByKey(request.competencyKey)) {
            throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No competency found with key: ${request.competencyKey}",
            )
        }

        val verification = verificationRepository.findByModuleId(module.id)?.apply {
            type = request.type
            prompt = request.prompt
            rubric = request.rubric
            canonicalAnswer = request.canonicalAnswer
            repositoryConnectionId = request.repositoryConnectionId
            competencyKey = request.competencyKey
            level = request.level
        } ?: Verification(
            moduleId = module.id,
            type = request.type,
            prompt = request.prompt,
            rubric = request.rubric,
            canonicalAnswer = request.canonicalAnswer,
            repositoryConnectionId = request.repositoryConnectionId,
            competencyKey = request.competencyKey,
            level = request.level,
        )

        return verificationRepository.save(verification).toResponse()
    }

//  ========================== Helper Methods ==========================

    private data class SubmissionContext(
        val userId: UUID,
        val verification: Verification,
        val lessonContent: String,
        val attemptNo: Int,
        /**
         * Another user already passed this check with this exact answer.
         *
         * Only meaningful for `ARTIFACT`, where the answer is a pull request number. Resolved
         * inside the read transaction, so grading itself stays free of repository access.
         */
        val answerAlreadyClaimed: Boolean = false,
        /**
         * The submitting user's declared GitHub login, or `null` when they have none. Only
         * `ARTIFACT` uses it, to check that they actually opened the pull request they submitted.
         */
        val githubLogin: String? = null,
    )

    /**
     * Resolves a module the authenticated user may open: it has to be live, and they have to be in
     * its project. Membership is the whole access rule -- a shared module has no per-user row to
     * look them up in.
     */
    private fun findModuleForUser(authId: String, moduleId: UUID): CompetencyModule {
        val module = competencyModuleRepository
            .findById(moduleId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No module found with id: $moduleId") }
        if (module.status != ModuleStatus.ACTIVE) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No module found with id: $moduleId")
        }
        if (!userApi.userHasAccessToProject(authId, module.projectId)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "User is not a member of this module's project")
        }
        return module
    }

    private fun findModuleVerification(moduleId: UUID): Verification =
        verificationRepository.findByModuleId(moduleId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No check configured for module: $moduleId")

    private data class GradedResult(
        val passed: Boolean,
        val score: Double,
        val feedback: String,
        val hint: String? = null,
    )

    private suspend fun grade(
        context: SubmissionContext,
        answer: String,
    ): GradedResult {
        val verification = context.verification

        // Somebody else already passed this task with this pull request. One PR cannot be evidence
        // that two people did the work, and no rubric judgment can establish otherwise -- so this
        // is rejected here rather than sent to the judge, which would happily pass it again.
        if (context.answerAlreadyClaimed) {
            return GradedResult(
                passed = false,
                score = 0.0,
                feedback = "That pull request has already been submitted by someone else for this task.",
                hint = "Submit a pull request you opened yourself.",
            )
        }

        return when (verification.type) {
            VerificationType.EXACT -> gradeExact(verification.canonicalAnswer ?: "", answer)
            VerificationType.ATTEST -> gradeAttest(answer)
            VerificationType.KNOWLEDGE ->
                gradeKnowledge(verification, context.lessonContent, answer, context.attemptNo)

            VerificationType.ARTIFACT -> gradeArtifact(verification, answer, context.githubLogin)
        }
    }

    /**
     * Rejects a pull request that cannot be attributed to the submitting user, or `null` to proceed.
     *
     * An artifact check claims "this person did this work", so it is only meaningful when the
     * submitter is known to be the PR's author. A user who has not declared a GitHub login is asked
     * for one rather than being graded on an unattributable PR -- the alternative, grading it
     * anyway, is what let a hire pass with a colleague's work.
     */
    private fun attributionFailure(githubLogin: String?, authorLogin: String?, prNumber: Int): GradedResult? {
        if (githubLogin.isNullOrBlank()) {
            return GradedResult(
                passed = false,
                score = 0.0,
                feedback = "We can't tell whether you opened this pull request.",
                hint = "Add your GitHub username to your profile, then submit again.",
            )
        }

        if (authorLogin == null || authorLogin != githubLogin) {
            return GradedResult(
                passed = false,
                score = 0.0,
                feedback = "PR #$prNumber was opened by someone else, so it can't verify your work.",
                hint = "Submit a pull request you opened yourself.",
            )
        }

        return null
    }

    /** Normalized (case/whitespace-insensitive) exact match -- mirrors the AI service's `grade_exact`. */
    private fun gradeExact(canonicalAnswer: String, answer: String): GradedResult {
        val normalizedAnswer = answer.trim().lowercase().replace(WHITESPACE, " ")
        val normalizedCanonical = canonicalAnswer.trim().lowercase().replace(WHITESPACE, " ")
        return if (normalizedAnswer.isNotEmpty() && normalizedAnswer == normalizedCanonical) {
            GradedResult(passed = true, score = 1.0, feedback = "Matches exactly.")
        } else {
            GradedResult(
                passed = false,
                score = 0.0,
                feedback = "Does not match the expected answer.",
                hint = "Check the exact wording expected for this step.",
            )
        }
    }

    /** Self-confirmation: a non-blank answer is logged as passed, not judged -- mirrors `grade_attest`. */
    private fun gradeAttest(answer: String): GradedResult =
        if (answer.isNotBlank()) {
            GradedResult(passed = true, score = 1.0, feedback = "Self-attested.")
        } else {
            GradedResult(passed = false, score = 0.0, feedback = "No attestation submitted.")
        }

    /**
     * Delegates to the AI service's LLM judge. Unlike [PhaseCheckService]'s fallback on AI
     * failure, there is no safe local approximation for rubric-based judging, so an unavailable
     * AI service surfaces a retryable `503` instead of a fabricated grade.
     */
    private suspend fun gradeKnowledge(
        verification: Verification,
        lessonContent: String,
        answer: String,
        attemptNo: Int,
    ): GradedResult {
        val rubric = verification.rubric
            ?: throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Verification ${verification.id} is type KNOWLEDGE but has no rubric configured",
            )
        val result = try {
            onboardingAiClient.gradeKnowledge(
                question = verification.prompt,
                rubric = rubric,
                evidence = lessonContent,
                answer = answer,
                attemptNo = attemptNo,
            )
        } catch (@Suppress("SwallowedException") e: OnboardingAiException) {
            logger.warn("AI knowledge grading unavailable: {}", e.message)
            throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Grading is temporarily unavailable, please try again",
            )
        }
        return GradedResult(
            passed = result.passed,
            score = result.score,
            feedback = result.feedback,
            hint = result.hint,
        )
    }

    /**
     * Gathers real GitHub state for a hire-submitted PR number, then delegates rubric judgment to
     * the AI service's `type: "artifact"` judge -- the highest-rigor rung of the ladder. Unlike
     * [gradeKnowledge], the AI service never sees GitHub itself, so an unparseable answer or a PR
     * that doesn't exist in the linked repository is graded locally as a fail without an AI call --
     * those aren't judgment calls, they're facts only Kotlin can observe.
     */
    @Suppress("ThrowsCount")
    private suspend fun gradeArtifact(
        verification: Verification,
        answer: String,
        githubLogin: String?,
    ): GradedResult {
        val prNumber = answer.trim().toIntOrNull()
            ?: return GradedResult(
                passed = false,
                score = 0.0,
                feedback = "Submit a PR number to verify this task.",
                hint = "Open a PR that addresses the task, then submit its number.",
            )
        val repositoryConnectionId = verification.repositoryConnectionId
            ?: throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Verification ${verification.id} is type ARTIFACT but has no repositoryConnectionId configured",
            )
        val rubric = verification.rubric
            ?: throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Verification ${verification.id} is type ARTIFACT but has no rubric configured",
            )

        val evidence = githubRepositoryApi.getPullRequestEvidence(repositoryConnectionId, prNumber)
            ?: return GradedResult(
                passed = false,
                score = 0.0,
                feedback = "No PR #$prNumber found in the linked repository.",
                hint = "Double-check the PR number and that it's on the linked repository.",
            )

        // Attribution, before any judgment: a PR somebody else opened is evidence that *they* did
        // the work. The judge cannot catch this -- it would look at a PR that genuinely satisfies
        // the rubric and pass it, for the wrong person.
        attributionFailure(githubLogin, evidence.authorLogin, prNumber)?.let { return it }

        val result = try {
            onboardingAiClient.gradeArtifact(
                taskDescription = verification.prompt,
                rubric = rubric,
                evidence = ArtifactEvidenceDto(
                    prTitle = evidence.title,
                    prBody = evidence.body,
                    prState = evidence.state,
                    filesChanged = evidence.filesChanged,
                    checksPassed = evidence.checksPassed,
                    commitMessages = evidence.commitMessages,
                    fileDiffs = evidence.fileDiffs.map {
                        FileDiffDto(
                            path = it.path,
                            additions = it.additions,
                            deletions = it.deletions,
                            patch = it.patch,
                            truncated = it.truncated,
                        )
                    },
                    omittedFileCount = evidence.omittedFileCount,
                ),
            )
        } catch (@Suppress("SwallowedException") e: OnboardingAiException) {
            logger.warn("AI artifact grading unavailable: {}", e.message)
            throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Grading is temporarily unavailable, please try again",
            )
        }

        return GradedResult(
            passed = result.passed,
            score = result.score,
            feedback = result.feedback,
            hint = result.hint,
        )
    }

    /**
     * Find-or-create write of the durable ledger, mirroring [AssessmentService]'s write pattern.
     *
     * Monotonic: passing a verification whose target level is below what the ledger already
     * records keeps the higher level ("never un-earns progress" applies to the ledger too) --
     * only the source still upgrades to [CompetencySource.VERIFIED], since the pass is proof at
     * at least that level.
     */
    private fun writeVerifiedCompetencyState(userId: UUID, competencyKey: String, level: String) {
        val rank = LEVEL_RANKS[level.trim().lowercase()] ?: 0
        val existing = userCompetencyStateRepository.findByUserIdAndCompetencyKey(userId, competencyKey)
        if (existing != null) {
            existing.level = maxOf(existing.level, rank)
            existing.source = CompetencySource.VERIFIED
            existing.updatedAt = Instant.now()
        } else {
            userCompetencyStateRepository.save(
                UserCompetencyState(
                    userId = userId,
                    competencyKey = competencyKey,
                    level = rank,
                    source = CompetencySource.VERIFIED,
                ),
            )
        }
    }

    @Suppress("ThrowsCount")
    private fun validateGradingConfig(request: UpsertVerificationRequest) {
        // An unrecognized level would rank to 0 on pass, which the projection never counts as
        // mastered -- the hire would pass the check without ever unlocking dependents. Reject it
        // here, at config time, instead of failing silently at grading time.
        if (LEVEL_RANKS[request.level.trim().lowercase()] == null) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "level must be one of: ${LEVEL_RANKS.keys.joinToString(", ")}",
            )
        }
        if (request.type == VerificationType.KNOWLEDGE && request.rubric.isNullOrBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "KNOWLEDGE verifications need a rubric")
        }
        if (request.type == VerificationType.EXACT && request.canonicalAnswer.isNullOrBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "EXACT verifications need a canonicalAnswer")
        }
        if (request.type == VerificationType.ARTIFACT) {
            if (request.rubric.isNullOrBlank()) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "ARTIFACT verifications need a rubric")
            }
            if (request.repositoryConnectionId == null) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ARTIFACT verifications need a repositoryConnectionId",
                )
            }
        }
    }

    private fun resolveUserId(authId: String): UUID =
        userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: $authId") }

    private companion object {
        val WHITESPACE = Regex("\\s+")

        // Aligned 1:1 with the AI SKILL_LEVELS (beginner..expert -> 1..4); unknown -> 0. Mirrors
        // AssessmentService.LEVEL_RANKS -- not shared, since both are 4 lines and belong to
        // different services' private grading concerns.
        val LEVEL_RANKS = mapOf(
            "beginner" to 1,
            "intermediate" to 2,
            "advanced" to 3,
            "expert" to 4,
        )
    }
}
