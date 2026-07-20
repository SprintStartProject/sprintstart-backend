package com.sprintstart.sprintstartbackend.connectors.github.util

import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

/**
 * Loads GraphQL queries from the classpath.
 *
 * Essentially just an abstraction over [ClassPathResource], for the sake of testability.
 */
@Component
class GithubQueryLoader {
    fun load(path: String): String =
        ClassPathResource(path)
            .inputStream
            .bufferedReader()
            .readText()
}
