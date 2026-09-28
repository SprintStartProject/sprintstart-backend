package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintCheckOption
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintCheckQuestion
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintResource
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintStep
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintTask
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintCheckOptionRepository
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintCheckQuestionRepository
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintPathRepository
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintPhaseRepository
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintResourceRepository
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintStepRepository
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintSubGraphNodeRepository
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintTaskRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.UUID
import kotlin.test.assertNull
import kotlin.test.assertSame

class BlueprintAccessServiceTest {
    private val blueprintPathRepository: BlueprintPathRepository = mockk()
    private val blueprintPhaseRepository: BlueprintPhaseRepository = mockk()
    private val blueprintStepRepository: BlueprintStepRepository = mockk()
    private val blueprintResourceRepository: BlueprintResourceRepository = mockk()
    private val blueprintTaskRepository: BlueprintTaskRepository = mockk()
    private val blueprintCheckQuestionRepository: BlueprintCheckQuestionRepository = mockk()
    private val blueprintCheckOptionRepository: BlueprintCheckOptionRepository = mockk()
    private val blueprintSubGraphNodeRepository: BlueprintSubGraphNodeRepository = mockk()
    private val service =
        BlueprintAccessService(
            blueprintPathRepository,
            blueprintPhaseRepository,
            blueprintStepRepository,
            blueprintResourceRepository,
            blueprintTaskRepository,
            blueprintCheckQuestionRepository,
            blueprintCheckOptionRepository,
            blueprintSubGraphNodeRepository,
        )

    private fun makeResource(step: BlueprintStep = blueprintStepFixture()): BlueprintResource {
        return BlueprintResource(
            blueprintStep = step,
            title = "Resource",
            description = "Description",
            url = "https://example.com",
        )
    }

    private fun makeTask(step: BlueprintStep = blueprintStepFixture()): BlueprintTask {
        return BlueprintTask(
            blueprintStep = step,
            position = 0,
            title = "Task",
            description = "Description",
        )
    }

    private fun makeOption(question: BlueprintCheckQuestion = blueprintQuestionFixture()): BlueprintCheckOption {
        return BlueprintCheckOption(
            blueprintCheckQuestion = question,
            position = 0,
            label = "Option",
            correct = true,
        )
    }

    @Nested
    inner class GetAuthorizedPath {
        @Test
        fun `returns the path for global scope`() {
            val path = blueprintPathFixture()
            every { blueprintPathRepository.findByProjectIdIsNullAndId(path.id) } returns path

            val result = service.getAuthorizedPath(BlueprintScope.Global, path.id)

            assertSame(path, result)
            verify(exactly = 1) { blueprintPathRepository.findByProjectIdIsNullAndId(path.id) }
        }

        @Test
        fun `resolves a project path only through the project-qualified repository query`() {
            val projectId = UUID.randomUUID()
            val path = blueprintPathFixture(projectId)
            every { blueprintPathRepository.findByProjectIdAndId(projectId, path.id) } returns path

            val result = service.getAuthorizedPath(BlueprintScope.Project(projectId), path.id)

            assertSame(path, result)
            verify(exactly = 1) { blueprintPathRepository.findByProjectIdAndId(projectId, path.id) }
        }

        @Test
        fun `rejects with not found when the path is missing for global scope`() {
            val pathId = UUID.randomUUID()
            every { blueprintPathRepository.findByProjectIdIsNullAndId(pathId) } returns null

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.getAuthorizedPath(BlueprintScope.Global, pathId)
            }
        }

