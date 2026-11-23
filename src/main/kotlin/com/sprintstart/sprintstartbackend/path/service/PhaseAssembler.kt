package com.sprintstart.sprintstartbackend.path.service

import com.sprintstart.sprintstartbackend.blueprint.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.path.external.PhaseContentAiClient
import com.sprintstart.sprintstartbackend.path.external.enums.GenerationStatus
import com.sprintstart.sprintstartbackend.path.external.model.AiProgressEvent
import com.sprintstart.sprintstartbackend.path.external.model.AssemblePhaseRequest
import com.sprintstart.sprintstartbackend.path.external.model.PhaseContentOutcome
import com.sprintstart.sprintstartbackend.path.factory.GeneratedOption
import com.sprintstart.sprintstartbackend.path.factory.GeneratedPhaseContent
import com.sprintstart.sprintstartbackend.path.factory.GeneratedQuestion
import com.sprintstart.sprintstartbackend.path.factory.GeneratedResource
import com.sprintstart.sprintstartbackend.path.factory.GeneratedStep
import com.sprintstart.sprintstartbackend.path.factory.GeneratedTask
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.slf4j.LoggerFactory
import java.util.UUID

/** One `AI_ENHANCED` blueprint phase the AI service has to assemble for a hire's path. */
internal data class PhaseAssemblyTarget(
    val id: UUID,
    val title: String,
    val description: String,
    val prompt: String,
)

/** One phase to assemble: the phase itself, the project whose material it draws on, and that project's industry. */
internal data class PhaseJob(
    val phase: PhaseAssemblyTarget,
    val project: ProjectId,
    val industry: String?,
)

/** What assembling one phase produced, and how it ended. */
internal data class PhaseGenerationResult(
    val content: GeneratedPhaseContent,
    val status: GenerationStatus,
)

/** The bounds one personalization run puts on its phases: how many at once and how long they may take. */
internal data class PhaseLimits(
    val phaseTimeoutMillis: Long,
    val totalTimeoutMillis: Long,
    val semaphore: Semaphore,
)

