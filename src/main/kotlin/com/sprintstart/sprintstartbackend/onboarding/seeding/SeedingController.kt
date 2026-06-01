package com.sprintstart.sprintstartbackend.onboarding.seeding

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("api/v1/onboarding")
class SeedingController(
    private val seedingService: SeedingService,
) {
    @PostMapping("/{userId}/seeding")
    fun seed(@PathVariable userId: UUID): ResponseEntity<String> {
        seedingService.seed(userId)
        return ResponseEntity.ok("Seeded successfully")
    }

    @PostMapping("/{userId}/reset")
    fun reset(@PathVariable userId: UUID): ResponseEntity<String> {
        seedingService.reset(userId)
        return ResponseEntity.ok("Reset successfully")
    }
}
