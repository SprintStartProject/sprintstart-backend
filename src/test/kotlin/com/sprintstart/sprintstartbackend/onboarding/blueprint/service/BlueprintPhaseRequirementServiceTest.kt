package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.RequirementType
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhase
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhaseRequirement
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phaseRequirement.CreateBlueprintPhaseRequirementRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phaseRequirement.CreateBlueprintPhaseRequirementsRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phaseRequirement.DeleteBlueprintPhaseRequirementsRequest
import com.sprintstart.sprintstartbackend.user.external.ProjectRoleApi
import com.sprintstart.sprintstartbackend.user.external.SkillsApi
import com.sprintstart.sprintstartbackend.user.external.dto.ProjectRoleShortDto
import com.sprintstart.sprintstartbackend.user.external.dto.SkillDto
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlueprintPhaseRequirementServiceTest {
    private val blueprintAccessService: BlueprintAccessService = mockk()
    private val skillsApi: SkillsApi = mockk()
    private val projectRoleApi: ProjectRoleApi = mockk()
    private val entityManager: EntityManager = mockk()
    private val service =
        BlueprintPhaseRequirementService(
            blueprintAccessService,
            skillsApi,
            projectRoleApi,
            entityManager,
        )

    private fun makePhase(revision: Long = 0): BlueprintPhase {
        return blueprintPhaseFixture().also { it.revision = revision }
    }

    private fun makeRequirement(
        phase: BlueprintPhase,
        type: RequirementType = RequirementType.SKILL,
        referenceId: UUID = UUID.randomUUID(),
        displayName: String = "Requirement",
    ): BlueprintPhaseRequirement {
        return BlueprintPhaseRequirement(
            blueprintPhase = phase,
            type = type,
            referenceId = referenceId,
            displayName = displayName,
        )
    }

    private fun allowFlush() {
        every { entityManager.flush() } just runs
    }

    private fun allowLock(phase: BlueprintPhase) {
        every { entityManager.lock(phase, LockModeType.OPTIMISTIC_FORCE_INCREMENT) } just runs
    }

    private fun assertStatus(
        expectedStatus: HttpStatus,
        action: () -> Unit,
    ): ResponseStatusException {
        val exception = assertThrows<ResponseStatusException>(action)
        assertEquals(expectedStatus, exception.statusCode)
        return exception
    }

    @Nested
    inner class CreateBlueprintPhaseRequirementsForPhase {
        @Test
        fun `creates skill and role requirements and bumps the phase revision for global scope`() {
            val phase = makePhase(revision = 5)
            val skillId = UUID.randomUUID()
            val roleId = UUID.randomUUID()
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phase.id)
            } returns phase
            every { skillsApi.getSkillsByIds(setOf(skillId)) } returns setOf(SkillDto(skillId, "Kotlin"))
            every { projectRoleApi.getProjectRolesByIds(setOf(roleId)) } returns
                setOf(ProjectRoleShortDto(roleId, "Developer"))
            allowLock(phase)
            allowFlush()

            val result =
                service.createBlueprintPhaseRequirementsForPhase(
                    BlueprintScope.Global,
                    phase.id,
                    CreateBlueprintPhaseRequirementsRequest(
                        revision = 5,
                        requirements =
                            setOf(
                                CreateBlueprintPhaseRequirementRequest(skillId, RequirementType.SKILL),
                                CreateBlueprintPhaseRequirementRequest(roleId, RequirementType.PROJECT_ROLE),
                            ),
                    ),
                )

            assertEquals(6, result.revision)
            assertEquals(
                setOf(skillId to RequirementType.SKILL, roleId to RequirementType.PROJECT_ROLE),
                result.requirements.map { it.referenceId to it.type }.toSet(),
            )
            assertEquals(setOf("Kotlin", "Developer"), result.requirements.map { it.displayName }.toSet())
            assertTrue(result.requirements.all { it.blueprintPhaseId == phase.id })
            assertEquals(2, phase.requirements.size)
            verify(exactly = 1) { entityManager.lock(phase, LockModeType.OPTIMISTIC_FORCE_INCREMENT) }
            verify(exactly = 1) { entityManager.flush() }
        }

        @Test
        fun `creates skill requirements and bumps the phase revision for project scope`() {
            val scope = BlueprintScope.Project(UUID.randomUUID())
            val phase = makePhase(revision = 2)
            val skillId = UUID.randomUUID()
            every { blueprintAccessService.getAuthorizedEditablePhase(scope, phase.id) } returns phase
            every { skillsApi.getSkillsByIds(setOf(skillId)) } returns setOf(SkillDto(skillId, "Kotlin"))
            allowLock(phase)
            allowFlush()

            val result =
                service.createBlueprintPhaseRequirementsForPhase(
                    scope,
                    phase.id,
                    CreateBlueprintPhaseRequirementsRequest(
                        revision = 2,
                        requirements = setOf(CreateBlueprintPhaseRequirementRequest(skillId, RequirementType.SKILL)),
                    ),
                )

            assertEquals(3, result.revision)
            assertEquals(setOf(skillId), result.requirements.map { it.referenceId }.toSet())
            verify(exactly = 0) { projectRoleApi.getProjectRolesByIds(any()) }
            verify(exactly = 1) { entityManager.lock(phase, LockModeType.OPTIMISTIC_FORCE_INCREMENT) }
            verify(exactly = 1) { entityManager.flush() }
        }

        @Test
        fun `keeps the phase revision when the request is empty`() {
            val phase = makePhase(revision = 5)
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phase.id)
            } returns phase

            val result =
                service.createBlueprintPhaseRequirementsForPhase(
                    BlueprintScope.Global,
                    phase.id,
                    CreateBlueprintPhaseRequirementsRequest(revision = 5, requirements = emptySet()),
                )

            assertEquals(5, result.revision)
            assertTrue(result.requirements.isEmpty())
            verify(exactly = 0) { skillsApi.getSkillsByIds(any()) }
            verify(exactly = 0) { projectRoleApi.getProjectRolesByIds(any()) }
            verify(exactly = 0) { entityManager.lock(phase, LockModeType.OPTIMISTIC_FORCE_INCREMENT) }
            verify(exactly = 0) { entityManager.flush() }
        }

        @Test
        fun `ignores requirements that already exist and keeps the revision`() {
            val phase = makePhase(revision = 5)
            val skillId = UUID.randomUUID()
            phase.requirements.add(makeRequirement(phase, RequirementType.SKILL, skillId, "Kotlin"))
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phase.id)
            } returns phase
            every { skillsApi.getSkillsByIds(setOf(skillId)) } returns setOf(SkillDto(skillId, "Kotlin"))

            val result =
                service.createBlueprintPhaseRequirementsForPhase(
                    BlueprintScope.Global,
                    phase.id,
                    CreateBlueprintPhaseRequirementsRequest(
                        revision = 5,
                        requirements = setOf(CreateBlueprintPhaseRequirementRequest(skillId, RequirementType.SKILL)),
                    ),
                )

            assertEquals(5, result.revision)
            assertEquals(setOf(skillId), result.requirements.map { it.referenceId }.toSet())
            assertEquals(1, phase.requirements.size)
            verify(exactly = 0) { entityManager.lock(phase, LockModeType.OPTIMISTIC_FORCE_INCREMENT) }
            verify(exactly = 0) { entityManager.flush() }
        }

        @Test
        fun `rejects unknown skill references`() {
            val phase = makePhase(revision = 5)
            val skillId = UUID.randomUUID()
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phase.id)
            } returns phase
            every { skillsApi.getSkillsByIds(setOf(skillId)) } returns emptySet()

            assertStatus(HttpStatus.BAD_REQUEST) {
                service.createBlueprintPhaseRequirementsForPhase(
                    BlueprintScope.Global,
                    phase.id,
                    CreateBlueprintPhaseRequirementsRequest(
                        revision = 5,
                        requirements = setOf(CreateBlueprintPhaseRequirementRequest(skillId, RequirementType.SKILL)),
                    ),
                )
            }
            assertTrue(phase.requirements.isEmpty())
            verify(exactly = 0) { entityManager.flush() }
        }

        @Test
        fun `rejects unknown project role references`() {
            val phase = makePhase(revision = 5)
            val roleId = UUID.randomUUID()
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phase.id)
            } returns phase
            every { projectRoleApi.getProjectRolesByIds(setOf(roleId)) } returns emptySet()

            assertStatus(HttpStatus.BAD_REQUEST) {
                service.createBlueprintPhaseRequirementsForPhase(
                    BlueprintScope.Global,
                    phase.id,
                    CreateBlueprintPhaseRequirementsRequest(
                        revision = 5,
                        requirements =
                            setOf(CreateBlueprintPhaseRequirementRequest(roleId, RequirementType.PROJECT_ROLE)),
                    ),
                )
            }
            assertTrue(phase.requirements.isEmpty())
            verify(exactly = 0) { skillsApi.getSkillsByIds(any()) }
            verify(exactly = 0) { entityManager.flush() }
        }

        @Test
        fun `rejects stale revision before resolving references`() {
            val phase = makePhase(revision = 5)
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phase.id)
            } returns phase

            assertStatus(HttpStatus.CONFLICT) {
                service.createBlueprintPhaseRequirementsForPhase(
                    BlueprintScope.Global,
                    phase.id,
                    CreateBlueprintPhaseRequirementsRequest(
                        revision = 4,
                        requirements =
                            setOf(
                                CreateBlueprintPhaseRequirementRequest(UUID.randomUUID(), RequirementType.SKILL),
                            ),
                    ),
                )
            }
            verify(exactly = 0) { skillsApi.getSkillsByIds(any()) }
            verify(exactly = 0) { projectRoleApi.getProjectRolesByIds(any()) }
            verify(exactly = 0) { entityManager.flush() }
        }

        @Test
        fun `propagates access service not found`() {
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, any())
            } throws ResponseStatusException(HttpStatus.NOT_FOUND, "Phase not found")

            assertStatus(HttpStatus.NOT_FOUND) {
                service.createBlueprintPhaseRequirementsForPhase(
                    BlueprintScope.Global,
                    UUID.randomUUID(),
                    CreateBlueprintPhaseRequirementsRequest(revision = 0, requirements = emptySet()),
                )
            }
            verify(exactly = 0) { skillsApi.getSkillsByIds(any()) }
            verify(exactly = 0) { projectRoleApi.getProjectRolesByIds(any()) }
        }
    }

    @Nested
    inner class DeleteBlueprintPhaseRequirementsForPhase {
        @Test
        fun `deletes selected requirements and bumps the phase revision for global scope`() {
            val phase = makePhase(revision = 5)
            val kept = makeRequirement(phase, displayName = "Kept")
            val removed = makeRequirement(phase, displayName = "Removed")
            phase.requirements.addAll(setOf(kept, removed))
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phase.id)
            } returns phase
            allowLock(phase)
            allowFlush()

            val result =
                service.deleteBlueprintPhaseRequirementsForPhase(
                    BlueprintScope.Global,
                    phase.id,
                    DeleteBlueprintPhaseRequirementsRequest(revision = 5, requirementIds = setOf(removed.id)),
                )

            assertEquals(6, result.revision)
            assertEquals(setOf(kept.id), phase.requirements.map { it.id }.toSet())
            verify(exactly = 1) { entityManager.lock(phase, LockModeType.OPTIMISTIC_FORCE_INCREMENT) }
            verify(exactly = 1) { entityManager.flush() }
        }

        @Test
        fun `deletes selected requirements and bumps the phase revision for project scope`() {
            val scope = BlueprintScope.Project(UUID.randomUUID())
            val phase = makePhase(revision = 1)
            val removed = makeRequirement(phase)
            phase.requirements.add(removed)
            every { blueprintAccessService.getAuthorizedEditablePhase(scope, phase.id) } returns phase
            allowLock(phase)
            allowFlush()

            val result =
                service.deleteBlueprintPhaseRequirementsForPhase(
                    scope,
                    phase.id,
                    DeleteBlueprintPhaseRequirementsRequest(revision = 1, requirementIds = setOf(removed.id)),
                )

            assertEquals(2, result.revision)
            assertTrue(phase.requirements.isEmpty())
            verify(exactly = 1) { entityManager.lock(phase, LockModeType.OPTIMISTIC_FORCE_INCREMENT) }
            verify(exactly = 1) { entityManager.flush() }
        }

        @Test
        fun `keeps the revision when no requirement ids are given`() {
            val phase = makePhase(revision = 5)
            val existing = makeRequirement(phase)
            phase.requirements.add(existing)
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phase.id)
            } returns phase

            val result =
                service.deleteBlueprintPhaseRequirementsForPhase(
                    BlueprintScope.Global,
                    phase.id,
                    DeleteBlueprintPhaseRequirementsRequest(revision = 5, requirementIds = emptySet()),
                )

            assertEquals(5, result.revision)
            assertEquals(setOf(existing.id), phase.requirements.map { it.id }.toSet())
            verify(exactly = 0) { entityManager.lock(phase, LockModeType.OPTIMISTIC_FORCE_INCREMENT) }
            verify(exactly = 0) { entityManager.flush() }
        }

        @Test
        fun `rejects requirement ids that do not belong to the phase`() {
            val phase = makePhase(revision = 5)
            val existing = makeRequirement(phase)
            phase.requirements.add(existing)
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phase.id)
            } returns phase

            assertStatus(HttpStatus.BAD_REQUEST) {
                service.deleteBlueprintPhaseRequirementsForPhase(
                    BlueprintScope.Global,
                    phase.id,
                    DeleteBlueprintPhaseRequirementsRequest(
                        revision = 5,
                        requirementIds = setOf(existing.id, UUID.randomUUID()),
                    ),
                )
            }
            assertEquals(setOf(existing.id), phase.requirements.map { it.id }.toSet())
            verify(exactly = 0) { entityManager.flush() }
        }

        @Test
        fun `rejects stale revision without deleting requirements`() {
            val phase = makePhase(revision = 5)
            val existing = makeRequirement(phase)
            phase.requirements.add(existing)
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phase.id)
            } returns phase

            assertStatus(HttpStatus.CONFLICT) {
                service.deleteBlueprintPhaseRequirementsForPhase(
                    BlueprintScope.Global,
                    phase.id,
                    DeleteBlueprintPhaseRequirementsRequest(revision = 4, requirementIds = setOf(existing.id)),
                )
            }
            assertEquals(setOf(existing.id), phase.requirements.map { it.id }.toSet())
            verify(exactly = 0) { entityManager.flush() }
        }

        @Test
        fun `propagates access service not found`() {
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, any())
            } throws ResponseStatusException(HttpStatus.NOT_FOUND, "Phase not found")

            assertStatus(HttpStatus.NOT_FOUND) {
                service.deleteBlueprintPhaseRequirementsForPhase(
                    BlueprintScope.Global,
                    UUID.randomUUID(),
                    DeleteBlueprintPhaseRequirementsRequest(revision = 0, requirementIds = emptySet()),
                )
            }
        }
    }
}
