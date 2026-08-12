package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencyKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencySource
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ModuleStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.SkillAssessmentSessionStatus
import com.sprintstart.sprintstartbackend.onboarding.external.model.AssessmentHistoryEntrySchema
import com.sprintstart.sprintstartbackend.onboarding.external.model.AssessmentTargetsSchema
import com.sprintstart.sprintstartbackend.onboarding.external.model.AssessmentTurnRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.AssessmentTurnResponse
import com.sprintstart.sprintstartbackend.onboarding.external.model.CandidateCompetencySchema
import com.sprintstart.sprintstartbackend.onboarding.external.model.CandidateSignalSchema
import com.sprintstart.sprintstartbackend.onboarding.model.entity.SkillAssessmentSession
import com.sprintstart.sprintstartbackend.onboarding.model.entity.SkillAssessmentTurn
import com.sprintstart.sprintstartbackend.onboarding.model.entity.UserCompetencyState
import com.sprintstart.sprintstartbackend.onboarding.model.exceptions.OnboardingAiException
import com.sprintstart.sprintstartbackend.onboarding.model.response.assessment.AnswerAssessmentResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.assessment.StartAssessmentResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyModuleRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.SkillAssessmentSessionRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.UserCompetencyStateRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

/**
 * The turn-based adaptive skill-assessment session: starts/resumes the interview, forwards each
 * turn to the AI interviewer (Seam 1), and writes the final placement to the durable ledger
 * ([UserCompetencyState]).
 *
 * Per-project: a hire runs this interview once per project they are on, not once ever. Candidate
 * competencies are the [CompetencyKind.SKILL] keys that project's live
 * [com.sprintstart.sprintstartbackend.onboarding.model.entity.CompetencyModule]s teach, never the
 * whole global catalog. ⚠️ The placement it writes still lands on the *global* ledger — "earn
 * once, transfers across projects".
 */
