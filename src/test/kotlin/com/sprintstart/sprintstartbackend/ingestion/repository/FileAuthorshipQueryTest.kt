package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileAuthorshipReading
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FileAuthorship
import com.sprintstart.sprintstartbackend.shared.crypto.CryptoConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

/** The lookups of the authorship are database features, so they meet a real one. */
@ActiveProfiles("test")
@DataJpaTest
@Import(CryptoConfiguration::class)
class FileAuthorshipQueryTest {
    @Autowired
    private lateinit var repository: FileAuthorshipRepository

    private fun store(repository: String, path: String, email: String, lines: Int) =
        this.repository.saveAndFlush(FileAuthorship(null, repository, path, email, lines))

    @Test
    fun `reads the authorship of the given repositories and nothing else, in order`() {
        store("github:acme/app", "b.kt", "ben@x.org", 3)
        store("github:acme/app", "a.kt", "ben@x.org", 1)
        store("github:acme/app", "a.kt", "ada@x.org", 9)
        store("github:acme/other", "a.kt", "ada@x.org", 5)

        assertThat(repository.readAll(listOf("github:acme/app"))).containsExactly(
            FileAuthorshipReading("github:acme/app", "a.kt", "ada@x.org", 9),
            FileAuthorshipReading("github:acme/app", "a.kt", "ben@x.org", 1),
            FileAuthorshipReading("github:acme/app", "b.kt", "ben@x.org", 3),
        )
    }

    @Test
    fun `forgets every author of one file and no other file`() {
        store("github:acme/app", "a.kt", "ada@x.org", 9)
        store("github:acme/app", "a.kt", "ben@x.org", 1)
        store("github:acme/app", "b.kt", "ben@x.org", 3)

        val removed = repository.deleteByFile("github:acme/app", "a.kt")

        assertThat(removed).isEqualTo(2)
        assertThat(repository.readAll(listOf("github:acme/app")).map { it.path }).containsExactly("b.kt")
    }
}
