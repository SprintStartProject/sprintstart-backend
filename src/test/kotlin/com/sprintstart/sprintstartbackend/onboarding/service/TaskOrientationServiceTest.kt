package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.ingestion.external.TaskSourceArtifact
import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.OrientationOrigin
import com.sprintstart.sprintstartbackend.onboarding.external.enums.OrientationStep
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.onboarding.external.model.AiProgressEvent
import com.sprintstart.sprintstartbackend.onboarding.external.model.AiProvenanceSchema
import com.sprintstart.sprintstartbackend.onboarding.external.model.CitationRefSchema
import com.sprintstart.sprintstartbackend.onboarding.external.model.OrientationOutcome
import com.sprintstart.sprintstartbackend.onboarding.external.model.OrientationPacketSchema
import com.sprintstart.sprintstartbackend.onboarding.external.model.OrientationSectionSchema
import com.sprintstart.sprintstartbackend.onboarding.external.model.OrientationSourceSchema
import com.sprintstart.sprintstartbackend.onboarding.model.entity.StarterWorkTaskProposal
import com.sprintstart.sprintstartbackend.onboarding.model.entity.TaskOrientationCitation
import com.sprintstart.sprintstartbackend.onboarding.model.entity.TaskOrientationPacket
import com.sprintstart.sprintstartbackend.onboarding.model.entity.TaskOrientationSection
import com.sprintstart.sprintstartbackend.onboarding.model.entity.TaskZeroAssignment
import com.sprintstart.sprintstartbackend.onboarding.model.exceptions.OnboardingAiException
import com.sprintstart.sprintstartbackend.onboarding.model.request.orientation.AuthorOrientationCitationRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.orientation.AuthorOrientationRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.orientation.AuthorOrientationSectionRequest
import com.sprintstart.sprintstartbackend.onboarding.repository.StarterWorkTaskProposalRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.TaskOrientationPacketRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.TaskZeroAssignmentRepository
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskOrientationServiceTest {
    private val packetRepository: TaskOrientationPacketRepository = mockk(relaxed = true)
    private val assignmentRepository: TaskZeroAssignmentRepository = mockk(relaxed = true)
    private val proposalRepository: StarterWorkTaskProposalRepository = mockk(relaxed = true)
    private val projectMembershipApi: ProjectMembershipApi = mockk()
    private val artifactIngestionApi: ArtifactIngestionApi = mockk()
    private val onboardingAiClient: OnboardingAiClient = mockk()
    private val json: Json = Json { ignoreUnknownKeys = true }
    private val transactionManager: PlatformTransactionManager = mockk(relaxed = true)

    private val now: Instant = Instant.parse("2026-07-21T12:00:00Z")
    private val hireId: UUID = UUID.randomUUID()
    private val projectId: UUID = UUID.randomUUID()

    private val service = TaskOrientationService(
        packetRepository,
        assignmentRepository,
        proposalRepository,
        projectMembershipApi,
        artifactIngestionApi,
        onboardingAiClient,
        json,
        transactionManager,
        Clock.fixed(now, ZoneOffset.UTC),
    )

    private val proposal = StarterWorkTaskProposal(
        sourceId = "github:org/repo:ISSUE:7",
        title = "Fix the stale cache header",
        summary = "The header is computed once at boot.",
        sourceUrl = "https://github.com/org/repo/issues/7",
        status = ProposalStatus.LIVE,
        taskZeroEligible = true,
    )

    private fun isMember() {
        every { projectMembershipApi.getProjectMembers(projectId) } returns
            listOf(ProjectMember(hireId, "A Hire", "hire", now.minus(Duration.ofDays(3))))
    }

    private fun hasTask(cached: TaskOrientationPacket? = null) {
        isMember()
        every { assignmentRepository.findByHireIdAndProjectId(hireId, projectId) } returns
            TaskZeroAssignment(hireId = hireId, projectId = projectId, proposalId = proposal.id, assignedAt = now)
        every { proposalRepository.findById(proposal.id) } returns Optional.of(proposal)
        every { artifactIngestionApi.getTaskSource(proposal.sourceId) } returns null
        every { packetRepository.findByTaskProposalIdAndProjectId(proposal.id, projectId) } returns cached
        every { packetRepository.save(any()) } answers { firstArg() }
    }

    private fun assembled(
        vararg sections: OrientationSectionSchema,
        fingerprint: String = "fp-1",
    ) = OrientationOutcome(
        status = "assembled",
        packet = OrientationPacketSchema(
            taskTitle = proposal.title,
            summary = "What you need to change the header.",
            sections = sections.toList(),
            sources = listOf(OrientationSourceSchema("README.md", "https://example.test/README.md", "FILE")),
        ),
        provenance = AiProvenanceSchema(corpusFingerprint = fingerprint, model = "test-model"),
    )

    private fun section(step: String, title: String = "A section") = OrientationSectionSchema(
        step = step,
        title = title,
        body = "$title body",
        citations = listOf(CitationRefSchema("README.md", "c1", "https://example.test/README.md")),
    )

    private fun cachedPacket(
        fingerprint: String,
        origin: OrientationOrigin = OrientationOrigin.AI,
    ) = TaskOrientationPacket(
        taskProposalId = proposal.id,
        projectId = projectId,
        taskTitle = proposal.title,
        origin = origin,
        summary = "A cached packet.",
        corpusFingerprint = fingerprint,
        assembledAt = now.minus(Duration.ofDays(1)),
    ).apply {
        sections.add(
            TaskOrientationSection(
                packet = this,
                step = OrientationStep.SET_UP,
                title = "Cached section",
                body = "Cached body",
                position = 0,
            ).apply {
                citations.add(
                    TaskOrientationCitation(section = this, filename = "README.md", chunkId = "c1", position = 0),
                )
            },
        )
    }

    @Test
    fun `stores an assembled packet with its provenance and serves it`() = runTest {
        hasTask()
        coEvery { onboardingAiClient.assembleOrientation(any(), any(), any(), any(), any()) } returns
            assembled(section("SET_UP", "Run it locally"), section("OPEN_THE_PR", "How review works"))

        val result = service.getForHire(hireId, projectId)

        val packet = assertNotNull(result.packet)
        assertEquals(listOf(OrientationStep.SET_UP, OrientationStep.OPEN_THE_PR), packet.sections.map { it.step })
        // Provenance has to survive to the client: it is what a hire checks the packet against.
        assertEquals("https://example.test/README.md", packet.sections[0].citations[0].sourceUrl)
        assertEquals("README.md", packet.sources[0].filename)
        assertEquals(proposal.id, result.taskId)
        assertNull(result.reason)
    }

    @Test
    fun `drops a section of a step the backend cannot store`() = runTest {
        hasTask()
        coEvery { onboardingAiClient.assembleOrientation(any(), any(), any(), any(), any()) } returns
            assembled(section("SET_UP"), section("DEPLOY_IT", "Not a step of ours"))

        val result = service.getForHire(hireId, projectId)

        assertEquals(listOf(OrientationStep.SET_UP), assertNotNull(result.packet).sections.map { it.step })
    }

    @Test
    fun `sends the cached fingerprint so an unchanged corpus is not re-assembled`() = runTest {
        hasTask(cached = cachedPacket("fp-old"))
        val sent = slot<String>()
        coEvery { onboardingAiClient.assembleOrientation(any(), any(), any(), any(), capture(sent)) } returns
            OrientationOutcome(status = "unchanged")

        val result = service.getForHire(hireId, projectId)

        assertEquals("fp-old", sent.captured)
        // The cache is served, and nothing is written.
        assertEquals("Cached section", assertNotNull(result.packet).sections[0].title)
        verify(exactly = 0) { packetRepository.save(any()) }
    }

    @Test
    fun `a moved corpus replaces the cached packet rather than serving it`() = runTest {
        val cached = cachedPacket("fp-old")
        hasTask(cached = cached)
        coEvery { onboardingAiClient.assembleOrientation(any(), any(), any(), any(), any()) } returns
            assembled(section("CHECK_LOCALLY", "Run the tests"), fingerprint = "fp-new")

        val result = service.getForHire(hireId, projectId)

        assertEquals("Run the tests", assertNotNull(result.packet).sections[0].title)
        verify(exactly = 1) { packetRepository.delete(cached) }
    }

    @Test
    fun `a skipped assembly drops the stale cache and says so, rather than serving it`() = runTest {
        hasTask(cached = cachedPacket("fp-old"))
        coEvery { onboardingAiClient.assembleOrientation(any(), any(), any(), any(), any()) } returns
            OrientationOutcome(status = "skipped", notes = listOf("no grounding evidence retrieved for this task"))

        val result = service.getForHire(hireId, projectId)

        // The AI compared against the *current* corpus, so what is cached describes code that moved.
        assertNull(result.packet)
        assertEquals("no grounding evidence retrieved for this task", result.reason)
        verify(exactly = 1) { packetRepository.deleteByTaskProposalIdAndProjectId(proposal.id, projectId) }
    }

    @Test
    fun `an empty corpus is an honest empty state, never a fabricated packet`() = runTest {
        hasTask()
        coEvery { onboardingAiClient.assembleOrientation(any(), any(), any(), any(), any()) } returns
            OrientationOutcome(status = "skipped", notes = listOf("corpus is empty"))

        val result = service.getForHire(hireId, projectId)

        assertNull(result.packet)
        assertEquals("corpus is empty", result.reason)
        // The task itself still reaches the hire, so they can open the issue and ask a person.
        assertEquals(proposal.id, result.taskId)
        assertEquals("https://github.com/org/repo/issues/7", result.taskUrl)
    }

    @Test
    fun `an unreachable AI service serves the last known good packet`() = runTest {
        hasTask(cached = cachedPacket("fp-old"))
        coEvery { onboardingAiClient.assembleOrientation(any(), any(), any(), any(), any()) } throws
            OnboardingAiException(503, "", "AI is down")

        val result = service.getForHire(hireId, projectId)

        // Unlike `skipped`, a transport failure is no evidence at all that the cache is stale.
        assertEquals("Cached section", assertNotNull(result.packet).sections[0].title)
        assertNull(result.reason)
        verify(exactly = 0) { packetRepository.deleteByTaskProposalIdAndProjectId(any(), any()) }
    }

    @Test
    fun `an unreachable AI service with nothing cached says so`() = runTest {
        hasTask()
        coEvery { onboardingAiClient.assembleOrientation(any(), any(), any(), any(), any()) } throws
            OnboardingAiException(503, "", "AI is down")

        val result = service.getForHire(hireId, projectId)

        assertNull(result.packet)
        assertEquals("Orientation is temporarily unavailable", result.reason)
    }

    @Test
    fun `no current task is a handled state and calls no AI`() = runTest {
        isMember()
        every { assignmentRepository.findByHireIdAndProjectId(hireId, projectId) } returns null

        val result = service.getForHire(hireId, projectId)

        assertNull(result.taskId)
        assertNull(result.packet)
        coVerify(exactly = 0) { onboardingAiClient.assembleOrientation(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `assembles from the issue's own words and labels, not the mined summary`() = runTest {
        hasTask()
        every { artifactIngestionApi.getTaskSource(proposal.sourceId) } returns
            TaskSourceArtifact(
                title = "Stale cache header on /api/v1/reports",
                body = "The header is set at boot and never refreshed.",
                labels = listOf("good first issue", "bug"),
                sourceUrl = "https://github.com/org/repo/issues/7",
            )
        val title = slot<String>()
        val body = slot<String>()
        val labels = slot<List<String>>()
        coEvery {
            onboardingAiClient.assembleOrientation(capture(title), capture(body), capture(labels), any(), any())
        } returns assembled(section("SET_UP"))

        service.getForHire(hireId, projectId)

        assertEquals("Stale cache header on /api/v1/reports", title.captured)
        assertEquals("The header is set at boot and never refreshed.", body.captured)
        assertEquals(listOf("good first issue", "bug"), labels.captured)
    }

    @Test
    fun `falls back to the proposal when the issue is no longer ingested`() = runTest {
        hasTask()
        val title = slot<String>()
        val body = slot<String>()
        coEvery {
            onboardingAiClient.assembleOrientation(capture(title), capture(body), any(), any(), any())
        } returns assembled(section("SET_UP"))

        service.getForHire(hireId, projectId)

        assertEquals("Fix the stale cache header", title.captured)
        assertEquals("The header is computed once at boot.", body.captured)
    }

    @Test
    fun `reading orientation never assigns a task`() = runTest {
        hasTask()
        coEvery { onboardingAiClient.assembleOrientation(any(), any(), any(), any(), any()) } returns
            assembled(section("SET_UP"))

        service.getForHire(hireId, projectId)

        verify(exactly = 0) { assignmentRepository.save(any()) }
    }

    @Test
    fun `404s when the hire is not a member of the project`() = runTest {
        every { projectMembershipApi.getProjectMembers(projectId) } returns emptyList()

        assertThrows<ResponseStatusException> { service.getForHire(hireId, projectId) }
    }

    @Test
    fun `stores every citation of every section`() = runTest {
        hasTask()
        val saved = slot<TaskOrientationPacket>()
        every { packetRepository.save(capture(saved)) } answers { firstArg() }
        coEvery { onboardingAiClient.assembleOrientation(any(), any(), any(), any(), any()) } returns
            assembled(section("SET_UP"), section("MAKE_THE_CHANGE"))

        service.getForHire(hireId, projectId)

        assertTrue(saved.captured.sections.all { it.citations.isNotEmpty() })
        assertEquals("fp-1", saved.captured.corpusFingerprint)
        assertEquals(now, saved.captured.assembledAt)
    }

    // ========================== Human authoring ==========================

    private fun authorRequest() = AuthorOrientationRequest(
        summary = "  How to do it.  ",
        sections = listOf(
            AuthorOrientationSectionRequest(
                step = OrientationStep.SET_UP,
                title = "  Run it  ",
                body = "  make dev  ",
                citations = listOf(AuthorOrientationCitationRequest("README.md", "https://example.test/README.md")),
            ),
            AuthorOrientationSectionRequest(
                step = OrientationStep.OPEN_THE_PR,
                title = "Open the PR",
                body = "Push and open a PR.",
                citations = emptyList(),
            ),
        ),
    )

    private fun proposalExists() {
        every { proposalRepository.findById(proposal.id) } returns Optional.of(proposal)
        every { packetRepository.findByTaskProposalIdAndProjectId(proposal.id, projectId) } returns null
        every { packetRepository.save(any()) } answers { firstArg() }
    }

    @Test
    fun `a human-authored cached packet is served as-is, with no AI call`() = runTest {
        hasTask(cached = cachedPacket("fp-old", origin = OrientationOrigin.HUMAN))

        val result = service.getForHire(hireId, projectId)

        assertEquals(OrientationOrigin.HUMAN, assertNotNull(result.packet).origin)
        assertEquals("Cached section", result.packet!!.sections[0].title)
        // The whole staleness dance is skipped: no assembly, no fingerprint, no delete, no re-store.
        coVerify(exactly = 0) { onboardingAiClient.assembleOrientation(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { packetRepository.save(any()) }
        verify(exactly = 0) { packetRepository.deleteByTaskProposalIdAndProjectId(any(), any()) }
    }

    @Test
    fun `authorPacket pins a HUMAN packet, trims it, and stores empty chunk ids`() = runTest {
        proposalExists()
        val saved = slot<TaskOrientationPacket>()
        every { packetRepository.save(capture(saved)) } answers { firstArg() }

        val result = service.authorPacket(proposal.id, projectId, authorRequest())

        assertEquals(OrientationOrigin.HUMAN, result.origin)
        assertEquals(OrientationOrigin.HUMAN, saved.captured.origin)
        assertEquals("How to do it.", saved.captured.summary)
        assertNull(saved.captured.corpusFingerprint)
        val steps = saved.captured.sections.map { it.step }
        assertEquals(listOf(OrientationStep.SET_UP, OrientationStep.OPEN_THE_PR), steps)
        assertEquals("Run it", saved.captured.sections[0].title)
        assertEquals("make dev", saved.captured.sections[0].body)
        // A human citation has no retrieval chunk; the column stays non-null via an empty string.
        val firstCitation = saved.captured.sections[0].citations[0]
        assertEquals("", firstCitation.chunkId)
        assertEquals(now, saved.captured.assembledAt)
    }

    @Test
    fun `authorPacket derives sources from the distinct citation links`() = runTest {
        proposalExists()
        val saved = slot<TaskOrientationPacket>()
        every { packetRepository.save(capture(saved)) } answers { firstArg() }

        service.authorPacket(proposal.id, projectId, authorRequest())

        // The one section with a citation gives the one source; the citationless section adds none.
        assertEquals(listOf("README.md"), saved.captured.sources.map { it.filename })
        assertEquals("https://example.test/README.md", saved.captured.sources[0].sourceUrl)
    }

    @Test
    fun `authorPacket replaces an existing packet wholesale`() = runTest {
        val existing = cachedPacket("fp-old")
        every { proposalRepository.findById(proposal.id) } returns Optional.of(proposal)
        every { packetRepository.findByTaskProposalIdAndProjectId(proposal.id, projectId) } returns existing
        every { packetRepository.save(any()) } answers { firstArg() }

        service.authorPacket(proposal.id, projectId, authorRequest())

        verify(exactly = 1) { packetRepository.delete(existing) }
        verify(exactly = 1) { packetRepository.save(any()) }
    }

    @Test
    fun `authorPacket 404s when the task does not exist`() = runTest {
        every { proposalRepository.findById(proposal.id) } returns Optional.empty()

        val error = assertThrows<ResponseStatusException> {
            service.authorPacket(proposal.id, projectId, authorRequest())
        }
        assertEquals(HttpStatus.NOT_FOUND, error.statusCode)
        verify(exactly = 0) { packetRepository.save(any()) }
    }

    @Test
    fun `authorPacket 400s on no sections`() = runTest {
        every { proposalRepository.findById(proposal.id) } returns Optional.of(proposal)

        val error = assertThrows<ResponseStatusException> {
            service.authorPacket(proposal.id, projectId, AuthorOrientationRequest(sections = emptyList()))
        }
        assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
    }

    @Test
    fun `authorPacket 400s on a blank section body`() = runTest {
        every { proposalRepository.findById(proposal.id) } returns Optional.of(proposal)

        val request = AuthorOrientationRequest(
            sections = listOf(AuthorOrientationSectionRequest(OrientationStep.SET_UP, "Title", "   ")),
        )
        val error = assertThrows<ResponseStatusException> { service.authorPacket(proposal.id, projectId, request) }
        assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
    }

    @Test
    fun `revertToAi deletes the packet so the next read re-assembles`() = runTest {
        every { proposalRepository.findById(proposal.id) } returns Optional.of(proposal)

        service.revertToAi(proposal.id, projectId)

        verify(exactly = 1) { packetRepository.deleteByTaskProposalIdAndProjectId(proposal.id, projectId) }
    }

    @Test
    fun `revertToAi 404s when the task does not exist`() = runTest {
        every { proposalRepository.findById(proposal.id) } returns Optional.empty()

        assertThrows<ResponseStatusException> { service.revertToAi(proposal.id, projectId) }
        verify(exactly = 0) { packetRepository.deleteByTaskProposalIdAndProjectId(any(), any()) }
    }

    @Test
    fun `getForAuthoring returns the cached packet to edit`() = runTest {
        every { proposalRepository.findById(proposal.id) } returns Optional.of(proposal)
        every { packetRepository.findByTaskProposalIdAndProjectId(proposal.id, projectId) } returns
            cachedPacket("fp-old", origin = OrientationOrigin.HUMAN)

        val result = service.getForAuthoring(proposal.id, projectId)

        assertEquals(proposal.id, result.taskId)
        assertEquals(OrientationOrigin.HUMAN, assertNotNull(result.packet).origin)
        // Authoring never assembles: no AI call is made when a PM opens the editor.
        coVerify(exactly = 0) { onboardingAiClient.assembleOrientation(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `getForAuthoring returns a shell to start from blank`() = runTest {
        every { proposalRepository.findById(proposal.id) } returns Optional.of(proposal)
        every { packetRepository.findByTaskProposalIdAndProjectId(proposal.id, projectId) } returns null

        val result = service.getForAuthoring(proposal.id, projectId)

        assertEquals(proposal.title, result.taskTitle)
        assertEquals(proposal.sourceUrl, result.taskUrl)
        assertNull(result.packet)
    }

    @Test
    fun `authorForHire resolves the hire's current task and pins it`() = runTest {
        hasTask()
        val saved = slot<TaskOrientationPacket>()
        every { packetRepository.save(capture(saved)) } answers { firstArg() }

        val result = service.authorForHire(hireId, projectId, authorRequest())

        assertEquals(OrientationOrigin.HUMAN, result.origin)
        assertEquals(proposal.id, saved.captured.taskProposalId)
    }

    @Test
    fun `authorForHire 404s when the hire has no current task`() = runTest {
        isMember()
        every { assignmentRepository.findByHireIdAndProjectId(hireId, projectId) } returns null

        val error = assertThrows<ResponseStatusException> {
            service.authorForHire(hireId, projectId, authorRequest())
        }
        assertEquals(HttpStatus.NOT_FOUND, error.statusCode)
        verify(exactly = 0) { packetRepository.save(any()) }
    }

    @Test
    fun `authorForHire 404s when the hire is not a member`() = runTest {
        every { projectMembershipApi.getProjectMembers(projectId) } returns emptyList()

        assertThrows<ResponseStatusException> { service.authorForHire(hireId, projectId, authorRequest()) }
    }

    @Test
    fun `revertForHire deletes the packet for the hire's current task`() = runTest {
        hasTask()

        service.revertForHire(hireId, projectId)

        verify(exactly = 1) { packetRepository.deleteByTaskProposalIdAndProjectId(proposal.id, projectId) }
    }

    // ========================== Streaming ==========================

    private fun assembledOutcome() = OrientationOutcome(
        status = "assembled",
        packet = OrientationPacketSchema(
            taskTitle = proposal.title,
            summary = "What you need.",
            sections = listOf(
                OrientationSectionSchema(
                    step = "SET_UP",
                    title = "Run it locally",
                    body = "make dev",
                    citations = listOf(CitationRefSchema("README.md", "c1", "https://example.test/README.md")),
                ),
            ),
            sources = listOf(OrientationSourceSchema("README.md", "https://example.test/README.md", "FILE")),
        ),
        provenance = AiProvenanceSchema(corpusFingerprint = "fp-new", model = "test-model"),
    )

    private fun doneEvent(outcome: OrientationOutcome) = AiProgressEvent(
        type = "done",
        operation = "orientation",
        result = json.encodeToJsonElement(outcome),
    )

    @Test
    fun `streams the assembly events through and persists the packet on done`() = runTest {
        hasTask()
        val saved = slot<TaskOrientationPacket>()
        every { packetRepository.save(capture(saved)) } answers { firstArg() }
        every { onboardingAiClient.streamOrientation(any(), any(), any(), any(), any()) } returns
            flowOf(
                AiProgressEvent(type = "stage", operation = "orientation", stage = "retrieving", label = "…"),
                AiProgressEvent(
                    type = "item",
                    operation = "orientation",
                    item = json.parseToJsonElement("""{"step":"SET_UP","title":"Run it locally"}"""),
                    label = "setting up",
                ),
                doneEvent(assembledOutcome()),
            )

        val events = service.streamForHire(hireId, projectId).toList()

        // Every AI event is relayed to the caller, in order.
        assertEquals(listOf("stage", "item", "done"), events.map { it.type })
        // The done persisted the packet through the same path getForHire uses.
        verify(exactly = 1) { packetRepository.save(any()) }
        assertEquals("fp-new", saved.captured.corpusFingerprint)
        assertEquals(OrientationOrigin.AI, saved.captured.origin)
    }

    @Test
    fun `a human-authored packet streams a single done and calls no AI`() = runTest {
        hasTask(cached = cachedPacket("fp-old", origin = OrientationOrigin.HUMAN))

        val events = service.streamForHire(hireId, projectId).toList()

        assertEquals(listOf("done"), events.map { it.type })
        verify(exactly = 0) { onboardingAiClient.streamOrientation(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { packetRepository.save(any()) }
    }

    @Test
    fun `no current task streams a single done and calls no AI`() = runTest {
        isMember()
        every { assignmentRepository.findByHireIdAndProjectId(hireId, projectId) } returns null

        val events = service.streamForHire(hireId, projectId).toList()

        assertEquals(listOf("done"), events.map { it.type })
        verify(exactly = 0) { onboardingAiClient.streamOrientation(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a failing AI stream ends in a synthesised error event`() = runTest {
        hasTask()
        every { onboardingAiClient.streamOrientation(any(), any(), any(), any(), any()) } returns
            flow { throw OnboardingAiException(503, "", "AI is down") }

        val events = service.streamForHire(hireId, projectId).toList()

        assertEquals("error", events.last().type)
        verify(exactly = 0) { packetRepository.save(any()) }
    }
}
