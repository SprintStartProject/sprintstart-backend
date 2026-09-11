package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintResource
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintStep
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.resource.CreateBlueprintResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.resource.DeleteBlueprintResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.resource.UpdateBlueprintResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintResourceRepository
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

class BlueprintResourceServiceTest {
    private val blueprintAccessService: BlueprintAccessService = mockk()
    private val blueprintResourceRepository: BlueprintResourceRepository = mockk()
    private val service = BlueprintResourceService(blueprintAccessService, blueprintResourceRepository)

    private fun makeResource(
        step: BlueprintStep = blueprintStepFixture(),
        revision: Long = 0,
        title: String = "Resource",
        description: String = "Description",
        url: String = "https://example.com",
    ): BlueprintResource {
        return BlueprintResource(
            blueprintStep = step,
            title = title,
            description = description,
            url = url,
        ).also { it.revision = revision }
    }

    @Nested
    inner class GetBlueprintResourcesForStep {
        @Test
        fun `returns mapped resources from the global query`() {
            val step = blueprintStepFixture()
            val resource = makeResource(step, revision = 2, title = "Guide", description = "Details")
            every {
                blueprintResourceRepository
                    .findAllByBlueprintStepBlueprintPhaseBlueprintPathProjectIdIsNullAndBlueprintStepId(step.id)
            } returns mutableListOf(resource)

            val result = service.getBlueprintResourcesForStep(BlueprintScope.Global, step.id)

            assertEquals(resource.id, result.single().id)
            assertEquals(step.id, result.single().blueprintStepId)
            assertEquals(2, result.single().revision)
            assertEquals("Guide", result.single().title)
            assertEquals("Details", result.single().description)
            assertEquals("https://example.com", result.single().url)
            verify(exactly = 1) {
                blueprintResourceRepository
                    .findAllByBlueprintStepBlueprintPhaseBlueprintPathProjectIdIsNullAndBlueprintStepId(step.id)
            }
        }

        @Test
        fun `restricts project scope lookup by project id`() {
            val projectId = UUID.randomUUID()
            val step = blueprintStepFixture(blueprintPhaseFixture(blueprintPathFixture(projectId)))
            val resource = makeResource(step)
            every {
                blueprintResourceRepository
                    .findAllByBlueprintStepBlueprintPhaseBlueprintPathProjectIdAndBlueprintStepId(projectId, step.id)
            } returns mutableListOf(resource)

            val result = service.getBlueprintResourcesForStep(BlueprintScope.Project(projectId), step.id)

            assertEquals(listOf(resource.id), result.map { it.id })
            verify(exactly = 1) {
                blueprintResourceRepository
                    .findAllByBlueprintStepBlueprintPhaseBlueprintPathProjectIdAndBlueprintStepId(projectId, step.id)
            }
        }
    }