/** Assembles the AI-enhanced phases of a personalized path, one streamed AI call per phase. */
internal class PhaseAssembler(
    private val phaseContentAiClient: PhaseContentAiClient,
    private val json: Json,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Runs one phase's AI assembly under the concurrency and timeout bounds.
     *
     * The worker emits a `Waiting` stage before it enters the concurrency queue, then assembles the
     * phase inside a phase-level timeout. The whole worker — including the wait for a concurrency
     * permit — runs inside the overall generation timeout, so a run past the total deadline neither
     * starts new phases nor lets a running one finish late. A timeout is not a failure: it returns
     * an empty result with [GenerationStatus.TIMED_OUT] so siblings keep working and the timed-out
     * phase is persisted as a hidden placeholder instead of blocking the path.
     */
    suspend fun generatePhase(
        job: PhaseJob,
        limits: PhaseLimits,
        emitStage: suspend (detail: String) -> Unit,
    ): PhaseGenerationResult {
        emitStage("Waiting")
        val result = withTimeoutOrNull(limits.totalTimeoutMillis) {
            limits.semaphore.withPermit {
                runPhaseAssembly(job, limits.phaseTimeoutMillis, emitStage)
            }
        }
        return result ?: timedOut(job, emitStage, limits.totalTimeoutMillis)
    }

    /**
     * Assembles one phase, cancelling it with a `TIMED_OUT` placeholder when the phase-level
     * timeout elapses and relaying a `Completed` stage when the AI produced content in time.
     */
    private suspend fun runPhaseAssembly(
        job: PhaseJob,
        phaseTimeoutMillis: Long,
        emitStage: suspend (detail: String) -> Unit,
    ): PhaseGenerationResult {
        val result = withTimeoutOrNull(phaseTimeoutMillis) {
            streamPhaseContent(job) { detail -> emitStage(detail) }
        }
        if (result == null) {
            return timedOut(job, emitStage, phaseTimeoutMillis)
        }
        if (result.status == GenerationStatus.GENERATED) {
            emitStage("Completed")
        }
        return result
    }

    private suspend fun timedOut(
        job: PhaseJob,
        emitStage: suspend (detail: String) -> Unit,
        timeoutMillis: Long,
    ): PhaseGenerationResult {
        emitStage("Timed out after ${timeoutMillis / 1_000} seconds")
        // A timed-out phase is cancelled mid-stream, so streamPhaseContent's own outcome log
        // never runs; log the status and which deadline caught it here so it is always visible.
        val (phase, project) = job
        logger.warn(
            "AI phase assembly for blueprint phase {} ('{}') in project {} ended with status {}: " +
                "timed out after {} seconds",
            phase.id,
            phase.title,
            project.value,
            GenerationStatus.TIMED_OUT,
            timeoutMillis / 1_000,
        )
        return PhaseGenerationResult(
            content = GeneratedPhaseContent(),
            status = GenerationStatus.TIMED_OUT,
        )
    }

    /**
     * Streams the AI assembly for one `AI_ENHANCED` phase and returns the assembled content.
     *
     * Relays the AI service's live progress onward via [relay] (each upstream `stage`/`item`/
     * `warning` becomes a stage event carrying its label), and takes the content from the terminal
     * `done` event's `result`, so it is byte-for-byte what the non-streaming call would return. A
     * stream that fails mid-way, a `skipped`/`unchanged` outcome, or a result that cannot be decoded
     * all yield empty content with an explicit generation status — never fabricated content.
     */
    private suspend fun streamPhaseContent(
        job: PhaseJob,
        relay: suspend (detail: String) -> Unit,
    ): PhaseGenerationResult {
        val (phase, project) = job
        val run = PhaseRun()
        phaseContentAiClient
            .streamPhase(job.toRequest())
            .onEach { event ->
                logStreamEvent(phase, event)
                when (event.type) {
                    AiProgressEvent.DONE -> run.settle(phase, decodeOutcome(event))
                    AiProgressEvent.ERROR -> run.fail(phase, event)
                    else -> relay(event.label ?: event.message ?: event.type)
                }
            }.catch { cause ->
                run.reason = "stream failed: ${cause.message ?: cause.javaClass.simpleName}"
                logger.error(
                    "Phase assembly stream failed for '{}' ({}) on project {}",
                    phase.title,
                    phase.id,
                    project.value,
                    cause,
                )
            }.collect { }
        return run.result(phase, project)
    }

    private fun logStreamEvent(phase: PhaseAssemblyTarget, event: AiProgressEvent) {
        logger.info(
            "AI phase stream event for phase {}: type={}, operation={}, seq={}, label={}",
            phase.id,
            event.type,
            event.operation,
            event.seq,
            event.label ?: event.message,
        )
    }

    /** What one phase's stream has told us so far; it starts out as the failure it is if nothing more arrives. */
    private inner class PhaseRun {
        var content: GeneratedPhaseContent? = null
        var status = GenerationStatus.FAILED
        var reason = "stream ended without a done event"

        fun settle(phase: PhaseAssemblyTarget, outcome: PhaseContentOutcome?) {
            when {
                outcome == null -> {
                    status = GenerationStatus.FAILED
                    reason = "done event carried no decodable result"
                    logger.warn("AI phase stream completed without a decodable result for phase {}", phase.id)
                }

                outcome.status != "assembled" -> {
                    status = when (outcome.status) {
                        "skipped", "unchanged" -> GenerationStatus.SKIPPED
                        else -> GenerationStatus.FAILED
                    }
                    reason = "AI reported status '${outcome.status}'"
                    logUnassembled(phase, outcome)
                }

                else -> assembled(phase, outcome.toGeneratedPhaseContent())
            }
        }

        fun fail(phase: PhaseAssemblyTarget, event: AiProgressEvent) {
            val message = event.message ?: event.label ?: "No error message supplied"
            reason = "stream returned an error event: $message"
            logger.error("AI phase stream returned an error event for phase {}: {}", phase.id, message)
        }

        fun result(phase: PhaseAssemblyTarget, project: ProjectId): PhaseGenerationResult {
            if (status != GenerationStatus.GENERATED) {
                logger.warn(
                    "AI phase assembly for blueprint phase {} ('{}') in project {} ended with status {}: {}",
                    phase.id,
                    phase.title,
                    project.value,
                    status,
                    reason,
                )
            }
            if (content == null) {
                logger.warn(
                    "Using empty generated content for blueprint phase {} ('{}') in project {}",
                    phase.id,
                    phase.title,
                    project.value,
                )
            }
            return PhaseGenerationResult(content = content ?: GeneratedPhaseContent(), status = status)
        }

        private fun assembled(phase: PhaseAssemblyTarget, assembled: GeneratedPhaseContent) {
            content = assembled
            status = if (assembled.isEmpty()) {
                reason = "AI assembled an empty phase"
                GenerationStatus.EMPTY
            } else {
                GenerationStatus.GENERATED
            }
            logger.info(
                "AI phase assembly completed for phase {} with {} step(s) and {} question(s)",
                phase.id,
                assembled.steps.size,
                assembled.checkQuestions.size,
            )
        }
    }

    private fun logUnassembled(phase: PhaseAssemblyTarget, outcome: PhaseContentOutcome) {
        logger.warn(
            "AI phase assembly did not produce content for phase {}: status={}, " +
                "chunksRetrieved={}, stepsDropped={}, questionsDropped={}, notes={}",
            phase.id,
            outcome.status,
            outcome.chunksRetrieved,
            outcome.stepsDropped,
            outcome.questionsDropped,
            outcome.notes.joinToString("; ").take(2_000),
        )
    }

    private fun decodeOutcome(event: AiProgressEvent): PhaseContentOutcome? =
        event.result?.let {
            runCatching { json.decodeFromJsonElement<PhaseContentOutcome>(it) }
                .onFailure { e -> logger.warn("Could not decode streamed phase result: {}", e.message) }
                .getOrNull()
        }
}

private fun PhaseJob.toRequest() =
    AssemblePhaseRequest(
        phaseTitle = phase.title,
        phaseDescription = phase.description,
        phasePrompt = phase.prompt,
        projectId = project.value.toString(),
        industry = industry,
    )

internal fun PhaseContentOutcome.toGeneratedPhaseContent(): GeneratedPhaseContent = GeneratedPhaseContent(
    steps = steps.map { step ->
        GeneratedStep(
            key = step.key,
            title = step.title,
            description = step.description,
            tasks = step.tasks.map { GeneratedTask(title = it.title, description = it.description) },
            resources = step.resources.map { GeneratedResource(title = it.title, url = it.url) },
            estimatedMinutes = step.estimatedMinutes,
            expectedOutcome = step.expectedOutcome,
            blockedBy = step.blockedBy,
        )
    },
    checkQuestions = checkQuestions.mapNotNull { question ->
        val type = runCatching { CheckQuestionType.valueOf(question.type) }.getOrNull()
            ?: return@mapNotNull null
        GeneratedQuestion(
            key = question.key,
            type = type,
            question = question.question,
            explanation = question.explanation,
            correctAnswer = question.correctAnswer,
            options = question.options.map { GeneratedOption(label = it.label, correct = it.correct) },
            blockedBy = question.blockedBy,
        )
    },
)

private fun GeneratedPhaseContent.isEmpty(): Boolean = steps.isEmpty() && checkQuestions.isEmpty()
