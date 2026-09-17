package com.sprintstart.sprintstartbackend.user.config

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sprintstart.sprintstartbackend.user.model.entity.Skill
import com.sprintstart.sprintstartbackend.user.repository.SkillRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

/**
 * Seeds a default skill list into the skill repository when starting the application.
 *
 * Default skills are treated as normal skill objects, only serving the purpose of having a pre-built
 * pool of skills for admins to choose from.
 *
 * @property skillRepository The repository used to persist the default skills.
 */
@Component
@ConditionalOnProperty(
    prefix = "sprintstart.default-skills",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class DefaultSkillSeeder(
    private val skillRepository: SkillRepository,
) : ApplicationRunner {
    private val objectMapper = jacksonObjectMapper()

    /**
     * Maps the default skills from the resource file to skill objects and persists them in the DB.
     *
     * Skills already present in the skill pool get skipped; skills which are set to RETIRED remain
     * in this state and do not get reactivated to ACTIVE.
     *
     * @param args Application startup arguments provided by SpringBoot.
     */
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
