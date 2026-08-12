package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.user.external.enums.SkillStatus
import com.sprintstart.sprintstartbackend.user.model.entity.ProjectRole
import com.sprintstart.sprintstartbackend.user.model.entity.Skill
import com.sprintstart.sprintstartbackend.user.model.request.skill.CreateSkillRequest
import com.sprintstart.sprintstartbackend.user.model.request.skill.UpdateSkillRequest
import com.sprintstart.sprintstartbackend.user.repository.ProjectRoleRepository
import com.sprintstart.sprintstartbackend.user.repository.SkillRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals

class SkillServiceTest {
    private val skillRepository: SkillRepository = mockk()
    private val projectRoleRepository: ProjectRoleRepository = mockk()
    private val service = SkillService(skillRepository, projectRoleRepository)

    private fun role(id: UUID = UUID.randomUUID()) = ProjectRole(id = id, name = "Dev", description = "Test")

    private fun skill(
        id: UUID = UUID.randomUUID(),
        name: String = "Kotlin",
        status: SkillStatus = SkillStatus.ACTIVE,
        roles: MutableSet<ProjectRole> = mutableSetOf(role()),
    ) = Skill(id = id, name = name, projectRoles = roles, status = status)

    @Test
    fun `getAllSkills returns list of mapped skills`() {
        val r = role()
        val s = skill(roles = mutableSetOf(r))
        every { skillRepository.findAll() } returns listOf(s)

        val result = service.getAllSkills()

        assertEquals(1, result.size)
        assertEquals("Kotlin", result[0].name)
        assertEquals(listOf(r.id), result[0].roleIds)
        assertEquals(SkillStatus.ACTIVE, result[0].status)
    }

    @Test
    fun `getSkillById returns skill when found`() {
        val s = skill()
        every { skillRepository.findById(s.id) } returns Optional.of(s)

        val result = service.getSkillById(s.id)

        assertEquals(s.id, result.id)
        assertEquals("Kotlin", result.name)
    }

    @Test
    fun `getSkillById throws 404 when not found`() {
        val id = UUID.randomUUID()
        every { skillRepository.findById(id) } returns Optional.empty()

        val ex = assertThrows<ResponseStatusException> { service.getSkillById(id) }
        assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
    }

    @Test
    fun `createSkill saves and returns skill`() {
        val roleId = UUID.randomUUID()
        val r = role(roleId)
        val request = CreateSkillRequest(name = "Kotlin", roleIds = listOf(roleId))

        every { skillRepository.findByNormalizedName("Kotlin") } returns null
        every { projectRoleRepository.findAllById(listOf(roleId)) } returns listOf(r)
        every { skillRepository.save(any()) } answers { firstArg() }

        val result = service.createSkill(request)

        assertEquals("Kotlin", result.name)
        assertEquals(listOf(roleId), result.roleIds)
        assertEquals(SkillStatus.ACTIVE, result.status)
        verify(exactly = 1) { skillRepository.save(any()) }
    }

    @Test
    fun `createSkill saves skill linked to multiple roles`() {
        val firstRoleId = UUID.randomUUID()
        val secondRoleId = UUID.randomUUID()
        val roles = listOf(role(firstRoleId), role(secondRoleId))
        val request = CreateSkillRequest(name = "Kotlin", roleIds = listOf(firstRoleId, secondRoleId))

        every { skillRepository.findByNormalizedName("Kotlin") } returns null
        every { projectRoleRepository.findAllById(listOf(firstRoleId, secondRoleId)) } returns roles
        every { skillRepository.save(any()) } answers { firstArg() }

        val result = service.createSkill(request)

        assertEquals(setOf(firstRoleId, secondRoleId), result.roleIds.toSet())
    }

    @Test
    fun `createSkill throws 409 if normalized name already exists`() {
        val roleId = UUID.randomUUID()
        val request = CreateSkillRequest(name = "kotlin", roleIds = listOf(roleId))

        every { projectRoleRepository.findAllById(listOf(roleId)) } returns listOf(role(roleId))
        every {
            skillRepository.findByNormalizedName("kotlin")
        } returns skill(name = "Kotlin", status = SkillStatus.ACTIVE)

        val ex = assertThrows<ResponseStatusException> { service.createSkill(request) }
        assertEquals(HttpStatus.CONFLICT, ex.statusCode)
    }