@Suppress("TooManyFunctions")
@Service
class AssessmentService(
    private val onboardingAiClient: OnboardingAiClient,
    private val skillAssessmentSessionRepository: SkillAssessmentSessionRepository,
    private val competencyRepository: CompetencyRepository,
    private val competencyModuleRepository: CompetencyModuleRepository,
    private val userCompetencyStateRepository: UserCompetencyStateRepository,
    private val userApi: UserApi,
    private val githubHistoryPriorService: GithubHistoryPriorService,
    transactionManager: PlatformTransactionManager,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // ⚠️ The AI call is a long-running suspend operation and must not run inside a transaction --
    // it would pin a DB connection for its whole duration. The shape is read-tx -> AI -> write-tx.
    private val txTemplate = TransactionTemplate(transactionManager)
    private val readTxTemplate =
        TransactionTemplate(transactionManager).apply { isReadOnly = true }

    /**
     * Whether the authenticated user has ever completed an assessment session for this project.
     *
     * ⚠️ Scoped per project: completing it for one project says nothing about another.
     *
     * @throws ResponseStatusException 404 if no user exists for [authId].
     */
    fun hasCompletedAssessment(authId: String, projectId: UUID): Boolean {
        val userId = resolveUserId(authId)
        return skillAssessmentSessionRepository.existsByUserIdAndProjectIdAndStatus(
            userId,
            projectId,
            SkillAssessmentSessionStatus.COMPLETED,
        )
    }

    /**
     * Starts a new assessment for the authenticated user on [projectId], or resumes their
     * in-progress one for it.
     *
     * @return The session id and the question to show next, or `done=true` with no question if the
     * project has nothing configured to assess yet.
     */
    suspend fun startAssessment(authId: String, projectId: UUID): StartAssessmentResponse {
        val userId = resolveUserId(authId)

        // ⚠️ Reserve the session *before* the slow AI call: a second start issued while the first
        // is still generating would see nothing to resume and create its own.
        val reserved = withContext(Dispatchers.IO) {
            reserveSessionSerially(userId, projectId)
        }
        reserved.openQuestion?.let {
            return StartAssessmentResponse(sessionId = reserved.sessionId, question = it)
        }

        val candidates = withContext(Dispatchers.IO) {
            readTxTemplate.execute { loadCandidateCompetencies(projectId) }.orEmpty()
        }
        if (candidates.isEmpty()) {
            // Nothing this project teaches yet: an honest empty result, and finishing without
            // calling the AI avoids handing it an empty candidate list it cannot finish over.
            return withContext(Dispatchers.IO) {
                txTemplate.execute { completeWithNothingToAssess(reserved.sessionId) }!!
            }
        }
        val candidateSignal = withContext(Dispatchers.IO) { readTxTemplate.execute { loadCandidateSignal(userId) }!! }
        val aiResponse = runAssessTurn(
            AssessmentTurnRequest(
                candidateCompetencies = candidates,
                candidateSignal = candidateSignal,
                turn = 0,
                maxTurns = MAX_TURNS,
                mustFinish = false,
            ),
        )
        val question = aiResponse.question ?: throw badGateway("start")

        return withContext(Dispatchers.IO) {
            txTemplate.execute {
                openFirstTurn(reserved.sessionId, question, aiResponse.targets.orEmpty())
            }!!
        }
    }

    private data class ReservedSession(
        val sessionId: UUID,
        val openQuestion: String?,
    )

    /**
     * Reserves the hire's single in-progress session, serializing concurrent starts.
     *
     * ⚠️ **A short transaction is not a serialization point.** Under `READ COMMITTED` two starts
     * both read no in-progress session and both insert one. The lock is what serializes them, and
     * it is **striped** rather than one per hire so the map cannot grow without bound.
     *
     * ⚠️ **A unique index would be the better guard and is not available here**: it would have to
     * be partial (`WHERE status = 'IN_PROGRESS'`), Hibernate cannot express a partial index, and
     * this service builds its schema from the entities -- see [reserveSession].
     */
    private fun reserveSessionSerially(userId: UUID, projectId: UUID): ReservedSession {
        val stripe = RESERVATION_LOCKS[
            Math.floorMod(java.util.Objects.hash(userId, projectId), RESERVATION_LOCKS.size),
        ]
        return synchronized(stripe) {
            txTemplate.execute { reserveSession(userId, projectId) }!!
        }
    }

    /**
     * Returns the user's single in-progress session for [projectId], creating an empty one if they
     * have none.
     *
     * ⚠️ **Not safe to call concurrently** -- the read and the insert are not atomic together.
     * [reserveSessionSerially] is the only caller for that reason. A session reserved this way has
     * no turn yet -- [openFirstTurn] fills that in once the interviewer answers.
     */
    private fun reserveSession(userId: UUID, projectId: UUID): ReservedSession {
        val existing = skillAssessmentSessionRepository.findFirstByUserIdAndProjectIdAndStatusOrderByCreatedAtDesc(
            userId,
            projectId,
            SkillAssessmentSessionStatus.IN_PROGRESS,
        )
        if (existing != null) {
            return ReservedSession(existing.id, existing.turns.lastOrNull { it.answer == null }?.question)
        }

        val session = skillAssessmentSessionRepository.save(
            SkillAssessmentSession(userId = userId, projectId = projectId),
        )
        return ReservedSession(session.id, null)
    }

    /**
     * Finishes a reserved session immediately with no question and nothing assessed, because its
     * project has no live competency module to interview against yet.
     */
    private fun completeWithNothingToAssess(sessionId: UUID): StartAssessmentResponse {
        val session = skillAssessmentSessionRepository.findByIdOrNull(sessionId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Assessment session no longer exists")
        session.status = SkillAssessmentSessionStatus.COMPLETED
        session.updatedAt = Instant.now()
        return StartAssessmentResponse(sessionId = session.id, question = null, done = true)
    }

    /**
     * Writes the first question onto a reserved session, unless a racing start already did.
     *
     * Two concurrent starts can both reach the interviewer; only one question is kept, so both
     * callers see the same interview rather than one silently overwriting the other's turn.
     */
    private fun openFirstTurn(
        sessionId: UUID,
        question: String,
        targets: List<String>,
    ): StartAssessmentResponse {
        val session = skillAssessmentSessionRepository.findByIdOrNull(sessionId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Assessment session no longer exists")

        session.turns.lastOrNull { it.answer == null }?.let {
            return StartAssessmentResponse(sessionId = session.id, question = it.question)
        }

        session.turns.add(
            SkillAssessmentTurn(
                session = session,
                turnIndex = session.turns.size,
                question = question,
                targets = targets.toMutableList(),
            ),
        )
        session.updatedAt = Instant.now()
        return StartAssessmentResponse(sessionId = session.id, question = question)
    }

    /**
     * Submits the candidate's answer for the currently open turn and advances the interview.
     *
     * @return The next question, or `done=true` once the AI has returned a final placement.
     * @throws ResponseStatusException 404 if no session with this id belongs to the user; 409 if
     * the session has no open turn to answer.
     */
    suspend fun answerAssessment(
        authId: String,
        sessionId: UUID,
        answer: String,
    ): AnswerAssessmentResponse {
        val userId = resolveUserId(authId)

        val turnState = withContext(Dispatchers.IO) {
            readTxTemplate.execute {
                val session = loadOwnedSession(userId, sessionId)
                val openTurn = requireOpenTurn(session, sessionId)
                TurnState(
                    history = buildHistory(session, openTurn, answer),
                    targets = buildTargets(session),
                    nextTurnIndex = openTurn.turnIndex + 1,
                    candidates = loadCandidateCompetencies(session.projectId),
                )
            }!!
        }

        val mustFinish = turnState.nextTurnIndex >= MAX_TURNS - 1
        val aiResponse = runAssessTurn(
            AssessmentTurnRequest(
                candidateCompetencies = turnState.candidates,
                candidateSignal = withContext(Dispatchers.IO) {
                    readTxTemplate.execute { loadCandidateSignal(userId) }!!
                },
                history = turnState.history,
                targets = turnState.targets,
                turn = turnState.nextTurnIndex,
                maxTurns = MAX_TURNS,
                mustFinish = mustFinish,
            ),
        )

        return withContext(Dispatchers.IO) {
            txTemplate.execute {
                val session = loadOwnedSession(userId, sessionId)
                val openTurn = requireOpenTurn(session, sessionId)
                openTurn.answer = answer
                session.updatedAt = Instant.now()

                if (aiResponse.done) {
                    val validKeys = turnState.candidates.map { it.key }.toSet()
                    for (result in aiResponse.assessments.orEmpty()) {
                        if (result.key !in validKeys) continue
                        writeCompetencyState(userId, result.key, result.level, result.confidence)
                    }
                    session.status = SkillAssessmentSessionStatus.COMPLETED
                    AnswerAssessmentResponse(done = true, question = null)
                } else {
                    val question = aiResponse.question ?: throw badGateway("continue")
                    session.turns.add(
                        SkillAssessmentTurn(
                            session = session,
                            turnIndex = openTurn.turnIndex + 1,
                            question = question,
                            targets = aiResponse.targets.orEmpty().toMutableList(),
                        ),
                    )
                    AnswerAssessmentResponse(done = false, question = question)
                }
            }!!
        }
    }

    private data class TurnState(
        val history: List<AssessmentHistoryEntrySchema>,
        val targets: List<AssessmentTargetsSchema>,
        val nextTurnIndex: Int,
        val candidates: List<CandidateCompetencySchema>,
    )

    /**
     * What every past question set out to probe, per turn.
     *
     * ⚠️ Turns that targeted nothing are dropped rather than sent as empty lists — an empty list
     * reads as "probed these keys and found nothing", which is not what happened.
     */
    private fun buildTargets(session: SkillAssessmentSession): List<AssessmentTargetsSchema> =
        session.turns
            .filter { it.targets.isNotEmpty() }
            .map { AssessmentTargetsSchema(turn = it.turnIndex, keys = it.targets.toList()) }

    /**
     * The candidate's consented involvement prior, or an empty signal.
     *
     * Sent on every turn because the AI service is stateless: omitting it after turn 0 would
     * silently change how later turns are calibrated. Consent is re-checked on each read, so
     * withdrawing it takes effect immediately, mid-interview included.
     *
     * ⚠️ **Must be called inside a transaction.** `signals` is a lazy `@ElementCollection`, so
     * reading it on a detached prior throws `LazyInitializationException`. Both call sites wrap it
     * in [readTxTemplate], and those two are the only ones.
     */
    private fun loadCandidateSignal(userId: UUID): CandidateSignalSchema {
        val prior = githubHistoryPriorService.getPrior(userId) ?: return CandidateSignalSchema()
        return CandidateSignalSchema(signals = prior.signals.toMap())
    }

    /**
     * The [CompetencyKind.SKILL] competencies [projectId] actually teaches: the keys of its live
     * [com.sprintstart.sprintstartbackend.onboarding.model.entity.CompetencyModule]s. ⚠️ A project
     * with no live modules returns an empty list -- callers must treat that as nothing to assess,
     * never as a request to send an empty candidate set to the AI.
     */
    private fun loadCandidateCompetencies(projectId: UUID): List<CandidateCompetencySchema> {
        val taughtKeys = competencyModuleRepository
            .findAllByProjectIdAndStatus(projectId, ModuleStatus.ACTIVE)
            .map { it.competencyKey }
            .toSet()
        if (taughtKeys.isEmpty()) return emptyList()

        return competencyRepository
            .findAllByKeyIn(taughtKeys)
            .filter { it.kind == CompetencyKind.SKILL }
            .map { CandidateCompetencySchema(key = it.key, label = it.label, description = it.description.orEmpty()) }
    }

    private fun loadOwnedSession(userId: UUID, sessionId: UUID): SkillAssessmentSession {
        val session = skillAssessmentSessionRepository
            .findById(sessionId)
            .orElseThrow { notFound(sessionId) }
        if (session.userId != userId) throw notFound(sessionId)
        return session
    }

    private fun requireOpenTurn(session: SkillAssessmentSession, sessionId: UUID): SkillAssessmentTurn =
        session.turns.lastOrNull { it.answer == null }
            ?: throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "No open turn to answer for session: $sessionId",
            )

    /** Flattens a session's turns into AI history, substituting [pendingAnswer] on [openTurn]. */
    private fun buildHistory(
        session: SkillAssessmentSession,
        openTurn: SkillAssessmentTurn,
        pendingAnswer: String,
    ): List<AssessmentHistoryEntrySchema> =
        session.turns.flatMap { turn ->
            val answer = if (turn.id == openTurn.id) pendingAnswer else turn.answer
            buildList {
                add(AssessmentHistoryEntrySchema(role = "assistant", content = turn.question))
                if (answer != null) add(AssessmentHistoryEntrySchema(role = "user", content = answer))
            }
        }

    /**
     * ⚠️ The ledger write is monotonic: a self-reported placement never overwrites a
     * [CompetencySource.VERIFIED] entry, and a re-assessment never lowers an already-recorded
     * level.
     */
    private fun writeCompetencyState(userId: UUID, competencyKey: String, level: String, confidence: Double) {
        val rank = placementRank(level, confidence)
        val existing = userCompetencyStateRepository.findByUserIdAndCompetencyKey(userId, competencyKey)
        if (existing != null) {
            if (existing.source == CompetencySource.VERIFIED) return
            existing.level = maxOf(existing.level, rank)
            existing.source = CompetencySource.ASSESSED
            existing.updatedAt = Instant.now()
        } else {
            userCompetencyStateRepository.save(
                UserCompetencyState(
                    userId = userId,
                    competencyKey = competencyKey,
                    level = rank,
                    source = CompetencySource.ASSESSED,
                ),
            )
        }
    }

    /**
     * The rank a placement is allowed to record.
     *
     * ⚠️ Two different things both record `0`, and they are not interchangeable:
     *
     * - **`none`** -- the hire *told* the interviewer they have never used it. Usually a
     *   *confident* answer, so the confidence floor below never catches it.
     * - **low confidence** -- the interviewer could not tell.
     *
     * `0` is a real state elsewhere in the ledger (known-but-unplaced, filtered out of matching),
     * so this records that the assessment happened without claiming a skill.
     */
    private fun placementRank(level: String, confidence: Double): Int {
        if (confidence < MIN_PLACEMENT_CONFIDENCE) {
            return 0
        }
        return LEVEL_RANKS[level.trim().lowercase()] ?: 0
    }

    /**
     * Runs one AI interviewer turn, translating a transport-level AI failure into a retryable
     * 503 instead of letting [OnboardingAiException] surface as an opaque 500.
     *
     * ⚠️ The AI service answers with its own 503 rather than a hollow `done=true` when it is too
     * early to legitimately finish, so this path also carries "please retry", never a finished
     * assessment nothing backed.
     */
    private suspend fun runAssessTurn(request: AssessmentTurnRequest): AssessmentTurnResponse =
        try {
            onboardingAiClient.assessTurn(request)
        } catch (@Suppress("SwallowedException") e: OnboardingAiException) {
            logger.warn("Assessment turn unavailable: {}", e.message)
            throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Assessment is temporarily unavailable, please try again",
            )
        }

    private fun resolveUserId(authId: String): UUID =
        userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: $authId") }

    private fun notFound(sessionId: UUID) =
        ResponseStatusException(HttpStatus.NOT_FOUND, "No assessment session found with id: $sessionId")

    private fun badGateway(phase: String) =
        ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service returned no question to $phase the assessment")

    private companion object {
        const val MAX_TURNS = 6

        /**
         * Stripes guarding session reservation. Fixed-size so the set of locks is bounded no
         * matter how many hires and projects exist; see [reserveSessionSerially].
         */
        val RESERVATION_LOCKS: List<Any> = List(32) { Any() }

        // Below this, a placement records 0 rather than the level it guessed. The interviewer uses
        // low confidence precisely to mean "no evidence either way".
        const val MIN_PLACEMENT_CONFIDENCE = 0.4

        // Aligned 1:1 with the AI SKILL_LEVELS (beginner..expert -> 1..4); unknown -> 0.
        // "none" is listed explicitly rather than left to fall through to the lookup's default.
        val LEVEL_RANKS = mapOf(
            NO_EXPERIENCE to 0,
            "beginner" to 1,
            "intermediate" to 2,
            "advanced" to 3,
            "expert" to 4,
        )

        /** What the interviewer sends when a hire says they have never used something. */
        const val NO_EXPERIENCE = "none"
    }
}
