package com.sprintstart.sprintstartbackend.health

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@SpringBootTest
class ApplicationSmokeTest {
    @Test
    fun contextLoads() {
    }
}
