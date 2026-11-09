package com.sprintstart.sprintstartbackend.buddy.service

import com.sprintstart.sprintstartbackend.assistant.external.ArtifactLookupService
import com.sprintstart.sprintstartbackend.assistant.external.BuddyCitationDto
import com.sprintstart.sprintstartbackend.assistant.external.ResolvedArtifact
import com.sprintstart.sprintstartbackend.assistant.external.ResolvedBuddyCitation
import com.sprintstart.sprintstartbackend.buddy.external.enums.BuddyMessageRole
import com.sprintstart.sprintstartbackend.buddy.model.entity.BuddyCitation
import com.sprintstart.sprintstartbackend.buddy.model.entity.BuddyMessage
import com.sprintstart.sprintstartbackend.buddy.model.entity.BuddySession
import com.sprintstart.sprintstartbackend.buddy.repository.BuddyCitationRepository
import com.sprintstart.sprintstartbackend.buddy.repository.BuddyMessageRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class BuddyReplyStoreTest {
    private val buddyMessageRepository = mockk<BuddyMessageRepository>()
    private val buddyCitationRepository = mockk<BuddyCitationRepository>()
    private val artifactLookupService = mockk<ArtifactLookupService>()
    private val store = BuddyReplyStore(buddyMessageRepository, buddyCitationRepository, artifactLookupService)
    private val session = BuddySession(userId = UUID.randomUUID())

    @Test
    fun `citations that resolve keep their location and the rest are dropped`() {
        val known = UUID.randomUUID()
        val gone = UUID.randomUUID()
        every { artifactLookupService.resolve(known) } returns ResolvedArtifact("guide.md", "https://src")
        every { artifactLookupService.resolve(gone) } returns null

        val resolved = store.resolveCitations(
            listOf(
                BuddyCitationDto(artifactId = known.toString(), startLine = 4, startPage = 2),
                BuddyCitationDto(artifactId = gone.toString()),
                BuddyCitationDto(artifactId = "not-a-uuid"),
                BuddyCitationDto(artifactId = null),
            ),
        )

        assertThat(resolved).containsExactly(
            ResolvedBuddyCitation(known, "guide.md", "https://src", startLine = 4, startPage = 2),
        )
    }

    @Test
    fun `a finished reply is stored with its citations attached to the message`() {
        val artifactId = UUID.randomUUID()
        val saved = slot<BuddyMessage>()
        val citations = slot<Iterable<BuddyCitation>>()
        every { buddyMessageRepository.save(capture(saved)) } answers { saved.captured }
        every { buddyCitationRepository.saveAll(capture(citations)) } returns emptyList()

        store.saveReply(session, "the answer", listOf(ResolvedBuddyCitation(artifactId, "a.md", null, 7, null)))

        assertThat(saved.captured.role).isEqualTo(BuddyMessageRole.ASSISTANT)
        assertThat(saved.captured.content).isEqualTo("the answer")
        assertThat(saved.captured.isIncomplete).isFalse()
        val citation = citations.captured.single()
        assertThat(citation.artifactId).isEqualTo(artifactId)
        assertThat(citation.filename).isEqualTo("a.md")
        assertThat(citation.startLine).isEqualTo(7)
        assertThat(citation.message).isSameAs(saved.captured)
    }

    @Test
    fun `an interrupted reply is kept as incomplete`() {
        val saved = slot<BuddyMessage>()
        every { buddyMessageRepository.save(capture(saved)) } answers { saved.captured }

        store.saveIncompleteReply(session, "half an ans")

        assertThat(saved.captured.content).isEqualTo("half an ans")
        assertThat(saved.captured.isIncomplete).isTrue()
    }

    @Test
    fun `an interrupted reply that showed nothing stores nothing`() {
        store.saveIncompleteReply(session, "  \n")

        verify(exactly = 0) { buddyMessageRepository.save(any()) }
    }
}
