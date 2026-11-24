package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.model.entity.FileComplexity
import com.sprintstart.sprintstartbackend.ingestion.repository.FileComplexityRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class FileComplexityServiceTest {
    private val now = Instant.parse("2026-11-23T10:00:00Z")
    private val repository = mockk<FileComplexityRepository>(relaxed = true)
    private val service = FileComplexityService(repository, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `a file read for the first time is stored with what was measured`() {
        every { repository.findByRepositoryAndPath("github:acme/app", "a.kt") } returns null
        val saved = slot<FileComplexity>()
        every { repository.save(capture(saved)) } answers { saved.captured }

        service.measure("github:acme/app", "a.kt", "a\n    b\n")

        assertThat(saved.captured.repository).isEqualTo("github:acme/app")
        assertThat(saved.captured.path).isEqualTo("a.kt")
        assertThat(saved.captured.indentation).isEqualTo(1.0)
        assertThat(saved.captured.lines).isEqualTo(2)
        assertThat(saved.captured.measuredAt).isEqualTo(now)
    }

    @Test
    fun `a file read again replaces its earlier measurement`() {
        val earlier = FileComplexity(null, "github:acme/app", "a.kt", 9.0, 40, now.minusSeconds(3600))
        every { repository.findByRepositoryAndPath("github:acme/app", "a.kt") } returns earlier

        service.measure("github:acme/app", "a.kt", "a\n        b\n")

        assertThat(earlier.indentation).isEqualTo(2.0)
        assertThat(earlier.lines).isEqualTo(2)
        assertThat(earlier.measuredAt).isEqualTo(now)
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `a file that is gone is forgotten`() {
        service.forget("github:acme/app", "a.kt")

        verify { repository.deleteByFile("github:acme/app", "a.kt") }
    }
}
