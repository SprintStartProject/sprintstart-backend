package com.sprintstart.sprintstartbackend.shared.seeder

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.BlueprintSeeder
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

/**
 * Entry point for database seeding on application startup.
 *
 * Runs in every environment by design: the seeded global blueprint is the baseline
 * template that every newly created project starts from — including production —
 * not dev-only demo data. The individual seeders are idempotent.
 */
@Component
class DatabaseSeeder(
    private val blueprintSeeder: BlueprintSeeder,
) : CommandLineRunner {
    override fun run(vararg args: String) {
        blueprintSeeder.seed()
    }
}
