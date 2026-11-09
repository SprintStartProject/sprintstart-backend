package com.sprintstart.sprintstartbackend.path.service

import com.sprintstart.sprintstartbackend.blueprint.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.path.external.PhaseContentAiClient
import com.sprintstart.sprintstartbackend.path.external.enums.GenerationStatus
import com.sprintstart.sprintstartbackend.path.external.model.AiProgressEvent
import com.sprintstart.sprintstartbackend.path.external.model.AssemblePhaseRequest
import com.sprintstart.sprintstartbackend.path.external.model.PhaseCheckOptionDto
import com.sprintstart.sprintstartbackend.path.external.model.PhaseCheckQuestionDto
import com.sprintstart.sprintstartbackend.path.external.model.PhaseContentOutcome
import com.sprintstart.sprintstartbackend.path.external.model.PhaseResourceDto
import com.sprintstart.sprintstartbackend.path.external.model.PhaseStepDto
import com.sprintstart.sprintstartbackend.path.external.model.PhaseTaskDto
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class PhaseAssemblerTest {
    private val client = mockk<PhaseContentAiClient>()
    private val json = Json { ignoreUnknownKeys = true }
    private val assembler = PhaseAssembler(client, json)
    private val project = ProjectId(UUID.randomUUID())
    private val job = PhaseJob(
        PhaseAssemblyTarget(UUID.randomUUID(), "Overview", "About", "Explain it"),
        project,
        "fintech",
    )
    private val limits = PhaseLimits(phaseTimeoutMillis = 5_000, totalTimeoutMillis = 10_000, semaphore = Semaphore(2))
    private val stages = mutableListOf<String>()

    private fun run(events: Flow<AiProgressEvent>, limits: PhaseLimits = this.limits): PhaseGenerationResult {
        every { client.streamPhase(any()) } returns events
        return runBlocking { assembler.generatePhase(job, limits) { stages += it } }
    }

    private fun done(outcome: PhaseContentOutcome) =
        AiProgressEvent(type = AiProgressEvent.DONE, result = json.encodeToJsonElement(outcome))

    private val step = PhaseStepDto(
        key = "s1",
        title = "Set up",
        description = "d",
        tasks = listOf(PhaseTaskDto("Install", "now")),
        resources = listOf(PhaseResourceDto("Docs", "https://docs")),
        estimatedMinutes = 15,
        expectedOutcome = "ready",
        blockedBy = listOf("s0"),
    )

    @Test
    fun `an assembled phase is generated, relays progress and sends the request for its job`() {
        val request = slot<AssemblePhaseRequest>()
        every { client.streamPhase(capture(request)) } returns flowOf(
            AiProgressEvent(type = "stage", label = "Searching"),
            AiProgressEvent(type = "item", message = "found one"),
            done(PhaseContentOutcome(status = "assembled", steps = listOf(step))),
        )

        val result = runBlocking { assembler.generatePhase(job, limits) { stages += it } }

        assertThat(result.status).isEqualTo(GenerationStatus.GENERATED)
        val generated = result.content.steps.single()
        assertThat(generated.title).isEqualTo("Set up")
        assertThat(generated.tasks.single().title).isEqualTo("Install")
        assertThat(generated.resources.single().url).isEqualTo("https://docs")
        assertThat(generated.estimatedMinutes).isEqualTo(15)
        assertThat(generated.blockedBy).containsExactly("s0")
        assertThat(stages).containsExactly("Waiting", "Searching", "found one", "Completed")
        assertThat(request.captured.phaseTitle).isEqualTo("Overview")
        assertThat(request.captured.phasePrompt).isEqualTo("Explain it")
        assertThat(request.captured.projectId).isEqualTo(project.value.toString())
        assertThat(request.captured.industry).isEqualTo("fintech")
    }

    @Test
    fun `questions of an unknown type are dropped and the rest keep their options`() {
        val outcome = PhaseContentOutcome(
            status = "assembled",
            checkQuestions = listOf(
                PhaseCheckQuestionDto(type = "NONSENSE", question = "?"),
                PhaseCheckQuestionDto(
                    key = "q1",
                    type = CheckQuestionType.entries.first().name,
                    question = "Which?",
                    explanation = "because",
                    options = listOf(PhaseCheckOptionDto(label = "A", correct = true)),
                    blockedBy = listOf("s1"),
                ),
            ),
        )

        val result = run(flowOf(done(outcome)))

        val question = result.content.checkQuestions.single()
        assertThat(question.question).isEqualTo("Which?")
        assertThat(question.explanation).isEqualTo("because")
        assertThat(question.options.single().correct).isTrue()
        assertThat(question.blockedBy).containsExactly("s1")
    }

    @Test
    fun `an assembled but empty phase is empty, not generated, and says nothing of completion`() {
        val result = run(flowOf(done(PhaseContentOutcome(status = "assembled"))))

        assertThat(result.status).isEqualTo(GenerationStatus.EMPTY)
        assertThat(result.content.steps).isEmpty()
        assertThat(stages).containsExactly("Waiting")
    }

    @Test
    fun `skipped and unchanged outcomes are skipped, any other status failed`() {
        assertThat(run(flowOf(done(PhaseContentOutcome(status = "skipped")))).status)
            .isEqualTo(GenerationStatus.SKIPPED)
        assertThat(run(flowOf(done(PhaseContentOutcome(status = "unchanged")))).status)
            .isEqualTo(GenerationStatus.SKIPPED)
        assertThat(run(flowOf(done(PhaseContentOutcome(status = "weird")))).status)
            .isEqualTo(GenerationStatus.FAILED)
    }

    @Test
    fun `a done event without a decodable result fails the phase with empty content`() {
        val result = run(flowOf(AiProgressEvent(type = AiProgressEvent.DONE)))

        assertThat(result.status).isEqualTo(GenerationStatus.FAILED)
        assertThat(result.content.steps).isEmpty()
    }

    @Test
    fun `an error event or a stream that ends early or throws fails the phase`() {
        assertThat(run(flowOf(AiProgressEvent.error("op", "boom"))).status).isEqualTo(GenerationStatus.FAILED)
        assertThat(run(flowOf()).status).isEqualTo(GenerationStatus.FAILED)
        assertThat(run(flow { error("connection reset") }).status).isEqualTo(GenerationStatus.FAILED)
    }

    @Test
    fun `a phase that outlasts its timeout is a timed out placeholder`() {
        val slow = flow<AiProgressEvent> { delay(5_000) }

        val result = run(slow, limits.copy(phaseTimeoutMillis = 50))

        assertThat(result.status).isEqualTo(GenerationStatus.TIMED_OUT)
        assertThat(result.content.steps).isEmpty()
        assertThat(stages).containsExactly("Waiting", "Timed out after 0 seconds")
    }

    @Test
    fun `a run past the total deadline times out before the phase does`() {
        val slow = flow<AiProgressEvent> { delay(5_000) }

        val result = run(slow, limits.copy(totalTimeoutMillis = 1_000, phaseTimeoutMillis = 30_000))

        assertThat(result.status).isEqualTo(GenerationStatus.TIMED_OUT)
        assertThat(stages).containsExactly("Waiting", "Timed out after 1 seconds")
    }
}
