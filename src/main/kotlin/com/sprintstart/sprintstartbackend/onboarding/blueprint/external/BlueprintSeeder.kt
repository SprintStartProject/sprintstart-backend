package com.sprintstart.sprintstartbackend.onboarding.blueprint.external

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintPhaseType
import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPath
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhase
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintPathRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Seeds the default global blueprint path into the database.
 *
 * Creates the "Seeded Blueprint" path with the phases defined in [BlueprintSeedData]
 * as `AI_ENHANCED` phases, wires the default `blockedBy` dependency graph between
 * the phases, and marks the path as [BlueprintStatus.ACTIVE]. The blueprint is
 * global (no project ID), so it serves as a template that is copied into projects
 * when they are created.
 *
 * Seeding intentionally runs in every environment, including production: the global
 * template is the baseline every new project starts from, not dev-only demo data.
 */
@Component
class BlueprintSeeder(
    private val blueprintPathRepository: BlueprintPathRepository,
) {
    /**
     * Seeds the default global blueprint path if none exists yet.
     *
     * The seeding is idempotent: if any global blueprint path (project ID is `null`)
     * is already present, the method returns without doing anything. The blocker
     * graph is wired by phase title against [BlueprintSeedData.phases] and fails
     * fast with a clear message when a referenced title is missing, so changing
     * the seed list only requires adjusting the affected edges.
     */
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

        val phasesByTitle = path.blueprintPhases.associateBy { it.title }

        fun blockedBy(title: String, vararg blockers: String) {
            val phase = requireNotNull(phasesByTitle[title]) { "Seed phase '$title' is missing" }
            blockers.forEach { blocker ->
                phase.blockedBy.add(requireNotNull(phasesByTitle[blocker]) { "Seed phase '$blocker' is missing" })
            }
        }

        listOf(
            "Environment Setup",
            "Meetings",
            "Working Agreements",
            "Time Tracking",
            "Definition of Done / Ready",
            "Industry Context",
            "Domain Vocabulary",
            "Requirements & Epics",
        ).forEach { blockedBy(it, "Project Overview") }

        blockedBy("Architecture", "Industry Context", "Domain Vocabulary")
        blockedBy("Technical Debt", "Architecture")
        blockedBy("Deployment", "Technical Debt")
        blockedBy("Release Planning", "Technical Debt")
        blockedBy("Guidelines", "Deployment", "Release Planning")
        blockedBy("Role-Specific Onboarding Task 1", "Guidelines", "Requirements & Epics")
        blockedBy("Role-Specific Onboarding Task 2", "Guidelines", "Requirements & Epics")

        blueprintPathRepository.save(path)
    }
}
