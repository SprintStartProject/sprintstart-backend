package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintCheckQuestion
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhase
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkquestion.CreateBlueprintCheckQuestionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkquestion.DeleteBlueprintCheckQuestionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkquestion.UpdateBlueprintCheckQuestionPositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkquestion.UpdateBlueprintCheckQuestionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintCheckQuestionRepository
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.test.assertEquals

class BlueprintCheckQuestionServiceTest {
    private val blueprintAccessService: BlueprintAccessService = mockk()
    private val blueprintCheckQuestionRepository: BlueprintCheckQuestionRepository = mockk()
    private val blueprintSubGraphNodeService: BlueprintSubGraphNodeService = mockk()
    private val entityManager: EntityManager = mockk()
    private val service =
        BlueprintCheckQuestionService(
            blueprintAccessService,
            blueprintCheckQuestionRepository,
            blueprintSubGraphNodeService,
            entityManager,
        )

    private fun makeQuestion(
        phase: BlueprintPhase = blueprintPhaseFixture(),
        revision: Long = 0,
        position: Int = 0,
        type: CheckQuestionType = CheckQuestionType.MULTIPLE_CHOICE,
        correctAnswer: String? = null,
    ): BlueprintCheckQuestion {
        return BlueprintCheckQuestion(
            blueprintPhase = phase,
            title = "Question",
            position = position,
            type = type,
            question = "Question?",
            correctAnswer = correctAnswer,
        ).also { it.revision = revision }
    }

    private fun createRequest(
        position: Int = 0,
        type: CheckQuestionType = CheckQuestionType.MULTIPLE_CHOICE,
        correctAnswer: String? = null,
    ): CreateBlueprintCheckQuestionRequest {
        return CreateBlueprintCheckQuestionRequest(
            position = position,
            type = type,
            title = "Question",
            question = "Question?",
            explanation = null,
            correctAnswer = correctAnswer,
            graphX = null,
            graphY = null,
        )
    }

    private fun updateRequest(
        revision: Long,
        position: Int = 0,
        type: CheckQuestionType = CheckQuestionType.MULTIPLE_CHOICE,
        correctAnswer: String? = null,
    ): UpdateBlueprintCheckQuestionRequest {
        return UpdateBlueprintCheckQuestionRequest(
            revision = revision,
            title = "Updated",
            position = position,
            type = type,
            question = "Updated?",
            explanation = "Explanation",
            correctAnswer = correctAnswer,
        )
    }

    private fun positionRequest(
        revision: Long,
        position: Int,
    ): UpdateBlueprintCheckQuestionPositionRequest {
        return UpdateBlueprintCheckQuestionPositionRequest(revision = revision, position = position)
    }

    private fun deleteRequest(revision: Long): DeleteBlueprintCheckQuestionRequest {
        return DeleteBlueprintCheckQuestionRequest(revision = revision)
    }

    private fun allowFlush() {
        every { entityManager.flush() } just runs
    }

    @Nested
    inner class GetBlueprintCheckQuestionsForPhase {
        @Test
        fun `returns mapped questions for global scope`() {
            val phase = blueprintPhaseFixture()
            val question = makeQuestion(phase = phase, revision = 2, position = 1)
            every {
                blueprintCheckQuestionRepository
                    .findAllByBlueprintPhaseBlueprintPathProjectIdIsNullAndBlueprintPhaseId(phase.id)
            } returns mutableListOf(question)

            val result = service.getBlueprintCheckQuestionsForPhase(BlueprintScope.Global, phase.id)

            assertEquals(listOf(question.id), result.map { it.id })
            assertEquals(2, result.single().revision)
            assertEquals(1, result.single().position)
            assertEquals(phase.id, result.single().blueprintPhaseId)
        }

        @Test
        fun `restricts project scope lookup by project id`() {
            val projectId = UUID.randomUUID()
            val phase = blueprintPhaseFixture(blueprintPathFixture(projectId))
            val question = makeQuestion(phase = phase)
            every {
                blueprintCheckQuestionRepository
                    .findAllByBlueprintPhaseBlueprintPathProjectIdAndBlueprintPhaseId(projectId, phase.id)
            } returns mutableListOf(question)

            val result = service.getBlueprintCheckQuestionsForPhase(BlueprintScope.Project(projectId), phase.id)

            assertEquals(listOf(question.id), result.map { it.id })
        }
    }

