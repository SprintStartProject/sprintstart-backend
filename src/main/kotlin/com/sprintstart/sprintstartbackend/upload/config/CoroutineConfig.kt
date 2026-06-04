package com.sprintstart.sprintstartbackend.upload.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Provides application-scoped coroutine infrastructure.
 *
 * Defines a [CoroutineScope] that lives for the entire lifetime of the application,
 * intended for fire-and-forget coroutines that are not tied to any specific request
 * or component lifecycle — such as event listeners that need to call suspend functions.
 *
 * ### Why [SupervisorJob]
 * A [SupervisorJob] ensures that if one child coroutine fails, it does not propagate
 * the failure upward and cancel all sibling coroutines sharing this scope. Without it,
 * a single failed [AiClient.ingest] call could cancel all other in-flight coroutines
 * across the application.
 *
 * ### Why [Dispatchers.Default]
 * [Dispatchers.Default] is used as the base dispatcher since the coroutines launched
 * from this scope are expected to delegate their actual blocking I/O to [Dispatchers.IO]
 * themselves (as [SyncExecution] and [StreamExecution] already do). If your use case
 * involves purely CPU-bound work, [Dispatchers.Default] remains the right choice.
 *
 * ### Usage
 * Inject [CoroutineScope] wherever a non-suspend context needs to launch a coroutine:
 * ```kotlin
 * @EventListener
 * fun handleEvent(event: MyEvent) {
 *     applicationScope.launch {
 *         suspendingClient.process(event)
 *     }
 * }
 * ```
 *
 * @see SupervisorJob
 * @see Dispatchers.Default
 */

@Configuration
class CoroutineConfig {
    @Bean
    fun applicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
