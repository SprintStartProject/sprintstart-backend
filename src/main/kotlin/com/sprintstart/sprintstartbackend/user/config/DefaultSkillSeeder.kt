package com.sprintstart.sprintstartbackend.user.config

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.sprintstart.sprintstartbackend.user.model.entity.Skill
import com.sprintstart.sprintstartbackend.user.repository.SkillRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "sprintstart.default-skills",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class DefaultSkillSeeder(
    private val skillRepository: SkillRepository,
    private val objectMapper: ObjectMapper
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        val defaultSkills = loadDefaultSkills()

        defaultSkills.forEach { defaultSkill ->
            if (skillRepository.findByNormalizedName(defaultSkill.name) == null) {
                skillRepository.save(
                    Skill(
                        name = defaultSkill.name,
                        category = defaultSkill.category,
                        universal = defaultSkill.universal,
                    ),
                )
            }
        }
    }

    private fun loadDefaultSkills(): List<DefaultSkillDefinition> {
        val resource = ClassPathResource("default-skills.json")

        return resource.inputStream.use { inputStream ->
            objectMapper.readValue(
                inputStream,
                object : TypeReference<List<DefaultSkillDefinition>>() {},
            )
        }
    }
}

data class DefaultSkillDefinition(
    val name: String,
    val category: String?,
    val universal: Boolean = false,
)
