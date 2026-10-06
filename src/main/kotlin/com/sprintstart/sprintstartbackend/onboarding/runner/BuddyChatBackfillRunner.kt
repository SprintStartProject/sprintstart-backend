package com.sprintstart.sprintstartbackend.onboarding.runner

import com.sprintstart.sprintstartbackend.onboarding.service.BuddyChatBackfillService
import kotlinx.coroutines.runBlocking
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
internal class BuddyChatBackfillRunner(
    private val backfillService: BuddyChatBackfillService,
) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments) {
        runBlocking {
            backfillService.run()
        }
    }
}
