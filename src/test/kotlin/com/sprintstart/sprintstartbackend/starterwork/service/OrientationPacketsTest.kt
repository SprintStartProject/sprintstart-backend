package com.sprintstart.sprintstartbackend.starterwork.service

import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.starterwork.external.enums.OrientationOrigin
import com.sprintstart.sprintstartbackend.starterwork.external.enums.OrientationStep
import com.sprintstart.sprintstartbackend.starterwork.external.model.AiProvenanceSchema
import com.sprintstart.sprintstartbackend.starterwork.external.model.CitationRefSchema
import com.sprintstart.sprintstartbackend.starterwork.external.model.OrientationOutcome
import com.sprintstart.sprintstartbackend.starterwork.external.model.OrientationPacketSchema
import com.sprintstart.sprintstartbackend.starterwork.external.model.OrientationSectionSchema
import com.sprintstart.sprintstartbackend.starterwork.external.model.OrientationSourceSchema
import com.sprintstart.sprintstartbackend.starterwork.model.entity.StarterWorkTaskProposal
import com.sprintstart.sprintstartbackend.starterwork.model.request.orientation.AuthorOrientationCitationRequest
import com.sprintstart.sprintstartbackend.starterwork.model.request.orientation.AuthorOrientationRequest
import com.sprintstart.sprintstartbackend.starterwork.model.request.orientation.AuthorOrientationSectionRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

class OrientationPacketsTest {
    private val at = Instant.parse("2026-10-01T10:00:00Z")
    private val project = ProjectId(UUID.randomUUID())
    private val proposal = StarterWorkTaskProposal(
        sourceId = "github:org/repo:ISSUE:7",
        title = "Fix the header",
        summary = "s",
    )

    private fun section(title: String = "T", body: String = "B", vararg cites: AuthorOrientationCitationRequest) =
        AuthorOrientationSectionRequest(OrientationStep.SET_UP, title, body, cites.toList())

    @Test
    fun `authoring needs a section with a title and a body`() {
        listOf(
            AuthorOrientationRequest(sections = emptyList()),
            AuthorOrientationRequest(sections = listOf(section(title = " "))),
            AuthorOrientationRequest(sections = listOf(section(body = ""))),
        ).forEach { request ->
            val e = assertThrows<ResponseStatusException> { requireValidAuthoring(request) }
            assertThat(e.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        requireValidAuthoring(AuthorOrientationRequest(sections = listOf(section())))
    }

    @Test
    fun `a human packet is trimmed, pinned human and carries no fingerprint`() {
        val request = AuthorOrientationRequest(
            summary = "  sum  ",
            sections = listOf(
                section(" Title ", " Body ", AuthorOrientationCitationRequest(" a.md ", " https://a ")),
                section("Second", "Body 2", AuthorOrientationCitationRequest("a.md", "https://a")),
            ),
        )

        val packet = humanOrientationPacket(proposal, project, request, at)

        assertThat(packet.origin).isEqualTo(OrientationOrigin.HUMAN)
        assertThat(packet.taskProposalId).isEqualTo(proposal.id)
        assertThat(packet.projectId).isEqualTo(project.value)
        assertThat(packet.taskTitle).isEqualTo("Fix the header")
        assertThat(packet.summary).isEqualTo("sum")
        assertThat(packet.corpusFingerprint).isNull()
        assertThat(packet.model).isNull()
        assertThat(packet.assembledAt).isEqualTo(at)
        assertThat(packet.sections.map { it.title }).containsExactly("Title", "Second")
        assertThat(packet.sections.map { it.position }).containsExactly(0, 1)
        val first = packet.sections.first()
        assertThat(first.body).isEqualTo("Body")
        val citation = first.citations.single()
        assertThat(citation.filename).isEqualTo("a.md")
        assertThat(citation.sourceUrl).isEqualTo("https://a")
        assertThat(citation.chunkId).isEmpty()
        // The same link cited twice is one source.
        assertThat(packet.sources.map { it.filename }).containsExactly("a.md")
        assertThat(packet.sources.single().sourceUrl).isEqualTo("https://a")
    }

    @Test
    fun `a blank summary or link is stored as absent`() {
        val request = AuthorOrientationRequest(
            summary = "  ",
            sections = listOf(section(cites = arrayOf(AuthorOrientationCitationRequest("a.md", " ")))),
        )

        val packet = humanOrientationPacket(proposal, project, request, at)

        assertThat(packet.summary).isNull()
        val section = packet.sections.single()
        assertThat(section.citations.single().sourceUrl).isNull()
        assertThat(packet.sources.single().sourceUrl).isNull()
    }

    @Test
    fun `an assembled packet keeps provenance, drops unknown steps and numbers what it keeps`() {
        val context = TaskContext(
            proposalId = proposal.id,
            projectId = project.value,
            title = "Context title",
            body = "",
            labels = emptyList(),
            taskUrl = null,
            cachedFingerprint = null,
            cachedPacket = null,
        )
        val schema = OrientationPacketSchema(
            taskTitle = " ",
            summary = "",
            sections = listOf(
                OrientationSectionSchema("nonsense", "Dropped", "x"),
                OrientationSectionSchema(
                    " set_up ",
                    "Kept",
                    "Body",
                    listOf(CitationRefSchema("f.md", "chunk-1", "https://f")),
                ),
            ),
            sources = listOf(OrientationSourceSchema("f.md", "https://f", "DOC")),
        )
        val outcome = OrientationOutcome(
            status = "assembled",
            packet = schema,
            provenance = AiProvenanceSchema(corpusFingerprint = "fp", model = "m"),
        )

        val packet = aiOrientationPacket(context, outcome, schema, at)

        assertThat(packet.origin).isEqualTo(OrientationOrigin.AI)
        assertThat(packet.taskTitle).isEqualTo("Context title")
        assertThat(packet.summary).isNull()
        assertThat(packet.corpusFingerprint).isEqualTo("fp")
        assertThat(packet.model).isEqualTo("m")
        assertThat(packet.assembledAt).isEqualTo(at)
        val kept = packet.sections.single()
        assertThat(kept.title).isEqualTo("Kept")
        assertThat(kept.step).isEqualTo(OrientationStep.SET_UP)
        assertThat(kept.position).isEqualTo(0)
        assertThat(kept.citations.single().chunkId).isEqualTo("chunk-1")
        assertThat(packet.sources.single().artifactType).isEqualTo("DOC")
    }

    @Test
    fun `the cached packet is served with no reason, and its absence carries the reason`() {
        val context = TaskContext(proposal.id, project.value, "T", "", emptyList(), null, null, null)

        assertThat(context.cachedOrEmpty("why").reason).isEqualTo("why")
        assertThat(context.cachedOrEmpty("why").taskId).isEqualTo(proposal.id)
    }
}
