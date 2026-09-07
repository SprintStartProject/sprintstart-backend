package com.sprintstart.sprintstartbackend.onboarding.blueprint.external

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintPhaseType
import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPath
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhase
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintPathRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class BlueprintSeeder(
    private val blueprintPathRepository: BlueprintPathRepository,
) {
    fun seed() {
        if (blueprintPathRepository.findAllByProjectIdIsNull().isNotEmpty()) {
            return
        }

        val logger = LoggerFactory.getLogger(BlueprintSeeder::class.java)

        logger.info("Seeding base blueprint path")

        val path = BlueprintPath(
            blueprintKey = UUID.randomUUID(),
            title = "Seeded Blueprint",
            description = "This is a default blueprint path that was created by the database seeder",
        )
        path.status = BlueprintStatus.ACTIVE

        BlueprintSeedData.phases.forEachIndexed { index, seed ->
            path.blueprintPhases.add(
                BlueprintPhase(
                    blueprintPath = path,
                    position = index,
                    title = seed.title,
                    description = seed.description,
                    aiPrompt = seed.aiPrompt,
                    type = BlueprintPhaseType.AI_ENHANCED,
                    graphX = seed.graphX,
                    graphY = seed.graphY,
                ),
            )
        }

        path.blueprintPhases[1].blockedBy.add(path.blueprintPhases[0])
        path.blueprintPhases[2].blockedBy.add(path.blueprintPhases[0])
        path.blueprintPhases[3].blockedBy.add(path.blueprintPhases[0])
        path.blueprintPhases[4].blockedBy.add(path.blueprintPhases[0])
        path.blueprintPhases[5].blockedBy.add(path.blueprintPhases[0])
        path.blueprintPhases[6].blockedBy.add(path.blueprintPhases[0])
        path.blueprintPhases[7].blockedBy.add(path.blueprintPhases[0])
        path.blueprintPhases[8].blockedBy.add(path.blueprintPhases[0])

        path.blueprintPhases[9].blockedBy.add(path.blueprintPhases[6])
        path.blueprintPhases[9].blockedBy.add(path.blueprintPhases[7])

        path.blueprintPhases[10].blockedBy.add(path.blueprintPhases[9])

        path.blueprintPhases[11].blockedBy.add(path.blueprintPhases[10])
        path.blueprintPhases[12].blockedBy.add(path.blueprintPhases[10])

        path.blueprintPhases[13].blockedBy.add(path.blueprintPhases[11])
        path.blueprintPhases[13].blockedBy.add(path.blueprintPhases[12])

        path.blueprintPhases[14].blockedBy.add(path.blueprintPhases[13])
        path.blueprintPhases[14].blockedBy.add(path.blueprintPhases[8])

        path.blueprintPhases[15].blockedBy.add(path.blueprintPhases[13])
        path.blueprintPhases[15].blockedBy.add(path.blueprintPhases[8])

        blueprintPathRepository.save(path)
    }
}
