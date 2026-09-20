package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.user.external.ProjectIndustryApi
import com.sprintstart.sprintstartbackend.user.external.SkillSuggestionAiClient
import com.sprintstart.sprintstartbackend.user.external.model.SkillSuggestionItemDto
import com.sprintstart.sprintstartbackend.user.external.model.SkillSuggestionResponseDto
import com.sprintstart.sprintstartbackend.user.model.entity.Project
import com.sprintstart.sprintstartbackend.user.model.entity.ProjectRole
import com.sprintstart.sprintstartbackend.user.model.entity.ProjectUserAssignment
import com.sprintstart.sprintstartbackend.user.model.entity.Skill
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.model.exceptions.SkillSuggestionAiException
import com.sprintstart.sprintstartbackend.user.model.request.CreateProjectRoleRequest
import com.sprintstart.sprintstartbackend.user.model.request.UpdateRoleSkillsRequest
import com.sprintstart.sprintstartbackend.user.repository.ProjectRoleRepository
import com.sprintstart.sprintstartbackend.user.repository.ProjectUserAssignmentRepository
import com.sprintstart.sprintstartbackend.user.repository.SkillRepository
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectRoleServiceTest {
    private val projectRoleRepository: ProjectRoleRepository = mockk()
    private val assignmentRepository: ProjectUserAssignmentRepository = mockk()
    private val skillRepository: SkillRepository = mockk()
    private val userRepository: UserRepository = mockk()
    private val projectIndustryApi: ProjectIndustryApi = mockk()
    private val skillSuggestionAiClient: SkillSuggestionAiClient = mockk()
    private val transactionManager: PlatformTransactionManager = mockk(relaxed = true)

    private val service = ProjectRoleService(
        projectRoleRepository,
        assignmentRepository,
        skillRepository,
        userRepository,
        projectIndustryApi,
        skillSuggestionAiClient,
        transactionManager,
    )

    private fun assignmentFor(userId: UUID, projectId: UUID): ProjectUserAssignment {
        val user = User(
            id = userId,
            authId = "auth1",
            username = "alice",
            firstname = "Alice",
            lastname = "Test",
            email = null,
        )
        return ProjectUserAssignment(user = user, project = Project(id = projectId, name = "A project"))
    }

    @Test
    fun `getAllRoles returns list of roles`() {
        val role = ProjectRole(id = UUID.randomUUID(), name = "Dev", description = "Test")
        every { projectRoleRepository.findAll() } returns listOf(role)

        val result = service.getAllRoles()

        assertEquals(1, result.size)
        assertEquals("Dev", result[0].name)
    }

    @Test
    fun `createRole saves and returns role`() = runTest {
        val request = CreateProjectRoleRequest(name = "Dev", description = "Test")
        every { projectRoleRepository.save(any()) } answers { firstArg() }
        every { skillRepository.findAll() } returns emptyList()
        coEvery { skillSuggestionAiClient.suggestSkills(any()) } returns SkillSuggestionResponseDto(emptyList())

        val result = service.createRole(request)

        assertEquals("Dev", result.name)
        verify(exactly = 1) { projectRoleRepository.save(any()) }
    }

    @Test
    fun `createRole uses lazy industry evaluation when projectId is present`() = runTest {
        val projectId = UUID.randomUUID()
        val request = CreateProjectRoleRequest(name = "Dev", description = "Test", projectId = projectId)
        every { projectRoleRepository.save(any()) } answers { firstArg() }
        every { skillRepository.findAll() } returns emptyList()
        coEvery { projectIndustryApi.getOrEvaluateIndustry(projectId) } returns "Fintech"
        coEvery {
            skillSuggestionAiClient.suggestSkills(
                match { it.projectIndustry == "Fintech" && it.projectId == projectId.toString() },
            )
        } returns SkillSuggestionResponseDto(emptyList())

        val result = service.createRole(request)

        assertEquals("Dev", result.name)
        coVerify(exactly = 1) { projectIndustryApi.getOrEvaluateIndustry(projectId) }
        coVerify(exactly = 1) {
            skillSuggestionAiClient.suggestSkills(
                match { it.projectIndustry == "Fintech" && it.projectId == projectId.toString() },
            )
        }
    }

    @Test
    fun `createRole uses fallback industry when projectId is null`() = runTest {
        val request = CreateProjectRoleRequest(
            name = "Dev",
            description = "Test",
            projectId = null,
            industry = "Healthcare",
        )
        every { projectRoleRepository.save(any()) } answers { firstArg() }
        every { skillRepository.findAll() } returns emptyList()
        coEvery {
            skillSuggestionAiClient.suggestSkills(
                match { it.projectIndustry == "Healthcare" && it.projectId == null },
            )
        } returns SkillSuggestionResponseDto(emptyList())

        val result = service.createRole(request)

        assertEquals("Dev", result.name)
        coVerify(exactly = 0) { projectIndustryApi.getOrEvaluateIndustry(any()) }
        coVerify(exactly = 1) {
            skillSuggestionAiClient.suggestSkills(
                match { it.projectIndustry == "Healthcare" && it.projectId == null },
            )
        }
    }

    @Test
    fun `createRole links existing skill and creates new skill with universal false`() = runTest {
        val roleId = UUID.randomUUID()
        val role = ProjectRole(id = roleId, name = "Dev", description = "Test")
        val existingSkill = Skill(name = "Kotlin", category = "Languages & Paradigms", universal = false)
        val request = CreateProjectRoleRequest(name = "Dev", description = "Test")

        every { projectRoleRepository.save(any()) } returns role
        every { projectRoleRepository.findById(roleId) } returns Optional.of(role)
        every { skillRepository.findAll() } returns listOf(existingSkill)
        every { skillRepository.findByNormalizedName("Kotlin") } returns existingSkill
        every { skillRepository.findByNormalizedName("Docker") } returns null
        every { skillRepository.save(any()) } answers { firstArg() }

        val suggestions = listOf(
            SkillSuggestionItemDto(name = "Kotlin", category = "Languages & Paradigms", isNew = false),
            SkillSuggestionItemDto(name = "Docker", category = "DevOps", isNew = true),
        )
        coEvery { skillSuggestionAiClient.suggestSkills(any()) } returns SkillSuggestionResponseDto(suggestions)

        val result = service.createRole(request)

        assertEquals("Dev", result.name)
        assertTrue(existingSkill.projectRoles.contains(role))
        verify(exactly = 1) { skillRepository.save(existingSkill) }
        verify(exactly = 1) {
            skillRepository.save(
                match {
                    it.name == "Docker" && it.category == "DevOps" && !it.universal && it.projectRoles.contains(role)
                },
            )
        }
    }

    @Test
    fun `createRole succeeds even when AI suggestion fails`() = runTest {
        val request = CreateProjectRoleRequest(name = "Dev", description = "Test")
        every { projectRoleRepository.save(any()) } answers { firstArg() }
        every { skillRepository.findAll() } returns emptyList()
        coEvery { skillSuggestionAiClient.suggestSkills(any()) } throws SkillSuggestionAiException(503, "down", "error")

        val result = service.createRole(request)

        assertEquals("Dev", result.name)
    }

    @Test
    fun `suggestSkillsForRole links suggested skills and returns updated skills`() = runTest {
        val roleId = UUID.randomUUID()
        val role = ProjectRole(id = roleId, name = "Dev", description = "Test")
        val existingSkill = Skill(
            name = "Kotlin",
            category = "Languages & Paradigms",
            projectRoles = mutableSetOf(role),
        )

        every { projectRoleRepository.findById(roleId) } returns Optional.of(role)
        every { skillRepository.findAll() } returns listOf(existingSkill)
        every { skillRepository.findByNormalizedName("Docker") } returns null
        every { skillRepository.save(any()) } answers { firstArg() }
        every { skillRepository.findAllByProjectRolesId(roleId) } returns listOf(
            existingSkill,
            Skill(name = "Docker", category = "DevOps", projectRoles = mutableSetOf(role)),
        )

        val suggestions = listOf(
            SkillSuggestionItemDto(name = "Docker", category = "DevOps", isNew = true),
        )
        coEvery {
            skillSuggestionAiClient.suggestSkills(
                match { it.projectId == null && it.projectIndustry == null },
            )
        } returns SkillSuggestionResponseDto(suggestions)

        val result = service.suggestSkillsForRole(roleId)

        assertEquals(2, result.size)
        assertEquals("Kotlin", result[0].name)
        assertEquals("Docker", result[1].name)
        verify(exactly = 1) {
            skillRepository.save(
                match { it.name == "Docker" && !it.universal && it.projectRoles.contains(role) },
            )
        }
    }

    @Test
    fun `suggestSkillsForRole throws 404 when role does not exist`() = runTest {
        val roleId = UUID.randomUUID()
        every { projectRoleRepository.findById(roleId) } returns Optional.empty()

        val ex = assertThrows<ResponseStatusException> { service.suggestSkillsForRole(roleId) }
        assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
    }

    @Test
    fun `suggestSkillsForRole propagates AI exceptions`() = runTest {
        val roleId = UUID.randomUUID()
        val role = ProjectRole(id = roleId, name = "Dev", description = "Test")

        every { projectRoleRepository.findById(roleId) } returns Optional.of(role)
        every { skillRepository.findAll() } returns emptyList()
        coEvery {
            skillSuggestionAiClient.suggestSkills(any())
        } throws SkillSuggestionAiException(502, "error", "message")

        val ex = assertThrows<SkillSuggestionAiException> { service.suggestSkillsForRole(roleId) }
        assertEquals(502, ex.statusCode)
    }

    @Test
    fun `deleteRole deletes when found`() {
        val id = UUID.randomUUID()
        every { projectRoleRepository.existsById(id) } returns true
        every { projectRoleRepository.deleteById(id) } just runs
        every { assignmentRepository.findAllHoldingRole(id) } returns emptyList()
        every { assignmentRepository.saveAll(any<List<ProjectUserAssignment>>()) } returns mutableListOf()

        service.deleteRole(id)

        verify(exactly = 1) { projectRoleRepository.deleteById(id) }
    }

    /**
     * Deleting a role takes it off everybody holding it first.
     *
     * The database would cascade, but the entity mapping declares none — so a schema built from
     * entities (every test, and anything on `ddl-auto`) would fail the constraint — and a DB-side
     * cascade leaves loaded assignments holding a role that no longer exists.
     */
    @Test
    fun `deleteRole releases the role from every assignment holding it`() {
        val id = UUID.randomUUID()
        val role = ProjectRole(id = id, name = "Dev", description = "Test")
        val holder = assignmentFor(UUID.randomUUID(), UUID.randomUUID())
        holder.projectRoles.add(role)

        every { projectRoleRepository.existsById(id) } returns true
        every { projectRoleRepository.deleteById(id) } just runs
        every { assignmentRepository.findAllHoldingRole(id) } returns listOf(holder)
        every { assignmentRepository.saveAll(any<List<ProjectUserAssignment>>()) } returns mutableListOf()

        service.deleteRole(id)

        assertTrue(holder.projectRoles.isEmpty())
        verify(exactly = 1) { assignmentRepository.saveAll(listOf(holder)) }
    }

    @Test
    fun `deleteRole throws 404 when not found`() {
        val id = UUID.randomUUID()
        every { projectRoleRepository.existsById(id) } returns false

        assertThrows<ResponseStatusException> {
            service.deleteRole(id)
        }
    }

    @Test
    fun `assignRoleToUser assigns role successfully`() {
        val userId = UUID.randomUUID()
        val roleId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        val assignment = assignmentFor(userId, projectId)
        val role = ProjectRole(id = roleId, name = "Dev", description = "Test")

        every { assignmentRepository.findByProjectIdAndUserId(projectId, userId) } returns assignment
        every { projectRoleRepository.findById(roleId) } returns Optional.of(role)
        every { assignmentRepository.save(any()) } answers { firstArg() }

        service.assignRoleToUser(userId, projectId, roleId)

        // The role lands on the assignment — it is scoped to this membership, so the same person
        // can hold a different role on a different project.
        assertTrue(assignment.projectRoles.contains(role))
        verify(exactly = 1) { assignmentRepository.save(assignment) }
    }

    @Test
    fun `assignRoleToUser throws 404 if the user is not on that project`() {
        val userId = UUID.randomUUID()
        val roleId = UUID.randomUUID()
        val projectId = UUID.randomUUID()

        every { assignmentRepository.findByProjectIdAndUserId(projectId, userId) } returns null

        // Setting a role does not quietly make somebody a member of the project.
        assertThrows<ResponseStatusException> {
            service.assignRoleToUser(userId, projectId, roleId)
        }
    }

    @Test
    fun `assignRoleToUser throws 404 if role not found`() {
        val userId = UUID.randomUUID()
        val roleId = UUID.randomUUID()
        val projectId = UUID.randomUUID()

        every { assignmentRepository.findByProjectIdAndUserId(projectId, userId) } returns
            assignmentFor(userId, projectId)
        every { projectRoleRepository.findById(roleId) } returns Optional.empty()

        assertThrows<ResponseStatusException> {
            service.assignRoleToUser(userId, projectId, roleId)
        }
    }

    @Test
    fun `unassignRoleFromUser unassigns successfully`() {
        val userId = UUID.randomUUID()
        val roleId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        val role = ProjectRole(id = roleId, name = "Dev", description = "Test")
        val assignment = assignmentFor(userId, projectId)
        assignment.projectRoles.add(role)

        every { assignmentRepository.findByProjectIdAndUserId(projectId, userId) } returns assignment
        every { projectRoleRepository.findById(roleId) } returns Optional.of(role)
        every { assignmentRepository.save(any()) } answers { firstArg() }

        service.unassignRoleFromUser(userId, projectId, roleId)

        assertTrue(assignment.projectRoles.isEmpty())
        verify(exactly = 1) { assignmentRepository.save(assignment) }
    }

    @Test
    fun `assignRoleToUser without a project applies the role to every assignment`() {
        val userId = UUID.randomUUID()
        val roleId = UUID.randomUUID()
        val first = assignmentFor(userId, UUID.randomUUID())
        val second = assignmentFor(userId, UUID.randomUUID())
        val user = first.user
        user.projectAssignments.addAll(listOf(first, second))
        val role = ProjectRole(id = roleId, name = "Dev", description = "Test")

        every { userRepository.findById(userId) } returns Optional.of(user)
        every { projectRoleRepository.findById(roleId) } returns Optional.of(role)
        every { userRepository.save(any()) } answers { firstArg() }

        service.assignRoleToUser(userId, roleId)

        // Roles are scoped to memberships, so a projectless assignment lands on each of them.
        assertTrue(first.projectRoles.contains(role))
        assertTrue(second.projectRoles.contains(role))
        verify(exactly = 1) { userRepository.save(user) }
    }

    @Test
    fun `assignRoleToUser without a project throws 404 if the user does not exist`() {
        val userId = UUID.randomUUID()
        every { userRepository.findById(userId) } returns Optional.empty()

        val ex = assertThrows<ResponseStatusException> {
            service.assignRoleToUser(userId, UUID.randomUUID())
        }

        assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
    }

    @Test
    fun `unassignRoleFromUser without a project removes the role from every assignment`() {
        val userId = UUID.randomUUID()
        val roleId = UUID.randomUUID()
        val role = ProjectRole(id = roleId, name = "Dev", description = "Test")
        val first = assignmentFor(userId, UUID.randomUUID()).apply { projectRoles.add(role) }
        val second = assignmentFor(userId, UUID.randomUUID()).apply { projectRoles.add(role) }
        val user = first.user
        user.projectAssignments.addAll(listOf(first, second))

        every { userRepository.findById(userId) } returns Optional.of(user)
        every { userRepository.save(any()) } answers { firstArg() }

        service.unassignRoleFromUser(userId, roleId)

        assertTrue(first.projectRoles.isEmpty())
        assertTrue(second.projectRoles.isEmpty())
        verify(exactly = 1) { userRepository.save(user) }
    }

    @Test
    fun `getSkillsForRole returns skills linked to the role`() {
        val roleId = UUID.randomUUID()
        val role = ProjectRole(id = roleId, name = "Dev", description = "Test")
        val skill = Skill(name = "Kotlin", projectRoles = mutableSetOf(role), category = "Languages & Paradigms")

        every { projectRoleRepository.existsById(roleId) } returns true
        every { skillRepository.findAllByProjectRolesId(roleId) } returns listOf(skill)

        val result = service.getSkillsForRole(roleId)

        assertEquals(1, result.size)
        assertEquals("Kotlin", result[0].name)
    }

    @Test
    fun `getSkillsForRole throws 404 when role not found`() {
        val roleId = UUID.randomUUID()
        every { projectRoleRepository.existsById(roleId) } returns false

        val ex = assertThrows<ResponseStatusException> { service.getSkillsForRole(roleId) }
        assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
    }

    @Test
    fun `setSkillsForRole links new skills and unlinks removed ones`() {
        val roleId = UUID.randomUUID()
        val role = ProjectRole(id = roleId, name = "Dev", description = "Test")
        val otherRole = ProjectRole(id = UUID.randomUUID(), name = "QA", description = "Test")

        val keptSkill = Skill(
            name = "Kotlin",
            projectRoles = mutableSetOf(role, otherRole),
            category = "Languages & Paradigms",
        )
        val removedSkill = Skill(
            name = "Java",
            projectRoles = mutableSetOf(role, otherRole),
            category = "Languages & Paradigms",
        )
        val addedSkill = Skill(
            name = "Docker",
            projectRoles = mutableSetOf(),
            category = "DevOps, Infrastructure & Cloud",
        )

        val request = UpdateRoleSkillsRequest(skillIds = listOf(keptSkill.id, addedSkill.id))

        every { projectRoleRepository.findById(roleId) } returns Optional.of(role)
        every { skillRepository.findAllById(request.skillIds) } returns listOf(keptSkill, addedSkill)
        every { skillRepository.findAllByProjectRolesId(roleId) } returnsMany
            listOf(listOf(keptSkill, removedSkill), listOf(keptSkill, addedSkill))
        every { skillRepository.saveAll(any<List<Skill>>()) } answers { firstArg() }

        val result = service.setSkillsForRole(roleId, request)

        assertEquals(setOf(keptSkill.id, addedSkill.id), result.map { it.id }.toSet())
        assertTrue(role !in removedSkill.projectRoles)
        assertTrue(role in addedSkill.projectRoles)
    }

    @Test
    fun `setSkillsForRole throws 404 when role not found`() {
        val roleId = UUID.randomUUID()
        every { projectRoleRepository.findById(roleId) } returns Optional.empty()

        val ex = assertThrows<ResponseStatusException> {
            service.setSkillsForRole(roleId, UpdateRoleSkillsRequest(skillIds = emptyList()))
        }
        assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
    }

    @Test
    fun `setSkillsForRole throws 404 when a skill id does not exist`() {
        val roleId = UUID.randomUUID()
        val role = ProjectRole(id = roleId, name = "Dev", description = "Test")
        val skillId = UUID.randomUUID()
        val request = UpdateRoleSkillsRequest(skillIds = listOf(skillId))

        every { projectRoleRepository.findById(roleId) } returns Optional.of(role)
        every { skillRepository.findAllById(request.skillIds) } returns emptyList()

        val ex = assertThrows<ResponseStatusException> { service.setSkillsForRole(roleId, request) }
        assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
    }

    @Test
    fun `setSkillsForRole throws 400 when unassigning would leave a skill with no roles`() {
        val roleId = UUID.randomUUID()
        val role = ProjectRole(id = roleId, name = "Dev", description = "Test")
        val orphanedSkill = Skill(name = "Java", projectRoles = mutableSetOf(role), category = "Languages & Paradigms")
        val request = UpdateRoleSkillsRequest(skillIds = emptyList())

        every { projectRoleRepository.findById(roleId) } returns Optional.of(role)
        every { skillRepository.findAllById(request.skillIds) } returns emptyList()
        every { skillRepository.findAllByProjectRolesId(roleId) } returns listOf(orphanedSkill)

        val ex = assertThrows<ResponseStatusException> { service.setSkillsForRole(roleId, request) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
    }

    @Test
    fun `getProjectRolesByIds returns short dtos for the requested ids`() {
        val role = ProjectRole(id = UUID.randomUUID(), name = "Dev", description = "Test")
        every { projectRoleRepository.findAllById(setOf(role.id)) } returns listOf(role)

        val result = service.getProjectRolesByIds(setOf(role.id))

        assertEquals(1, result.size)
        assertEquals(role.id, result.single().id)
        assertEquals("Dev", result.single().name)
    }

    @Test
    fun `getProjectRolesByIds omits unknown ids from the result`() {
        val role = ProjectRole(id = UUID.randomUUID(), name = "Dev", description = "Test")
        every { projectRoleRepository.findAllById(any()) } returns listOf(role)

        val result = service.getProjectRolesByIds(setOf(role.id, UUID.randomUUID()))

        assertEquals(setOf(role.id), result.map { it.id }.toSet())
    }
}
