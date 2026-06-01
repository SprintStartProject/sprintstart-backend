package com.sprintstart.sprintstartbackend.onboarding.seeding

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class SeedingControllerTest {
    private val seedingService: SeedingService = mockk()
    private val seedingController = SeedingController(seedingService)

    @Test
    fun `seed should call service and return success message`() {
        val userId = UUID.randomUUID()

        every {
            seedingService.seed(userId)
        } just runs

        val response = seedingController.seed(userId)

        assertThat(response.statusCode.value()).isEqualTo(200)
        assertThat(response.body).isEqualTo("Seeded successfully")

        verify(exactly = 1) {
            seedingService.seed(userId)
        }
    }

    @Test
    fun `reset should call service and return success message`() {
        val userId = UUID.randomUUID()

        every {
            seedingService.reset(userId)
        } just runs

        val response = seedingController.reset(userId)

        assertThat(response.statusCode.value()).isEqualTo(200)
        assertThat(response.body).isEqualTo("Reset successfully")

        verify(exactly = 1) {
            seedingService.reset(userId)
        }
    }
}
