package com.sprintstart.sprintstartbackend.user.seeder

import com.sprintstart.sprintstartbackend.user.config.DefaultSkillSeeder
import com.sprintstart.sprintstartbackend.user.external.enums.SkillStatus
import com.sprintstart.sprintstartbackend.user.model.entity.Skill
import com.sprintstart.sprintstartbackend.user.repository.SkillRepository
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.boot.ApplicationArguments
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultSkillSeederTest {
    private val skillRepository: SkillRepository = mockk()
    private val applicationArguments: ApplicationArguments = mockk()

    private val seeder = DefaultSkillSeeder(
        skillRepository = skillRepository,
    )

    @Test
    fun `second run does not save skills again`() {
        val existingSkills = mutableMapOf<String, Skill>()

        every {
            skillRepository.findByNormalizedName(any())
        } answers {
            existingSkills[firstArg<String>().trim().lowercase()]
        }

        every {
            skillRepository.save(any())
        } answers {
            val skill = firstArg<Skill>()
            existingSkills[skill.name.trim().lowercase()] = skill
            skill
        }

        seeder.run(applicationArguments)

        val savesAfterFirstRun = mutableListOf<Skill>()
        verify {
            skillRepository.save(capture(savesAfterFirstRun))
        }

        clearMocks(skillRepository, answers = false)

        // Keep the in-memory state, but reset MockK call history.
        every {
            skillRepository.findByNormalizedName(any())
        } answers {
            existingSkills[firstArg<String>().trim().lowercase()]
        }

        every {
            skillRepository.save(any())
        } answers {
            val skill = firstArg<Skill>()
            existingSkills[skill.name.trim().lowercase()] = skill
            skill
        }

        seeder.run(applicationArguments)

        verify(exactly = 0) {
            skillRepository.save(any())
        }
    }

    @Test
    fun `retired default skill stays retired`() {
        val retiredSkill = Skill(
            id = UUID.randomUUID(),
            name = "Kotlin",
            projectRoles = mutableSetOf(),
            status = SkillStatus.RETIRED,
            category = "Programming",
            universal = false,
        )

        every {
            skillRepository.findByNormalizedName("Kotlin")
        } returns retiredSkill

        every {
            skillRepository.findByNormalizedName(match { it != "Kotlin" })
        } returns null

        every { skillRepository.save(any()) } answers { firstArg() }

        seeder.run(applicationArguments)

        assertEquals(SkillStatus.RETIRED, retiredSkill.status)

        verify(exactly = 0) {
            skillRepository.save(retiredSkill)
        }
    }
}
