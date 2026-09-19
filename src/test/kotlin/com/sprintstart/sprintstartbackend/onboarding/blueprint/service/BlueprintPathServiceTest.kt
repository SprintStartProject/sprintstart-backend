package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.blueprint.factory.BlueprintPathCopyFactory
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPath
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.path.CreateBlueprintPathRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.path.UpdateBlueprintPathRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintPathRepository
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

class BlueprintPathServiceTest {
    private val blueprintAccessService: BlueprintAccessService = mockk()
    private val blueprintPathRepository: BlueprintPathRepository = mockk()
    private val blueprintPathCopyFactory: BlueprintPathCopyFactory = mockk()
    private val entityManager: EntityManager = mockk()
    private val service =
        BlueprintPathService(
            blueprintAccessService,
            blueprintPathRepository,
            blueprintPathCopyFactory,
            entityManager,
        )

    private fun makePath(
        blueprintKey: UUID = UUID.randomUUID(),
        projectId: UUID? = null,
        title: String = "Blueprint",
        description: String? = "Description",
        version: Int = 0,
        revision: Long = 0,
        status: BlueprintStatus = BlueprintStatus.DRAFT,
    ): BlueprintPath {
        return BlueprintPath(
            blueprintKey = blueprintKey,
            projectId = projectId,
            title = title,
            description = description,
            version = version,
            status = status,
        ).also { it.revision = revision }
    }

    private fun allowPersist() {
        every { entityManager.persist(any()) } just runs
    }

    private fun allowFlush() {
        every { entityManager.flush() } just runs
    }

    @Nested
    inner class GetBlueprintPathOverviewsGroupedByBlueprintKey {
        @Test
        fun `returns mapped overviews for global scope`() {
            val first = makePath(status = BlueprintStatus.ACTIVE)
            val second = makePath(status = BlueprintStatus.ARCHIVED)
            every {
                blueprintPathRepository.findLatestVersionForEachBlueprintKeyAndProjectIdIsNull()
            } returns listOf(first, second)

            val result = service.getBlueprintPathOverviewsGroupedByBlueprintKey(BlueprintScope.Global)

            assertEquals(listOf(first.id, second.id), result.map { it.id })
            assertEquals(listOf(first.blueprintKey, second.blueprintKey), result.map { it.blueprintKey })
            assertEquals(BlueprintStatus.ACTIVE, result[0].status)
            verify(exactly = 0) {
                blueprintPathRepository.findLatestVersionForEachBlueprintKeyAndProjectId(any())
            }
        }

        @Test
        fun `restricts project scope overview lookup by project id`() {
            val projectId = UUID.randomUUID()
            val path = makePath(projectId = projectId, status = BlueprintStatus.ACTIVE)
            every {
                blueprintPathRepository.findLatestVersionForEachBlueprintKeyAndProjectId(projectId)
            } returns listOf(path)

            val result = service.getBlueprintPathOverviewsGroupedByBlueprintKey(BlueprintScope.Project(projectId))

            assertEquals(listOf(path.id), result.map { it.id })
            verify(exactly = 0) {
                blueprintPathRepository.findLatestVersionForEachBlueprintKeyAndProjectIdIsNull()
            }
        }
    }

    @Nested
    inner class GetBlueprintPathHistoryByBlueprintKey {
        @Test
        fun `returns version history for global scope`() {
            val blueprintKey = UUID.randomUUID()
            val newer = makePath(blueprintKey = blueprintKey, version = 1, status = BlueprintStatus.ACTIVE)
            val older = makePath(blueprintKey = blueprintKey, version = 0, status = BlueprintStatus.ARCHIVED)
            every {
                blueprintPathRepository.findAllByProjectIdNullAndBlueprintKeyOrderByVersionDesc(blueprintKey)
            } returns mutableListOf(newer, older)

            val result = service.getBlueprintPathHistoryByBlueprintKey(BlueprintScope.Global, blueprintKey)

            assertEquals(listOf(newer.id, older.id), result.map { it.id })
            assertEquals(listOf(1, 0), result.map { it.version })
        }

        @Test
        fun `restricts project scope history lookup by project id`() {
            val projectId = UUID.randomUUID()
            val blueprintKey = UUID.randomUUID()
            val path = makePath(blueprintKey = blueprintKey, projectId = projectId, status = BlueprintStatus.ACTIVE)
            every {
                blueprintPathRepository.findAllByProjectIdAndBlueprintKeyOrderByVersionDesc(projectId, blueprintKey)
            } returns mutableListOf(path)

            val result =
                service.getBlueprintPathHistoryByBlueprintKey(BlueprintScope.Project(projectId), blueprintKey)

            assertEquals(listOf(path.id), result.map { it.id })
            verify(exactly = 0) {
                blueprintPathRepository.findAllByProjectIdNullAndBlueprintKeyOrderByVersionDesc(any())
            }
        }
    }

