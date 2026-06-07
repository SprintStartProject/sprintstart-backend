package com.sprintstart.sprintstartbackend.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Provides an application scoped coroutine to use for starting background tasks.
 *
 * When running asynchronous code in kotlin, it needs to run in a scope. That means it
 * needs to be started, and completed in a given scope. For just running jobs asynchronously,
 * it's fine to define such a scope inside the desired `suspend` function. However, as stated,
 * the asynchronous code must finish in that scope too.
 *
 * By providing this global application scope for coroutines, when using it, jobs can be started
 * without having to worry about completion of these jobs in that local scope. That means, it's
 * true fire-and-forget. Using this scope, functions can now start jobs, and just continue what
 * they were doing, return values, finish, whatever. Doesn't matter anymore.
 *
 * Usage:
 *
 * ```kt
 * class TestClass(
 *     private val applicationScope: CoroutineScope,
 * ) {
 *     fun doSomething(): UUID {
 *         val jobId = UUID.randomUUID()
 *
 *         applicationScope.launch {
 *             launch { doSomethingElse(...) }
 *             launch { doOtherStuff(...) }
 *         }
 *
 *         return jobId
 *     }
 * }
 * ```
 */
@Configuration
class CoroutineConfig {
    @Bean
    fun applicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
