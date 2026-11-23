package com.sprintstart.sprintstartbackend.config

import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Keeps the published OpenAPI operation ids free of Kotlin's name mangling.
 *
 * A function that takes a value class, such as [com.sprintstart.sprintstartbackend.shared.ids.ProjectId],
 * compiles to a JVM method whose name carries a hash (`getMyBoard-6lUknxE`), and springdoc names an
 * operation after that method. The hash is dropped, so an endpoint keeps its id when its parameters
 * become typed.
 */
@Configuration
class OpenApiConfig {
    @Bean
    fun unmangledOperationIds(): OperationCustomizer =
        OperationCustomizer { operation, _ ->
            operation.operationId = operation.operationId?.replace(MANGLING_HASH, "")
            operation
        }

    private companion object {
        /** The `-` and seven characters Kotlin appends, before any `_1` springdoc adds for a duplicate. */
        val MANGLING_HASH = Regex("""-[A-Za-z0-9_-]{7}(?=(_\d+)?$)""")
    }
}
