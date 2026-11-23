package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.model.entity.FileAuthorship
import com.sprintstart.sprintstartbackend.ingestion.repository.FileAuthorshipRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FileAuthorshipServiceTest {
    private val repository = mockk<FileAuthorshipRepository>(relaxed = true)
    private val service = FileAuthorshipService(repository)

    @Test
    fun `what was stored of a file is replaced by what blame said`() {
        val saved = slot<Iterable<FileAuthorship>>()
        every { repository.saveAll(capture(saved)) } answers { saved.captured.toList() }

        service.record("github:acme/app", "a.kt", mapOf("ada@x.org" to 7, "ben@x.org" to 3))

        verify { repository.deleteByFile("github:acme/app", "a.kt") }
        assertThat(saved.captured.map { Triple(it.repository, it.authorEmail, it.lines) })
            .containsExactlyInAnyOrder(
                Triple("github:acme/app", "ada@x.org", 7),
                Triple("github:acme/app", "ben@x.org", 3),
            )
    }

    @Test
    fun `an empty answer keeps what was stored`() {
        service.record("github:acme/app", "a.kt", emptyMap())

        verify(exactly = 0) { repository.deleteByFile(any(), any()) }
        verify(exactly = 0) { repository.saveAll(any<Iterable<FileAuthorship>>()) }
    }

    @Test
    fun `a file that is gone is forgotten`() {
        service.forget("github:acme/app", "a.kt")

        verify { repository.deleteByFile("github:acme/app", "a.kt") }
    }
}
