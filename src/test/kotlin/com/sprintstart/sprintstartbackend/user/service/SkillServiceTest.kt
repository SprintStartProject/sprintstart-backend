package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.user.model.entity.ProjectRole
import com.sprintstart.sprintstartbackend.user.model.entity.Skill
import com.sprintstart.sprintstartbackend.user.model.entity.SkillLevel
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.model.entity.UserSkillAssessment
import com.sprintstart.sprintstartbackend.user.model.request.CreateSkillAssessmentRequest
import com.sprintstart.sprintstartbackend.user.model.request.CreateSkillRequest
import com.sprintstart.sprintstartbackend.user.repository.ProjectRoleRepository
import com.sprintstart.sprintstartbackend.user.repository.SkillRepository
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import com.sprintstart.sprintstartbackend.user.repository.UserSkillAssessmentRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals

class SkillServiceTest {
    private val skillRepository: SkillRepository = mockk()
    private val projectRoleRepository: ProjectRoleRepository = mockk()
    private val userRepository: UserRepository = mockk()
    private val userSkillAssessmentRepository: UserSkillAssessmentRepository = mockk()
    private val service =
        SkillService(skillRepository, projectRoleRepository, userRepository, userSkillAssessmentRepository)

    @Test
    fun `getAllSkills returns list of mapped skills`() {
        val role = ProjectRole(id = UUID.randomUUID(), name = "Dev", description = "Test")
        val skill = Skill(id = UUID.randomUUID(), name = "Kotlin", projectRole = role)
        every { skillRepository.findAll() } returns listOf(skill)

        val result = service.getAllSkills()

        assertEquals(1, result.size)
        assertEquals("Kotlin", result[0].name)
        assertEquals(role.id, result[0].roleId)
    }

    @Test
    fun `createSkill saves and returns skill`() {
        val roleId = UUID.randomUUID()
        val role = ProjectRole(id = roleId, name = "Dev", description = "Test")
        val request = CreateSkillRequest(name = "Kotlin", roleId = roleId)

        every { projectRoleRepository.findById(roleId) } returns Optional.of(role)
        every { skillRepository.save(any()) } answers { firstArg() }

        val result = service.createSkill(request)

        assertEquals("Kotlin", result.name)
        assertEquals(roleId, result.roleId)
        verify(exactly = 1) { skillRepository.save(any()) }
    }

    @Test
    fun `createSkill throws 404 if role not found`() {
        val roleId = UUID.randomUUID()
        val request = CreateSkillRequest(name = "Kotlin", roleId = roleId)

        every { projectRoleRepository.findById(roleId) } returns Optional.empty()

        assertThrows<ResponseStatusException> {
            service.createSkill(request)
        }
    }

    @Test
    fun `deleteSkill deletes when found`() {
        val id = UUID.randomUUID()
        every { skillRepository.existsById(id) } returns true
        every { skillRepository.deleteById(id) } just runs

        service.deleteSkill(id)

        verify(exactly = 1) { skillRepository.deleteById(id) }
    }

    @Test
    fun `deleteSkill throws 404 when not found`() {
        val id = UUID.randomUUID()
        every { skillRepository.existsById(id) } returns false

        assertThrows<ResponseStatusException> {
            service.deleteSkill(id)
        }
    }

    @Test
    fun `assessSkillForMe removes old assessment and saves new one`() {
        val userId = UUID.randomUUID()
        val user = User(
            id = userId,
            authId = "auth1",
            username = "alice",
            firstname = "Alice",
            lastname = "Test",
            email = null,
        )
        val skillId = UUID.randomUUID()
        val skill =
            Skill(
                id = skillId,
                name = "Kotlin",
                projectRole = ProjectRole(id = UUID.randomUUID(), name = "Dev", description = "Test"),
            )

        val oldAssessment =
            UserSkillAssessment(id = UUID.randomUUID(), user = user, skill = skill, level = SkillLevel.BEGINNER)
        user.skillAssessments.add(oldAssessment)

        val request = CreateSkillAssessmentRequest(skillId = skillId, level = SkillLevel.EXPERT)

        every { userRepository.findByAuthId("auth1") } returns Optional.of(user)
        every { skillRepository.findById(skillId) } returns Optional.of(skill)
        every { userRepository.save(any()) } answers { firstArg() }

        val result = service.assessSkillForMe("auth1", request)

        assertEquals(SkillLevel.EXPERT, result.level)
        assertEquals(1, user.skillAssessments.size) // Old one removed, new one added
        assertEquals(SkillLevel.EXPERT, user.skillAssessments.first().level)
        verify(exactly = 1) { userRepository.save(user) }
    }

    @Test
    fun `getUserSkillAssessments returns mapped assessments`() {
        val userId = UUID.randomUUID()
        val user = User(
            id = userId,
            authId = "auth1",
            username = "alice",
            firstname = "Alice",
            lastname = "Test",
            email = null,
        )
        val skillId = UUID.randomUUID()
        val skill =
            Skill(
                id = skillId,
                name = "Kotlin",
                projectRole = ProjectRole(id = UUID.randomUUID(), name = "Dev", description = "Test"),
            )
        val assessment =
            UserSkillAssessment(id = UUID.randomUUID(), user = user, skill = skill, level = SkillLevel.EXPERT)
        user.skillAssessments.add(assessment)

        every { userRepository.existsById(userId) } returns true
        every { userSkillAssessmentRepository.findByUserId(userId) } returns listOf(assessment)

        val result = service.getUserSkillAssessments(userId)

        assertEquals(1, result.size)
        assertEquals(skillId, result[0].skillId)
        assertEquals(SkillLevel.EXPERT, result[0].level)
    }
}
