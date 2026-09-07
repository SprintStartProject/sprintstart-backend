package com.sprintstart.sprintstartbackend.shared.seeder

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.BlueprintSeeder
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class DatabaseSeeder(
    private val blueprintSeeder: BlueprintSeeder,
) : CommandLineRunner {
    override fun run(vararg args: String) {
        blueprintSeeder.seed()
    }
}
