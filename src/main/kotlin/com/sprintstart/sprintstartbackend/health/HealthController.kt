package com.sprintstart.sprintstartbackend.health

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/health")
class HealthController {
    @GetMapping
    fun health(): Map<String, String> = mapOf("status" to "UP")
}
