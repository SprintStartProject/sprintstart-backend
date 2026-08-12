package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencyKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencySource
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ModuleStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.SkillAssessmentSessionStatus
import com.sprintstart.sprintstartbackend.onboarding.external.model.AssessmentResultSchema
import com.sprintstart.sprintstartbackend.onboarding.external.model.AssessmentTurnRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.AssessmentTurnResponse
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Competency
import com.sprintstart.sprintstartbackend.onboarding.model.entity.CompetencyModule
import com.sprintstart.sprintstartbackend.onboarding.model.entity.GithubHistoryPrior
import com.sprintstart.sprintstartbackend.onboarding.model.entity.SkillAssessmentSession
import com.sprintstart.sprintstartbackend.onboarding.model.entity.SkillAssessmentTurn
import com.sprintstart.sprintstartbackend.onboarding.model.entity.UserCompetencyState
import com.sprintstart.sprintstartbackend.onboarding.model.exceptions.OnboardingAiException
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyModuleRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.SkillAssessmentSessionRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.UserCompetencyStateRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssessmentServiceTest {
    private val onboardingAiClient: OnboardingAiClient = mockk()
    private val skillAssessmentSessionRepository: SkillAssessmentSessionRepository = mockk()
    private val competencyRepository: CompetencyRepository = mockk()
    private val competencyModuleRepository: CompetencyModuleRepository = mockk()
    private val userCompetencyStateRepository: UserCompetencyStateRepository = mockk()
    private val userApi: UserApi = mockk()
    private val transactionManager: PlatformTransactionManager = mockk(relaxed = true)
    private val githubHistoryPriorService: GithubHistoryPriorService = mockk()
    private val service =
        AssessmentService(
            onboardingAiClient,
            skillAssessmentSessionRepository,
            competencyRepository,
            competencyModuleRepository,
            userCompetencyStateRepository,
            userApi,
            githubHistoryPriorService,
            transactionManager,
        )

    private val userId = UUID.randomUUID()
    private val projectId = UUID.randomUUID()
    private val authId = "auth|test-user"
    private val kotlinCompetency = Competency(key = "kotlin", label = "Kotlin", kind = CompetencyKind.SKILL)

    private fun setUpUser() {
        // Most tests are about the interview itself; the consented involvement prior defaults to
        // "not consented", which is the common case.
        every { githubHistoryPriorService.getPrior(userId) } returns null
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
    }

    /** Wires the project to have a live module for each competency, so it is a valid candidate. */
    private fun setUpCandidates(vararg competencies: Competency) {
        val keys = competencies.map { it.key }.toSet()
        every {
            competencyModuleRepository.findAllByProjectIdAndStatus(projectId, ModuleStatus.ACTIVE)
        } returns keys.map {
            CompetencyModule(
                competencyKey = it,
                projectId = projectId,
                version = 1,
                status = ModuleStatus.ACTIVE,
                title = it,
            )
        }
        every { competencyRepository.findAllByKeyIn(keys) } returns competencies.toList()
    }

    /** Wires the project to have no live modules at all -- nothing to assess. */
    private fun setUpNoCandidates() {
        every {
            competencyModuleRepository.findAllByProjectIdAndStatus(projectId, ModuleStatus.ACTIVE)
        } returns emptyList()
    }

    @Nested
    inner class HasCompletedAssessment {
        @Test
        fun `returns true when the user has a completed session for this project`() {
            setUpUser()
            every {
                skillAssessmentSessionRepository.existsByUserIdAndProjectIdAndStatus(
                    userId,
                    projectId,
                    SkillAssessmentSessionStatus.COMPLETED,
                )
            } returns true

            assertTrue(service.hasCompletedAssessment(authId, projectId))
        }

        @Test
        fun `returns false when the user has never completed a session for this project`() {
            setUpUser()
            every {
                skillAssessmentSessionRepository.existsByUserIdAndProjectIdAndStatus(
                    userId,
                    projectId,
                    SkillAssessmentSessionStatus.COMPLETED,
                )
            } returns false

            assertEquals(false, service.hasCompletedAssessment(authId, projectId))
        }
    }

    @Nested
    inner class StartAssessment {
        @Test
        fun `creates a new session and first turn when none is in progress`() = runTest {
            setUpUser()
            every {
                skillAssessmentSessionRepository.findFirstByUserIdAndProjectIdAndStatusOrderByCreatedAtDesc(
                    userId,
                    projectId,
                    SkillAssessmentSessionStatus.IN_PROGRESS,
                )
            } returns null
            setUpCandidates(kotlinCompetency)
            coEvery { onboardingAiClient.assessTurn(any()) } returns
                AssessmentTurnResponse(done = false, question = "Walk me through a Kotlin null-safety pitfall.")
            val savedSlot = slot<SkillAssessmentSession>()
            every { skillAssessmentSessionRepository.save(capture(savedSlot)) } answers { savedSlot.captured }
            // The session is reserved first, then re-read to write its first turn onto.
            every { skillAssessmentSessionRepository.findById(any()) } answers { Optional.of(savedSlot.captured) }

            val result = service.startAssessment(authId, projectId)

            assertEquals("Walk me through a Kotlin null-safety pitfall.", result.question)
            assertEquals(false, result.done)
            assertEquals(savedSlot.captured.id, result.sessionId)
            assertEquals(projectId, savedSlot.captured.projectId)
            assertEquals(1, savedSlot.captured.turns.size)
            assertEquals(0, savedSlot.captured.turns[0].turnIndex)
            assertNull(savedSlot.captured.turns[0].answer)
        }

        /**
         * ⚠️ The bug this exists to stop: one hire, two interviews running at once.
         *
         * Two starts arrive together -- `<React.StrictMode>` double-invokes the effect that calls
         * start, so this is the normal case in development, not a rare race. Both read no
         * in-progress session and, unserialized, both insert one.
         *
         * A short write transaction does not prevent it: under `READ COMMITTED` each sees the
         * state from before the other committed.
         */
        @Test
        fun `two starts arriving together create one session, not two`() {
            setUpUser()
            setUpCandidates(kotlinCompetency)
            coEvery { onboardingAiClient.assessTurn(any()) } returns
                AssessmentTurnResponse(done = false, question = "Walk me through a Kotlin null-safety pitfall.")

            // The repository behaves like the database: a reader sees whatever has been saved so
            // far, and `save` records it. Serialization has to come from the service.
            val stored = java.util.concurrent.CopyOnWriteArrayList<SkillAssessmentSession>()
            every {
                skillAssessmentSessionRepository.findFirstByUserIdAndProjectIdAndStatusOrderByCreatedAtDesc(
                    userId,
                    projectId,
                    SkillAssessmentSessionStatus.IN_PROGRESS,
                )
            } answers { stored.lastOrNull() }
            every { skillAssessmentSessionRepository.save(any()) } answers {
                val session = firstArg<SkillAssessmentSession>()
                if (stored.none { it.id == session.id }) stored.add(session)
                session
            }
            every { skillAssessmentSessionRepository.findById(any()) } answers {
                Optional.ofNullable(stored.firstOrNull { it.id == firstArg<UUID>() })
            }

            val barrier = java.util.concurrent.CyclicBarrier(2)
            val results = java.util.concurrent.CopyOnWriteArrayList<UUID>()
            val threads = (1..2).map {
                Thread {
                    barrier.await()
                    runBlocking { results.add(service.startAssessment(authId, projectId).sessionId) }
                }
            }
            threads.forEach { it.start() }
            threads.forEach { it.join() }

            assertEquals(1, stored.size, "one hire on one project has exactly one session")
            assertEquals(1, results.toSet().size, "both starts land on the same session")
        }

        @Test
        fun `reuses the in-progress session instead of creating a second one`() = runTest {
            setUpUser()
            val reserved = SkillAssessmentSession(userId = userId, projectId = projectId)
            every {
                skillAssessmentSessionRepository.findFirstByUserIdAndProjectIdAndStatusOrderByCreatedAtDesc(
                    userId,
                    projectId,
                    SkillAssessmentSessionStatus.IN_PROGRESS,
                )
            } returns reserved
            every { skillAssessmentSessionRepository.findById(reserved.id) } returns Optional.of(reserved)
            setUpCandidates(kotlinCompetency)
            coEvery { onboardingAiClient.assessTurn(any()) } returns
                AssessmentTurnResponse(done = false, question = "q")

            val result = service.startAssessment(authId, projectId)

            // The session is reserved before the AI call, so a start racing another one joins it
            // rather than creating the stranded duplicates this replaced.
            assertEquals(reserved.id, result.sessionId)
            verify(exactly = 0) { skillAssessmentSessionRepository.save(any()) }
        }

        @Test
        fun `keeps the question a racing start already wrote`() = runTest {
            setUpUser()
            val reserved = SkillAssessmentSession(userId = userId, projectId = projectId)
            reserved.turns.add(
                SkillAssessmentTurn(session = reserved, turnIndex = 0, question = "the first question"),
            )
            every {
                skillAssessmentSessionRepository.findFirstByUserIdAndProjectIdAndStatusOrderByCreatedAtDesc(
                    userId,
                    projectId,
                    SkillAssessmentSessionStatus.IN_PROGRESS,
                )
            } returns reserved

            val result = service.startAssessment(authId, projectId)

            assertEquals("the first question", result.question)
            coVerify(exactly = 0) { onboardingAiClient.assessTurn(any()) }
        }

        /**
         * ⚠️ Regression: this threw a 500 on the hire's own landing page, for months.
         *
         * `GithubHistoryPrior.signals` is a lazy `@ElementCollection`, and the prior was read
         * *outside* any transaction — so the moment a hire actually had a consented prior, reading
         * it threw `LazyInitializationException` and `POST /me/assessment/start` returned 500.
         *
         * ⚠️ **The suite missed it because the default fixture returns `null`**, which short-circuits
         * before `signals` is ever touched, and the one test that supplies a prior supplies a plain
         * in-memory map that cannot fail to initialise. The feature was broken for exactly the
         * hires it was built for, and every test passed.
         *
         * So this asserts the *structural* property a mock cannot fake: the read happens while a
         * transaction is open.
         */
        @Test
        fun `reads the consented prior inside a transaction`() = runTest {
            setUpUser()
            var inTransaction = false
            var readInsideTransaction: Boolean? = null
            every { transactionManager.getTransaction(any()) } answers {
                inTransaction = true
                mockk(relaxed = true)
            }
            every { transactionManager.commit(any()) } answers { inTransaction = false }
            every { transactionManager.rollback(any()) } answers { inTransaction = false }
            every { githubHistoryPriorService.getPrior(userId) } answers {
                readInsideTransaction = inTransaction
                GithubHistoryPrior(userId = userId, signals = mutableMapOf("repo:owner/api" to 9))
            }
            every {
                skillAssessmentSessionRepository.findFirstByUserIdAndProjectIdAndStatusOrderByCreatedAtDesc(
                    userId,
                    projectId,
                    SkillAssessmentSessionStatus.IN_PROGRESS,
                )
            } returns null
            setUpCandidates(kotlinCompetency)
            coEvery { onboardingAiClient.assessTurn(any()) } returns
                AssessmentTurnResponse(done = false, question = "q")
            val savedSlot = slot<SkillAssessmentSession>()
            every { skillAssessmentSessionRepository.save(capture(savedSlot)) } answers { savedSlot.captured }
            every { skillAssessmentSessionRepository.findById(any()) } answers { Optional.of(savedSlot.captured) }

            service.startAssessment(authId, projectId)

            // Its signals are a lazy @ElementCollection: read detached, this is a 500.
            assertEquals(true, readInsideTransaction, "the prior must be read inside a transaction")
        }

        @Test
        fun `sends the consented involvement prior to the interviewer`() = runTest {
            setUpUser()
            val prior = GithubHistoryPrior(
                userId = userId,
                signals = mutableMapOf("repo:owner/api" to 9, "type:PULL_REQUEST" to 9),
            )
            every { githubHistoryPriorService.getPrior(userId) } returns prior
            every {
                skillAssessmentSessionRepository.findFirstByUserIdAndProjectIdAndStatusOrderByCreatedAtDesc(
                    userId,
                    projectId,
                    SkillAssessmentSessionStatus.IN_PROGRESS,
                )
            } returns null
            setUpCandidates(kotlinCompetency)
            val request = slot<AssessmentTurnRequest>()
            coEvery { onboardingAiClient.assessTurn(capture(request)) } returns
                AssessmentTurnResponse(done = false, question = "q")
            val savedSlot = slot<SkillAssessmentSession>()
            every { skillAssessmentSessionRepository.save(capture(savedSlot)) } answers { savedSlot.captured }
            // The session is reserved first, then re-read to write its first turn onto.
            every { skillAssessmentSessionRepository.findById(any()) } answers { Optional.of(savedSlot.captured) }

            service.startAssessment(authId, projectId)

            assertEquals(9, request.captured.candidateSignal.signals["repo:owner/api"])
        }

        @Test
        fun `sends an empty prior when the user has not consented`() = runTest {
            setUpUser()
            every {
                skillAssessmentSessionRepository.findFirstByUserIdAndProjectIdAndStatusOrderByCreatedAtDesc(
                    userId,
                    projectId,
                    SkillAssessmentSessionStatus.IN_PROGRESS,
                )
            } returns null
            setUpCandidates(kotlinCompetency)
            val request = slot<AssessmentTurnRequest>()
            coEvery { onboardingAiClient.assessTurn(capture(request)) } returns
                AssessmentTurnResponse(done = false, question = "q")
            val savedSlot = slot<SkillAssessmentSession>()
            every { skillAssessmentSessionRepository.save(capture(savedSlot)) } answers { savedSlot.captured }
            // The session is reserved first, then re-read to write its first turn onto.
            every { skillAssessmentSessionRepository.findById(any()) } answers { Optional.of(savedSlot.captured) }

            service.startAssessment(authId, projectId)

            // Not consenting must be indistinguishable from having no history, never an error.
            val signals = request.captured.candidateSignal.signals
            assertTrue(signals.isEmpty())
        }

        @Test
        fun `resumes an existing in-progress session instead of creating a duplicate`() = runTest {
            setUpUser()
            val existing = SkillAssessmentSession(userId = userId, projectId = projectId)
            existing.turns.add(SkillAssessmentTurn(session = existing, turnIndex = 0, question = "First question?"))
            every {
                skillAssessmentSessionRepository.findFirstByUserIdAndProjectIdAndStatusOrderByCreatedAtDesc(
                    userId,
                    projectId,
                    SkillAssessmentSessionStatus.IN_PROGRESS,
                )
            } returns existing

            val result = service.startAssessment(authId, projectId)

            assertEquals(existing.id, result.sessionId)
            assertEquals("First question?", result.question)
            coVerify(exactly = 0) { onboardingAiClient.assessTurn(any()) }
            verify(exactly = 0) { skillAssessmentSessionRepository.save(any()) }
        }

        @Test
        fun `records what the first question probed`() = runTest {
            setUpUser()
            every {
                skillAssessmentSessionRepository.findFirstByUserIdAndProjectIdAndStatusOrderByCreatedAtDesc(
                    userId,
                    projectId,
                    SkillAssessmentSessionStatus.IN_PROGRESS,
                )
            } returns null
            setUpCandidates(kotlinCompetency)
            coEvery { onboardingAiClient.assessTurn(any()) } returns AssessmentTurnResponse(
                done = false,
                question = "q",
                targets = listOf("kotlin", "jpa-persistence"),
            )
            val savedSlot = slot<SkillAssessmentSession>()
            every { skillAssessmentSessionRepository.save(capture(savedSlot)) } answers { savedSlot.captured }
            every { skillAssessmentSessionRepository.findById(any()) } answers { Optional.of(savedSlot.captured) }

            service.startAssessment(authId, projectId)

            assertEquals(listOf("kotlin", "jpa-persistence"), savedSlot.captured.turns[0].targets)
        }

        @Test
        fun `surfaces an unavailable AI service as a retryable 503, not a raw failure`() = runTest {
            setUpUser()
            every {
                skillAssessmentSessionRepository.findFirstByUserIdAndProjectIdAndStatusOrderByCreatedAtDesc(
                    userId,
                    projectId,
                    SkillAssessmentSessionStatus.IN_PROGRESS,
                )
            } returns null
            setUpCandidates(kotlinCompetency)
            val savedSlot = slot<SkillAssessmentSession>()
            every { skillAssessmentSessionRepository.save(capture(savedSlot)) } answers { savedSlot.captured }
            coEvery { onboardingAiClient.assessTurn(any()) } throws
                OnboardingAiException(503, "", "Failed to run assessment turn (HTTP 503): ")

            val exception = assertThrows<ResponseStatusException> { service.startAssessment(authId, projectId) }

            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.statusCode)
        }

        @Test
        fun `finishes immediately with no question when the project has no live module yet`() = runTest {
            setUpUser()
            every {
                skillAssessmentSessionRepository.findFirstByUserIdAndProjectIdAndStatusOrderByCreatedAtDesc(
                    userId,
                    projectId,
                    SkillAssessmentSessionStatus.IN_PROGRESS,
                )
            } returns null
            setUpNoCandidates()
            val savedSlot = slot<SkillAssessmentSession>()
            every { skillAssessmentSessionRepository.save(capture(savedSlot)) } answers { savedSlot.captured }
            every { skillAssessmentSessionRepository.findById(any()) } answers { Optional.of(savedSlot.captured) }

            val result = service.startAssessment(authId, projectId)

            assertTrue(result.done)
            assertNull(result.question)
            assertEquals(SkillAssessmentSessionStatus.COMPLETED, savedSlot.captured.status)
            coVerify(exactly = 0) { onboardingAiClient.assessTurn(any()) }
        }
    }

    @Nested
    inner class AssessmentCoverage {
        @Test
        fun `sends every past question's targets so the interviewer can be held to covering them`() = runTest {
            setUpUser()
            val session = SkillAssessmentSession(userId = userId, projectId = projectId)
            session.turns.add(
                SkillAssessmentTurn(
                    session = session,
                    turnIndex = 0,
                    question = "Q0",
                    answer = "a0",
                    targets = mutableListOf("kotlin"),
                ),
            )
            session.turns.add(
                SkillAssessmentTurn(
                    session = session,
                    turnIndex = 1,
                    question = "Q1",
                    targets = mutableListOf("jpa-persistence"),
                ),
            )
            every { skillAssessmentSessionRepository.findById(session.id) } returns Optional.of(session)
            setUpCandidates(kotlinCompetency)
            val request = slot<AssessmentTurnRequest>()
            coEvery { onboardingAiClient.assessTurn(capture(request)) } returns
                AssessmentTurnResponse(done = false, question = "Q2", targets = listOf("testing"))

            service.answerAssessment(authId, session.id, "a1")

            assertEquals(listOf(0, 1), request.captured.targets.map { it.turn })
            assertEquals(listOf("kotlin"), request.captured.targets[0].keys)
            assertEquals(listOf("jpa-persistence"), request.captured.targets[1].keys)
        }

        @Test
        fun `omits turns that recorded no targets rather than sending them as probed-but-empty`() = runTest {
            setUpUser()
            // A session started before targets were recorded: absent is the honest reading, and it
            // makes the interviewer cover more rather than less.
            val session = SkillAssessmentSession(userId = userId, projectId = projectId)
            session.turns.add(
                SkillAssessmentTurn(session = session, turnIndex = 0, question = "Q0", answer = "a0"),
            )
            session.turns.add(
                SkillAssessmentTurn(
                    session = session,
                    turnIndex = 1,
                    question = "Q1",
                    targets = mutableListOf("kotlin"),
                ),
            )
            every { skillAssessmentSessionRepository.findById(session.id) } returns Optional.of(session)
            setUpCandidates(kotlinCompetency)
            val request = slot<AssessmentTurnRequest>()
            coEvery { onboardingAiClient.assessTurn(capture(request)) } returns
                AssessmentTurnResponse(done = false, question = "Q2")

            service.answerAssessment(authId, session.id, "a1")

            assertEquals(listOf(1), request.captured.targets.map { it.turn })
        }

        @Test
        fun `records what each following question probed`() = runTest {
            setUpUser()
            val session = SkillAssessmentSession(userId = userId, projectId = projectId)
            session.turns.add(SkillAssessmentTurn(session = session, turnIndex = 0, question = "Q0"))
            every { skillAssessmentSessionRepository.findById(session.id) } returns Optional.of(session)
            setUpCandidates(kotlinCompetency)
            coEvery { onboardingAiClient.assessTurn(any()) } returns
                AssessmentTurnResponse(done = false, question = "Q1", targets = listOf("testing"))

            service.answerAssessment(authId, session.id, "a0")

            assertEquals(listOf("testing"), session.turns[1].targets)
        }
    }

    @Nested
    inner class AnswerAssessment {
        private fun sessionWithOpenTurn(turnIndex: Int = 0, question: String = "Q$turnIndex"): SkillAssessmentSession {
            val session = SkillAssessmentSession(userId = userId, projectId = projectId)
            session.turns.add(SkillAssessmentTurn(session = session, turnIndex = turnIndex, question = question))
            return session
        }

        @Test
        fun `persists the answer and advances to the next question when not done`() = runTest {
            setUpUser()
            val session = sessionWithOpenTurn()
            every { skillAssessmentSessionRepository.findById(session.id) } returns Optional.of(session)
            setUpCandidates(kotlinCompetency)
            coEvery { onboardingAiClient.assessTurn(any()) } returns
                AssessmentTurnResponse(done = false, question = "Second question?")

            val result = service.answerAssessment(authId, session.id, "my answer")

            assertEquals(false, result.done)
            assertEquals("Second question?", result.question)
            assertEquals(2, session.turns.size)
            assertEquals("my answer", session.turns[0].answer)
            assertEquals("Second question?", session.turns[1].question)
            assertNull(session.turns[1].answer)
        }

        @Test
        fun `writes UserCompetencyState and completes the session when done`() = runTest {
            setUpUser()
            val session = sessionWithOpenTurn()
            every { skillAssessmentSessionRepository.findById(session.id) } returns Optional.of(session)
            setUpCandidates(kotlinCompetency)
            coEvery { onboardingAiClient.assessTurn(any()) } returns
                AssessmentTurnResponse(
                    done = true,
                    assessments = listOf(
                        AssessmentResultSchema(
                            key = "kotlin",
                            level = "advanced",
                            confidence = 0.8,
                            evidence = "Discussed null-safety tradeoffs unprompted.",
                        ),
                    ),
                )
            every { userCompetencyStateRepository.findByUserIdAndCompetencyKey(userId, "kotlin") } returns null
            val savedSlot = slot<UserCompetencyState>()
            every { userCompetencyStateRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

            val result = service.answerAssessment(authId, session.id, "my answer")

            assertTrue(result.done)
            assertNull(result.question)
            assertEquals(SkillAssessmentSessionStatus.COMPLETED, session.status)
            assertEquals("kotlin", savedSlot.captured.competencyKey)
            assertEquals(3, savedSlot.captured.level)
            assertEquals(CompetencySource.ASSESSED, savedSlot.captured.source)
        }

        @Test
        fun `records level 0 when the interviewer has no confidence in the placement`() = runTest {
            setUpUser()
            val session = sessionWithOpenTurn()
            every { skillAssessmentSessionRepository.findById(session.id) } returns Optional.of(session)
            setUpCandidates(kotlinCompetency)
            coEvery { onboardingAiClient.assessTurn(any()) } returns
                AssessmentTurnResponse(
                    done = true,
                    assessments = listOf(
                        AssessmentResultSchema(
                            key = "kotlin",
                            level = "beginner",
                            confidence = 0.1,
                            evidence = "Candidate said they did not know.",
                        ),
                    ),
                )
            every { userCompetencyStateRepository.findByUserIdAndCompetencyKey(userId, "kotlin") } returns null
            val saved = slot<UserCompetencyState>()
            every { userCompetencyStateRepository.save(capture(saved)) } answers { saved.captured }

            service.answerAssessment(authId, session.id, "i dont know, i am a beginner")

            // "I don't know" reaches us as beginner/low-confidence. Recording rank 1 would credit
            // the hire with a competency they just told us they lack -- and the ledger is monotonic.
            assertEquals(0, saved.captured.level)
        }

        /**
         * ⚠️ The bug this exists to stop: a hire who says they have never used something being
         * recorded as having it.
         *
         * The confidence floor above does not catch this, and cannot. "I have never used Next.js"
         * is a *clear* answer, so the interviewer reports it with **high** confidence -- the
         * plainer the disclaimer, the surer the model, and the further it is from the floor. A
         * hire really did end up with `nextjs` at level 1, and the buddy then told them they had
         * "already shown Next.js at level 1".
         *
         * `none` is what makes the placement honest at the value instead of relying on a
         * downstream floor to rescue it.
         */
        @Test
        fun `records level 0 when the hire says they have never used it`() = runTest {
            setUpUser()
            val session = sessionWithOpenTurn()
            every { skillAssessmentSessionRepository.findById(session.id) } returns Optional.of(session)
            setUpCandidates(kotlinCompetency)
            coEvery { onboardingAiClient.assessTurn(any()) } returns
                AssessmentTurnResponse(
                    done = true,
                    assessments = listOf(
                        AssessmentResultSchema(
                            key = "kotlin",
                            level = "none",
                            confidence = 0.95,
                            evidence = "Candidate says they have never used it.",
                        ),
                    ),
                )
            every { userCompetencyStateRepository.findByUserIdAndCompetencyKey(userId, "kotlin") } returns null
            val saved = slot<UserCompetencyState>()
            every { userCompetencyStateRepository.save(capture(saved)) } answers { saved.captured }

            service.answerAssessment(authId, session.id, "i have never used kotlin")

            assertEquals(0, saved.captured.level)
        }

        @Test
        fun `never overwrites a VERIFIED ledger entry with a placement result`() = runTest {
            setUpUser()
            val session = sessionWithOpenTurn()
            val verified = UserCompetencyState(
                userId = userId,
                competencyKey = "kotlin",
                level = 2,
                source = CompetencySource.VERIFIED,
            )
            every { skillAssessmentSessionRepository.findById(session.id) } returns Optional.of(session)
            setUpCandidates(kotlinCompetency)
            coEvery { onboardingAiClient.assessTurn(any()) } returns
                AssessmentTurnResponse(
                    done = true,
                    assessments = listOf(
                        AssessmentResultSchema(key = "kotlin", level = "beginner", confidence = 0.8),
                    ),
                )
            every { userCompetencyStateRepository.findByUserIdAndCompetencyKey(userId, "kotlin") } returns verified

            service.answerAssessment(authId, session.id, "my answer")

            assertEquals(2, verified.level)
            assertEquals(CompetencySource.VERIFIED, verified.source)
            verify(exactly = 0) { userCompetencyStateRepository.save(any()) }
        }

        @Test
        fun `never lowers an existing assessed level on re-assessment`() = runTest {
            setUpUser()
            val session = sessionWithOpenTurn()
            val assessed = UserCompetencyState(
                userId = userId,
                competencyKey = "kotlin",
                level = 4,
                source = CompetencySource.ASSESSED,
            )
            every { skillAssessmentSessionRepository.findById(session.id) } returns Optional.of(session)
            setUpCandidates(kotlinCompetency)
            coEvery { onboardingAiClient.assessTurn(any()) } returns
                AssessmentTurnResponse(
                    done = true,
                    assessments = listOf(
                        AssessmentResultSchema(key = "kotlin", level = "beginner", confidence = 0.8),
                    ),
                )
            every { userCompetencyStateRepository.findByUserIdAndCompetencyKey(userId, "kotlin") } returns assessed

            service.answerAssessment(authId, session.id, "my answer")

            assertEquals(4, assessed.level)
            verify(exactly = 0) { userCompetencyStateRepository.save(any()) }
        }

        @Test
        fun `drops an assessment key outside the candidate set`() = runTest {
            setUpUser()
            val session = sessionWithOpenTurn()
            every { skillAssessmentSessionRepository.findById(session.id) } returns Optional.of(session)
            setUpCandidates(kotlinCompetency)
            coEvery { onboardingAiClient.assessTurn(any()) } returns
                AssessmentTurnResponse(
                    done = true,
                    assessments = listOf(
                        AssessmentResultSchema(key = "kotlin", level = "advanced", confidence = 0.8),
                        AssessmentResultSchema(key = "rust", level = "expert", confidence = 0.9),
                    ),
                )
            every { userCompetencyStateRepository.findByUserIdAndCompetencyKey(userId, "kotlin") } returns null
            every { userCompetencyStateRepository.save(any()) } answers { firstArg() }

            service.answerAssessment(authId, session.id, "my answer")

            verify(exactly = 1) { userCompetencyStateRepository.save(any()) }
        }

        @Test
        fun `sets must_finish on the final allowed turn`() = runTest {
            setUpUser()
            // MAX_TURNS = 6 (indices 0..5); turns 0..3 answered, turn 4 open -> nextTurnIndex = 5,
            // the last allowed index, so must_finish should flip to true.
            val session = SkillAssessmentSession(userId = userId, projectId = projectId)
            repeat(4) { i ->
                session.turns.add(
                    SkillAssessmentTurn(session = session, turnIndex = i, question = "Q$i", answer = "A$i"),
                )
            }
            session.turns.add(SkillAssessmentTurn(session = session, turnIndex = 4, question = "Q4"))
            every { skillAssessmentSessionRepository.findById(session.id) } returns Optional.of(session)
            setUpCandidates(kotlinCompetency)
            val requestSlot = slot<AssessmentTurnRequest>()
            coEvery { onboardingAiClient.assessTurn(capture(requestSlot)) } returns
                AssessmentTurnResponse(done = true, assessments = emptyList())
            every { userCompetencyStateRepository.findByUserIdAndCompetencyKey(any(), any()) } returns null

            service.answerAssessment(authId, session.id, "final answer")

            assertEquals(5, requestSlot.captured.turn)
            assertTrue(requestSlot.captured.mustFinish)
        }

        @Test
        fun `throws 404 for a session that does not exist`() {
            setUpUser()
            val sessionId = UUID.randomUUID()
            every { skillAssessmentSessionRepository.findById(sessionId) } returns Optional.empty()

            val ex = assertThrows<ResponseStatusException> {
                runBlocking { service.answerAssessment(authId, sessionId, "answer") }
            }

            assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
        }

        @Test
        fun `throws 404 for a session owned by a different user`() {
            setUpUser()
            val otherUsersSession = SkillAssessmentSession(userId = UUID.randomUUID(), projectId = projectId)
            otherUsersSession.turns.add(
                SkillAssessmentTurn(session = otherUsersSession, turnIndex = 0, question = "Q0"),
            )
            every { skillAssessmentSessionRepository.findById(otherUsersSession.id) } returns
                Optional.of(otherUsersSession)

            val ex = assertThrows<ResponseStatusException> {
                runBlocking { service.answerAssessment(authId, otherUsersSession.id, "answer") }
            }

            assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
        }

        @Test
        fun `throws 409 when the session has no open turn`() {
            setUpUser()
            val session = SkillAssessmentSession(userId = userId, projectId = projectId)
            session.turns.add(
                SkillAssessmentTurn(session = session, turnIndex = 0, question = "Q0", answer = "already answered"),
            )
            every { skillAssessmentSessionRepository.findById(session.id) } returns Optional.of(session)

            val ex = assertThrows<ResponseStatusException> {
                runBlocking { service.answerAssessment(authId, session.id, "answer") }
            }

            assertEquals(HttpStatus.CONFLICT, ex.statusCode)
        }
    }
}