    @Nested
    inner class GetBlueprintResourceById {
        @Test
        fun `returns mapped resource for global scope`() {
            val resource = makeResource(revision = 3, title = "Guide")
            every { blueprintAccessService.getAuthorizedResource(BlueprintScope.Global, resource.id) } returns
                resource

            val result = service.getBlueprintResourceById(BlueprintScope.Global, resource.id)

            assertEquals(resource.id, result.id)
            assertEquals(resource.blueprintStep.id, result.blueprintStepId)
            assertEquals(3, result.revision)
            assertEquals("Guide", result.title)
            assertEquals("https://example.com", result.url)
            verify(exactly = 1) {
                blueprintAccessService.getAuthorizedResource(BlueprintScope.Global, resource.id)
            }
        }

        @Test
        fun `returns mapped resource for project scope`() {
            val scope = BlueprintScope.Project(UUID.randomUUID())
            val resource = makeResource()
            every { blueprintAccessService.getAuthorizedResource(scope, resource.id) } returns resource

            val result = service.getBlueprintResourceById(scope, resource.id)

            assertEquals(resource.id, result.id)
            verify(exactly = 1) { blueprintAccessService.getAuthorizedResource(scope, resource.id) }
        }

        @Test
        fun `propagates not found from the access service`() {
            val resourceId = UUID.randomUUID()
            every { blueprintAccessService.getAuthorizedResource(BlueprintScope.Global, resourceId) } throws
                ResponseStatusException(HttpStatus.NOT_FOUND)

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.getBlueprintResourceById(BlueprintScope.Global, resourceId)
            }
        }
    }

    @Nested
    inner class CreateBlueprintResourceForStep {
        @Test
        fun `saves the new resource under the given step`() {
            val step = blueprintStepFixture()
            every { blueprintAccessService.getAuthorizedEditableStep(BlueprintScope.Global, step.id) } returns step
            every { blueprintResourceRepository.save(any()) } answers { firstArg() }

            val result =
                service.createBlueprintResourceForStep(
                    BlueprintScope.Global,
                    step.id,
                    CreateBlueprintResourceRequest(
                        title = "Guide",
                        description = "Details",
                        url = "https://example.com/guide",
                    ),
                )

            assertEquals(step.id, result.blueprintStepId)
            assertEquals("Guide", result.title)
            assertEquals("Details", result.description)
            assertEquals("https://example.com/guide", result.url)
            verify(exactly = 1) { blueprintResourceRepository.save(any()) }
            verify(exactly = 1) { blueprintAccessService.getAuthorizedEditableStep(BlueprintScope.Global, step.id) }
        }

        @Test
        fun `creates resource for project scope`() {
            val scope = BlueprintScope.Project(UUID.randomUUID())
            val step = blueprintStepFixture()
            every { blueprintAccessService.getAuthorizedEditableStep(scope, step.id) } returns step
            every { blueprintResourceRepository.save(any()) } answers { firstArg() }

            val result =
                service.createBlueprintResourceForStep(
                    scope,
                    step.id,
                    CreateBlueprintResourceRequest(
                        title = "Resource",
                        description = "Description",
                        url = "https://example.com",
                    ),
                )

            assertEquals(step.id, result.blueprintStepId)
            verify(exactly = 1) { blueprintAccessService.getAuthorizedEditableStep(scope, step.id) }
            verify(exactly = 1) { blueprintResourceRepository.save(any()) }
        }

        @Test
        fun `propagates not found from the access service`() {
            val stepId = UUID.randomUUID()
            every { blueprintAccessService.getAuthorizedEditableStep(BlueprintScope.Global, stepId) } throws
                ResponseStatusException(HttpStatus.NOT_FOUND)

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.createBlueprintResourceForStep(
                    BlueprintScope.Global,
                    stepId,
                    CreateBlueprintResourceRequest(
                        title = "Resource",
                        description = "Description",
                        url = "https://example.com",
                    ),
                )
            }
            verify(exactly = 0) { blueprintResourceRepository.save(any()) }
        }
    }

    @Nested
    inner class UpdateBlueprintResourceById {
        @Test
        fun `updates fields and persists when the revision matches`() {
            val resource = makeResource(revision = 4, title = "Old", description = "Old", url = "https://old.com")
            every {
                blueprintAccessService.getAuthorizedEditableResource(BlueprintScope.Global, resource.id)
            } returns resource
            every { blueprintResourceRepository.save(any()) } answers { firstArg() }

            val result =
                service.updateBlueprintResourceById(
                    BlueprintScope.Global,
                    resource.id,
                    UpdateBlueprintResourceRequest(
                        revision = 4,
                        title = "Updated",
                        description = "Updated",
                        url = "https://example.com/updated",
                    ),
                )

            assertEquals(resource.id, result.id)
            assertEquals("Updated", result.title)
            assertEquals("Updated", result.description)
            assertEquals("https://example.com/updated", result.url)
            assertEquals(4, result.revision)
            assertEquals("Updated", resource.title)
            verify(exactly = 1) { blueprintResourceRepository.save(any()) }
        }

        @Test
        fun `updates resource for project scope`() {
            val scope = BlueprintScope.Project(UUID.randomUUID())
            val resource = makeResource(revision = 1)
            every { blueprintAccessService.getAuthorizedEditableResource(scope, resource.id) } returns resource
            every { blueprintResourceRepository.save(any()) } answers { firstArg() }

            val result =
                service.updateBlueprintResourceById(
                    scope,
                    resource.id,
                    UpdateBlueprintResourceRequest(
                        revision = 1,
                        title = "Updated",
                        description = "Updated",
                        url = "https://example.com/updated",
                    ),
                )

            assertEquals("Updated", result.title)
            verify(exactly = 1) { blueprintAccessService.getAuthorizedEditableResource(scope, resource.id) }
            verify(exactly = 1) { blueprintResourceRepository.save(any()) }
        }

        @Test
        fun `rejects stale resource updates without saving`() {
            val resource = makeResource(revision = 4, title = "Resource")
            every {
                blueprintAccessService.getAuthorizedEditableResource(BlueprintScope.Global, resource.id)
            } returns resource

            assertBlueprintStatus(HttpStatus.CONFLICT) {
                service.updateBlueprintResourceById(
                    BlueprintScope.Global,
                    resource.id,
                    UpdateBlueprintResourceRequest(
                        revision = 3,
                        title = "Updated",
                        description = "Updated",
                        url = "https://example.com/updated",
                    ),
                )
            }
            assertEquals("Resource", resource.title)
            verify(exactly = 0) { blueprintResourceRepository.save(any()) }
        }

        @Test
        fun `propagates not found from the access service`() {
            val resourceId = UUID.randomUUID()
            every { blueprintAccessService.getAuthorizedEditableResource(BlueprintScope.Global, resourceId) } throws
                ResponseStatusException(HttpStatus.NOT_FOUND)

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.updateBlueprintResourceById(
                    BlueprintScope.Global,
                    resourceId,
                    UpdateBlueprintResourceRequest(
                        revision = 0,
                        title = "Updated",
                        description = "Updated",
                        url = "https://example.com/updated",
                    ),
                )
            }
            verify(exactly = 0) { blueprintResourceRepository.save(any()) }
        }
    }

    @Nested
    inner class DeleteBlueprintResourceById {
        @Test
        fun `deletes the resource when the revision matches`() {
            val resource = makeResource(revision = 6)
            every {
                blueprintAccessService.getAuthorizedEditableResource(BlueprintScope.Global, resource.id)
            } returns resource
            every { blueprintResourceRepository.delete(resource) } just runs

            service.deleteBlueprintResourceById(
                BlueprintScope.Global,
                resource.id,
                DeleteBlueprintResourceRequest(revision = 6),
            )

            verify(exactly = 1) { blueprintResourceRepository.delete(resource) }
        }

        @Test
        fun `deletes the resource for project scope`() {
            val scope = BlueprintScope.Project(UUID.randomUUID())
            val resource = makeResource(revision = 6)
            every { blueprintAccessService.getAuthorizedEditableResource(scope, resource.id) } returns resource
            every { blueprintResourceRepository.delete(resource) } just runs

            service.deleteBlueprintResourceById(scope, resource.id, DeleteBlueprintResourceRequest(revision = 6))

            verify(exactly = 1) { blueprintAccessService.getAuthorizedEditableResource(scope, resource.id) }
            verify(exactly = 1) { blueprintResourceRepository.delete(resource) }
        }

        @Test
        fun `rejects stale revision without deleting`() {
            val resource = makeResource(revision = 6)
            every {
                blueprintAccessService.getAuthorizedEditableResource(BlueprintScope.Global, resource.id)
            } returns resource

            assertBlueprintStatus(HttpStatus.CONFLICT) {
                service.deleteBlueprintResourceById(
                    BlueprintScope.Global,
                    resource.id,
                    DeleteBlueprintResourceRequest(revision = 5),
                )
            }
            verify(exactly = 0) { blueprintResourceRepository.delete(any()) }
        }

        @Test
        fun `propagates not found from the access service`() {
            val resourceId = UUID.randomUUID()
            every { blueprintAccessService.getAuthorizedEditableResource(BlueprintScope.Global, resourceId) } throws
                ResponseStatusException(HttpStatus.NOT_FOUND)

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.deleteBlueprintResourceById(
                    BlueprintScope.Global,
                    resourceId,
                    DeleteBlueprintResourceRequest(revision = 0),
                )
            }
            verify(exactly = 0) { blueprintResourceRepository.delete(any()) }
        }
    }
}
