package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintPathRepository
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.OnboardingSseEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs onboarding path generations detached from the request that started them.
 *
 * [OnboardingPersonalizationService.personalize] is a cold flow: collected straight into an SSE
 * response, it lives exactly as long as that connection. Navigating to another page, reloading, or
 * closing the tab closed the connection -- which cancelled a generation several minutes in, threw
 * away every phase the AI had already assembled, and sent the hire back to "Start personalization".
 *
 * Here a generation is collected in the application scope instead, and a request only *watches* it:
 *
 * - **One run per user.** A second request while one is in flight -- a reload, a second tab, a
 *   double click -- attaches to the running generation instead of starting a competing one, which
 *   would race it for the same path.
 * - **Late watchers catch up.** Recent events are replayed, so a page opened halfway through shows
 *   the phases that already finished, and the terminal `path`/`done` or `error` event always reaches
 *   whoever is watching at the end.
 * - **A dropped watcher costs nothing.** Only the watcher's own collection ends; the run keeps going
 *   and persists the path, which the next page load simply reads.
 *
 * In-memory on purpose: a generation is a few minutes of work tied to this instance. A restart
 * loses the run the same way it would have lost the request.
 *
 * **Per instance.** "One run per user" holds within one backend process. With several replicas, two
 * requests for the same user landing on different instances would each start a generation, and both
 * would replace the same path -- one of them lost. Fine while the backend runs as one instance; a
 * row-level claim (or sticky routing) is needed before it runs as several.
 */
@Component
class OnboardingGenerationRegistry(
    private val personalizationService: OnboardingPersonalizationService,
    private val blueprintPathRepository: BlueprintPathRepository,
    private val applicationScope: CoroutineScope,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val runs = ConcurrentHashMap<String, GenerationRun>()

    /**
     * One lock per user, so starting a generation -- which looks the user and project up first --
     * serializes only that user's repeated starts, not everybody's.
     */
    private val startLocks = ConcurrentHashMap<String, Any>()

    /** A generation in flight for one user. */
    class GenerationRun(
        val projectId: UUID,
        val startedAt: Instant,
    ) {
        internal val events = MutableSharedFlow<OnboardingSseEvent>(
            replay = REPLAYED_EVENTS,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

        /** The run's events from the replayed backlog up to and including its terminal event. */
        fun watch(): Flow<OnboardingSseEvent> =
            events.transformWhile { event ->
                emit(event)
                event.type !in TERMINAL_EVENT_TYPES
            }
    }

    /**
     * Starts a generation for the caller from [projectId], or attaches to the one already running.
     *
     * An attach ignores [projectId]: the running generation builds from the project it was started
     * with, and a user has one path, so there is nothing a second project could do but replace it
     * again. [status] reports which project that is.
     *
     * @throws org.springframework.web.server.ResponseStatusException the checks
     * [OnboardingPersonalizationService.personalize] makes before its flow exists (unknown user, user
     * not assigned to the project), so they still arrive as HTTP statuses.
     */
    fun startOrAttach(authId: String, projectId: UUID): Flow<OnboardingSseEvent> {
        runs[authId]?.let { return it.watch() }

        synchronized(startLocks.computeIfAbsent(authId) { Any() }) {
            runs[authId]?.let { return it.watch() }

            val generation = personalizationService.personalize(authId, projectId)
            val run = GenerationRun(projectId = projectId, startedAt = Instant.now())
            runs[authId] = run

            applicationScope.launch {
                try {
                    generation.collect { event -> run.events.emit(event) }
                } catch (cause: CancellationException) {
                    throw cause
                } catch (
                    @Suppress("TooGenericExceptionCaught") cause: Exception,
                ) {
                    // personalize() already turns failures into an error event; this is the last line
                    // for anything it lets through, so a watcher is never left waiting forever.
                    logger.error("Onboarding generation for {} ended unexpectedly", authId, cause)
                    run.events.emit(OnboardingSseEvent(type = "error", message = cause.message))
                } finally {
                    runs.remove(authId, run)
                }
            }

            return run.watch()
        }
    }

    /**
     * How many active blueprints [projectId] has. A path is built from exactly one: none means there
     * is nothing to build from, several means the build refuses to guess (409). Lets the onboarding
     * page say which, up front, instead of starting a generation that can only end in an error.
     */
    fun activeBlueprintCount(projectId: UUID): Long =
        blueprintPathRepository.countByProjectIdAndStatus(projectId, BlueprintStatus.ACTIVE)

    /** The generation currently running for the caller, or null. */
    fun status(authId: String): GenerationRun? = runs[authId]

    private companion object {
        /**
         * How many recent events a late watcher is replayed.
         *
         * Stage events are progress and only the latest few per phase matter, while the terminal
         * events come last -- so a bounded backlog always still holds everything a watcher needs.
         */
        const val REPLAYED_EVENTS = 400

        val TERMINAL_EVENT_TYPES = setOf("done", "error")
    }
}