    @Test
    fun `createSkill throws 404 if role not found`() {
        val roleId = UUID.randomUUID()
        val request = CreateSkillRequest(name = "Kotlin", roleIds = listOf(roleId))

        every { skillRepository.findByNormalizedName("Kotlin") } returns null
        every { projectRoleRepository.findAllById(listOf(roleId)) } returns emptyList()

        val ex = assertThrows<ResponseStatusException> { service.createSkill(request) }
        assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
    }

    @Test
    fun `createSkill reactivates retired skill with same normalized name`() {
        val roleId = UUID.randomUUID()
        val existingRole = role()
        val newRole = role(roleId)
        val retiredSkill = skill(name = "Kotlin", status = SkillStatus.RETIRED, roles = mutableSetOf(existingRole))
        val request = CreateSkillRequest(name = " kotlin ", roleIds = listOf(roleId))

        every { projectRoleRepository.findAllById(listOf(roleId)) } returns listOf(newRole)
        every { skillRepository.findByNormalizedName(" kotlin ") } returns retiredSkill
        every { skillRepository.save(retiredSkill) } returns retiredSkill

        val result = service.createSkill(request)

        assertEquals(SkillStatus.ACTIVE, retiredSkill.status)
        assertEquals(" kotlin ", retiredSkill.name)
        assertEquals(setOf(roleId), retiredSkill.projectRoles.map { it.id }.toSet())
        assertEquals(retiredSkill.id, result.id)
        assertEquals(SkillStatus.ACTIVE, result.status)
    }

    @Test
    fun `updateSkill changes editable fields`() {
        val s = skill()
        val newRoleId = UUID.randomUUID()
        val newRole = role(newRoleId)
        val request = UpdateSkillRequest(name = "Go", roleIds = listOf(newRoleId))

        every { skillRepository.findById(s.id) } returns Optional.of(s)
        every { skillRepository.existsByNormalizedNameExcluding("Go", s.id) } returns false
        every { projectRoleRepository.findAllById(listOf(newRoleId)) } returns listOf(newRole)
        every { skillRepository.save(any()) } answers { firstArg() }

        val result = service.updateSkill(s.id, request)

        assertEquals("Go", result.name)
        assertEquals(listOf(newRoleId), result.roleIds)
    }

    @Test
    fun `updateSkill throws 409 if name conflicts with another skill`() {
        val s = skill()
        val request = UpdateSkillRequest(name = "Go", roleIds = null)

        every { skillRepository.findById(s.id) } returns Optional.of(s)
        every { skillRepository.existsByNormalizedNameExcluding("Go", s.id) } returns true

        val ex = assertThrows<ResponseStatusException> { service.updateSkill(s.id, request) }
        assertEquals(HttpStatus.CONFLICT, ex.statusCode)
    }

    @Test
    fun `updateSkill throws 404 when skill not found`() {
        val id = UUID.randomUUID()
        every { skillRepository.findById(id) } returns Optional.empty()

        val ex = assertThrows<ResponseStatusException> {
            service.updateSkill(id, UpdateSkillRequest(name = "Go", roleIds = null))
        }
        assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
    }

    @Test
    fun `retireSkill marks skill as RETIRED`() {
        val s = skill()
        every { skillRepository.findById(s.id) } returns Optional.of(s)
        every { skillRepository.save(any()) } answers { firstArg() }

        service.retireSkill(s.id)

        assertEquals(SkillStatus.RETIRED, s.status)
        verify(exactly = 1) { skillRepository.save(s) }
    }

    @Test
    fun `retireSkill throws 404 when skill not found`() {
        val id = UUID.randomUUID()
        every { skillRepository.findById(id) } returns Optional.empty()

        val ex = assertThrows<ResponseStatusException> { service.retireSkill(id) }
        assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
    }
}
