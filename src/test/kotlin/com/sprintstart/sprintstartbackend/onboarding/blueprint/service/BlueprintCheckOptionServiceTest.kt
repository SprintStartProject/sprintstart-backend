package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintCheckOption
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintCheckQuestion
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkoption.CreateBlueprintCheckOptionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkoption.DeleteBlueprintCheckOptionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkoption.UpdateBlueprintCheckOptionPositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkoption.UpdateBlueprintCheckOptionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintCheckOptionRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.test.assertEquals

class BlueprintCheckOptionServiceTest {
    private val blueprintAccessService: BlueprintAccessService = mockk()
    private val blueprintCheckOptionRepository: BlueprintCheckOptionRepository = mockk()
    private val service =
        BlueprintCheckOptionService(
            blueprintAccessService,
            blueprintCheckOptionRepository,
        )

    private fun makeOption(
        question: BlueprintCheckQuestion = blueprintQuestionFixture(),
        revision: Long = 0,
        position: Int = 0,
        label: String = "Option",
        correct: Boolean = false,
    ): BlueprintCheckOption {
        return BlueprintCheckOption(
            blueprintCheckQuestion = question,
            revision = revision,
            position = position,
            label = label,
            correct = correct,
        )
    }

    private fun createRequest(
        position: Int = 0,
        label: String = "Option",
        correct: Boolean = false,
    ): CreateBlueprintCheckOptionRequest {
        return CreateBlueprintCheckOptionRequest(
            position = position,
            label = label,
            correct = correct,
        )
    }

    private fun updateRequest(
        revision: Long,
        position: Int = 0,
        label: String = "Updated",
        correct: Boolean = true,
    ): UpdateBlueprintCheckOptionRequest {
        return UpdateBlueprintCheckOptionRequest(
            revision = revision,
            position = position,
            label = label,
            correct = correct,
        )
    }

    private fun positionRequest(
        revision: Long,
        position: Int,
    ): UpdateBlueprintCheckOptionPositionRequest {
        return UpdateBlueprintCheckOptionPositionRequest(revision = revision, position = position)
    }

    private fun deleteRequest(revision: Long): DeleteBlueprintCheckOptionRequest {
        return DeleteBlueprintCheckOptionRequest(revision = revision)
    }

    @Nested
    inner class GetBlueprintCheckOptionsForQuestion {
        @Test
        fun `returns mapped options for global scope`() {
            val question = blueprintQuestionFixture()
            val option = makeOption(question = question, revision = 2, position = 1, label = "First", correct = true)
            every {
                blueprintCheckOptionRepository
                    .findAllByBlueprintCheckQuestionBlueprintPhaseBlueprintPathProjectIdNullAndBlueprintCheckQuestionId(
                        question.id,
                    )
            } returns mutableListOf(option)

            val result = service.getBlueprintCheckOptionsForQuestion(BlueprintScope.Global, question.id)

            assertEquals(listOf(option.id), result.map { it.id })
            assertEquals(2, result.single().revision)
            assertEquals(1, result.single().position)
            assertEquals("First", result.single().label)
            assertEquals(true, result.single().correct)
            assertEquals(question.id, result.single().blueprintCheckQuestionId)
        }

        @Test
        fun `restricts project scope lookup by project id`() {
            val projectId = UUID.randomUUID()
            val question = blueprintQuestionFixture(blueprintPhaseFixture(blueprintPathFixture(projectId)))
            val option = makeOption(question = question)
            every {
                blueprintCheckOptionRepository
                    .findAllByBlueprintCheckQuestionBlueprintPhaseBlueprintPathProjectIdAndBlueprintCheckQuestionId(
                        projectId,
                        question.id,
                    )
            } returns mutableListOf(option)

            val result = service.getBlueprintCheckOptionsForQuestion(BlueprintScope.Project(projectId), question.id)

            assertEquals(listOf(option.id), result.map { it.id })
        }
    }