    @Nested
    inner class GetBlueprintCheckQuestionById {
        @Test
        fun `returns mapped question for global scope`() {
            val question = makeQuestion(revision = 3)
            every { blueprintAccessService.getAuthorizedCheckQuestion(BlueprintScope.Global, question.id) } returns
                question

            val result = service.getBlueprintCheckQuestionById(BlueprintScope.Global, question.id)

            assertEquals(question.id, result.id)
            assertEquals(3, result.revision)
            assertEquals(CheckQuestionType.MULTIPLE_CHOICE, result.type)
        }

        @Test
        fun `returns mapped question for project scope`() {
            val scope = BlueprintScope.Project(UUID.randomUUID())
            val question = makeQuestion()
            every { blueprintAccessService.getAuthorizedCheckQuestion(scope, question.id) } returns question

            val result = service.getBlueprintCheckQuestionById(scope, question.id)

            assertEquals(question.id, result.id)
            verify(exactly = 1) { blueprintAccessService.getAuthorizedCheckQuestion(scope, question.id) }
        }

        @Test
        fun `propagates not found from the access service`() {
            val questionId = UUID.randomUUID()
            every { blueprintAccessService.getAuthorizedCheckQuestion(BlueprintScope.Global, questionId) } throws
                ResponseStatusException(HttpStatus.NOT_FOUND)

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.getBlueprintCheckQuestionById(BlueprintScope.Global, questionId)
            }
        }
    }

    @Nested
    inner class CreateBlueprintCheckQuestionForPhase {
        @Test
        fun `creates question for global scope and shifts following questions right`() {
            val phase = blueprintPhaseFixture()
            val existing = makeQuestion(phase = phase, position = 0)
            every { blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phase.id) } returns phase
            every { blueprintCheckQuestionRepository.countByBlueprintPhaseId(phase.id) } returns 1
            every {
                blueprintCheckQuestionRepository
                    .findAllByBlueprintPhaseIdAndPositionGreaterThanEqualOrderByPositionDesc(phase.id, 0)
            } returns mutableListOf(existing)
            every { blueprintCheckQuestionRepository.save(any()) } answers { firstArg() }

            val result =
                service.createBlueprintCheckQuestionForPhase(
                    BlueprintScope.Global,
                    phase.id,
                    createRequest(position = 0),
                )

            assertEquals(0, result.position)
            assertEquals(1, result.revision)
            assertEquals(phase.id, result.blueprintPhaseId)
            assertEquals("Question", result.title)
            assertEquals(CheckQuestionType.MULTIPLE_CHOICE, result.type)
            assertEquals(1, existing.position)
        }

        @Test
        fun `creates question for project scope`() {
            val projectId = UUID.randomUUID()
            val scope = BlueprintScope.Project(projectId)
            val phase = blueprintPhaseFixture(blueprintPathFixture(projectId))
            every { blueprintAccessService.getAuthorizedEditablePhase(scope, phase.id) } returns phase
            every { blueprintCheckQuestionRepository.countByBlueprintPhaseId(phase.id) } returns 0
            every {
                blueprintCheckQuestionRepository
                    .findAllByBlueprintPhaseIdAndPositionGreaterThanEqualOrderByPositionDesc(phase.id, 0)
            } returns mutableListOf()
            every {
                blueprintCheckQuestionRepository.save(any())
            } answers { firstArg() }

            val result = service.createBlueprintCheckQuestionForPhase(scope, phase.id, createRequest(position = 0))

            assertEquals(0, result.position)
            verify(exactly = 1) { blueprintAccessService.getAuthorizedEditablePhase(scope, phase.id) }
        }

        @Test
        fun `accepts short text question with a correct answer`() {
            val phase = blueprintPhaseFixture()
            every { blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phase.id) } returns phase
            every { blueprintCheckQuestionRepository.countByBlueprintPhaseId(phase.id) } returns 0
            every {
                blueprintCheckQuestionRepository
                    .findAllByBlueprintPhaseIdAndPositionGreaterThanEqualOrderByPositionDesc(phase.id, 0)
            } returns mutableListOf()
            every {
                blueprintCheckQuestionRepository.save(any())
            } answers { firstArg() }

            val result =
                service.createBlueprintCheckQuestionForPhase(
                    BlueprintScope.Global,
                    phase.id,
                    createRequest(
                        position = 0,
                        type = CheckQuestionType.SHORT_TEXT,
                        correctAnswer = "Answer",
                    ),
                )

            assertEquals(CheckQuestionType.SHORT_TEXT, result.type)
            assertEquals("Answer", result.correctAnswer)
        }

        @Test
        fun `accepts multiple choice question without a correct answer`() {
            val phase = blueprintPhaseFixture()
            every { blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phase.id) } returns phase
            every { blueprintCheckQuestionRepository.countByBlueprintPhaseId(phase.id) } returns 0
            every {
                blueprintCheckQuestionRepository
                    .findAllByBlueprintPhaseIdAndPositionGreaterThanEqualOrderByPositionDesc(phase.id, 0)
            } returns mutableListOf()
            every {
                blueprintCheckQuestionRepository.save(any())
            } answers { firstArg() }

            val result =
                service.createBlueprintCheckQuestionForPhase(
                    BlueprintScope.Global,
                    phase.id,
                    createRequest(position = 0, correctAnswer = null),
                )

            assertEquals(CheckQuestionType.MULTIPLE_CHOICE, result.type)
            assertEquals(null, result.correctAnswer)
        }

        @Test
        fun `rejects short text question without a correct answer before resolving the phase`() {
            assertBlueprintStatus(HttpStatus.BAD_REQUEST) {
                service.createBlueprintCheckQuestionForPhase(
                    BlueprintScope.Global,
                    UUID.randomUUID(),
                    createRequest(type = CheckQuestionType.SHORT_TEXT, correctAnswer = null),
                )
            }
            verify(exactly = 0) { blueprintAccessService.getAuthorizedEditablePhase(any(), any()) }
        }

        @Test
        fun `rejects position below zero`() {
            val phase = blueprintPhaseFixture()
            every { blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phase.id) } returns phase
            every { blueprintCheckQuestionRepository.countByBlueprintPhaseId(phase.id) } returns 1

            assertBlueprintStatus(HttpStatus.BAD_REQUEST) {
                service.createBlueprintCheckQuestionForPhase(
                    BlueprintScope.Global,
                    phase.id,
                    createRequest(position = -1),
                )
            }
        }

        @Test
        fun `rejects position above the question count`() {
            val phase = blueprintPhaseFixture()
            every { blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phase.id) } returns phase
            every { blueprintCheckQuestionRepository.countByBlueprintPhaseId(phase.id) } returns 1

            assertBlueprintStatus(HttpStatus.BAD_REQUEST) {
                service.createBlueprintCheckQuestionForPhase(
                    BlueprintScope.Global,
                    phase.id,
                    createRequest(position = 2),
                )
            }
        }

        @Test
        fun `propagates not found from the access service`() {
            val phaseId = UUID.randomUUID()
            every { blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phaseId) } throws
                ResponseStatusException(HttpStatus.NOT_FOUND)

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.createBlueprintCheckQuestionForPhase(
                    BlueprintScope.Global,
                    phaseId,
                    createRequest(position = 0),
                )
            }
        }
    }

    @Nested
    inner class UpdateBlueprintCheckQuestionById {
        @Test
        fun `updates question fields for global scope and shifts siblings`() {
            val phase = blueprintPhaseFixture()
            val question = makeQuestion(phase = phase, revision = 3, position = 0)
            val sibling = makeQuestion(phase = phase, position = 1)
            every {
                blueprintAccessService.getAuthorizedEditableCheckQuestion(BlueprintScope.Global, question.id)
            } returns question
            every { blueprintCheckQuestionRepository.countByBlueprintPhaseId(phase.id) } returns 2
            every {
                blueprintCheckQuestionRepository
                    .findAllByBlueprintPhaseIdAndPositionBetween(phase.id, 1, 1)
            } returns mutableListOf(sibling)
            every { blueprintCheckQuestionRepository.save(question) } answers { firstArg() }

            val result =
                service.updateBlueprintCheckQuestionById(
                    BlueprintScope.Global,
                    question.id,
                    updateRequest(revision = 3, position = 1),
                )

            assertEquals(1, result.position)
            assertEquals(3, result.revision)
            assertEquals("Updated", result.title)
            assertEquals("Updated?", result.question)
            assertEquals("Explanation", result.explanation)
            assertEquals(0, sibling.position)
        }

        @Test
        fun `updates question for project scope`() {
            val scope = BlueprintScope.Project(UUID.randomUUID())
            val phase = blueprintPhaseFixture()
            val question = makeQuestion(phase = phase, revision = 3, position = 0)
            every { blueprintAccessService.getAuthorizedEditableCheckQuestion(scope, question.id) } returns question
            every { blueprintCheckQuestionRepository.countByBlueprintPhaseId(phase.id) } returns 1
            every { blueprintCheckQuestionRepository.save(question) } answers { firstArg() }

            val result = service.updateBlueprintCheckQuestionById(scope, question.id, updateRequest(revision = 3))

            assertEquals(3, result.revision)
            verify(exactly = 1) { blueprintAccessService.getAuthorizedEditableCheckQuestion(scope, question.id) }
        }

        @Test
        fun `rejects short text update without a correct answer before resolving the question`() {
            assertBlueprintStatus(HttpStatus.BAD_REQUEST) {
                service.updateBlueprintCheckQuestionById(
                    BlueprintScope.Global,
                    UUID.randomUUID(),
                    updateRequest(revision = 0, type = CheckQuestionType.SHORT_TEXT, correctAnswer = null),
                )
            }
            verify(exactly = 0) { blueprintAccessService.getAuthorizedEditableCheckQuestion(any(), any()) }
        }

        @Test
        fun `accepts short text update with a correct answer`() {
            val phase = blueprintPhaseFixture()
            val question = makeQuestion(phase = phase, revision = 3, position = 0)
            every {
                blueprintAccessService.getAuthorizedEditableCheckQuestion(BlueprintScope.Global, question.id)
            } returns question
            every { blueprintCheckQuestionRepository.countByBlueprintPhaseId(phase.id) } returns 1
            every { blueprintCheckQuestionRepository.save(question) } answers { firstArg() }

            val result =
                service.updateBlueprintCheckQuestionById(
                    BlueprintScope.Global,
                    question.id,
                    updateRequest(
                        revision = 3,
                        type = CheckQuestionType.SHORT_TEXT,
                        correctAnswer = "Answer",
                    ),
                )

            assertEquals(CheckQuestionType.SHORT_TEXT, result.type)
            assertEquals("Answer", result.correctAnswer)
        }

        @Test
        fun `rejects stale revision without saving`() {
            val phase = blueprintPhaseFixture()
            val question = makeQuestion(phase = phase, revision = 3, position = 0)
            every {
                blueprintAccessService.getAuthorizedEditableCheckQuestion(BlueprintScope.Global, question.id)
            } returns question

            assertBlueprintStatus(HttpStatus.CONFLICT) {
                service.updateBlueprintCheckQuestionById(
                    BlueprintScope.Global,
                    question.id,
                    updateRequest(revision = 2),
                )
            }
            verify(exactly = 0) { blueprintCheckQuestionRepository.save(any()) }
        }

        @Test
        fun `rejects position above the question count without saving`() {
            val phase = blueprintPhaseFixture()
            val question = makeQuestion(phase = phase, revision = 3, position = 0)
            every {
                blueprintAccessService.getAuthorizedEditableCheckQuestion(BlueprintScope.Global, question.id)
            } returns question
            every { blueprintCheckQuestionRepository.countByBlueprintPhaseId(phase.id) } returns 1

            assertBlueprintStatus(HttpStatus.BAD_REQUEST) {
                service.updateBlueprintCheckQuestionById(
                    BlueprintScope.Global,
                    question.id,
                    updateRequest(revision = 3, position = 1),
                )
            }
            verify(exactly = 0) { blueprintCheckQuestionRepository.save(any()) }
        }

        @Test
        fun `propagates not found from the access service`() {
            val questionId = UUID.randomUUID()
            every {
                blueprintAccessService.getAuthorizedEditableCheckQuestion(BlueprintScope.Global, questionId)
            } throws ResponseStatusException(HttpStatus.NOT_FOUND)

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.updateBlueprintCheckQuestionById(
                    BlueprintScope.Global,
                    questionId,
                    updateRequest(revision = 0),
                )
            }
        }
    }

    @Nested
    inner class UpdateBlueprintCheckQuestionPositionById {
        @Test
        fun `reorders moving question toward the end and flushes changed positions`() {
            val phase = blueprintPhaseFixture()
            val question = makeQuestion(phase = phase, revision = 2, position = 0)
            val sibling = makeQuestion(phase = phase, position = 1)
            every {
                blueprintAccessService.getAuthorizedEditableCheckQuestion(BlueprintScope.Global, question.id)
            } returns question
            every { blueprintCheckQuestionRepository.countByBlueprintPhaseId(phase.id) } returns 2
            every {
                blueprintCheckQuestionRepository
                    .findAllByBlueprintPhaseIdAndPositionBetween(phase.id, 1, 1)
            } returns mutableListOf(sibling)
            every {
                blueprintCheckQuestionRepository.saveAllAndFlush(any<Iterable<BlueprintCheckQuestion>>())
            } answers { firstArg<Iterable<BlueprintCheckQuestion>>().toList() }

            val result =
                service.updateBlueprintCheckQuestionPositionById(
                    BlueprintScope.Global,
                    question.id,
                    positionRequest(revision = 2, position = 1),
                )

            assertEquals(2, result.size)
            assertEquals(1, question.position)
            assertEquals(0, sibling.position)
            assertEquals(listOf(sibling.id, question.id), result.map { it.id })
            verify(exactly = 1) {
                blueprintCheckQuestionRepository.saveAllAndFlush(any<Iterable<BlueprintCheckQuestion>>())
            }
        }

        @Test
        fun `reorders moving question toward the front shifting siblings right`() {
            val phase = blueprintPhaseFixture()
            val question = makeQuestion(phase = phase, revision = 2, position = 2)
            val sibling = makeQuestion(phase = phase, position = 0)
            every {
                blueprintAccessService.getAuthorizedEditableCheckQuestion(BlueprintScope.Global, question.id)
            } returns question
            every { blueprintCheckQuestionRepository.countByBlueprintPhaseId(phase.id) } returns 3
            every {
                blueprintCheckQuestionRepository
                    .findAllByBlueprintPhaseIdAndPositionBetween(phase.id, 0, 1)
            } returns mutableListOf(sibling)
            every {
                blueprintCheckQuestionRepository.saveAllAndFlush(any<Iterable<BlueprintCheckQuestion>>())
            } answers { firstArg<Iterable<BlueprintCheckQuestion>>().toList() }

            val result =
                service.updateBlueprintCheckQuestionPositionById(
                    BlueprintScope.Global,
                    question.id,
                    positionRequest(revision = 2, position = 0),
                )

            assertEquals(0, question.position)
            assertEquals(1, sibling.position)
            assertEquals(listOf(sibling.id, question.id), result.map { it.id })
        }

        @Test
        fun `rejects stale revision without saving`() {
            val phase = blueprintPhaseFixture()
            val question = makeQuestion(phase = phase, revision = 2, position = 0)
            every {
                blueprintAccessService.getAuthorizedEditableCheckQuestion(BlueprintScope.Global, question.id)
            } returns question

            assertBlueprintStatus(HttpStatus.CONFLICT) {
                service.updateBlueprintCheckQuestionPositionById(
                    BlueprintScope.Global,
                    question.id,
                    positionRequest(revision = 1, position = 0),
                )
            }
            verify(exactly = 0) {
                blueprintCheckQuestionRepository.saveAllAndFlush(any<Iterable<BlueprintCheckQuestion>>())
            }
        }

        @Test
        fun `rejects position above the question count without saving`() {
            val phase = blueprintPhaseFixture()
            val question = makeQuestion(phase = phase, revision = 2, position = 0)
            every {
                blueprintAccessService.getAuthorizedEditableCheckQuestion(BlueprintScope.Global, question.id)
            } returns question
            every { blueprintCheckQuestionRepository.countByBlueprintPhaseId(phase.id) } returns 1

            assertBlueprintStatus(HttpStatus.BAD_REQUEST) {
                service.updateBlueprintCheckQuestionPositionById(
                    BlueprintScope.Global,
                    question.id,
                    positionRequest(revision = 2, position = 1),
                )
            }
            verify(exactly = 0) {
                blueprintCheckQuestionRepository.saveAllAndFlush(any<Iterable<BlueprintCheckQuestion>>())
            }
        }

        @Test
        fun `propagates not found from the access service`() {
            val questionId = UUID.randomUUID()
            every {
                blueprintAccessService.getAuthorizedEditableCheckQuestion(BlueprintScope.Global, questionId)
            } throws ResponseStatusException(HttpStatus.NOT_FOUND)

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.updateBlueprintCheckQuestionPositionById(
                    BlueprintScope.Global,
                    questionId,
                    positionRequest(revision = 0, position = 0),
                )
            }
        }
    }

    @Nested
    inner class DeleteBlueprintCheckQuestionById {
        @Test
        fun `removes connections deletes and flushes returning bumped revisions`() {
            val question = makeQuestion(revision = 5)
            val dependant = makeQuestion(revision = 1)
            every {
                blueprintAccessService.getAuthorizedEditableCheckQuestion(BlueprintScope.Global, question.id)
            } returns question
            every { blueprintSubGraphNodeService.removeAllConnections(question) } returns
                mutableListOf(question, dependant)
            every { blueprintCheckQuestionRepository.delete(question) } just runs
            allowFlush()

            val result =
                service.deleteBlueprintCheckQuestionById(
                    BlueprintScope.Global,
                    question.id,
                    deleteRequest(revision = 5),
                )

            assertEquals(listOf(question.id, dependant.id), result.updatedQuestions.map { it.id })
            assertEquals(listOf(6L, 2L), result.updatedQuestions.map { it.revision })
            verify(exactly = 1) { blueprintSubGraphNodeService.removeAllConnections(question) }
            verify(exactly = 1) { blueprintCheckQuestionRepository.delete(question) }
            verify(exactly = 1) { entityManager.flush() }
        }

        @Test
        fun `rejects stale revision without deleting`() {
            val question = makeQuestion(revision = 5)
            every {
                blueprintAccessService.getAuthorizedEditableCheckQuestion(BlueprintScope.Global, question.id)
            } returns question

            assertBlueprintStatus(HttpStatus.CONFLICT) {
                service.deleteBlueprintCheckQuestionById(
                    BlueprintScope.Global,
                    question.id,
                    deleteRequest(revision = 4),
                )
            }
            verify(exactly = 0) { blueprintSubGraphNodeService.removeAllConnections(any()) }
            verify(exactly = 0) { blueprintCheckQuestionRepository.delete(any()) }
            verify(exactly = 0) { entityManager.flush() }
        }

        @Test
        fun `propagates not found from the access service`() {
            val questionId = UUID.randomUUID()
            every {
                blueprintAccessService.getAuthorizedEditableCheckQuestion(BlueprintScope.Global, questionId)
            } throws ResponseStatusException(HttpStatus.NOT_FOUND)

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.deleteBlueprintCheckQuestionById(BlueprintScope.Global, questionId, deleteRequest(revision = 0))
            }
        }
    }
}
