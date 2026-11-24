package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileComplexityReading
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FileComplexity
import com.sprintstart.sprintstartbackend.shared.crypto.CryptoConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Instant

/** The lookups of the measured complexity are database features, so they meet a real one. */
@ActiveProfiles("test")
@DataJpaTest
@Import(CryptoConfiguration::class)
class FileComplexityQueryTest {
    @Autowired
    private lateinit var repository: FileComplexityRepository

    private val at = Instant.parse("2026-11-23T10:00:00Z")

    private fun store(repository: String, path: String, indentation: Double = 1.0, lines: Int = 2) =
        this.repository.saveAndFlush(FileComplexity(null, repository, path, indentation, lines, at))

    @Test
    fun `reads every file of the given repositories and nothing else, in order`() {
        store("github:acme/app", "b.kt", 3.0, 10)
        store("github:acme/app", "a.kt", 1.5, 4)
        store("github:acme/other", "c.kt")

        val read = repository.readAll(listOf("github:acme/app"))

        assertThat(read).containsExactly(
            FileComplexityReading("github:acme/app", "a.kt", 1.5, 4),
            FileComplexityReading("github:acme/app", "b.kt", 3.0, 10),
        )
    }

    @Test
    fun `finds the measurement of one file by repository and path`() {
        store("github:acme/app", "a.kt", 7.0, 3)
        store("github:acme/other", "a.kt")

        val found = repository.findByRepositoryAndPath("github:acme/app", "a.kt")

        assertThat(found?.indentation).isEqualTo(7.0)
        assertThat(repository.findByRepositoryAndPath("github:acme/app", "missing.kt")).isNull()
    }

    @Test
    fun `forgets one file only`() {
        store("github:acme/app", "a.kt")
        store("github:acme/app", "b.kt")

        val removed = repository.deleteByFile("github:acme/app", "a.kt")

        assertThat(removed).isEqualTo(1)
        assertThat(repository.readAll(listOf("github:acme/app")).map { it.path }).containsExactly("b.kt")
    }
}