    @Nested
    inner class GetBlueprintCheckOptionById {
        @Test
        fun `returns mapped option for global scope`() {
            val option = makeOption(revision = 3, correct = true)
            every { blueprintAccessService.getAuthorizedCheckOption(BlueprintScope.Global, option.id) } returns option

            val result = service.getBlueprintCheckOptionById(BlueprintScope.Global, option.id)

            assertEquals(option.id, result.id)
            assertEquals(3, result.revision)
            assertEquals(true, result.correct)
        }

        @Test
        fun `returns mapped option for project scope`() {
            val scope = BlueprintScope.Project(UUID.randomUUID())
            val option = makeOption()
            every { blueprintAccessService.getAuthorizedCheckOption(scope, option.id) } returns option

            val result = service.getBlueprintCheckOptionById(scope, option.id)

            assertEquals(option.id, result.id)
            verify(exactly = 1) { blueprintAccessService.getAuthorizedCheckOption(scope, option.id) }
        }

        @Test
        fun `propagates not found from the access service`() {
            val optionId = UUID.randomUUID()
            every { blueprintAccessService.getAuthorizedCheckOption(BlueprintScope.Global, optionId) } throws
                ResponseStatusException(HttpStatus.NOT_FOUND)

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.getBlueprintCheckOptionById(BlueprintScope.Global, optionId)
            }
        }
    }

    @Nested
    inner class CreateBlueprintCheckOptionForQuestion {
        @Test
        fun `inserts option for global scope and shifts following options right`() {
            val question = blueprintQuestionFixture()
            val existing = makeOption(question = question, position = 0, label = "Existing")
            every {
                blueprintAccessService.getAuthorizedEditableCheckQuestion(
                    BlueprintScope.Global,
                    question.id,
                )
            } returns
                question
            every { blueprintCheckOptionRepository.countByBlueprintCheckQuestionId(question.id) } returns 1
            every {
                blueprintCheckOptionRepository
                    .findAllByBlueprintCheckQuestionIdAndPositionGreaterThanEqualOrderByPositionDesc(question.id, 0)
            } returns mutableListOf(existing)
            every { blueprintCheckOptionRepository.save(any()) } answers { firstArg() }

            val result =
                service.createBlueprintCheckOptionForQuestion(
                    BlueprintScope.Global,
                    question.id,
                    createRequest(position = 0, label = "New", correct = true),
                )

            assertEquals(0, result.position)
            assertEquals(0, result.revision)
            assertEquals("New", result.label)
            assertEquals(true, result.correct)
            assertEquals(question.id, result.blueprintCheckQuestionId)
            assertEquals(1, existing.position)
        }

        @Test
        fun `creates option for project scope`() {
            val scope = BlueprintScope.Project(UUID.randomUUID())
            val question = blueprintQuestionFixture()
            every { blueprintAccessService.getAuthorizedEditableCheckQuestion(scope, question.id) } returns question
            every { blueprintCheckOptionRepository.countByBlueprintCheckQuestionId(question.id) } returns 0
            every {
                blueprintCheckOptionRepository
                    .findAllByBlueprintCheckQuestionIdAndPositionGreaterThanEqualOrderByPositionDesc(question.id, 0)
            } returns mutableListOf()
            every { blueprintCheckOptionRepository.save(any()) } answers { firstArg() }

            val result = service.createBlueprintCheckOptionForQuestion(scope, question.id, createRequest(position = 0))

            assertEquals(0, result.position)
            verify(exactly = 1) { blueprintAccessService.getAuthorizedEditableCheckQuestion(scope, question.id) }
        }

        @Test
        fun `rejects position below zero`() {
            val question = blueprintQuestionFixture()
            every {
                blueprintAccessService.getAuthorizedEditableCheckQuestion(
                    BlueprintScope.Global,
                    question.id,
                )
            } returns
                question
            every { blueprintCheckOptionRepository.countByBlueprintCheckQuestionId(question.id) } returns 1

            assertBlueprintStatus(HttpStatus.BAD_REQUEST) {
                service.createBlueprintCheckOptionForQuestion(
                    BlueprintScope.Global,
                    question.id,
                    createRequest(position = -1),
                )
            }
            verify(exactly = 0) { blueprintCheckOptionRepository.save(any()) }
        }

        @Test
        fun `rejects position above the option count`() {
            val question = blueprintQuestionFixture()
            every {
                blueprintAccessService.getAuthorizedEditableCheckQuestion(
                    BlueprintScope.Global,
                    question.id,
                )
            } returns
                question
            every { blueprintCheckOptionRepository.countByBlueprintCheckQuestionId(question.id) } returns 1

            assertBlueprintStatus(HttpStatus.BAD_REQUEST) {
                service.createBlueprintCheckOptionForQuestion(
                    BlueprintScope.Global,
                    question.id,
                    createRequest(position = 2),
                )
            }
            verify(exactly = 0) { blueprintCheckOptionRepository.save(any()) }
        }

        @Test
        fun `propagates not found from the access service`() {
            val questionId = UUID.randomUUID()
            every {
                blueprintAccessService.getAuthorizedEditableCheckQuestion(
                    BlueprintScope.Global,
                    questionId,
                )
            } throws
                ResponseStatusException(HttpStatus.NOT_FOUND)

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.createBlueprintCheckOptionForQuestion(
                    BlueprintScope.Global,
                    questionId,
                    createRequest(position = 0),
                )
            }
        }
    }

    @Nested
    inner class UpdateBlueprintCheckOptionById {
        @Test
        fun `updates option fields for global scope and shifts siblings`() {
            val question = blueprintQuestionFixture()
            val option = makeOption(question = question, revision = 3, position = 0, label = "Old", correct = false)
            val sibling = makeOption(question = question, position = 1)
            every { blueprintAccessService.getAuthorizedEditableCheckOption(BlueprintScope.Global, option.id) } returns
                option
            every { blueprintCheckOptionRepository.countByBlueprintCheckQuestionId(question.id) } returns 2
            every {
                blueprintCheckOptionRepository
                    .findAllByBlueprintCheckQuestionIdAndPositionBetween(question.id, 1, 1)
            } returns mutableListOf(sibling)
            every { blueprintCheckOptionRepository.save(option) } answers { firstArg() }

            val result =
                service.updateBlueprintCheckOptionById(
                    BlueprintScope.Global,
                    option.id,
                    updateRequest(revision = 3, position = 1, label = "Updated", correct = true),
                )

            assertEquals(1, result.position)
            assertEquals(3, result.revision)
            assertEquals("Updated", result.label)
            assertEquals(true, result.correct)
            assertEquals(1, option.position)
            assertEquals(0, sibling.position)
        }

        @Test
        fun `updates option for project scope`() {
            val scope = BlueprintScope.Project(UUID.randomUUID())
            val question = blueprintQuestionFixture()
            val option = makeOption(question = question, revision = 3, position = 0)
            every { blueprintAccessService.getAuthorizedEditableCheckOption(scope, option.id) } returns option
            every { blueprintCheckOptionRepository.countByBlueprintCheckQuestionId(question.id) } returns 1
            every { blueprintCheckOptionRepository.save(option) } answers { firstArg() }

            val result = service.updateBlueprintCheckOptionById(scope, option.id, updateRequest(revision = 3))

            assertEquals(3, result.revision)
            verify(exactly = 1) { blueprintAccessService.getAuthorizedEditableCheckOption(scope, option.id) }
        }

        @Test
        fun `rejects stale revision without saving`() {
            val question = blueprintQuestionFixture()
            val option = makeOption(question = question, revision = 3, position = 0, label = "Old")
            every { blueprintAccessService.getAuthorizedEditableCheckOption(BlueprintScope.Global, option.id) } returns
                option

            assertBlueprintStatus(HttpStatus.CONFLICT) {
                service.updateBlueprintCheckOptionById(
                    BlueprintScope.Global,
                    option.id,
                    updateRequest(revision = 2, position = 1),
                )
            }
            verify(exactly = 0) { blueprintCheckOptionRepository.save(any()) }
            assertEquals(0, option.position)
            assertEquals("Old", option.label)
        }

        @Test
        fun `rejects position above the option count without saving`() {
            val question = blueprintQuestionFixture()
            val option = makeOption(question = question, revision = 3, position = 0)
            every { blueprintAccessService.getAuthorizedEditableCheckOption(BlueprintScope.Global, option.id) } returns
                option
            every { blueprintCheckOptionRepository.countByBlueprintCheckQuestionId(question.id) } returns 1

            assertBlueprintStatus(HttpStatus.BAD_REQUEST) {
                service.updateBlueprintCheckOptionById(
                    BlueprintScope.Global,
                    option.id,
                    updateRequest(revision = 3, position = 1),
                )
            }
            verify(exactly = 0) { blueprintCheckOptionRepository.save(any()) }
        }

        @Test
        fun `propagates not found from the access service`() {
            val optionId = UUID.randomUUID()
            every { blueprintAccessService.getAuthorizedEditableCheckOption(BlueprintScope.Global, optionId) } throws
                ResponseStatusException(HttpStatus.NOT_FOUND)

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.updateBlueprintCheckOptionById(
                    BlueprintScope.Global,
                    optionId,
                    updateRequest(revision = 0),
                )
            }
        }
    }

    @Nested
    inner class UpdateBlueprintCheckOptionPositionById {
        @Test
        fun `reorders moving option toward the end and flushes changed positions`() {
            val question = blueprintQuestionFixture()
            val option = makeOption(question = question, revision = 2, position = 0)
            val sibling = makeOption(question = question, position = 1)
            every { blueprintAccessService.getAuthorizedEditableCheckOption(BlueprintScope.Global, option.id) } returns
                option
            every { blueprintCheckOptionRepository.countByBlueprintCheckQuestionId(question.id) } returns 2
            every {
                blueprintCheckOptionRepository
                    .findAllByBlueprintCheckQuestionIdAndPositionBetween(question.id, 1, 1)
            } returns mutableListOf(sibling)
            every {
                blueprintCheckOptionRepository.saveAllAndFlush(any<Iterable<BlueprintCheckOption>>())
            } answers { firstArg<Iterable<BlueprintCheckOption>>().toList() }

            val result =
                service.updateBlueprintCheckOptionPositionById(
                    BlueprintScope.Global,
                    option.id,
                    positionRequest(revision = 2, position = 1),
                )

            assertEquals(2, result.size)
            assertEquals(1, option.position)
            assertEquals(0, sibling.position)
            assertEquals(listOf(sibling.id, option.id), result.map { it.id })
            assertEquals(listOf(0, 1), result.map { it.position })
            verify(exactly = 1) {
                blueprintCheckOptionRepository.saveAllAndFlush(any<Iterable<BlueprintCheckOption>>())
            }
        }

        @Test
        fun `reorders moving option toward the front shifting siblings right`() {
            val question = blueprintQuestionFixture()
            val option = makeOption(question = question, revision = 2, position = 2)
            val sibling = makeOption(question = question, position = 0)
            every { blueprintAccessService.getAuthorizedEditableCheckOption(BlueprintScope.Global, option.id) } returns
                option
            every { blueprintCheckOptionRepository.countByBlueprintCheckQuestionId(question.id) } returns 3
            every {
                blueprintCheckOptionRepository
                    .findAllByBlueprintCheckQuestionIdAndPositionBetween(question.id, 0, 1)
            } returns mutableListOf(sibling)
            every {
                blueprintCheckOptionRepository.saveAllAndFlush(any<Iterable<BlueprintCheckOption>>())
            } answers { firstArg<Iterable<BlueprintCheckOption>>().toList() }

            val result =
                service.updateBlueprintCheckOptionPositionById(
                    BlueprintScope.Global,
                    option.id,
                    positionRequest(revision = 2, position = 0),
                )

            assertEquals(0, option.position)
            assertEquals(1, sibling.position)
            assertEquals(listOf(sibling.id, option.id), result.map { it.id })
        }

        @Test
        fun `rejects stale revision without saving`() {
            val question = blueprintQuestionFixture()
            val option = makeOption(question = question, revision = 6, position = 0)
            every { blueprintAccessService.getAuthorizedEditableCheckOption(BlueprintScope.Global, option.id) } returns
                option

            assertBlueprintStatus(HttpStatus.CONFLICT) {
                service.updateBlueprintCheckOptionPositionById(
                    BlueprintScope.Global,
                    option.id,
                    positionRequest(revision = 5, position = 0),
                )
            }
            verify(exactly = 0) {
                blueprintCheckOptionRepository.saveAllAndFlush(any<Iterable<BlueprintCheckOption>>())
            }
            assertEquals(0, option.position)
        }

        @Test
        fun `rejects position above the option count without saving`() {
            val question = blueprintQuestionFixture()
            val option = makeOption(question = question, revision = 6, position = 0)
            every { blueprintAccessService.getAuthorizedEditableCheckOption(BlueprintScope.Global, option.id) } returns
                option
            every { blueprintCheckOptionRepository.countByBlueprintCheckQuestionId(question.id) } returns 1

            assertBlueprintStatus(HttpStatus.BAD_REQUEST) {
                service.updateBlueprintCheckOptionPositionById(
                    BlueprintScope.Global,
                    option.id,
                    positionRequest(revision = 6, position = 1),
                )
            }
            verify(exactly = 0) {
                blueprintCheckOptionRepository.saveAllAndFlush(any<Iterable<BlueprintCheckOption>>())
            }
        }

        @Test
        fun `propagates not found from the access service`() {
            val optionId = UUID.randomUUID()
            every { blueprintAccessService.getAuthorizedEditableCheckOption(BlueprintScope.Global, optionId) } throws
                ResponseStatusException(HttpStatus.NOT_FOUND)

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.updateBlueprintCheckOptionPositionById(
                    BlueprintScope.Global,
                    optionId,
                    positionRequest(revision = 0, position = 0),
                )
            }
        }
    }

    @Nested
    inner class DeleteBlueprintCheckOptionById {
        @Test
        fun `deletes option for global scope`() {
            val option = makeOption(revision = 4)
            every { blueprintAccessService.getAuthorizedEditableCheckOption(BlueprintScope.Global, option.id) } returns
                option
            every { blueprintCheckOptionRepository.delete(option) } just runs

            service.deleteBlueprintCheckOptionById(BlueprintScope.Global, option.id, deleteRequest(revision = 4))

            verify(exactly = 1) { blueprintCheckOptionRepository.delete(option) }
        }

        @Test
        fun `deletes option for project scope`() {
            val scope = BlueprintScope.Project(UUID.randomUUID())
            val option = makeOption(revision = 4)
            every { blueprintAccessService.getAuthorizedEditableCheckOption(scope, option.id) } returns option
            every { blueprintCheckOptionRepository.delete(option) } just runs

            service.deleteBlueprintCheckOptionById(scope, option.id, deleteRequest(revision = 4))

            verify(exactly = 1) { blueprintAccessService.getAuthorizedEditableCheckOption(scope, option.id) }
            verify(exactly = 1) { blueprintCheckOptionRepository.delete(option) }
        }

        @Test
        fun `rejects stale revision without deleting`() {
            val option = makeOption(revision = 4)
            every { blueprintAccessService.getAuthorizedEditableCheckOption(BlueprintScope.Global, option.id) } returns
                option

            assertBlueprintStatus(HttpStatus.CONFLICT) {
                service.deleteBlueprintCheckOptionById(BlueprintScope.Global, option.id, deleteRequest(revision = 3))
            }
            verify(exactly = 0) { blueprintCheckOptionRepository.delete(any()) }
        }

        @Test
        fun `propagates not found from the access service`() {
            val optionId = UUID.randomUUID()
            every { blueprintAccessService.getAuthorizedEditableCheckOption(BlueprintScope.Global, optionId) } throws
                ResponseStatusException(HttpStatus.NOT_FOUND)

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.deleteBlueprintCheckOptionById(BlueprintScope.Global, optionId, deleteRequest(revision = 0))
            }
        }
    }
}
