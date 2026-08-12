package com.sprintstart.sprintstartbackend.onboarding.seeding

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencyKind
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Competency
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Seeds a small development competency vocabulary when the application starts.
 *
 * Populates a handful of grounded backend competencies so placement and the learning area have real
 * data to work against locally. Idempotent: a competency is inserted only when no row has its stable
 * key, so repeated restarts and partial prior seeds converge without duplicates, and an existing row
 * is never overwritten — a hand-edited label survives a restart.
 *
 * Seeds no relationships, because competencies have none.
 *
 * Intended for development/local setup only; gated behind `sprintstart.dev-competency-graph.enabled`.
 *
 * @property competencyRepository Repository used to check for and persist competencies.
 */
@Component
@ConditionalOnProperty(
    prefix = "sprintstart.dev-competency-graph",
    name = ["enabled"],
    havingValue = "true",
)
class CompetencyGraphSeeder(
    private val competencyRepository: CompetencyRepository,
) : ApplicationRunner {
    private data class SeedNode(
        val key: String,
        val label: String,
        val kind: CompetencyKind,
    )

    private val nodes = listOf(
        SeedNode("git", "Git", CompetencyKind.SKILL),
        SeedNode("kotlin", "Kotlin", CompetencyKind.SKILL),
        SeedNode("spring-boot", "Spring Boot", CompetencyKind.SKILL),
        SeedNode("our-domain-model", "Our Domain Model", CompetencyKind.CONCEPT),
        SeedNode("jpa-persistence", "JPA Persistence", CompetencyKind.CONCEPT),
        SeedNode("sse-streaming", "SSE Streaming", CompetencyKind.CONCEPT),
    )

    /**
     * Inserts any missing seed competencies after the application context has started.
     *
     * @param args Application startup arguments provided by Spring Boot.
     */
    override fun run(args: ApplicationArguments) {
        nodes.forEach { node ->
            if (!competencyRepository.existsByKey(node.key)) {
                competencyRepository.save(
                    Competency(key = node.key, label = node.label, kind = node.kind),
                )
            }
        }
    }
}