        @Test
        fun `rejects with not found when the path is missing for project scope`() {
            val projectId = UUID.randomUUID()
            val pathId = UUID.randomUUID()
            every { blueprintPathRepository.findByProjectIdAndId(projectId, pathId) } returns null

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.getAuthorizedPath(BlueprintScope.Project(projectId), pathId)
            }
        }
    }

    @Nested
    inner class GetAuthorizedDraftPath {
        @Test
        fun `returns a draft path for global scope`() {
            val path = blueprintPathFixture(status = BlueprintStatus.DRAFT)
            every { blueprintPathRepository.findByProjectIdIsNullAndId(path.id) } returns path

            val result = service.getAuthorizedDraftPath(BlueprintScope.Global, path.id)

            assertSame(path, result)
        }

        @Test
        fun `returns a draft path for project scope`() {
            val projectId = UUID.randomUUID()
            val path = blueprintPathFixture(projectId = projectId, status = BlueprintStatus.DRAFT)
            every { blueprintPathRepository.findByProjectIdAndId(projectId, path.id) } returns path

            val result = service.getAuthorizedDraftPath(BlueprintScope.Project(projectId), path.id)

            assertSame(path, result)
            verify(exactly = 1) { blueprintPathRepository.findByProjectIdAndId(projectId, path.id) }
        }

        @Test
        fun `rejects editing an active path`() {
            val path = blueprintPathFixture(status = BlueprintStatus.ACTIVE)
            every { blueprintPathRepository.findByProjectIdIsNullAndId(path.id) } returns path

            assertBlueprintStatus(HttpStatus.CONFLICT) {
                service.getAuthorizedDraftPath(BlueprintScope.Global, path.id)
            }
        }

        @Test
        fun `rejects editing an archived path`() {
            val path = blueprintPathFixture(status = BlueprintStatus.ARCHIVED)
            every { blueprintPathRepository.findByProjectIdIsNullAndId(path.id) } returns path

            assertBlueprintStatus(HttpStatus.CONFLICT) {
                service.getAuthorizedDraftPath(BlueprintScope.Global, path.id)
            }
        }

        @Test
        fun `rejects with not found when the path is missing`() {
            val pathId = UUID.randomUUID()
            every { blueprintPathRepository.findByProjectIdIsNullAndId(pathId) } returns null

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.getAuthorizedDraftPath(BlueprintScope.Global, pathId)
            }
        }
    }

    @Nested
    inner class FindActiveForAuthorizedBlueprintKey {
        @Test
        fun `returns null when no active version exists for global scope`() {
            val blueprintKey = UUID.randomUUID()
            every {
                blueprintPathRepository
                    .findByProjectIdNullAndBlueprintKeyAndStatus(blueprintKey, BlueprintStatus.ACTIVE)
            } returns mutableListOf()

            val result = service.findActiveForAuthorizedBlueprintKey(BlueprintScope.Global, blueprintKey)

            assertNull(result)
        }

        @Test
        fun `returns the single active version for global scope`() {
            val path = blueprintPathFixture(status = BlueprintStatus.ACTIVE)
            every {
                blueprintPathRepository
                    .findByProjectIdNullAndBlueprintKeyAndStatus(path.blueprintKey, BlueprintStatus.ACTIVE)
            } returns mutableListOf(path)

            val result = service.findActiveForAuthorizedBlueprintKey(BlueprintScope.Global, path.blueprintKey)

            assertSame(path, result)
        }

        @Test
        fun `rejects when more than one active version exists for global scope`() {
            val blueprintKey = UUID.randomUUID()
            val first = blueprintPathFixture(status = BlueprintStatus.ACTIVE)
            val second = blueprintPathFixture(status = BlueprintStatus.ACTIVE)
            every {
                blueprintPathRepository
                    .findByProjectIdNullAndBlueprintKeyAndStatus(blueprintKey, BlueprintStatus.ACTIVE)
            } returns mutableListOf(first, second)

            assertBlueprintStatus(HttpStatus.INTERNAL_SERVER_ERROR) {
                service.findActiveForAuthorizedBlueprintKey(BlueprintScope.Global, blueprintKey)
            }
        }

        @Test
        fun `returns null when no active version exists for project scope`() {
            val projectId = UUID.randomUUID()
            val blueprintKey = UUID.randomUUID()
            every {
                blueprintPathRepository
                    .findByProjectIdAndBlueprintKeyAndStatus(projectId, blueprintKey, BlueprintStatus.ACTIVE)
            } returns mutableListOf()

            val result = service.findActiveForAuthorizedBlueprintKey(BlueprintScope.Project(projectId), blueprintKey)

            assertNull(result)
            verify(exactly = 1) {
                blueprintPathRepository
                    .findByProjectIdAndBlueprintKeyAndStatus(projectId, blueprintKey, BlueprintStatus.ACTIVE)
            }
        }

        @Test
        fun `returns the single active version for project scope`() {
            val projectId = UUID.randomUUID()
            val path = blueprintPathFixture(projectId = projectId, status = BlueprintStatus.ACTIVE)
            every {
                blueprintPathRepository
                    .findByProjectIdAndBlueprintKeyAndStatus(projectId, path.blueprintKey, BlueprintStatus.ACTIVE)
            } returns mutableListOf(path)

            val result =
                service.findActiveForAuthorizedBlueprintKey(BlueprintScope.Project(projectId), path.blueprintKey)

            assertSame(path, result)
        }

        @Test
        fun `rejects when more than one active version exists for project scope`() {
            val projectId = UUID.randomUUID()
            val blueprintKey = UUID.randomUUID()
            val first = blueprintPathFixture(projectId = projectId, status = BlueprintStatus.ACTIVE)
            val second = blueprintPathFixture(projectId = projectId, status = BlueprintStatus.ACTIVE)
            every {
                blueprintPathRepository
                    .findByProjectIdAndBlueprintKeyAndStatus(projectId, blueprintKey, BlueprintStatus.ACTIVE)
            } returns mutableListOf(first, second)

            assertBlueprintStatus(HttpStatus.INTERNAL_SERVER_ERROR) {
                service.findActiveForAuthorizedBlueprintKey(BlueprintScope.Project(projectId), blueprintKey)
            }
        }
    }

    @Nested
    inner class FindDraftForAuthorizedBlueprintKey {
        @Test
        fun `returns null when no draft version exists for global scope`() {
            val blueprintKey = UUID.randomUUID()
            every {
                blueprintPathRepository.findByProjectIdNullAndBlueprintKeyAndStatus(blueprintKey, BlueprintStatus.DRAFT)
            } returns mutableListOf()

            val result = service.findDraftForAuthorizedBlueprintKey(BlueprintScope.Global, blueprintKey)

            assertNull(result)
        }

        @Test
        fun `returns the single draft version for global scope`() {
            val path = blueprintPathFixture(status = BlueprintStatus.DRAFT)
            every {
                blueprintPathRepository
                    .findByProjectIdNullAndBlueprintKeyAndStatus(path.blueprintKey, BlueprintStatus.DRAFT)
            } returns mutableListOf(path)

            val result = service.findDraftForAuthorizedBlueprintKey(BlueprintScope.Global, path.blueprintKey)

            assertSame(path, result)
        }

        @Test
        fun `rejects when more than one draft version exists for global scope`() {
            val blueprintKey = UUID.randomUUID()
            val first = blueprintPathFixture(status = BlueprintStatus.DRAFT)
            val second = blueprintPathFixture(status = BlueprintStatus.DRAFT)
            every {
                blueprintPathRepository.findByProjectIdNullAndBlueprintKeyAndStatus(blueprintKey, BlueprintStatus.DRAFT)
            } returns mutableListOf(first, second)

            assertBlueprintStatus(HttpStatus.INTERNAL_SERVER_ERROR) {
                service.findDraftForAuthorizedBlueprintKey(BlueprintScope.Global, blueprintKey)
            }
        }

        @Test
        fun `returns null when no draft version exists for project scope`() {
            val projectId = UUID.randomUUID()
            val blueprintKey = UUID.randomUUID()
            every {
                blueprintPathRepository
                    .findByProjectIdAndBlueprintKeyAndStatus(projectId, blueprintKey, BlueprintStatus.DRAFT)
            } returns mutableListOf()

            val result = service.findDraftForAuthorizedBlueprintKey(BlueprintScope.Project(projectId), blueprintKey)

            assertNull(result)
            verify(exactly = 1) {
                blueprintPathRepository
                    .findByProjectIdAndBlueprintKeyAndStatus(projectId, blueprintKey, BlueprintStatus.DRAFT)
            }
        }

        @Test
        fun `returns the single draft version for project scope`() {
            val projectId = UUID.randomUUID()
            val path = blueprintPathFixture(projectId = projectId, status = BlueprintStatus.DRAFT)
            every {
                blueprintPathRepository
                    .findByProjectIdAndBlueprintKeyAndStatus(projectId, path.blueprintKey, BlueprintStatus.DRAFT)
            } returns mutableListOf(path)

            val result =
                service.findDraftForAuthorizedBlueprintKey(BlueprintScope.Project(projectId), path.blueprintKey)

            assertSame(path, result)
        }

        @Test
        fun `rejects when more than one draft version exists for project scope`() {
            val projectId = UUID.randomUUID()
            val blueprintKey = UUID.randomUUID()
            val first = blueprintPathFixture(projectId = projectId, status = BlueprintStatus.DRAFT)
            val second = blueprintPathFixture(projectId = projectId, status = BlueprintStatus.DRAFT)
            every {
                blueprintPathRepository
                    .findByProjectIdAndBlueprintKeyAndStatus(projectId, blueprintKey, BlueprintStatus.DRAFT)
            } returns mutableListOf(first, second)

            assertBlueprintStatus(HttpStatus.INTERNAL_SERVER_ERROR) {
                service.findDraftForAuthorizedBlueprintKey(BlueprintScope.Project(projectId), blueprintKey)
            }
        }
    }

    @Nested
    inner class GetArchivedForAuthorizedBlueprintKey {
        @Test
        fun `returns the archived version for global scope`() {
            val path = blueprintPathFixture(status = BlueprintStatus.ARCHIVED)
            every {
                blueprintPathRepository.findByProjectIdNullAndBlueprintKeyAndVersion(path.blueprintKey, path.version)
            } returns mutableListOf(path)

            val result =
                service.getArchivedForAuthorizedBlueprintKey(BlueprintScope.Global, path.blueprintKey, path.version)

            assertSame(path, result)
            verify(exactly = 1) {
                blueprintPathRepository.findByProjectIdNullAndBlueprintKeyAndVersion(path.blueprintKey, path.version)
            }
        }

        @Test
        fun `returns the archived version for project scope`() {
            val projectId = UUID.randomUUID()
            val path = blueprintPathFixture(projectId = projectId, status = BlueprintStatus.ARCHIVED)
            every {
                blueprintPathRepository
                    .findByProjectIdAndBlueprintKeyAndVersion(projectId, path.blueprintKey, path.version)
            } returns mutableListOf(path)

            val result =
                service.getArchivedForAuthorizedBlueprintKey(
                    BlueprintScope.Project(projectId),
                    path.blueprintKey,
                    path.version,
                )

            assertSame(path, result)
            verify(exactly = 1) {
                blueprintPathRepository
                    .findByProjectIdAndBlueprintKeyAndVersion(projectId, path.blueprintKey, path.version)
            }
        }

        @Test
        fun `rejects with internal server error when the archived version is missing for global scope`() {
            val blueprintKey = UUID.randomUUID()
            every {
                blueprintPathRepository.findByProjectIdNullAndBlueprintKeyAndVersion(blueprintKey, 2)
            } returns mutableListOf()

            assertBlueprintStatus(HttpStatus.INTERNAL_SERVER_ERROR) {
                service.getArchivedForAuthorizedBlueprintKey(BlueprintScope.Global, blueprintKey, 2)
            }
        }

        @Test
        fun `rejects with internal server error when the archived version is missing for project scope`() {
            val projectId = UUID.randomUUID()
            val blueprintKey = UUID.randomUUID()
            every {
                blueprintPathRepository.findByProjectIdAndBlueprintKeyAndVersion(projectId, blueprintKey, 2)
            } returns mutableListOf()

            assertBlueprintStatus(HttpStatus.INTERNAL_SERVER_ERROR) {
                service.getArchivedForAuthorizedBlueprintKey(BlueprintScope.Project(projectId), blueprintKey, 2)
            }
        }

        @Test
        fun `rejects with internal server error when the archived version is duplicated for global scope`() {
            val blueprintKey = UUID.randomUUID()
            val first = blueprintPathFixture(status = BlueprintStatus.ARCHIVED)
            val second = blueprintPathFixture(status = BlueprintStatus.ARCHIVED)
            every {
                blueprintPathRepository.findByProjectIdNullAndBlueprintKeyAndVersion(blueprintKey, 3)
            } returns mutableListOf(first, second)

            assertBlueprintStatus(HttpStatus.INTERNAL_SERVER_ERROR) {
                service.getArchivedForAuthorizedBlueprintKey(BlueprintScope.Global, blueprintKey, 3)
            }
        }

        @Test
        fun `rejects with internal server error when the archived version is duplicated for project scope`() {
            val projectId = UUID.randomUUID()
            val blueprintKey = UUID.randomUUID()
            val first = blueprintPathFixture(projectId = projectId, status = BlueprintStatus.ARCHIVED)
            val second = blueprintPathFixture(projectId = projectId, status = BlueprintStatus.ARCHIVED)
            every {
                blueprintPathRepository.findByProjectIdAndBlueprintKeyAndVersion(projectId, blueprintKey, 3)
            } returns mutableListOf(first, second)

            assertBlueprintStatus(HttpStatus.INTERNAL_SERVER_ERROR) {
                service.getArchivedForAuthorizedBlueprintKey(BlueprintScope.Project(projectId), blueprintKey, 3)
            }
        }
    }

    @Nested
    inner class GetAuthorizedPhase {
        @Test
        fun `returns the phase for global scope`() {
            val phase = blueprintPhaseFixture()
            every { blueprintPhaseRepository.findByBlueprintPathProjectIdIsNullAndId(phase.id) } returns phase

            val result = service.getAuthorizedPhase(BlueprintScope.Global, phase.id)

            assertSame(phase, result)
        }

        @Test
        fun `resolves a project phase only through the project-qualified repository query`() {
            val projectId = UUID.randomUUID()
            val phase = blueprintPhaseFixture(blueprintPathFixture(projectId))
            every { blueprintPhaseRepository.findByBlueprintPathProjectIdAndId(projectId, phase.id) } returns phase

            val result = service.getAuthorizedPhase(BlueprintScope.Project(projectId), phase.id)

            assertSame(phase, result)
            verify(exactly = 1) { blueprintPhaseRepository.findByBlueprintPathProjectIdAndId(projectId, phase.id) }
        }

        @Test
        fun `rejects with not found when the phase is missing`() {
            val phaseId = UUID.randomUUID()
            every { blueprintPhaseRepository.findByBlueprintPathProjectIdIsNullAndId(phaseId) } returns null

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.getAuthorizedPhase(BlueprintScope.Global, phaseId)
            }
        }
    }

    @Nested
    inner class GetAuthorizedEditablePhase {
        @Test
        fun `returns the phase when its path is a draft for global scope`() {
            val phase = blueprintPhaseFixture(blueprintPathFixture(status = BlueprintStatus.DRAFT))
            every { blueprintPhaseRepository.findByBlueprintPathProjectIdIsNullAndId(phase.id) } returns phase

            val result = service.getAuthorizedEditablePhase(BlueprintScope.Global, phase.id)

            assertSame(phase, result)
        }

        @Test
        fun `returns the phase when its path is a draft for project scope`() {
            val projectId = UUID.randomUUID()
            val phase = blueprintPhaseFixture(blueprintPathFixture(projectId, BlueprintStatus.DRAFT))
            every { blueprintPhaseRepository.findByBlueprintPathProjectIdAndId(projectId, phase.id) } returns phase

            val result = service.getAuthorizedEditablePhase(BlueprintScope.Project(projectId), phase.id)

            assertSame(phase, result)
        }

        @Test
        fun `rejects with not found when the phase is missing`() {
            val phaseId = UUID.randomUUID()
            every { blueprintPhaseRepository.findByBlueprintPathProjectIdIsNullAndId(phaseId) } returns null

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.getAuthorizedEditablePhase(BlueprintScope.Global, phaseId)
            }
        }

        @Test
        fun `rejects when the owning path is active`() {
            val phase = blueprintPhaseFixture(blueprintPathFixture(status = BlueprintStatus.ACTIVE))
            every { blueprintPhaseRepository.findByBlueprintPathProjectIdIsNullAndId(phase.id) } returns phase

            assertBlueprintStatus(HttpStatus.CONFLICT) {
                service.getAuthorizedEditablePhase(BlueprintScope.Global, phase.id)
            }
        }
    }

    @Nested
    inner class GetAuthorizedStep {
        @Test
        fun `returns the step for global scope`() {
            val step = blueprintStepFixture()
            every {
                blueprintStepRepository.findByBlueprintPhaseBlueprintPathProjectIdIsNullAndId(step.id)
            } returns step

            val result = service.getAuthorizedStep(BlueprintScope.Global, step.id)

            assertSame(step, result)
        }

        @Test
        fun `resolves a project step only through the project-qualified repository query`() {
            val projectId = UUID.randomUUID()
            val step = blueprintStepFixture(blueprintPhaseFixture(blueprintPathFixture(projectId)))
            every {
                blueprintStepRepository.findByBlueprintPhaseBlueprintPathProjectIdAndId(projectId, step.id)
            } returns step

            val result = service.getAuthorizedStep(BlueprintScope.Project(projectId), step.id)

            assertSame(step, result)
            verify(exactly = 1) {
                blueprintStepRepository.findByBlueprintPhaseBlueprintPathProjectIdAndId(projectId, step.id)
            }
        }

        @Test
        fun `rejects with not found when the step is missing`() {
            val stepId = UUID.randomUUID()
            every {
                blueprintStepRepository.findByBlueprintPhaseBlueprintPathProjectIdIsNullAndId(stepId)
            } returns null

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.getAuthorizedStep(BlueprintScope.Global, stepId)
            }
        }
    }

    @Nested
    inner class GetAuthorizedEditableStep {
        @Test
        fun `returns the step when its path is a draft for global scope`() {
            val step = blueprintStepFixture(blueprintPhaseFixture(blueprintPathFixture(status = BlueprintStatus.DRAFT)))
            every {
                blueprintStepRepository.findByBlueprintPhaseBlueprintPathProjectIdIsNullAndId(step.id)
            } returns step

            val result = service.getAuthorizedEditableStep(BlueprintScope.Global, step.id)

            assertSame(step, result)
        }

        @Test
        fun `returns the step when its path is a draft for project scope`() {
            val projectId = UUID.randomUUID()
            val step =
                blueprintStepFixture(blueprintPhaseFixture(blueprintPathFixture(projectId, BlueprintStatus.DRAFT)))
            every {
                blueprintStepRepository.findByBlueprintPhaseBlueprintPathProjectIdAndId(projectId, step.id)
            } returns step

            val result = service.getAuthorizedEditableStep(BlueprintScope.Project(projectId), step.id)

            assertSame(step, result)
        }

        @Test
        fun `rejects with not found when the step is missing`() {
            val stepId = UUID.randomUUID()
            every {
                blueprintStepRepository.findByBlueprintPhaseBlueprintPathProjectIdIsNullAndId(stepId)
            } returns null

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.getAuthorizedEditableStep(BlueprintScope.Global, stepId)
            }
        }

        @Test
        fun `rejects when the owning path is archived`() {
            val step =
                blueprintStepFixture(blueprintPhaseFixture(blueprintPathFixture(status = BlueprintStatus.ARCHIVED)))
            every {
                blueprintStepRepository.findByBlueprintPhaseBlueprintPathProjectIdIsNullAndId(step.id)
            } returns step

            assertBlueprintStatus(HttpStatus.CONFLICT) {
                service.getAuthorizedEditableStep(BlueprintScope.Global, step.id)
            }
        }
    }

    @Nested
    inner class GetAuthorizedResource {
        @Test
        fun `returns the resource for global scope`() {
            val resource = makeResource()
            every {
                blueprintResourceRepository
                    .findByBlueprintStepBlueprintPhaseBlueprintPathProjectIdIsNullAndId(resource.id)
            } returns resource

            val result = service.getAuthorizedResource(BlueprintScope.Global, resource.id)

            assertSame(resource, result)
        }

        @Test
        fun `resolves a project resource only through the project-qualified repository query`() {
            val projectId = UUID.randomUUID()
            val resource = makeResource(blueprintStepFixture(blueprintPhaseFixture(blueprintPathFixture(projectId))))
            every {
                blueprintResourceRepository
                    .findByBlueprintStepBlueprintPhaseBlueprintPathProjectIdAndId(projectId, resource.id)
            } returns resource

            val result = service.getAuthorizedResource(BlueprintScope.Project(projectId), resource.id)

            assertSame(resource, result)
            verify(exactly = 1) {
                blueprintResourceRepository
                    .findByBlueprintStepBlueprintPhaseBlueprintPathProjectIdAndId(projectId, resource.id)
            }
        }

        @Test
        fun `rejects with not found when the resource is missing`() {
            val resourceId = UUID.randomUUID()
            every {
                blueprintResourceRepository
                    .findByBlueprintStepBlueprintPhaseBlueprintPathProjectIdIsNullAndId(resourceId)
            } returns null

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.getAuthorizedResource(BlueprintScope.Global, resourceId)
            }
        }
    }

    @Nested
    inner class GetAuthorizedEditableResource {
        @Test
        fun `returns the resource when its path is a draft for global scope`() {
            val resource =
                makeResource(
                    blueprintStepFixture(blueprintPhaseFixture(blueprintPathFixture(status = BlueprintStatus.DRAFT))),
                )
            every {
                blueprintResourceRepository
                    .findByBlueprintStepBlueprintPhaseBlueprintPathProjectIdIsNullAndId(resource.id)
            } returns resource

            val result = service.getAuthorizedEditableResource(BlueprintScope.Global, resource.id)

            assertSame(resource, result)
        }

        @Test
        fun `returns the resource when its path is a draft for project scope`() {
            val projectId = UUID.randomUUID()
            val resource =
                makeResource(
                    blueprintStepFixture(
                        blueprintPhaseFixture(blueprintPathFixture(projectId, BlueprintStatus.DRAFT)),
                    ),
                )
            every {
                blueprintResourceRepository
                    .findByBlueprintStepBlueprintPhaseBlueprintPathProjectIdAndId(projectId, resource.id)
            } returns resource

            val result = service.getAuthorizedEditableResource(BlueprintScope.Project(projectId), resource.id)

            assertSame(resource, result)
        }

        @Test
        fun `rejects with not found when the resource is missing`() {
            val resourceId = UUID.randomUUID()
            every {
                blueprintResourceRepository
                    .findByBlueprintStepBlueprintPhaseBlueprintPathProjectIdIsNullAndId(resourceId)
            } returns null

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.getAuthorizedEditableResource(BlueprintScope.Global, resourceId)
            }
        }

        @Test
        fun `rejects when the owning path is active`() {
            val resource =
                makeResource(
                    blueprintStepFixture(
                        blueprintPhaseFixture(blueprintPathFixture(status = BlueprintStatus.ACTIVE)),
                    ),
                )
            every {
                blueprintResourceRepository
                    .findByBlueprintStepBlueprintPhaseBlueprintPathProjectIdIsNullAndId(resource.id)
            } returns resource

            assertBlueprintStatus(HttpStatus.CONFLICT) {
                service.getAuthorizedEditableResource(BlueprintScope.Global, resource.id)
            }
        }
    }

    @Nested
    inner class GetAuthorizedTask {
        @Test
        fun `returns the task for global scope`() {
            val task = makeTask()
            every {
                blueprintTaskRepository
                    .findByBlueprintStepBlueprintPhaseBlueprintPathProjectIdIsNullAndId(task.id)
            } returns task

            val result = service.getAuthorizedTask(BlueprintScope.Global, task.id)

            assertSame(task, result)
        }

        @Test
        fun `resolves a project task only through the project-qualified repository query`() {
            val projectId = UUID.randomUUID()
            val task = makeTask(blueprintStepFixture(blueprintPhaseFixture(blueprintPathFixture(projectId))))
            every {
                blueprintTaskRepository
                    .findByBlueprintStepBlueprintPhaseBlueprintPathProjectIdAndId(projectId, task.id)
            } returns task

            val result = service.getAuthorizedTask(BlueprintScope.Project(projectId), task.id)

            assertSame(task, result)
            verify(exactly = 1) {
                blueprintTaskRepository
                    .findByBlueprintStepBlueprintPhaseBlueprintPathProjectIdAndId(projectId, task.id)
            }
        }

        @Test
        fun `rejects with not found when the task is missing`() {
            val taskId = UUID.randomUUID()
            every {
                blueprintTaskRepository
                    .findByBlueprintStepBlueprintPhaseBlueprintPathProjectIdIsNullAndId(taskId)
            } returns null

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.getAuthorizedTask(BlueprintScope.Global, taskId)
            }
        }
    }

    @Nested
    inner class GetAuthorizedEditableTask {
        @Test
        fun `returns the task when its path is a draft for global scope`() {
            val task =
                makeTask(
                    blueprintStepFixture(blueprintPhaseFixture(blueprintPathFixture(status = BlueprintStatus.DRAFT))),
                )
            every {
                blueprintTaskRepository
                    .findByBlueprintStepBlueprintPhaseBlueprintPathProjectIdIsNullAndId(task.id)
            } returns task

            val result = service.getAuthorizedEditableTask(BlueprintScope.Global, task.id)

            assertSame(task, result)
        }

        @Test
        fun `returns the task when its path is a draft for project scope`() {
            val projectId = UUID.randomUUID()
            val task =
                makeTask(
                    blueprintStepFixture(
                        blueprintPhaseFixture(blueprintPathFixture(projectId, BlueprintStatus.DRAFT)),
                    ),
                )
            every {
                blueprintTaskRepository
                    .findByBlueprintStepBlueprintPhaseBlueprintPathProjectIdAndId(projectId, task.id)
            } returns task

            val result = service.getAuthorizedEditableTask(BlueprintScope.Project(projectId), task.id)

            assertSame(task, result)
        }

        @Test
        fun `rejects with not found when the task is missing`() {
            val taskId = UUID.randomUUID()
            every {
                blueprintTaskRepository
                    .findByBlueprintStepBlueprintPhaseBlueprintPathProjectIdIsNullAndId(taskId)
            } returns null

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.getAuthorizedEditableTask(BlueprintScope.Global, taskId)
            }
        }

        @Test
        fun `rejects when the owning path is archived`() {
            val task =
                makeTask(
                    blueprintStepFixture(
                        blueprintPhaseFixture(blueprintPathFixture(status = BlueprintStatus.ARCHIVED)),
                    ),
                )
            every {
                blueprintTaskRepository
                    .findByBlueprintStepBlueprintPhaseBlueprintPathProjectIdIsNullAndId(task.id)
            } returns task

            assertBlueprintStatus(HttpStatus.CONFLICT) {
                service.getAuthorizedEditableTask(BlueprintScope.Global, task.id)
            }
        }
    }

    @Nested
    inner class GetAuthorizedCheckQuestion {
        @Test
        fun `returns the question for global scope`() {
            val question = blueprintQuestionFixture()
            every {
                blueprintCheckQuestionRepository
                    .findByBlueprintPhaseBlueprintPathProjectIdIsNullAndId(question.id)
            } returns question

            val result = service.getAuthorizedCheckQuestion(BlueprintScope.Global, question.id)

            assertSame(question, result)
        }

        @Test
        fun `resolves a project question only through the project-qualified repository query`() {
            val projectId = UUID.randomUUID()
            val question = blueprintQuestionFixture(blueprintPhaseFixture(blueprintPathFixture(projectId)))
            every {
                blueprintCheckQuestionRepository
                    .findByBlueprintPhaseBlueprintPathProjectIdAndId(projectId, question.id)
            } returns question

            val result = service.getAuthorizedCheckQuestion(BlueprintScope.Project(projectId), question.id)

            assertSame(question, result)
            verify(exactly = 1) {
                blueprintCheckQuestionRepository
                    .findByBlueprintPhaseBlueprintPathProjectIdAndId(projectId, question.id)
            }
        }

        @Test
        fun `rejects with not found when the question is missing`() {
            val questionId = UUID.randomUUID()
            every {
                blueprintCheckQuestionRepository
                    .findByBlueprintPhaseBlueprintPathProjectIdIsNullAndId(questionId)
            } returns null

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.getAuthorizedCheckQuestion(BlueprintScope.Global, questionId)
            }
        }
    }

    @Nested
    inner class GetAuthorizedEditableCheckQuestion {
        @Test
        fun `returns the question when its path is a draft for global scope`() {
            val question =
                blueprintQuestionFixture(blueprintPhaseFixture(blueprintPathFixture(status = BlueprintStatus.DRAFT)))
            every {
                blueprintCheckQuestionRepository
                    .findByBlueprintPhaseBlueprintPathProjectIdIsNullAndId(question.id)
            } returns question

            val result = service.getAuthorizedEditableCheckQuestion(BlueprintScope.Global, question.id)

            assertSame(question, result)
        }

        @Test
        fun `returns the question when its path is a draft for project scope`() {
            val projectId = UUID.randomUUID()
            val question =
                blueprintQuestionFixture(
                    blueprintPhaseFixture(blueprintPathFixture(projectId, BlueprintStatus.DRAFT)),
                )
            every {
                blueprintCheckQuestionRepository
                    .findByBlueprintPhaseBlueprintPathProjectIdAndId(projectId, question.id)
            } returns question

            val result = service.getAuthorizedEditableCheckQuestion(BlueprintScope.Project(projectId), question.id)

            assertSame(question, result)
        }

        @Test
        fun `rejects with not found when the question is missing`() {
            val questionId = UUID.randomUUID()
            every {
                blueprintCheckQuestionRepository
                    .findByBlueprintPhaseBlueprintPathProjectIdIsNullAndId(questionId)
            } returns null

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.getAuthorizedEditableCheckQuestion(BlueprintScope.Global, questionId)
            }
        }

        @Test
        fun `rejects when the owning path is active`() {
            val question =
                blueprintQuestionFixture(blueprintPhaseFixture(blueprintPathFixture(status = BlueprintStatus.ACTIVE)))
            every {
                blueprintCheckQuestionRepository
                    .findByBlueprintPhaseBlueprintPathProjectIdIsNullAndId(question.id)
            } returns question

            assertBlueprintStatus(HttpStatus.CONFLICT) {
                service.getAuthorizedEditableCheckQuestion(BlueprintScope.Global, question.id)
            }
        }
    }

    @Nested
    inner class GetAuthorizedCheckOption {
        @Test
        fun `returns the option for global scope`() {
            val option = makeOption()
            every {
                blueprintCheckOptionRepository
                    .findByBlueprintCheckQuestionBlueprintPhaseBlueprintPathProjectIdIsNullAndId(option.id)
            } returns option

            val result = service.getAuthorizedCheckOption(BlueprintScope.Global, option.id)

            assertSame(option, result)
        }

        @Test
        fun `resolves a project option only through the project-qualified repository query`() {
            val projectId = UUID.randomUUID()
            val option = makeOption(blueprintQuestionFixture(blueprintPhaseFixture(blueprintPathFixture(projectId))))
            every {
                blueprintCheckOptionRepository
                    .findByBlueprintCheckQuestionBlueprintPhaseBlueprintPathProjectIdAndId(projectId, option.id)
            } returns option

            val result = service.getAuthorizedCheckOption(BlueprintScope.Project(projectId), option.id)

            assertSame(option, result)
            verify(exactly = 1) {
                blueprintCheckOptionRepository
                    .findByBlueprintCheckQuestionBlueprintPhaseBlueprintPathProjectIdAndId(projectId, option.id)
            }
        }

        @Test
        fun `rejects with not found when the option is missing`() {
            val optionId = UUID.randomUUID()
            every {
                blueprintCheckOptionRepository
                    .findByBlueprintCheckQuestionBlueprintPhaseBlueprintPathProjectIdIsNullAndId(optionId)
            } returns null

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.getAuthorizedCheckOption(BlueprintScope.Global, optionId)
            }
        }
    }

    @Nested
    inner class GetAuthorizedEditableCheckOption {
        @Test
        fun `returns the option when its path is a draft for global scope`() {
            val option =
                makeOption(
                    blueprintQuestionFixture(
                        blueprintPhaseFixture(blueprintPathFixture(status = BlueprintStatus.DRAFT)),
                    ),
                )
            every {
                blueprintCheckOptionRepository
                    .findByBlueprintCheckQuestionBlueprintPhaseBlueprintPathProjectIdIsNullAndId(option.id)
            } returns option

            val result = service.getAuthorizedEditableCheckOption(BlueprintScope.Global, option.id)

            assertSame(option, result)
        }

        @Test
        fun `returns the option when its path is a draft for project scope`() {
            val projectId = UUID.randomUUID()
            val option =
                makeOption(
                    blueprintQuestionFixture(
                        blueprintPhaseFixture(blueprintPathFixture(projectId, BlueprintStatus.DRAFT)),
                    ),
                )
            every {
                blueprintCheckOptionRepository
                    .findByBlueprintCheckQuestionBlueprintPhaseBlueprintPathProjectIdAndId(projectId, option.id)
            } returns option

            val result = service.getAuthorizedEditableCheckOption(BlueprintScope.Project(projectId), option.id)

            assertSame(option, result)
        }

        @Test
        fun `rejects with not found when the option is missing`() {
            val optionId = UUID.randomUUID()
            every {
                blueprintCheckOptionRepository
                    .findByBlueprintCheckQuestionBlueprintPhaseBlueprintPathProjectIdIsNullAndId(optionId)
            } returns null

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.getAuthorizedEditableCheckOption(BlueprintScope.Global, optionId)
            }
        }

        @Test
        fun `rejects when the owning path is archived`() {
            val option =
                makeOption(
                    blueprintQuestionFixture(
                        blueprintPhaseFixture(blueprintPathFixture(status = BlueprintStatus.ARCHIVED)),
                    ),
                )
            every {
                blueprintCheckOptionRepository
                    .findByBlueprintCheckQuestionBlueprintPhaseBlueprintPathProjectIdIsNullAndId(option.id)
            } returns option

            assertBlueprintStatus(HttpStatus.CONFLICT) {
                service.getAuthorizedEditableCheckOption(BlueprintScope.Global, option.id)
            }
        }
    }

    @Nested
    inner class GetAuthorizedEditableSubGraphNode {
        @Test
        fun `returns the node when its path is a draft for global scope`() {
            val node = blueprintStepFixture(blueprintPhaseFixture(blueprintPathFixture(status = BlueprintStatus.DRAFT)))
            every {
                blueprintSubGraphNodeRepository.findByBlueprintPhaseBlueprintPathProjectIdIsNullAndId(node.id)
            } returns node

            val result = service.getAuthorizedEditableSubGraphNode(BlueprintScope.Global, node.id)

            assertSame(node, result)
        }

        @Test
        fun `returns the node when its path is a draft for project scope`() {
            val projectId = UUID.randomUUID()
            val node =
                blueprintStepFixture(blueprintPhaseFixture(blueprintPathFixture(projectId, BlueprintStatus.DRAFT)))
            every {
                blueprintSubGraphNodeRepository.findByBlueprintPhaseBlueprintPathProjectIdAndId(projectId, node.id)
            } returns node

            val result = service.getAuthorizedEditableSubGraphNode(BlueprintScope.Project(projectId), node.id)

            assertSame(node, result)
            verify(exactly = 1) {
                blueprintSubGraphNodeRepository.findByBlueprintPhaseBlueprintPathProjectIdAndId(projectId, node.id)
            }
        }

        @Test
        fun `rejects with not found when the node is missing`() {
            val nodeId = UUID.randomUUID()
            every {
                blueprintSubGraphNodeRepository.findByBlueprintPhaseBlueprintPathProjectIdIsNullAndId(nodeId)
            } returns null

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.getAuthorizedEditableSubGraphNode(BlueprintScope.Global, nodeId)
            }
        }

        @Test
        fun `rejects when the owning path is active`() {
            val node =
                blueprintStepFixture(blueprintPhaseFixture(blueprintPathFixture(status = BlueprintStatus.ACTIVE)))
            every {
                blueprintSubGraphNodeRepository.findByBlueprintPhaseBlueprintPathProjectIdIsNullAndId(node.id)
            } returns node

            assertBlueprintStatus(HttpStatus.CONFLICT) {
                service.getAuthorizedEditableSubGraphNode(BlueprintScope.Global, node.id)
            }
        }
    }
}
