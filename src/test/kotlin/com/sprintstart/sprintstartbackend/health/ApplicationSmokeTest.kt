package com.sprintstart.sprintstartbackend.health

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:mydb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
    ],
)
class ApplicationSmokeTest {
    @Test
    fun contextLoads() {
        println("Test: Hello World")
    }
}