    @Nested
    inner class GetBlueprintPathOverviewsForProjectId {
        @Test
        fun `returns mapped overviews for the project`() {
            val projectId = UUID.randomUUID()
            val first = makePath(projectId = projectId)
            val second = makePath(projectId = projectId, status = BlueprintStatus.ACTIVE)
            every { blueprintPathRepository.findAllByProjectId(projectId) } returns mutableListOf(first, second)

            val result = service.getBlueprintPathOverviewsForProjectId(projectId)

            assertEquals(listOf(first.id, second.id), result.map { it.id })
            assertEquals(listOf(first.title, second.title), result.map { it.title })
        }

        @Test
        fun `returns empty list when the project has no paths`() {
            val projectId = UUID.randomUUID()
            every { blueprintPathRepository.findAllByProjectId(projectId) } returns mutableListOf()

            val result = service.getBlueprintPathOverviewsForProjectId(projectId)

            assertEquals(emptyList(), result)
        }
    }

    @Nested
    inner class GetBlueprintPathById {
        @Test
        fun `returns mapped path for global scope`() {
            val path = makePath(status = BlueprintStatus.ACTIVE)
            every { blueprintAccessService.getAuthorizedPath(BlueprintScope.Global, path.id) } returns path

            val result = service.getBlueprintPathById(BlueprintScope.Global, path.id)

            assertEquals(path.id, result.id)
            assertEquals(path.blueprintKey, result.blueprintKey)
            assertEquals(path.title, result.title)
            assertEquals(BlueprintStatus.ACTIVE, result.status)
        }

        @Test
        fun `returns mapped path for project scope`() {
            val projectId = UUID.randomUUID()
            val path = makePath(projectId = projectId, status = BlueprintStatus.ACTIVE)
            every {
                blueprintAccessService.getAuthorizedPath(BlueprintScope.Project(projectId), path.id)
            } returns path

            val result = service.getBlueprintPathById(BlueprintScope.Project(projectId), path.id)

            assertEquals(path.id, result.id)
        }

        @Test
        fun `propagates not found when the path is missing in the scope`() {
            val pathId = UUID.randomUUID()
            every { blueprintAccessService.getAuthorizedPath(BlueprintScope.Global, pathId) } throws
                ResponseStatusException(HttpStatus.NOT_FOUND, "Blueprint Path not found for this scope")

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.getBlueprintPathById(BlueprintScope.Global, pathId)
            }
        }
    }

    @Nested
    inner class CreateBlueprintPath {
        @Test
        fun `creates a project-owned version zero draft`() {
            val projectId = UUID.randomUUID()
            every { blueprintPathRepository.save(any()) } answers { firstArg() }

            val result =
                service.createBlueprintPath(
                    BlueprintScope.Project(projectId),
                    CreateBlueprintPathRequest(title = "New path", description = "Description"),
                )

            assertEquals("New path", result.title)
            assertEquals("Description", result.description)
            assertEquals(0, result.version)
            assertEquals(0L, result.revision)
            assertEquals(BlueprintStatus.DRAFT, result.status)
            verify {
                blueprintPathRepository.save(
                    match {
                        it.projectId == projectId &&
                            it.version == 0 &&
                            it.revision == 0L &&
                            it.status == BlueprintStatus.DRAFT
                    },
                )
            }
        }

        @Test
        fun `creates a globally owned version zero draft`() {
            every { blueprintPathRepository.save(any()) } answers { firstArg() }

            val result =
                service.createBlueprintPath(
                    BlueprintScope.Global,
                    CreateBlueprintPathRequest(title = "Global path", description = "Description"),
                )

            assertEquals("Global path", result.title)
            assertEquals(0, result.version)
            assertEquals(BlueprintStatus.DRAFT, result.status)
            verify {
                blueprintPathRepository.save(match { it.projectId == null && it.version == 0 })
            }
        }
    }

    @Nested
    inner class OpenBlueprintPathDraftByBlueprintKey {
        @Test
        fun `copies the active path into the next draft version for global scope`() {
            val blueprintKey = UUID.randomUUID()
            val activePath = makePath(blueprintKey = blueprintKey, version = 2, status = BlueprintStatus.ACTIVE)
            val copy = makePath(blueprintKey = blueprintKey, version = 3, status = BlueprintStatus.DRAFT)
            every {
                blueprintAccessService.findActiveForAuthorizedBlueprintKey(BlueprintScope.Global, blueprintKey)
            } returns activePath
            every {
                blueprintAccessService.findDraftForAuthorizedBlueprintKey(BlueprintScope.Global, blueprintKey)
            } returns null
            every {
                blueprintPathCopyFactory.createCopyFrom(
                    path = activePath,
                    blueprintKey = blueprintKey,
                    projectId = null,
                    status = BlueprintStatus.DRAFT,
                    version = 3,
                )
            } returns copy
            allowPersist()
            allowFlush()

            val result = service.openBlueprintPathDraftByBlueprintKey(BlueprintScope.Global, blueprintKey)

            assertEquals(copy.id, result.id)
            assertEquals(3, result.version)
            assertEquals(BlueprintStatus.DRAFT, result.status)
            verify(exactly = 1) { entityManager.persist(copy) }
            verify(exactly = 1) { entityManager.flush() }
        }

        @Test
        fun `copies the active path with project ownership for project scope`() {
            val projectId = UUID.randomUUID()
            val blueprintKey = UUID.randomUUID()
            val activePath = makePath(
                blueprintKey = blueprintKey,
                projectId = projectId,
                version = 0,
                status = BlueprintStatus.ACTIVE,
            )
            val copy = makePath(
                blueprintKey = blueprintKey,
                projectId = projectId,
                version = 1,
                status = BlueprintStatus.DRAFT,
            )
            val scope = BlueprintScope.Project(projectId)
            every {
                blueprintAccessService.findActiveForAuthorizedBlueprintKey(scope, blueprintKey)
            } returns activePath
            every {
                blueprintAccessService.findDraftForAuthorizedBlueprintKey(scope, blueprintKey)
            } returns null
            every {
                blueprintPathCopyFactory.createCopyFrom(
                    path = activePath,
                    blueprintKey = blueprintKey,
                    projectId = projectId,
                    status = BlueprintStatus.DRAFT,
                    version = 1,
                )
            } returns copy
            allowPersist()
            allowFlush()

            val result = service.openBlueprintPathDraftByBlueprintKey(scope, blueprintKey)

            assertEquals(copy.id, result.id)
            verify(exactly = 1) {
                blueprintPathCopyFactory.createCopyFrom(activePath, blueprintKey, projectId, any(), 1)
            }
        }

        @Test
        fun `returns the existing draft without copying when one already exists`() {
            val blueprintKey = UUID.randomUUID()
            val activePath = makePath(blueprintKey = blueprintKey, version = 2, status = BlueprintStatus.ACTIVE)
            val existingDraft = makePath(blueprintKey = blueprintKey, version = 3, status = BlueprintStatus.DRAFT)
            every {
                blueprintAccessService.findActiveForAuthorizedBlueprintKey(BlueprintScope.Global, blueprintKey)
            } returns activePath
            every {
                blueprintAccessService.findDraftForAuthorizedBlueprintKey(BlueprintScope.Global, blueprintKey)
            } returns existingDraft

            val result = service.openBlueprintPathDraftByBlueprintKey(BlueprintScope.Global, blueprintKey)

            assertEquals(existingDraft.id, result.id)
            assertEquals(BlueprintStatus.DRAFT, result.status)
            verify(exactly = 0) { blueprintPathCopyFactory.createCopyFrom(any(), any(), any(), any(), any()) }
            verify(exactly = 0) { entityManager.persist(any()) }
            verify(exactly = 0) { entityManager.flush() }
        }

        @Test
        fun `rejects opening a draft when no active path exists for global scope`() {
            val blueprintKey = UUID.randomUUID()
            every {
                blueprintAccessService.findActiveForAuthorizedBlueprintKey(BlueprintScope.Global, blueprintKey)
            } returns null

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.openBlueprintPathDraftByBlueprintKey(BlueprintScope.Global, blueprintKey)
            }
            verify(exactly = 0) { blueprintPathCopyFactory.createCopyFrom(any(), any(), any(), any(), any()) }
        }

        @Test
        fun `rejects opening a draft when no active path exists for project scope`() {
            val projectId = UUID.randomUUID()
            val blueprintKey = UUID.randomUUID()
            every {
                blueprintAccessService.findActiveForAuthorizedBlueprintKey(
                    BlueprintScope.Project(projectId),
                    blueprintKey,
                )
            } returns null

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.openBlueprintPathDraftByBlueprintKey(BlueprintScope.Project(projectId), blueprintKey)
            }
            verify(exactly = 0) { blueprintPathCopyFactory.createCopyFrom(any(), any(), any(), any(), any()) }
        }
    }

    @Nested
    inner class PublishBlueprintPathDraftById {
        @Test
        fun `activates the draft and archives the previously active version for global scope`() {
            val blueprintKey = UUID.randomUUID()
            val activePath = makePath(blueprintKey = blueprintKey, version = 0, status = BlueprintStatus.ACTIVE)
            val draft = makePath(blueprintKey = blueprintKey, version = 1, status = BlueprintStatus.DRAFT)
            every {
                blueprintAccessService.getAuthorizedDraftPath(BlueprintScope.Global, draft.id)
            } returns draft
            every {
                blueprintAccessService.findActiveForAuthorizedBlueprintKey(BlueprintScope.Global, blueprintKey)
            } returns activePath

            val result = service.publishBlueprintPathDraftById(BlueprintScope.Global, draft.id)

            assertEquals(draft.id, result.id)
            assertEquals(BlueprintStatus.ACTIVE, result.status)
            assertEquals(BlueprintStatus.ACTIVE, draft.status)
            assertEquals(BlueprintStatus.ARCHIVED, activePath.status)
        }

        @Test
        fun `activates the draft and archives the previously active version for project scope`() {
            val projectId = UUID.randomUUID()
            val blueprintKey = UUID.randomUUID()
            val scope = BlueprintScope.Project(projectId)
            val activePath = makePath(
                blueprintKey = blueprintKey,
                projectId = projectId,
                version = 0,
                status = BlueprintStatus.ACTIVE,
            )
            val draft = makePath(
                blueprintKey = blueprintKey,
                projectId = projectId,
                version = 1,
                status = BlueprintStatus.DRAFT,
            )
            every { blueprintAccessService.getAuthorizedDraftPath(scope, draft.id) } returns draft
            every {
                blueprintAccessService.findActiveForAuthorizedBlueprintKey(scope, blueprintKey)
            } returns activePath

            val result = service.publishBlueprintPathDraftById(scope, draft.id)

            assertEquals(BlueprintStatus.ACTIVE, result.status)
            assertEquals(BlueprintStatus.ARCHIVED, activePath.status)
        }

        @Test
        fun `rejects publishing when no active version exists`() {
            val blueprintKey = UUID.randomUUID()
            val draft = makePath(blueprintKey = blueprintKey, version = 1, status = BlueprintStatus.DRAFT)
            every {
                blueprintAccessService.getAuthorizedDraftPath(BlueprintScope.Global, draft.id)
            } returns draft
            every {
                blueprintAccessService.findActiveForAuthorizedBlueprintKey(BlueprintScope.Global, blueprintKey)
            } returns null

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.publishBlueprintPathDraftById(BlueprintScope.Global, draft.id)
            }
            assertEquals(BlueprintStatus.DRAFT, draft.status)
        }

        @Test
        fun `propagates not found when the path is missing in the scope`() {
            val pathId = UUID.randomUUID()
            every { blueprintAccessService.getAuthorizedDraftPath(BlueprintScope.Global, pathId) } throws
                ResponseStatusException(HttpStatus.NOT_FOUND, "Blueprint Path not found for this scope")

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.publishBlueprintPathDraftById(BlueprintScope.Global, pathId)
            }
        }

        @Test
        fun `propagates conflict when the path is not a draft`() {
            val pathId = UUID.randomUUID()
            every { blueprintAccessService.getAuthorizedDraftPath(BlueprintScope.Global, pathId) } throws
                ResponseStatusException(HttpStatus.CONFLICT, "Blueprint can only be modified while in DRAFT status")

            assertBlueprintStatus(HttpStatus.CONFLICT) {
                service.publishBlueprintPathDraftById(BlueprintScope.Global, pathId)
            }
        }
    }

    @Nested
    inner class RollbackBlueprintPathByBlueprintKey {
        @Test
        fun `deletes later versions and reactivates the archive for global scope`() {
            val blueprintKey = UUID.randomUUID()
            val activePath = makePath(blueprintKey = blueprintKey, version = 3, status = BlueprintStatus.ACTIVE)
            val archivedPath = makePath(blueprintKey = blueprintKey, version = 1, status = BlueprintStatus.ARCHIVED)
            every {
                blueprintAccessService.findActiveForAuthorizedBlueprintKey(BlueprintScope.Global, blueprintKey)
            } returns activePath
            every {
                blueprintAccessService.getArchivedForAuthorizedBlueprintKey(BlueprintScope.Global, blueprintKey, 1)
            } returns archivedPath
            every {
                blueprintPathRepository
                    .deleteAllByProjectIdIsNullAndBlueprintKeyAndVersionAfter(blueprintKey, 1)
            } just runs

            val result = service.rollbackBlueprintPathByBlueprintKey(BlueprintScope.Global, blueprintKey, 1)

            assertEquals(archivedPath.id, result.id)
            assertEquals(BlueprintStatus.ACTIVE, result.status)
            assertEquals(BlueprintStatus.ACTIVE, archivedPath.status)
            verify(exactly = 1) {
                blueprintPathRepository
                    .deleteAllByProjectIdIsNullAndBlueprintKeyAndVersionAfter(blueprintKey, 1)
            }
            verify(exactly = 0) {
                blueprintPathRepository
                    .deleteAllByProjectIdAndBlueprintKeyAndVersionAfter(any(), any(), any())
            }
        }

        @Test
        fun `deletes later versions and reactivates the archive for project scope`() {
            val projectId = UUID.randomUUID()
            val blueprintKey = UUID.randomUUID()
            val scope = BlueprintScope.Project(projectId)
            val activePath = makePath(
                blueprintKey = blueprintKey,
                projectId = projectId,
                version = 2,
                status = BlueprintStatus.ACTIVE,
            )
            val archivedPath = makePath(
                blueprintKey = blueprintKey,
                projectId = projectId,
                version = 0,
                status = BlueprintStatus.ARCHIVED,
            )
            every {
                blueprintAccessService.findActiveForAuthorizedBlueprintKey(scope, blueprintKey)
            } returns activePath
            every {
                blueprintAccessService.getArchivedForAuthorizedBlueprintKey(scope, blueprintKey, 0)
            } returns archivedPath
            every {
                blueprintPathRepository
                    .deleteAllByProjectIdAndBlueprintKeyAndVersionAfter(projectId, blueprintKey, 0)
            } just runs

            val result = service.rollbackBlueprintPathByBlueprintKey(scope, blueprintKey, 0)

            assertEquals(archivedPath.id, result.id)
            assertEquals(BlueprintStatus.ACTIVE, archivedPath.status)
            verify(exactly = 1) {
                blueprintPathRepository
                    .deleteAllByProjectIdAndBlueprintKeyAndVersionAfter(projectId, blueprintKey, 0)
            }
        }

        @Test
        fun `rejects rollback when no active path exists`() {
            val blueprintKey = UUID.randomUUID()
            every {
                blueprintAccessService.findActiveForAuthorizedBlueprintKey(BlueprintScope.Global, blueprintKey)
            } returns null

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.rollbackBlueprintPathByBlueprintKey(BlueprintScope.Global, blueprintKey, 0)
            }
            verify(exactly = 0) {
                blueprintPathRepository
                    .deleteAllByProjectIdIsNullAndBlueprintKeyAndVersionAfter(any(), any())
            }
        }

        @Test
        fun `rejects rollback to the active or a newer version`() {
            val blueprintKey = UUID.randomUUID()
            val activePath = makePath(blueprintKey = blueprintKey, version = 2, status = BlueprintStatus.ACTIVE)
            every {
                blueprintAccessService.findActiveForAuthorizedBlueprintKey(BlueprintScope.Global, blueprintKey)
            } returns activePath

            assertBlueprintStatus(HttpStatus.BAD_REQUEST) {
                service.rollbackBlueprintPathByBlueprintKey(BlueprintScope.Global, blueprintKey, 2)
            }
            assertBlueprintStatus(HttpStatus.BAD_REQUEST) {
                service.rollbackBlueprintPathByBlueprintKey(BlueprintScope.Global, blueprintKey, 5)
            }
        }

        @Test
        fun `rejects rollback to a negative version`() {
            val blueprintKey = UUID.randomUUID()
            val activePath = makePath(blueprintKey = blueprintKey, version = 2, status = BlueprintStatus.ACTIVE)
            every {
                blueprintAccessService.findActiveForAuthorizedBlueprintKey(BlueprintScope.Global, blueprintKey)
            } returns activePath

            assertBlueprintStatus(HttpStatus.BAD_REQUEST) {
                service.rollbackBlueprintPathByBlueprintKey(BlueprintScope.Global, blueprintKey, -1)
            }
        }

        @Test
        fun `propagates server error when the archived version is missing`() {
            val blueprintKey = UUID.randomUUID()
            val activePath = makePath(blueprintKey = blueprintKey, version = 2, status = BlueprintStatus.ACTIVE)
            every {
                blueprintAccessService.findActiveForAuthorizedBlueprintKey(BlueprintScope.Global, blueprintKey)
            } returns activePath
            every {
                blueprintAccessService.getArchivedForAuthorizedBlueprintKey(BlueprintScope.Global, blueprintKey, 1)
            } throws ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Archived blueprint path not found")

            assertBlueprintStatus(HttpStatus.INTERNAL_SERVER_ERROR) {
                service.rollbackBlueprintPathByBlueprintKey(BlueprintScope.Global, blueprintKey, 1)
            }
            verify(exactly = 0) {
                blueprintPathRepository
                    .deleteAllByProjectIdIsNullAndBlueprintKeyAndVersionAfter(any(), any())
            }
        }
    }

    @Nested
    inner class UpdateBlueprintPathById {
        @Test
        fun `updates title and description when the revision matches for global scope`() {
            val draft = makePath(title = "Old title", revision = 4, status = BlueprintStatus.DRAFT)
            every {
                blueprintAccessService.getAuthorizedDraftPath(BlueprintScope.Global, draft.id)
            } returns draft
            every { blueprintPathRepository.save(any()) } answers { firstArg() }

            val result =
                service.updateBlueprintPathById(
                    BlueprintScope.Global,
                    draft.id,
                    UpdateBlueprintPathRequest(
                        title = "New title",
                        description = "New description",
                        version = draft.version,
                        revision = 4,
                    ),
                )

            assertEquals("New title", result.title)
            assertEquals("New description", result.description)
            assertEquals("New title", draft.title)
            assertEquals("New description", draft.description)
            verify(exactly = 1) { blueprintPathRepository.save(draft) }
        }

        @Test
        fun `updates title and description when the revision matches for project scope`() {
            val projectId = UUID.randomUUID()
            val scope = BlueprintScope.Project(projectId)
            val draft = makePath(projectId = projectId, title = "Old title", revision = 0)
            every { blueprintAccessService.getAuthorizedDraftPath(scope, draft.id) } returns draft
            every { blueprintPathRepository.save(any()) } answers { firstArg() }

            val result =
                service.updateBlueprintPathById(
                    scope,
                    draft.id,
                    UpdateBlueprintPathRequest(
                        title = "New title",
                        description = "New description",
                        version = draft.version,
                        revision = 0,
                    ),
                )

            assertEquals("New title", result.title)
            verify(exactly = 1) { blueprintPathRepository.save(draft) }
        }

        @Test
        fun `rejects a stale revision without changing the draft`() {
            val draft = makePath(title = "Old title", description = "Old description", revision = 4)
            every {
                blueprintAccessService.getAuthorizedDraftPath(BlueprintScope.Global, draft.id)
            } returns draft

            assertBlueprintStatus(HttpStatus.CONFLICT) {
                service.updateBlueprintPathById(
                    BlueprintScope.Global,
                    draft.id,
                    UpdateBlueprintPathRequest(
                        title = "New title",
                        description = "New description",
                        version = draft.version,
                        revision = 3,
                    ),
                )
            }
            assertEquals("Old title", draft.title)
            assertEquals("Old description", draft.description)
            verify(exactly = 0) { blueprintPathRepository.save(any()) }
        }

        @Test
        fun `propagates not found when the path is missing in the scope`() {
            val pathId = UUID.randomUUID()
            every { blueprintAccessService.getAuthorizedDraftPath(BlueprintScope.Global, pathId) } throws
                ResponseStatusException(HttpStatus.NOT_FOUND, "Blueprint Path not found for this scope")

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.updateBlueprintPathById(
                    BlueprintScope.Global,
                    pathId,
                    UpdateBlueprintPathRequest(
                        title = "New title",
                        description = "New description",
                        version = 0,
                        revision = 0,
                    ),
                )
            }
            verify(exactly = 0) { blueprintPathRepository.save(any()) }
        }

        @Test
        fun `propagates conflict when the path is not a draft`() {
            val pathId = UUID.randomUUID()
            every { blueprintAccessService.getAuthorizedDraftPath(BlueprintScope.Global, pathId) } throws
                ResponseStatusException(HttpStatus.CONFLICT, "Blueprint can only be modified while in DRAFT status")

            assertBlueprintStatus(HttpStatus.CONFLICT) {
                service.updateBlueprintPathById(
                    BlueprintScope.Global,
                    pathId,
                    UpdateBlueprintPathRequest(
                        title = "New title",
                        description = "New description",
                        version = 0,
                        revision = 0,
                    ),
                )
            }
            verify(exactly = 0) { blueprintPathRepository.save(any()) }
        }
    }

    @Nested
    inner class DeleteBlueprintPathDraftById {
        @Test
        fun `deletes the draft for global scope`() {
            val draft = makePath(status = BlueprintStatus.DRAFT)
            every { blueprintAccessService.getAuthorizedPath(BlueprintScope.Global, draft.id) } returns draft
            every { blueprintPathRepository.delete(draft) } just runs

            service.deleteBlueprintPathDraftById(BlueprintScope.Global, draft.id)

            verify(exactly = 1) { blueprintPathRepository.delete(draft) }
        }

        @Test
        fun `deletes the draft for project scope`() {
            val projectId = UUID.randomUUID()
            val scope = BlueprintScope.Project(projectId)
            val draft = makePath(projectId = projectId, status = BlueprintStatus.DRAFT)
            every { blueprintAccessService.getAuthorizedPath(scope, draft.id) } returns draft
            every { blueprintPathRepository.delete(draft) } just runs

            service.deleteBlueprintPathDraftById(scope, draft.id)

            verify(exactly = 1) { blueprintPathRepository.delete(draft) }
        }

        @Test
        fun `rejects deleting an active path`() {
            val path = makePath(status = BlueprintStatus.ACTIVE)
            every { blueprintAccessService.getAuthorizedPath(BlueprintScope.Global, path.id) } returns path

            assertBlueprintStatus(HttpStatus.BAD_REQUEST) {
                service.deleteBlueprintPathDraftById(BlueprintScope.Global, path.id)
            }
            verify(exactly = 0) { blueprintPathRepository.delete(any()) }
        }

        @Test
        fun `rejects deleting an archived path`() {
            val path = makePath(status = BlueprintStatus.ARCHIVED)
            every { blueprintAccessService.getAuthorizedPath(BlueprintScope.Global, path.id) } returns path

            assertBlueprintStatus(HttpStatus.BAD_REQUEST) {
                service.deleteBlueprintPathDraftById(BlueprintScope.Global, path.id)
            }
            verify(exactly = 0) { blueprintPathRepository.delete(any()) }
        }

        @Test
        fun `propagates not found when the path is missing in the scope`() {
            val pathId = UUID.randomUUID()
            every { blueprintAccessService.getAuthorizedPath(BlueprintScope.Global, pathId) } throws
                ResponseStatusException(HttpStatus.NOT_FOUND, "Blueprint Path not found for this scope")

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.deleteBlueprintPathDraftById(BlueprintScope.Global, pathId)
            }
            verify(exactly = 0) { blueprintPathRepository.delete(any()) }
        }
    }

    @Nested
    inner class ArchiveBlueprintPathByBlueprintKey {
        @Test
        fun `archives the active path and deletes the unpublished draft for global scope`() {
            val blueprintKey = UUID.randomUUID()
            val activePath = makePath(blueprintKey = blueprintKey, status = BlueprintStatus.ACTIVE)
            val draft = makePath(blueprintKey = blueprintKey, version = 1, status = BlueprintStatus.DRAFT)
            every {
                blueprintAccessService.findActiveForAuthorizedBlueprintKey(BlueprintScope.Global, blueprintKey)
            } returns activePath
            every {
                blueprintAccessService.findDraftForAuthorizedBlueprintKey(BlueprintScope.Global, blueprintKey)
            } returns draft
            every { blueprintPathRepository.delete(draft) } just runs

            service.archiveBlueprintPathByBlueprintKey(BlueprintScope.Global, blueprintKey)

            assertEquals(BlueprintStatus.ARCHIVED, activePath.status)
            verify(exactly = 1) { blueprintPathRepository.delete(draft) }
        }

        @Test
        fun `archives the active path and deletes the unpublished draft for project scope`() {
            val projectId = UUID.randomUUID()
            val blueprintKey = UUID.randomUUID()
            val scope = BlueprintScope.Project(projectId)
            val activePath = makePath(
                blueprintKey = blueprintKey,
                projectId = projectId,
                status = BlueprintStatus.ACTIVE,
            )
            val draft = makePath(
                blueprintKey = blueprintKey,
                projectId = projectId,
                version = 1,
                status = BlueprintStatus.DRAFT,
            )
            every {
                blueprintAccessService.findActiveForAuthorizedBlueprintKey(scope, blueprintKey)
            } returns activePath
            every {
                blueprintAccessService.findDraftForAuthorizedBlueprintKey(scope, blueprintKey)
            } returns draft
            every { blueprintPathRepository.delete(draft) } just runs

            service.archiveBlueprintPathByBlueprintKey(scope, blueprintKey)

            assertEquals(BlueprintStatus.ARCHIVED, activePath.status)
            verify(exactly = 1) { blueprintPathRepository.delete(draft) }
        }

        @Test
        fun `archives the active path without a delete when no draft exists`() {
            val blueprintKey = UUID.randomUUID()
            val activePath = makePath(blueprintKey = blueprintKey, status = BlueprintStatus.ACTIVE)
            every {
                blueprintAccessService.findActiveForAuthorizedBlueprintKey(BlueprintScope.Global, blueprintKey)
            } returns activePath
            every {
                blueprintAccessService.findDraftForAuthorizedBlueprintKey(BlueprintScope.Global, blueprintKey)
            } returns null

            service.archiveBlueprintPathByBlueprintKey(BlueprintScope.Global, blueprintKey)

            assertEquals(BlueprintStatus.ARCHIVED, activePath.status)
            verify(exactly = 0) { blueprintPathRepository.delete(any()) }
        }

        @Test
        fun `rejects archiving when no active path exists`() {
            val blueprintKey = UUID.randomUUID()
            every {
                blueprintAccessService.findActiveForAuthorizedBlueprintKey(BlueprintScope.Global, blueprintKey)
            } returns null

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.archiveBlueprintPathByBlueprintKey(BlueprintScope.Global, blueprintKey)
            }
            verify(exactly = 0) { blueprintPathRepository.delete(any()) }
        }
    }
}
