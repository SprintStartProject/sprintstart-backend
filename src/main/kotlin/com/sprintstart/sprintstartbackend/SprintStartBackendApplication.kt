package com.sprintstart.sprintstartbackend

import com.sprintstart.sprintstartbackend.shared.git.GitConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableConfigurationProperties(ApplicationConfig::class, GitConfig::class)
@EnableScheduling
class SprintStartBackendApplication

fun main(args: Array<String>) {
    runApplication<SprintStartBackendApplication>(*args)
}
