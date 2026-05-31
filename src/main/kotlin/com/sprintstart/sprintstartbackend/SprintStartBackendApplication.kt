package com.sprintstart.sprintstartbackend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(ApplicationConfig::class)
class SprintStartBackendApplication

fun main(args: Array<String>) {
    runApplication<SprintStartBackendApplication>(*args)
}
